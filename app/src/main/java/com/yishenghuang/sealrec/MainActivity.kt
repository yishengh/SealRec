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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yishenghuang.sealrec.ui.LibraryScreen
import com.yishenghuang.sealrec.ui.RecordScreen
import com.yishenghuang.sealrec.ui.SealRecViewModel
import com.yishenghuang.sealrec.ui.VerifyScreen
import com.yishenghuang.sealrec.ui.theme.SealRecTheme

class MainActivity : ComponentActivity() {
    private val viewModel: SealRecViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SealRecTheme {
                SealRecAppScaffold(viewModel)
            }
        }
    }
}

@Composable
private fun SealRecAppScaffold(viewModel: SealRecViewModel) {
    val context = LocalContext.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val report by viewModel.report.collectAsStateWithLifecycle()
    val bars by viewModel.waveformBars.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }

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

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_record)) },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_library)) },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.VerifiedUser, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_verify)) },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (tab) {
                0 -> RecordScreen(
                    state = ui,
                    bars = bars,
                    onStart = { ensurePermsAndStart() },
                    onPause = { viewModel.pauseRecording(context) },
                    onResume = { viewModel.resumeRecording(context) },
                    onStop = { viewModel.stopRecording(context) },
                    onRepair = { viewModel.repairIncomplete() },
                    onDiscard = { viewModel.discardIncomplete() },
                )
                1 -> LibraryScreen(
                    recordings = recordings,
                    playback = playback,
                    onPlayToggle = { viewModel.togglePlayback(it) },
                    onSeek = { viewModel.seekPlayback(it) },
                    onRename = { id, name -> viewModel.renameRecording(id, name) },
                    onVerify = {
                        viewModel.verifyRecording(it)
                        tab = 2
                    },
                    onExport = { viewModel.exportRecording(it) },
                    onDelete = { viewModel.deleteRecording(it) },
                )
                else -> VerifyScreen(
                    report = report,
                    onPickFile = { viewModel.verifyUri(it) },
                    onClear = { viewModel.clearReport() },
                )
            }
        }
    }
}
