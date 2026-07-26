package com.yishenghuang.sealrec

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yishenghuang.sealrec.core.pipeline.SealEngineState
import com.yishenghuang.sealrec.data.NightModeOption
import com.yishenghuang.sealrec.ui.AboutScreen
import com.yishenghuang.sealrec.ui.LibraryScreen
import com.yishenghuang.sealrec.ui.RecordScreen
import com.yishenghuang.sealrec.ui.SealRecViewModel
import com.yishenghuang.sealrec.ui.SettingsScreen
import com.yishenghuang.sealrec.ui.TrashScreen
import com.yishenghuang.sealrec.ui.VerifyScreen
import com.yishenghuang.sealrec.ui.theme.SealRecTheme

private const val TAB_RECORD = 0
private const val TAB_LIBRARY = 1
private const val TAB_VERIFY = 2
private const val TAB_SETTINGS = 3

private enum class Overlay { None, About, Trash }

class MainActivity : ComponentActivity() {
    private val viewModel: SealRecViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val darkTheme = when (settings.nightMode) {
                NightModeOption.FollowSystem -> isSystemInDarkTheme()
                NightModeOption.Light -> false
                NightModeOption.Dark -> true
            }
            SealRecTheme(darkTheme = darkTheme) {
                SealRecAppScaffold(viewModel)
            }
        }
    }
}

@Composable
private fun SealRecAppScaffold(viewModel: SealRecViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val trash by viewModel.trash.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val report by viewModel.report.collectAsStateWithLifecycle()
    val bars by viewModel.waveformBars.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(TAB_RECORD) }
    var overlay by remember { mutableStateOf(Overlay.None) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val mic = result[Manifest.permission.RECORD_AUDIO] == true
        if (mic) {
            viewModel.startRecording(context)
        } else {
            Toast.makeText(context, R.string.permission_mic, Toast.LENGTH_LONG).show()
        }
    }

    fun ensurePermsAndStart() {
        val need = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            need += Manifest.permission.RECORD_AUDIO
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            need += Manifest.permission.POST_NOTIFICATIONS
        }
        if (need.isEmpty()) {
            viewModel.startRecording(context)
        } else {
            permissionLauncher.launch(need.toTypedArray())
        }
    }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(tab, overlay) {
        if (tab != TAB_LIBRARY || overlay != Overlay.None) {
            viewModel.stopPlayback()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.stopPlayback()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (overlay == Overlay.About) {
        AboutScreen(onBack = { overlay = Overlay.None })
        return
    }
    if (overlay == Overlay.Trash) {
        TrashScreen(
            items = trash,
            onRestore = { viewModel.restoreFromTrash(it) },
            onPurge = { viewModel.purgeFromTrash(it) },
            onEmptyTrash = { viewModel.emptyTrash() },
            onBack = { overlay = Overlay.None },
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == TAB_RECORD,
                    onClick = { tab = TAB_RECORD },
                    icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_record)) },
                )
                NavigationBarItem(
                    selected = tab == TAB_LIBRARY,
                    onClick = { tab = TAB_LIBRARY },
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_library)) },
                )
                NavigationBarItem(
                    selected = tab == TAB_VERIFY,
                    onClick = { tab = TAB_VERIFY },
                    icon = { Icon(Icons.Default.VerifiedUser, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_verify)) },
                )
                NavigationBarItem(
                    selected = tab == TAB_SETTINGS,
                    onClick = { tab = TAB_SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_settings)) },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (tab) {
                TAB_RECORD -> RecordScreen(
                    state = ui,
                    bars = bars,
                    onStart = { ensurePermsAndStart() },
                    onPause = { viewModel.pauseRecording(context) },
                    onResume = { viewModel.resumeRecording(context) },
                    onStop = { viewModel.stopRecording(context) },
                    onRepair = { viewModel.repairIncomplete() },
                    onDiscard = { viewModel.discardIncomplete() },
                )
                TAB_LIBRARY -> LibraryScreen(
                    recordings = recordings,
                    playback = playback,
                    playbackEnabled = when (ui.engineState) {
                        SealEngineState.Idle -> true
                        else -> false
                    },
                    onPlayToggle = { viewModel.togglePlayback(it) },
                    onSeek = { viewModel.seekPlayback(it) },
                    onRename = { id, name -> viewModel.renameRecording(id, name) },
                    onVerify = {
                        viewModel.verifyRecording(it)
                        tab = TAB_VERIFY
                    },
                    onExport = { viewModel.exportRecording(it) },
                    onDelete = { viewModel.deleteRecording(it) },
                )
                TAB_VERIFY -> VerifyScreen(
                    report = report,
                    onPickFile = { viewModel.verifyUri(it) },
                    onClear = { viewModel.clearReport() },
                )
                else -> SettingsScreen(
                    settings = settings,
                    trashCount = trash.size,
                    onLanguage = { viewModel.setLanguage(it) },
                    onNightMode = { viewModel.setNightMode(it) },
                    onQuality = { viewModel.setQuality(it) },
                    onNotifSounds = { viewModel.setAllowNotificationSounds(it) },
                    onOpenTrash = { overlay = Overlay.Trash },
                    onOpenAbout = { overlay = Overlay.About },
                )
            }
        }
    }
}
