package com.yishenghuang.sealrec.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yishenghuang.sealrec.R
import com.yishenghuang.sealrec.core.audio.PlaybackState
import com.yishenghuang.sealrec.data.RecordingEntity
import com.yishenghuang.sealrec.service.SealRecordService
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
    onPlayToggle: (Long) -> Unit,
    onVerify: (Long) -> Unit,
    onExport: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Foam, Mist))),
    ) {
        Text(
            text = stringResource(R.string.library_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(24.dp, 24.dp, 24.dp, 8.dp),
        )
        Text(
            text = stringResource(R.string.library_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = Slate,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        if (recordings.isEmpty()) {
            Text(
                text = stringResource(R.string.library_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = Slate,
                modifier = Modifier.padding(24.dp),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(recordings, key = { it.id }) { item ->
                    RecordingRow(
                        item = item,
                        playing = playback.recordingId == item.id && playback.isPlaying,
                        onPlayToggle = onPlayToggle,
                        onVerify = onVerify,
                        onExport = onExport,
                        onDelete = onDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingRow(
    item: RecordingEntity,
    playing: Boolean,
    onPlayToggle: (Long) -> Unit,
    onVerify: (Long) -> Unit,
    onExport: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        .format(Date(item.createdAtMs))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Mist, MaterialTheme.shapes.medium)
            .padding(16.dp),
    ) {
        Text(item.fileName, style = MaterialTheme.typography.titleLarge)
        Text(
            "$date · ${SealRecordService.formatDuration(item.durationMs)} · ${item.fileSizeBytes / 1024} KB",
            style = MaterialTheme.typography.labelSmall,
            color = Slate,
        )
        item.keyFingerprintHex?.let {
            Text(
                stringResource(R.string.key_fingerprint, it),
                style = MaterialTheme.typography.labelLarge,
                color = Teal,
            )
        }
        item.lastVerifyStatus?.let {
            Text(
                stringResource(R.string.last_verify, it),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = { onPlayToggle(item.id) }) {
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "pause" else "play",
                    tint = Teal,
                )
            }
            TextButton(onClick = { onVerify(item.id) }) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null)
                Text(stringResource(R.string.action_verify), modifier = Modifier.padding(start = 4.dp))
            }
            IconButton(onClick = { onExport(item.id) }) {
                Icon(Icons.Default.Share, contentDescription = "export")
            }
            IconButton(onClick = { onDelete(item.id) }) {
                Icon(Icons.Default.Delete, contentDescription = "delete")
            }
        }
    }
}
