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
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class SealRecordService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main.immediate + job)

    private lateinit var engine: SealEngine
    private lateinit var recordingFocus: RecordingAudioFocus
    private var exclusiveFocusHeld = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        recordingFocus = RecordingAudioFocus(this)
        val app = application as SealRecApp
        scope.launch(Dispatchers.IO) {
            val settings = app.settingsRepository.settings.first()
            val config = app.settingsRepository.audioConfig(settings.quality)
            engine = SealEngine(
                scope = scope,
                keystore = app.keystore,
                audioConfig = config,
            )
            app.bindEngine(engine)
            if (!settings.allowNotificationSoundsWhileRecording) {
                exclusiveFocusHeld = recordingFocus.requestExclusive()
            }
            engine.elapsedMs.collectLatest { ms ->
                if (engine.state.value == SealEngineState.Recording ||
                    engine.state.value == SealEngineState.Paused
                ) {
                    updateNotification(ms, engine.state.value)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startAsForeground()
                scope.launch(Dispatchers.IO) {
                    // Ensure engine is ready
                    while (!::engine.isInitialized) {
                        kotlinx.coroutines.delay(20)
                    }
                    val repo = (application as SealRecApp).repository
                    val raw = repo.newRawFile()
                    engine.start(raw)
                    updateNotification(0L, SealEngineState.Recording)
                }
            }
            ACTION_PAUSE -> if (::engine.isInitialized) engine.pause()
            ACTION_RESUME -> {
                if (::engine.isInitialized) {
                    val raw = engine.currentRawFile
                    if (raw != null) engine.start(raw)
                }
            }
            ACTION_STOP -> {
                scope.launch(Dispatchers.IO) {
                    while (!::engine.isInitialized) {
                        kotlinx.coroutines.delay(20)
                    }
                    val app = application as SealRecApp
                    try {
                        val wav = app.repository.newWavFile()
                        val elapsed = engine.elapsedMs.value
                        val payload = engine.stopAndSeal(wav)
                        app.repository.registerSealedFile(
                            wav = wav,
                            durationMs = elapsed,
                            keyFingerprintHex = Fingerprint.toHex(payload.keyFingerprint),
                            deviceTimeUtcMs = payload.deviceTimeUtcMs,
                        )
                        app.onRecordingFinished(wav.absolutePath)
                    } catch (_: EmptyRecordingException) {
                        app.emitMessage(getString(R.string.record_too_short))
                    } catch (e: Exception) {
                        app.emitMessage(e.message ?: getString(R.string.record_failed))
                    } finally {
                        releaseRecordingFocus()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
            ACTION_CANCEL -> {
                if (::engine.isInitialized) engine.cancel()
                releaseRecordingFocus()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (::engine.isInitialized) {
            (application as SealRecApp).unbindEngine(engine)
        }
        releaseRecordingFocus()
        scope.cancel()
        super.onDestroy()
    }

    private fun releaseRecordingFocus() {
        if (exclusiveFocusHeld) {
            recordingFocus.abandon()
            exclusiveFocusHeld = false
        }
    }

    private fun startAsForeground() {
        val notification = buildNotification(0L, SealEngineState.Recording)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
                getString(R.string.action_resume),
                servicePending(ACTION_RESUME, 1),
            )
        } else {
            NotificationCompat.Action(
                0,
                getString(R.string.action_pause),
                servicePending(ACTION_PAUSE, 2),
            )
        }
        val stopAction = NotificationCompat.Action(
            0,
            getString(R.string.action_stop),
            servicePending(ACTION_STOP, 3),
        )
        val title = when (state) {
            SealEngineState.Paused -> getString(R.string.notif_paused)
            else -> getString(R.string.notif_recording)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(formatDuration(elapsedMs))
            .setSmallIcon(R.drawable.ic_notif_mic)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(pauseResumeAction)
            .addAction(stopAction)
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
                getString(R.string.notif_channel),
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
