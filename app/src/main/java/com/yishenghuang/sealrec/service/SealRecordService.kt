package com.yishenghuang.sealrec.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.yishenghuang.sealrec.MainActivity
import com.yishenghuang.sealrec.R
import com.yishenghuang.sealrec.SealRecApp
import com.yishenghuang.sealrec.core.audio.RecordingAudioFocus
import com.yishenghuang.sealrec.core.pipeline.EmptyRecordingException
import com.yishenghuang.sealrec.core.pipeline.SealEngine
import com.yishenghuang.sealrec.core.pipeline.SealEngineState
import com.yishenghuang.sealrec.core.wav.Fingerprint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class SealRecordService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main.immediate + job)

    private lateinit var engine: SealEngine
    private lateinit var recordingFocus: RecordingAudioFocus
    private var exclusiveFocusHeld = false

    private val commands = kotlinx.coroutines.channels.Channel<String>(32)
    private var exclusive = false
    private var closing = false
    private val cpuLock by lazy {
        getSystemService(android.os.PowerManager::class.java).newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK, "SealRec:Capture").apply { setReferenceCounted(false) }
    }
    private fun holdCpu() {
        if (!cpuLock.isHeld) cpuLock.acquire(TimeUnit.HOURS.toMillis(24))
    }
    private fun releaseCpu() { if (cpuLock.isHeld) cpuLock.release() }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        recordingFocus = RecordingAudioFocus(this) {
            commands.trySend(ACTION_PAUSE)
            (application as SealRecApp).emitMessage((application as SealRecApp).localizedString(R.string.record_interrupted))
        }
        scope.launch {
            for (action in commands) {
                if (closing) continue
                val app = application as SealRecApp
                try {
                    when (action) {
                        ACTION_START -> {
                            if (::engine.isInitialized) continue
                            val settings = app.settingsRepository.settings.first()
                            exclusive = !settings.allowNotificationSoundsWhileRecording
                            engine = SealEngine(scope, app.keystore, app.settingsRepository.audioConfig(settings.quality))
                            app.bindEngine(engine)
                            check(recordingFocus.request(exclusive)) { "Audio focus denied" }
                            exclusiveFocusHeld = true
                            checkNoCall()
                            holdCpu()
                            engine.start(app.repository.newRawFile())
                            scope.launch {
                                kotlinx.coroutines.flow.combine(engine.elapsedMs, engine.state) { ms, state -> (ms / 1000) to state }
                                    .distinctUntilChanged()
                                    .collect { (seconds, state) ->
                                        if (!closing) updateNotification(seconds * 1000, state)
                                        val audio = getSystemService(android.media.AudioManager::class.java)
                                        if (state == SealEngineState.Recording && (audio.mode == android.media.AudioManager.MODE_IN_CALL ||
                                                audio.mode == android.media.AudioManager.MODE_IN_COMMUNICATION)) {
                                            commands.trySend(ACTION_PAUSE)
                                            app.emitMessage((application as SealRecApp).localizedString(R.string.record_interrupted))
                                        }
                                    }
                            }
                            scope.launch {
                                engine.error.collect { failed ->
                                    if (failed) { releaseRecordingFocus(); releaseCpu(); app.emitMessage((application as SealRecApp).localizedString(R.string.record_interrupted)) }
                                }
                            }
                        }
                        ACTION_PAUSE -> if (::engine.isInitialized) {
                            engine.pause()
                            releaseRecordingFocus()
                            releaseCpu()
                        }
                        ACTION_RESUME -> if (::engine.isInitialized && engine.state.value == SealEngineState.Paused) {
                            check(recordingFocus.request(exclusive)) { "Audio focus denied" }
                            exclusiveFocusHeld = true
                            checkNoCall()
                            holdCpu()
                            engine.currentRawFile?.let { engine.start(it) }
                        }
                        ACTION_STOP -> {
                            if (::engine.isInitialized && engine.state.value != SealEngineState.Idle) {
                                holdCpu()
                                val wav = app.repository.newWavFile()
                                val payload = engine.stopAndSeal(wav)
                                app.repository.registerSealedFile(wav,
                                    com.yishenghuang.sealrec.core.wav.WavIO.inspect(wav).durationMs.toLong(),
                                    Fingerprint.toHex(payload.keyFingerprint), payload.deviceTimeUtcMs)
                                engine.acknowledgeSaved()
                                app.onRecordingFinished(wav.absolutePath)
                            }
                            finishRecording()
                        }
                        ACTION_CANCEL -> {
                            if (::engine.isInitialized) engine.close(discard = true)
                            finishRecording()
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: EmptyRecordingException) {
                    app.emitMessage((application as SealRecApp).localizedString(R.string.record_too_short))
                    finishRecording()
                } catch (_: Exception) {
                    app.emitMessage((application as SealRecApp).localizedString(R.string.record_failed))
                    if (action != ACTION_RESUME) finishRecording() else { releaseRecordingFocus(); releaseCpu() }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_START && !closing) {
            (application as SealRecApp).recordingSessionActive.value = true
            try { startAsForeground() } catch (_: Exception) {
                (application as SealRecApp).recordingSessionActive.value = false
                (application as SealRecApp).emitMessage((application as SealRecApp).localizedString(R.string.record_failed))
                stopSelf()
                return START_NOT_STICKY
            }
        }
        if (action != null && !commands.trySend(action).isSuccess) {
            (application as SealRecApp).emitMessage((application as SealRecApp).localizedString(R.string.record_failed))
        }
        if (action == null) stopSelf()
        // Never restart microphone capture without an explicit user action.
        return START_NOT_STICKY
    }

    private suspend fun finishRecording() {
        closing = true
        try { if (::engine.isInitialized) engine.close() } finally {
            releaseCpu()
            releaseRecordingFocus()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        commands.close()
        releaseCpu()
        releaseRecordingFocus()
        if (::engine.isInitialized) {
            (application as SealRecApp).unbindEngine(engine)
            // Cleanup must survive cancellation of the service scope.
            scope.launch(kotlinx.coroutines.NonCancellable) { runCatching { engine.close() } }
        }
        (application as SealRecApp).recordingSessionActive.value = false
        scope.cancel()
        super.onDestroy()
    }

    private fun releaseRecordingFocus() {
        if (::recordingFocus.isInitialized) recordingFocus.abandon()
        exclusiveFocusHeld = false
    }

    private fun checkNoCall() {
        val mode = getSystemService(android.media.AudioManager::class.java).mode
        check(mode != android.media.AudioManager.MODE_IN_CALL && mode != android.media.AudioManager.MODE_IN_COMMUNICATION)
    }
    private fun startAsForeground() {
        val notification = buildNotification(0L, SealEngineState.Recording)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(elapsedMs: Long, state: SealEngineState) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(elapsedMs, state))
    }

    private fun buildNotification(elapsedMs: Long, state: SealEngineState): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val pauseResumeAction = if (state == SealEngineState.Paused) {
            NotificationCompat.Action(
                0,
                (application as SealRecApp).localizedString(R.string.action_resume),
                servicePending(ACTION_RESUME, 1),
            )
        } else {
            NotificationCompat.Action(
                0,
                (application as SealRecApp).localizedString(R.string.action_pause),
                servicePending(ACTION_PAUSE, 2),
            )
        }
        val stopAction = NotificationCompat.Action(
            0,
            (application as SealRecApp).localizedString(R.string.action_stop),
            servicePending(ACTION_STOP, 3),
        )
        val title = when (state) {
            SealEngineState.Paused -> (application as SealRecApp).localizedString(R.string.notif_paused)
            SealEngineState.Finalizing -> (application as SealRecApp).localizedString(R.string.state_finalizing)
            else -> (application as SealRecApp).localizedString(R.string.notif_recording)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(formatDuration(elapsedMs))
            .setSmallIcon(R.drawable.ic_notif_mic)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                if (state == SealEngineState.Recording || state == SealEngineState.Paused) {
                    addAction(pauseResumeAction)
                    addAction(stopAction)
                }
            }
            .build()
    }

    private fun servicePending(action: String, req: Int): PendingIntent {
        val i = Intent(this, SealRecordService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            req,
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                (application as SealRecApp).localizedString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "sealrec_recording"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.yishenghuang.sealrec.START"
        const val ACTION_PAUSE = "com.yishenghuang.sealrec.PAUSE"
        const val ACTION_RESUME = "com.yishenghuang.sealrec.RESUME"
        const val ACTION_STOP = "com.yishenghuang.sealrec.STOP"
        const val ACTION_CANCEL = "com.yishenghuang.sealrec.CANCEL"

        fun start(context: Context) {
            val i = Intent(context, SealRecordService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, i)
        }

        fun pause(context: Context) {
            context.startService(Intent(context, SealRecordService::class.java).setAction(ACTION_PAUSE))
        }

        fun resume(context: Context) {
            context.startService(Intent(context, SealRecordService::class.java).setAction(ACTION_RESUME))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, SealRecordService::class.java).setAction(ACTION_STOP))
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, SealRecordService::class.java).setAction(ACTION_CANCEL))
        }

        fun formatDuration(ms: Long): String {
            val totalSec = TimeUnit.MILLISECONDS.toSeconds(ms)
            val m = totalSec / 60
            val s = totalSec % 60
            return "%02d:%02d".format(m, s)
        }
    }
}
