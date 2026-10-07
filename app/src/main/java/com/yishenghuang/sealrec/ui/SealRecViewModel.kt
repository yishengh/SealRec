package com.yishenghuang.sealrec.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yishenghuang.sealrec.R
import com.yishenghuang.sealrec.SealRecApp
import com.yishenghuang.sealrec.core.audio.PlaybackState
import com.yishenghuang.sealrec.core.audio.RecordingPlayer
import com.yishenghuang.sealrec.core.pipeline.SealEngine
import com.yishenghuang.sealrec.core.pipeline.SealEngineState
import com.yishenghuang.sealrec.core.verify.IntegrityReport
import com.yishenghuang.sealrec.data.AppLanguage
import com.yishenghuang.sealrec.data.AudioQuality
import com.yishenghuang.sealrec.data.NightModeOption
import com.yishenghuang.sealrec.data.RecordingEntity
import com.yishenghuang.sealrec.data.UserSettings
import com.yishenghuang.sealrec.service.SealRecordService
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class RecordUiState(
    val engineState: SealEngineState = SealEngineState.Idle,
    val elapsedMs: Long = 0L,
    val rmsLevel: Float = 0f,
    val incompleteRaw: File? = null,
    val message: String? = null,
    val recovering: Boolean = false,
)

class SealRecViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SealRecApp
    private val repo = app.repository
    private val settingsRepo = app.settingsRepository
    private val player = RecordingPlayer(app, viewModelScope)

    val recordings: StateFlow<List<RecordingEntity>> =
        repo.observeRecordings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trash: StateFlow<List<RecordingEntity>> =
        repo.observeTrash().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<UserSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    private val _ui = MutableStateFlow(RecordUiState())
    val ui: StateFlow<RecordUiState> = _ui.asStateFlow()

    private val _report = MutableStateFlow<IntegrityReport?>(null)
    val report: StateFlow<IntegrityReport?> = _report.asStateFlow()

    private val waveform = MutableStateFlow(List(48) { 0.05f })
    val waveformBars: StateFlow<List<Float>> = waveform.asStateFlow()

    val playback: StateFlow<PlaybackState> = player.state

    val recordingBusy: Boolean
        get() = app.recordingSessionActive.value || when (_ui.value.engineState) {
            SealEngineState.Recording,
            SealEngineState.Paused,
            SealEngineState.Finalizing,
            -> true
            SealEngineState.Idle -> false
        }

    private var engineObserveJob: Job? = null
    private var recoveryBusy = false
    private var verificationJob: Job? = null
    private val fileActions = Mutex()

    init {
        refreshIncomplete()
        viewModelScope.launch {
            app.recordingSessionActive.collect { active ->
                if (!active) { _ui.value = _ui.value.copy(engineState = SealEngineState.Idle); refreshIncomplete() }
                else if (_ui.value.engineState == SealEngineState.Idle) _ui.value = _ui.value.copy(engineState = SealEngineState.Finalizing)
            }
        }
        viewModelScope.launch {
            app.engine.collectLatest { engine ->
                engineObserveJob?.cancel()
                if (engine == null) {
                    _ui.value = _ui.value.copy(engineState = if (app.recordingSessionActive.value) SealEngineState.Finalizing else SealEngineState.Idle, elapsedMs = 0L)
                    refreshIncomplete()
                    return@collectLatest
                }
                engineObserveJob = viewModelScope.launch {
                    launch { engine.state.collect { _ui.value = _ui.value.copy(engineState = if (it == SealEngineState.Idle && app.recordingSessionActive.value) SealEngineState.Finalizing else it) } }
                    launch { engine.elapsedMs.collect { _ui.value = _ui.value.copy(elapsedMs = it) } }
                    launch { observeRms(engine) }
                }
            }
        }
        viewModelScope.launch {
            app.recordingFinished.collect {
                _ui.value = _ui.value.copy(message = app.localizedString(R.string.msg_sealed))
                refreshIncomplete()
            }
        }
        viewModelScope.launch {
            app.userMessages.collect { msg ->
                _ui.value = _ui.value.copy(message = msg)
            }
        }
        viewModelScope.launch {
            player.state.collect { playback ->
                playback.error?.let { err ->
                    _ui.value = _ui.value.copy(
                        message = app.localizedString(R.string.operation_failed),
                    )
                    player.clearError()
                }
            }
        }
    }

    private suspend fun observeRms(engine: SealEngine) {
        engine.rms.collect { level ->
            _ui.value = _ui.value.copy(rmsLevel = level)
            val bars = waveform.value.toMutableList()
            bars.removeAt(0)
            bars.add(level.coerceIn(0.02f, 1f))
            waveform.value = bars
        }
    }

    fun refreshIncomplete() {
        val incomplete = repo.listIncompleteRaws().firstOrNull { it != app.engine.value?.currentRawFile }
        _ui.value = _ui.value.copy(incompleteRaw = incomplete)
    }

    fun startRecording(context: android.content.Context) {
        if (recordingBusy || recoveryBusy) return
        app.recordingSessionActive.value = true
        _ui.value = _ui.value.copy(engineState = SealEngineState.Finalizing, incompleteRaw = null)
        action {
            try { player.stopAndWait(); SealRecordService.start(context) } catch (e: Exception) {
                app.recordingSessionActive.value = false
                _ui.value = _ui.value.copy(engineState = SealEngineState.Idle)
                throw e
            }
        }
    }

    fun pauseRecording(context: android.content.Context) {
        SealRecordService.pause(context)
    }

    fun resumeRecording(context: android.content.Context) {
        SealRecordService.resume(context)
    }

    fun stopRecording(context: android.content.Context) {
        SealRecordService.stop(context)
    }

    fun repairIncomplete() {
        if (recordingBusy || recoveryBusy) return
        val raw = _ui.value.incompleteRaw ?: return
        recoveryBusy = true
        _ui.value = _ui.value.copy(recovering = true)
        mutatingAction {
            try {
                repo.repairIncomplete(raw)
                _ui.value = _ui.value.copy(message = app.localizedString(R.string.msg_repaired))
            } finally { recoveryBusy = false; _ui.value = _ui.value.copy(recovering = false); refreshIncomplete() }
        }
    }

    fun discardIncomplete() {
        if (recordingBusy || recoveryBusy) return
        val raw = _ui.value.incompleteRaw ?: return
        mutatingAction {
            repo.discardIncomplete(raw)
            refreshIncomplete()
        }
    }

    fun verifyRecording(id: Long) {
        verificationJob?.cancel()
        verificationJob = action {
            _report.value = repo.verifyRecording(id)
        }
    }

    fun verifyUri(uri: Uri) {
        verificationJob?.cancel()
        _report.value = null
        verificationJob = action {
            _report.value = withContext(Dispatchers.IO) {
                val tmp = File.createTempFile("verify_", ".wav", app.cacheDir)
                try {
                    val displayName = app.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    } ?: "WAV"
                    requireNotNull(app.contentResolver.openInputStream(uri)).use { input ->
                        tmp.outputStream().use { out ->
                            val buffer = ByteArray(64 * 1024)
                            var copied = 0L
                            while (true) {
                                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                val n = input.read(buffer)
                                if (n < 0) break
                                copied += n
                                check(copied <= com.yishenghuang.sealrec.core.wav.WavIO.MAX_PCM_BYTES + 200_000 &&
                                    app.cacheDir.usableSpace > 8 * 1024 * 1024) { "File too large" }
                                out.write(buffer, 0, n)
                            }
                        }
                    }
                    repo.verifyFile(tmp).copy(fileName = displayName, filePath = "")
                } finally { tmp.delete() }
            }
        }
    }

    fun clearReport() {
        verificationJob?.cancel()
        _report.value = null
    }

    fun togglePlayback(id: Long) {
        if (recordingBusy) {
            _ui.value = _ui.value.copy(message = app.localizedString(R.string.play_busy_recording))
            return
        }
        val entity = recordings.value.find { it.id == id } ?: return
        player.toggle(id, File(entity.filePath))
    }

    fun seekPlayback(positionMs: Int) {
        player.seekTo(positionMs)
    }

    fun stopPlayback() {
        player.stop()
    }

    fun renameRecording(id: Long, newName: String) {
        mutatingAction {
            val playingId = player.state.value.recordingId
            if (playingId == id) {
                player.stopAndWait()
            }
            val ok = repo.renameRecording(id, newName)
            _ui.value = _ui.value.copy(
                message = app.localizedString(
                    if (ok) R.string.rename_ok else R.string.rename_fail,
                ),
            )
        }
    }

    fun deleteRecording(id: Long) {
        mutatingAction {
            if (player.state.value.recordingId == id) player.stopAndWait()
            repo.moveToTrash(id)
        }
    }

    fun restoreFromTrash(id: Long) {
        mutatingAction { repo.restoreFromTrash(id) }
    }

    fun purgeFromTrash(id: Long) {
        mutatingAction { repo.purgeFromTrash(id) }
    }

    fun emptyTrash() {
        mutatingAction { repo.purgeAllTrash() }
    }

    fun exportRecording(id: Long) {
        mutatingAction {
            val ok = repo.exportVerificationPackage(id)
            _ui.value = _ui.value.copy(
                message = if (ok) app.localizedString(R.string.export_verify_ok)
                else app.localizedString(R.string.export_fail),
            )
        }
    }

    fun shareRecording(id: Long, context: android.content.Context) = mutatingAction {
        val uris = repo.shareUris(id)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/octet-stream"
            putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(app.contentResolver, "SealRec", uris.first()).apply {
                uris.drop(1).forEach { addItem(android.content.ClipData.Item(it)) }
            }
        }
        context.startActivity(android.content.Intent.createChooser(intent, app.localizedString(R.string.action_share)))
    }

    fun setLanguage(value: AppLanguage) {
        action { settingsRepo.setLanguage(value) }
    }

    fun setNightMode(value: NightModeOption) {
        action { settingsRepo.setNightMode(value) }
    }

    fun setQuality(value: AudioQuality) {
        action { settingsRepo.setQuality(value) }
    }

    fun setAllowNotificationSounds(value: Boolean) {
        action { settingsRepo.setAllowNotificationSounds(value) }
    }

    fun clearMessage() {
        _ui.value = _ui.value.copy(message = null)
    }

    private fun action(block: suspend () -> Unit): Job = viewModelScope.launch {
        try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { _ui.value = _ui.value.copy(message = app.localizedString(R.string.operation_failed)) }
    }
    private fun mutatingAction(block: suspend () -> Unit) = action { fileActions.withLock { block() } }

    override fun onCleared() {
        player.release()
    }
}
