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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yishenghuang.sealrec.R
import com.yishenghuang.sealrec.data.RecordingEntity
import com.yishenghuang.sealrec.service.SealRecordService
import com.yishenghuang.sealrec.ui.theme.Crimson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TrashScreen(
    items: List<RecordingEntity>,
    onRestore: (Long) -> Unit,
    onPurge: (Long) -> Unit,
    onEmptyTrash: () -> Unit,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
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
                text = stringResource(R.string.trash_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            if (items.isNotEmpty()) {
                TextButton(onClick = onEmptyTrash) {
                    Text(stringResource(R.string.trash_empty_all), color = Crimson)
                }
            }
        }

        if (items.isEmpty()) {
            Text(
                text = stringResource(R.string.trash_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(24.dp),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    TrashRow(item = item, onRestore = onRestore, onPurge = onPurge)
                }
            }
        }
    }
}

@Composable
private fun TrashRow(
    item: RecordingEntity,
    onRestore: (Long) -> Unit,
    onPurge: (Long) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val deletedDate = item.deletedAtMs?.let {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surface, MaterialTheme.shapes.medium)
            .padding(16.dp),
    ) {
        Text(item.fileName, style = MaterialTheme.typography.titleLarge)
        Text(
            text = buildString {
                append(SealRecordService.formatDuration(item.durationMs))
                append(" · ${item.fileSizeBytes / 1024} KB")
                deletedDate?.let { append(" · $it") }
            },
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurface.copy(alpha = 0.7f),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = { onRestore(item.id) }) {
                Icon(Icons.Default.Restore, contentDescription = null, tint = scheme.primary)
                Text(
                    text = stringResource(R.string.trash_restore),
                    color = scheme.primary,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            TextButton(onClick = { onPurge(item.id) }) {
                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Crimson)
                Text(
                    text = stringResource(R.string.trash_purge),
                    color = Crimson,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}
