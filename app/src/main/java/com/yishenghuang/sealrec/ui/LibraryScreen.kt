package com.yishenghuang.sealrec.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.yishenghuang.sealrec.R
import com.yishenghuang.sealrec.core.audio.PlaybackState
import com.yishenghuang.sealrec.data.RecordingEntity
import com.yishenghuang.sealrec.service.SealRecordService
import com.yishenghuang.sealrec.ui.layout.sealContentColumn
import com.yishenghuang.sealrec.ui.theme.Foam
import com.yishenghuang.sealrec.ui.theme.Mist
import com.yishenghuang.sealrec.ui.theme.Slate
import com.yishenghuang.sealrec.ui.theme.Teal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LibraryScreen(
    recordings: List<RecordingEntity>,
    playback: PlaybackState,
    playbackEnabled: Boolean,
    onPlayToggle: (Long) -> Unit,
    onSeek: (Int) -> Unit,
    onRename: (Long, String) -> Unit,
    onVerify: (Long) -> Unit,
    onExport: (Long) -> Unit,
    onShare: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var renameTarget by remember { mutableStateOf<RecordingEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface)))
            .sealContentColumn(),
    ) {
        Text(
            text = stringResource(R.string.library_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(24.dp, 24.dp, 24.dp, 8.dp),
        )
        Text(
            text = stringResource(R.string.library_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        if (recordings.isEmpty()) {
            Text(
                text = stringResource(R.string.library_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(recordings, key = { it.id }) { item ->
                    val active = playback.recordingId == item.id
                    RecordingRow(
                        item = item,
                        active = active,
                        playing = active && playback.isPlaying,
                        playbackEnabled = playbackEnabled,
                        positionMs = if (active) playback.positionMs else 0,
                        durationMs = if (active) {
                            playback.durationMs.takeIf { it > 0 } ?: item.durationMs.toInt()
                        } else {
                            item.durationMs.toInt()
                        },
                        onPlayToggle = onPlayToggle,
                        onSeek = onSeek,
                        onRename = { renameTarget = item },
                        onVerify = onVerify,
                        onExport = onExport,
                        onShare = onShare,
                        onDelete = onDelete,
                    )
                }
            }
        }
    }

    renameTarget?.let { target ->
        RenameDialog(
            initialName = target.fileName.removeSuffix(".wav").removeSuffix(".WAV"),
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                onRename(target.id, name)
                renameTarget = null
            },
        )
    }
}

@Composable
private fun RecordingRow(
    item: RecordingEntity,
    active: Boolean,
    playing: Boolean,
    playbackEnabled: Boolean,
    positionMs: Int,
    durationMs: Int,
    onPlayToggle: (Long) -> Unit,
    onSeek: (Int) -> Unit,
    onRename: () -> Unit,
    onVerify: (Long) -> Unit,
    onExport: (Long) -> Unit,
    onShare: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val date = SimpleDateFormat("yyyy-MM-dd HH:mm", androidx.compose.ui.platform.LocalConfiguration.current.locales[0])
        .format(Date(item.createdAtMs))
    var scrubbing by remember(item.id) { mutableStateOf(false) }
    var scrubValue by remember(item.id) { mutableFloatStateOf(0f) }
    val sliderMax = durationMs.coerceAtLeast(1).toFloat()
    val sliderValue = if (scrubbing) scrubValue else positionMs.toFloat().coerceIn(0f, sliderMax)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .padding(16.dp),
    ) {
        Text(item.fileName, style = MaterialTheme.typography.titleLarge)
        Text(
            "$date · ${SealRecordService.formatDuration(item.durationMs)} · ${item.fileSizeBytes / 1024} KB",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        item.keyFingerprintHex?.let {
            Text(
                stringResource(R.string.key_fingerprint, it),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        item.lastVerifyStatus?.let {
            val status = stringResource(when (it) {
                "Intact" -> R.string.status_intact
                "Tampered" -> R.string.status_tampered
                "BadSignature" -> R.string.status_bad_sig
                else -> R.string.status_not_seal
            })
            Text(
                stringResource(R.string.last_verify, status),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (active) {
            val seekLabel = stringResource(R.string.playback_position)
            Slider(
                value = sliderValue,
                onValueChange = {
                    scrubbing = true
                    scrubValue = it
                },
                onValueChangeFinished = {
                    onSeek(scrubValue.toInt())
                    scrubbing = false
                },
                valueRange = 0f..sliderMax,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = seekLabel },
                colors = SliderDefaults.colors(
                    thumbColor = Teal,
                    activeTrackColor = Teal,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    SealRecordService.formatDuration(sliderValue.toLong()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    SealRecordService.formatDuration(durationMs.toLong()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(
                onClick = { onPlayToggle(item.id) },
                enabled = playbackEnabled || playing,
            ) {
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(if (playing) R.string.action_pause else R.string.action_play),
                    tint = if (playbackEnabled || playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }
            TextButton(onClick = { onVerify(item.id) }) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null)
                Text(stringResource(R.string.action_verify), modifier = Modifier.padding(start = 4.dp))
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = stringResource(R.string.rename_title), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onShare(item.id) }) {
                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share))
            }
            IconButton(onClick = { onExport(item.id) }) {
                Icon(Icons.Default.Download, contentDescription = stringResource(R.string.action_export))
            }
            IconButton(onClick = { onDelete(item.id) }) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_trash))
            }
        }
    }
}

@Composable
private fun RenameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(stringResource(R.string.rename_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) {
                Text(stringResource(R.string.rename_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.rename_cancel))
            }
        },
    )
}
