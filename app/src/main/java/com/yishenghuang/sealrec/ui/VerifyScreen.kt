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
import androidx.compose.ui.graphics.luminance
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
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface)))
            .sealContentColumn()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.verify_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.verify_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val (title, lightColor) = when (report.status) {
        IntegrityStatus.Intact -> stringResource(R.string.status_intact) to IntactGreen
        IntegrityStatus.Tampered -> stringResource(R.string.status_tampered) to Crimson
        IntegrityStatus.BadSignature -> stringResource(R.string.status_bad_sig) to Amber
        IntegrityStatus.NotSealRec -> stringResource(R.string.status_not_seal) to SignalGray
    }
    val color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) when (report.status) {
        IntegrityStatus.Intact -> Color(0xFF77D6A1)
        IntegrityStatus.Tampered -> Color(0xFFFF8A80)
        IntegrityStatus.BadSignature -> Color(0xFFFFD180)
        IntegrityStatus.NotSealRec -> Color(0xFFB8C9D0)
    } else lightColor

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.12f), MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = color)
        if (report.status == IntegrityStatus.Intact) {
            Text(stringResource(R.string.about_proves_body), style = MaterialTheme.typography.bodyMedium)
        }
        Text(report.fileName, style = MaterialTheme.typography.labelLarge)
        report.deviceTimeUtcMs?.let {
            val formatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", androidx.compose.ui.platform.LocalConfiguration.current.locales[0])
                .format(Date(it))
            Text("DeviceTime: $formatted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(R.string.device_time_disclaimer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        report.keyFingerprintHex?.let {
            Text(stringResource(R.string.key_fingerprint, it), style = MaterialTheme.typography.labelLarge)
        }
        report.embeddedHashHex?.let {
            Text(stringResource(R.string.report_embedded_hash, it), style = MaterialTheme.typography.labelSmall)
        }
        report.computedHashHex?.let {
            Text(stringResource(R.string.report_computed_hash, it), style = MaterialTheme.typography.labelSmall)
        }
        report.headerHexPreview?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.report_header), style = MaterialTheme.typography.titleLarge)
            Text(
                it.chunked(32).joinToString("\n"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
