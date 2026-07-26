package com.yishenghuang.sealrec.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yishenghuang.sealrec.R
import com.yishenghuang.sealrec.data.AppLanguage
import com.yishenghuang.sealrec.data.AudioQuality
import com.yishenghuang.sealrec.data.NightModeOption
import com.yishenghuang.sealrec.data.UserSettings

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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(Modifier.height(20.dp))
        SettingCard(title = stringResource(R.string.settings_language)) {
            LanguageChips(
                selected = settings.language,
                onSelect = onLanguage,
            )
        }

        Spacer(Modifier.height(16.dp))
        SettingCard(title = stringResource(R.string.settings_night)) {
            OptionRow(
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
            OptionRow(
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
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.settings_notif_sounds),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                )
                Switch(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageChips(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppLanguage.entries.forEach { lang ->
            val label = if (lang == AppLanguage.System) {
                stringResource(R.string.lang_system)
            } else {
                lang.nativeLabel
            }
            FilterChip(
                selected = lang == selected,
                onClick = { onSelect(lang) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = scheme.primary,
                    selectedLabelColor = scheme.onPrimary,
                ),
            )
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
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        content()
    }
}

@Composable
private fun <T> OptionRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = scheme.primary,
                    activeContentColor = scheme.onPrimary,
                ),
            ) {
                Text(label(option), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
