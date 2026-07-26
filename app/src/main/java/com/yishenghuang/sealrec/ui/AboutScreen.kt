package com.yishenghuang.sealrec.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yishenghuang.sealrec.R
import com.yishenghuang.sealrec.ui.layout.sealContentColumn

private const val SUPPORT_EMAIL = "sealrec@fastmail.com"
private const val PRIVACY_URL = "https://sealrec-privacy.netlify.app/#en"

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .sealContentColumn()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "back",
                    tint = scheme.onBackground,
                )
            }
            Text(
                text = stringResource(R.string.about_title),
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        Text(
            text = stringResource(R.string.offline_badge),
            style = MaterialTheme.typography.labelLarge,
            color = scheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Spacer(Modifier.height(24.dp))
        AboutSection(
            title = stringResource(R.string.about_proves_title),
            body = stringResource(R.string.about_proves_body),
        )
        Spacer(Modifier.height(16.dp))
        AboutSection(
            title = stringResource(R.string.about_not_title),
            body = stringResource(R.string.about_not_body),
        )
        Spacer(Modifier.height(16.dp))
        AboutSection(
            title = stringResource(R.string.about_time_title),
            body = stringResource(R.string.about_time_body),
        )

        Spacer(Modifier.height(16.dp))
        ContactSection(
            onEmail = {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:$SUPPORT_EMAIL")
                }
                runCatching { context.startActivity(intent) }
            },
            onPrivacy = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))
                try {
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                }
            },
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ContactSection(onEmail: () -> Unit, onPrivacy: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .background(scheme.surface, MaterialTheme.shapes.medium)
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.about_contact_title),
            style = MaterialTheme.typography.titleLarge,
            color = scheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        ContactRow(
            icon = Icons.Default.MailOutline,
            label = SUPPORT_EMAIL,
            onClick = onEmail,
        )
        Spacer(Modifier.height(4.dp))
        ContactRow(
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            label = stringResource(R.string.about_privacy),
            onClick = onPrivacy,
        )
    }
}

@Composable
private fun ContactRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = scheme.primary)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.primary,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun AboutSection(title: String, body: String) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .background(scheme.surface, MaterialTheme.shapes.medium)
            .padding(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = scheme.primary,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
