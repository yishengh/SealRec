package com.yishenghuang.sealrec

import android.app.Application
import com.yishenghuang.sealrec.core.crypto.KeystoreManager
import com.yishenghuang.sealrec.core.pipeline.SealEngine
import com.yishenghuang.sealrec.data.RecordingRepository
import com.yishenghuang.sealrec.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SealRecApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var repository: RecordingRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var keystore: KeystoreManager
        private set

    private val _engine = MutableStateFlow<SealEngine?>(null)
    val engine: StateFlow<SealEngine?> = _engine.asStateFlow()
    val recordingSessionActive = MutableStateFlow(false)

    private val _recordingFinished = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val recordingFinished: SharedFlow<String> = _recordingFinished.asSharedFlow()

    private val _userMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val userMessages: SharedFlow<String> = _userMessages.asSharedFlow()

    override fun onCreate() {
        super.onCreate()
        keystore = KeystoreManager()
        repository = RecordingRepository(this)
        settingsRepository = SettingsRepository(this)
        appScope.launch {
            val s = settingsRepository.settings.first()
            SettingsRepository.applyLanguage(s.language)
            SettingsRepository.applyNightMode(s.nightMode)
        }
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

    fun emitMessage(message: String) {
        _userMessages.tryEmit(message)
    }

    fun localizedString(@androidx.annotation.StringRes id: Int, vararg args: Any): String {
        val locales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return getString(id, *args)
        val config = android.content.res.Configuration(resources.configuration)
        config.setLocales(android.os.LocaleList.forLanguageTags(locales.toLanguageTags()))
        return createConfigurationContext(config).getString(id, *args)
    }
}
