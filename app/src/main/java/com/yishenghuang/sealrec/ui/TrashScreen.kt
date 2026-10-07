package com.yishenghuang.sealrec.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.*
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yishenghuang.sealrec.R
import com.yishenghuang.sealrec.data.RecordingEntity
import com.yishenghuang.sealrec.service.SealRecordService
import com.yishenghuang.sealrec.ui.layout.sealContentColumn
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
    var pendingDelete by remember { mutableStateOf<Long?>(null) }
    pendingDelete?.let { id ->
        AlertDialog(onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.trash_purge)) },
            text = { Text(stringResource(R.string.confirm_purge)) },
            confirmButton = { TextButton(onClick = {
                pendingDelete = null
                if (id == -1L) onEmptyTrash() else onPurge(id)
            }) { Text(stringResource(R.string.trash_purge)) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.rename_cancel)) } })
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .sealContentColumn(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = scheme.onBackground,
                )
            }
            Text(
                text = stringResource(R.string.trash_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            if (items.isNotEmpty()) {
                TextButton(onClick = { pendingDelete = -1L }) {
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
                    TrashRow(item = item, onRestore = onRestore, onPurge = { pendingDelete = it })
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
        SimpleDateFormat("yyyy-MM-dd HH:mm", androidx.compose.ui.platform.LocalConfiguration.current.locales[0]).format(Date(it))
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
