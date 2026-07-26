package com.yishenghuang.sealrec.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yishenghuang.sealrec.R
import com.yishenghuang.sealrec.core.verify.IntegrityReport
import com.yishenghuang.sealrec.core.verify.IntegrityStatus
import com.yishenghuang.sealrec.ui.layout.sealContentColumn
import com.yishenghuang.sealrec.ui.theme.Amber
import com.yishenghuang.sealrec.ui.theme.Crimson
import com.yishenghuang.sealrec.ui.theme.Foam
import com.yishenghuang.sealrec.ui.theme.IntactGreen
import com.yishenghuang.sealrec.ui.theme.Mist
import com.yishenghuang.sealrec.ui.theme.SignalGray
import com.yishenghuang.sealrec.ui.theme.Slate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VerifyScreen(
    report: IntegrityReport?,
    onPickFile: (android.net.Uri) -> Unit,
    onClear: () -> Unit,
) {
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(onPickFile)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Foam, Mist)))
            .sealContentColumn()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.verify_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.verify_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = Slate,
        )
        Button(onClick = { picker.launch(arrayOf("audio/*", "audio/wav", "*/*")) }) {
            Text(stringResource(R.string.verify_pick))
        }
        if (report != null) {
            OutlinedButton(onClick = onClear) { Text(stringResource(R.string.verify_clear)) }
            ReportCard(report)
        }
    }
}

@Composable
fun ReportCard(report: IntegrityReport) {
    val (title, color) = when (report.status) {
        IntegrityStatus.Intact -> stringResource(R.string.status_intact) to IntactGreen
        IntegrityStatus.Tampered -> stringResource(R.string.status_tampered) to Crimson
        IntegrityStatus.BadSignature -> stringResource(R.string.status_bad_sig) to Amber
        IntegrityStatus.NotSealRec -> stringResource(R.string.status_not_seal) to SignalGray
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.12f), MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = color)
        Text(report.message, style = MaterialTheme.typography.bodyLarge)
        Text(report.fileName, style = MaterialTheme.typography.labelLarge)
        report.deviceTimeUtcMs?.let {
            val formatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(it))
            Text("DeviceTime: $formatted", style = MaterialTheme.typography.labelSmall, color = Slate)
            Text(
                stringResource(R.string.device_time_disclaimer),
                style = MaterialTheme.typography.bodyMedium,
                color = Slate,
            )
        }
        report.keyFingerprintHex?.let {
            Text("Key: $it", style = MaterialTheme.typography.labelLarge)
        }
        report.embeddedHashHex?.let {
            Text("Embedded hash: $it", style = MaterialTheme.typography.labelSmall)
        }
        report.computedHashHex?.let {
            Text("Computed hash: $it", style = MaterialTheme.typography.labelSmall)
        }
        report.headerHexPreview?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Header Hex", style = MaterialTheme.typography.titleLarge)
            Text(
                it.chunked(32).joinToString("\n"),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF1A3038),
            )
        }
    }
}
