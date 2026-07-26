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
import com.yishenghuang.sealrec.data.RecordingEntity
import com.yishenghuang.sealrec.service.SealRecordService
import kotlinx.coroutines.Job
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
)

class SealRecViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SealRecApp
    private val repo = app.repository
    private val player = RecordingPlayer(viewModelScope)

    val recordings: StateFlow<List<RecordingEntity>> =
        repo.observeRecordings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ui = MutableStateFlow(RecordUiState())
    val ui: StateFlow<RecordUiState> = _ui.asStateFlow()

    private val _report = MutableStateFlow<IntegrityReport?>(null)
    val report: StateFlow<IntegrityReport?> = _report.asStateFlow()

    private val waveform = MutableStateFlow(List(48) { 0.05f })
    val waveformBars: StateFlow<List<Float>> = waveform.asStateFlow()

    val playback: StateFlow<PlaybackState> = player.state

    private var engineObserveJob: Job? = null

    init {
        refreshIncomplete()
        viewModelScope.launch {
            app.engine.collectLatest { engine ->
                engineObserveJob?.cancel()
                if (engine == null) {
                    _ui.value = _ui.value.copy(engineState = SealEngineState.Idle, elapsedMs = 0L)
                    return@collectLatest
                }
                engineObserveJob = viewModelScope.launch {
                    launch { engine.state.collect { _ui.value = _ui.value.copy(engineState = it) } }
                    launch { engine.elapsedMs.collect { _ui.value = _ui.value.copy(elapsedMs = it) } }
                    launch { observeRms(engine) }
                }
            }
        }
        viewModelScope.launch {
            app.recordingFinished.collect {
                _ui.value = _ui.value.copy(message = app.getString(R.string.msg_sealed))
                refreshIncomplete()
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
        val incomplete = repo.listIncompleteRaws().firstOrNull()
        _ui.value = _ui.value.copy(incompleteRaw = incomplete)
    }

    fun startRecording(context: android.content.Context) {
        player.stop()
        SealRecordService.start(context)
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
        val raw = _ui.value.incompleteRaw ?: return
        viewModelScope.launch {
            repo.repairIncomplete(raw)
            refreshIncomplete()
            _ui.value = _ui.value.copy(message = app.getString(R.string.msg_repaired))
        }
    }

    fun discardIncomplete() {
        val raw = _ui.value.incompleteRaw ?: return
        viewModelScope.launch {
            repo.discardIncomplete(raw)
            refreshIncomplete()
        }
    }

    fun verifyRecording(id: Long) {
        viewModelScope.launch {
            _report.value = repo.verifyRecording(id)
        }
    }

    fun verifyUri(uri: Uri) {
        viewModelScope.launch {
            val tmp = File(app.cacheDir, "verify_${System.currentTimeMillis()}.wav")
            app.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            } ?: run {
                _report.value = null
                return@launch
            }
            _report.value = repo.verifyFile(tmp)
        }
    }

    fun clearReport() {
        _report.value = null
    }

    fun togglePlayback(id: Long) {
        val entity = recordings.value.find { it.id == id } ?: return
        player.toggle(id, File(entity.filePath))
    }

    fun seekPlayback(positionMs: Int) {
        player.seekTo(positionMs)
    }

    fun renameRecording(id: Long, newName: String) {
        viewModelScope.launch {
            val playingId = player.state.value.recordingId
            if (playingId == id) {
                player.stop()
            }
            val ok = repo.renameRecording(id, newName)
            _ui.value = _ui.value.copy(
                message = app.getString(
                    if (ok) R.string.rename_ok else R.string.rename_fail,
                ),
            )
        }
    }

    fun deleteRecording(id: Long) {
        if (player.state.value.recordingId == id) {
            player.stop()
        }
        viewModelScope.launch { repo.deleteRecording(id) }
    }

    fun exportRecording(id: Long) {
        viewModelScope.launch {
            val entity = recordings.value.find { it.id == id } ?: return@launch
            val uri = repo.exportToMusic(File(entity.filePath))
            _ui.value = _ui.value.copy(
                message = if (uri != null) app.getString(R.string.export_ok)
                else app.getString(R.string.export_fail),
            )
        }
    }

    fun clearMessage() {
        _ui.value = _ui.value.copy(message = null)
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}
