package com.yishenghuang.sealrec.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.yishenghuang.sealrec.R
import com.yishenghuang.sealrec.data.AppLanguage
import com.yishenghuang.sealrec.data.AudioQuality
import com.yishenghuang.sealrec.data.NightModeOption
import com.yishenghuang.sealrec.data.UserSettings
import com.yishenghuang.sealrec.ui.layout.sealContentColumn

@Composable
fun SettingsScreen(
    settings: UserSettings,
    trashCount: Int,
    onLanguage: (AppLanguage) -> Unit,
    onNightMode: (NightModeOption) -> Unit,
    onQuality: (AudioQuality) -> Unit,
    onNotifSounds: (Boolean) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val notificationLabel = stringResource(R.string.settings_notif_sounds)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .sealContentColumn()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(Modifier.height(20.dp))
        SettingCard(title = stringResource(R.string.settings_language)) {
            OptionList(
                options = AppLanguage.entries,
                selected = settings.language,
                label = { lang ->
                    if (lang == AppLanguage.System) {
                        stringResource(R.string.lang_system)
                    } else {
                        lang.nativeLabel
                    }
                },
                onSelect = onLanguage,
            )
        }

        Spacer(Modifier.height(16.dp))
        SettingCard(title = stringResource(R.string.settings_night)) {
            OptionList(
                options = NightModeOption.entries,
                selected = settings.nightMode,
                label = { mode ->
                    when (mode) {
                        NightModeOption.FollowSystem -> stringResource(R.string.night_system)
                        NightModeOption.Light -> stringResource(R.string.night_light)
                        NightModeOption.Dark -> stringResource(R.string.night_dark)
                    }
                },
                onSelect = onNightMode,
            )
        }

        Spacer(Modifier.height(16.dp))
        SettingCard(title = stringResource(R.string.settings_quality)) {
            OptionList(
                options = AudioQuality.entries,
                selected = settings.quality,
                label = { quality ->
                    when (quality) {
                        AudioQuality.Voice16k -> stringResource(R.string.quality_16k)
                        AudioQuality.Cd44k -> stringResource(R.string.quality_44k)
                        AudioQuality.Studio48k -> stringResource(R.string.quality_48k)
                    }
                },
                onSelect = onQuality,
            )
        }

        Spacer(Modifier.height(16.dp))
        SettingCard(title = null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_notif_sounds),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                )
                Switch(
                    modifier = Modifier.semantics { contentDescription = notificationLabel },
                    checked = settings.allowNotificationSoundsWhileRecording,
                    onCheckedChange = onNotifSounds,
                    colors = SwitchDefaults.colors(checkedTrackColor = scheme.primary),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SettingCard(title = null) {
            TextButton(onClick = onOpenTrash, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = scheme.onSurface)
                Text(
                    text = stringResource(R.string.settings_trash, trashCount),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )
            }
            TextButton(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Info, contentDescription = null, tint = scheme.onSurface)
                Text(
                    text = stringResource(R.string.settings_about),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun <T> OptionList(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.selectableGroup()) {
        options.forEachIndexed { index, option ->
            val isSelected = option == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelect(option) },
                        role = Role.RadioButton,
                    )
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = scheme.primary,
                    ),
                )
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) scheme.primary else scheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (index < options.lastIndex) {
                HorizontalDivider(
                    color = scheme.onSurface.copy(alpha = 0.08f),
                )
            }
        }
    }
}

@Composable
private fun SettingCard(title: String?, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surface, MaterialTheme.shapes.medium)
            .padding(16.dp),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        content()
    }
}
