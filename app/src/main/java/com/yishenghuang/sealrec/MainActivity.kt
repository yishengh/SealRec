package com.yishenghuang.sealrec

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.yishenghuang.sealrec.ui.layout.isExpandedWidth
import com.yishenghuang.sealrec.ui.theme.SealRecTheme

private const val TAB_RECORD = 0
private const val TAB_LIBRARY = 1
private const val TAB_VERIFY = 2
private const val TAB_SETTINGS = 3

private enum class Overlay { None, About, Trash }

class MainActivity : AppCompatActivity() {
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
    var tab by rememberSaveable { mutableIntStateOf(TAB_RECORD) }
    var overlayName by rememberSaveable { mutableStateOf(Overlay.None.name) }
    val overlay = Overlay.entries.find { it.name == overlayName } ?: Overlay.None
    fun setOverlay(value: Overlay) {
        overlayName = value.name
    }
    val useRail = isExpandedWidth()
    BackHandler(enabled = overlay != Overlay.None) { setOverlay(Overlay.None) }
    var micDenied by rememberSaveable { mutableStateOf(false) }
    if (micDenied) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { micDenied = false },
            title = { Text(stringResource(R.string.permission_mic)) },
            text = { Text(stringResource(R.string.permission_mic_settings)) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                micDenied = false
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + context.packageName)))
            }) { Text(stringResource(R.string.tab_settings)) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { micDenied = false }) { Text(stringResource(R.string.rename_cancel)) } },
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (mic) {
            viewModel.startRecording(context)
        } else {
            micDenied = true
        }
    }

    var pendingExport by rememberSaveable { mutableStateOf<Long?>(null) }
    val exportPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        val id = pendingExport
        pendingExport = null
        if (granted.values.all { it } && id != null) viewModel.exportRecording(id)
        else Toast.makeText(context, R.string.operation_failed, Toast.LENGTH_LONG).show()
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
        AboutScreen(onBack = { setOverlay(Overlay.None) })
        return
    }
    if (overlay == Overlay.Trash) {
        TrashScreen(
            items = trash,
            onRestore = { viewModel.restoreFromTrash(it) },
            onPurge = { viewModel.purgeFromTrash(it) },
            onEmptyTrash = { viewModel.emptyTrash() },
            onBack = { setOverlay(Overlay.None) },
        )
        return
    }

    @Composable
    fun TabContent() {
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
                onShare = { viewModel.shareRecording(it, context) },
                onExport = {
                    val storagePermissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    if (Build.VERSION.SDK_INT <= 28 && storagePermissions.any {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }) {
                        pendingExport = it
                        exportPermission.launch(storagePermissions)
                    } else viewModel.exportRecording(it)
                },
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
                onOpenTrash = { setOverlay(Overlay.Trash) },
                onOpenAbout = { setOverlay(Overlay.About) },
            )
        }
    }

    if (useRail) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            NavigationRail(modifier = Modifier.fillMaxHeight()) {
                NavigationRailItem(
                    selected = tab == TAB_RECORD,
                    onClick = { tab = TAB_RECORD },
                    icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_record)) },
                )
                NavigationRailItem(
                    selected = tab == TAB_LIBRARY,
                    onClick = { tab = TAB_LIBRARY },
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_library)) },
                )
                NavigationRailItem(
                    selected = tab == TAB_VERIFY,
                    onClick = { tab = TAB_VERIFY },
                    icon = { Icon(Icons.Default.VerifiedUser, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_verify)) },
                )
                NavigationRailItem(
                    selected = tab == TAB_SETTINGS,
                    onClick = { tab = TAB_SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_settings)) },
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                TabContent()
            }
        }
    } else {
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
                TabContent()
            }
        }
    }
}
