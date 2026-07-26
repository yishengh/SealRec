package com.yishenghuang.sealrec

import android.app.Application
import com.yishenghuang.sealrec.core.crypto.KeystoreManager
import com.yishenghuang.sealrec.core.pipeline.SealEngine
import com.yishenghuang.sealrec.data.RecordingRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class SealRecApp : Application() {
    lateinit var repository: RecordingRepository
        private set

    lateinit var keystore: KeystoreManager
        private set

    private val _engine = MutableStateFlow<SealEngine?>(null)
    val engine: StateFlow<SealEngine?> = _engine.asStateFlow()

    private val _recordingFinished = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val recordingFinished: SharedFlow<String> = _recordingFinished.asSharedFlow()

    override fun onCreate() {
        super.onCreate()
        keystore = KeystoreManager()
        keystore.ensureKey()
        repository = RecordingRepository(this)
    }

    fun bindEngine(engine: SealEngine) {
        _engine.value = engine
    }

    fun unbindEngine(engine: SealEngine) {
        if (_engine.value === engine) {
            _engine.value = null
        }
    }

    fun onRecordingFinished(path: String) {
        _recordingFinished.tryEmit(path)
    }
}
