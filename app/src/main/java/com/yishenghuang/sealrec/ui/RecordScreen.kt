package com.yishenghuang.sealrec.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yishenghuang.sealrec.R
import com.yishenghuang.sealrec.core.pipeline.SealEngineState
import com.yishenghuang.sealrec.service.SealRecordService
import com.yishenghuang.sealrec.ui.layout.sealContentColumn
import com.yishenghuang.sealrec.ui.theme.Foam
import com.yishenghuang.sealrec.ui.theme.Ink
import com.yishenghuang.sealrec.ui.theme.Mist
import com.yishenghuang.sealrec.ui.theme.Slate
import com.yishenghuang.sealrec.ui.theme.Teal
import com.yishenghuang.sealrec.ui.theme.TealBright
import java.io.File

@Composable
fun RecordScreen(
    state: RecordUiState,
    bars: List<Float>,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onRepair: () -> Unit,
    onDiscard: () -> Unit,
) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    val sweep by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface)),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path().apply {
                moveTo(0f, size.height * 0.72f)
                cubicTo(
                    size.width * 0.25f, size.height * 0.62f,
                    size.width * 0.55f, size.height * 0.82f,
                    size.width, size.height * 0.68f,
                )
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(path, Teal.copy(alpha = 0.08f))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .sealContentColumn()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "SealRec",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.offline_badge),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = stringResource(R.string.brand_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(modifier = Modifier.height(28.dp))

            WaveformCanvas(
                bars = bars,
                active = state.engineState == SealEngineState.Recording,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = SealRecordService.formatDuration(state.elapsedMs),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = when (state.engineState) {
                    SealEngineState.Idle -> stringResource(R.string.state_idle)
                    SealEngineState.Recording -> stringResource(R.string.state_recording)
                    SealEngineState.Paused -> stringResource(R.string.state_paused)
                    SealEngineState.Finalizing -> stringResource(R.string.state_finalizing)
                },
                style = MaterialTheme.typography.labelLarge,
                color = TealBright,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))

            state.incompleteRaw?.let { file ->
                IncompleteBanner(file, onRepair, onDiscard, enabled = !state.recovering)
                Spacer(modifier = Modifier.height(16.dp))
            }

            RecordControls(
                state = if (state.recovering) SealEngineState.Finalizing else state.engineState,
                pulseScale = if (state.engineState == SealEngineState.Recording) pulseScale else 1f,
                sweep = sweep,
                onStart = onStart,
                onPause = onPause,
                onResume = onResume,
                onStop = onStop,
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.device_time_disclaimer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun IncompleteBanner(file: File, onRepair: () -> Unit, onDiscard: () -> Unit, enabled: Boolean) {
    var confirmingDiscard by remember(file) { mutableStateOf(false) }
    if (confirmingDiscard) {
        AlertDialog(onDismissRequest = { confirmingDiscard = false },
            title = { Text(stringResource(R.string.incomplete_discard)) },
            text = { Text(stringResource(R.string.confirm_purge)) },
            confirmButton = { TextButton(onClick = { confirmingDiscard = false; onDiscard() }) { Text(stringResource(R.string.incomplete_discard)) } },
            dismissButton = { TextButton(onClick = { confirmingDiscard = false }) { Text(stringResource(R.string.rename_cancel)) } })
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.incomplete_title), style = MaterialTheme.typography.titleLarge)
        Text(file.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRepair, enabled = enabled) { Text(stringResource(R.string.incomplete_repair)) }
            TextButton(onClick = { confirmingDiscard = true }, enabled = enabled) { Text(stringResource(R.string.incomplete_discard)) }
        }
    }
}

@Composable
private fun RecordControls(
    state: SealEngineState,
    pulseScale: Float,
    sweep: Float,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        when (state) {
            SealEngineState.Idle, SealEngineState.Finalizing -> {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size((96 * pulseScale).dp)) {
                        drawCircle(
                            color = Teal.copy(alpha = 0.15f),
                            radius = size.minDimension / 2,
                        )
                        drawArc(
                            color = TealBright.copy(alpha = 0.5f),
                            startAngle = sweep,
                            sweepAngle = 80f,
                            useCenter = false,
                            style = Stroke(width = 4f),
                        )
                    }
                    FilledIconButton(
                        onClick = onStart,
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                        enabled = state == SealEngineState.Idle,
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.tab_record), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
            SealEngineState.Recording -> {
                FilledIconButton(
                    onClick = onPause,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                ) {
                    Icon(Icons.Default.Pause, contentDescription = stringResource(R.string.action_pause))
                }
                FilledIconButton(
                    onClick = onStop,
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.action_stop), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
            SealEngineState.Paused -> {
                FilledIconButton(
                    onClick = onResume,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.action_resume))
                }
                FilledIconButton(
                    onClick = onStop,
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.action_stop), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@Composable
fun WaveformCanvas(
    bars: List<Float>,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (active) TealBright else Teal.copy(alpha = 0.45f)
    Canvas(modifier = modifier) {
        val gap = 4.dp.toPx()
        val barWidth = ((size.width - gap * (bars.size - 1)) / bars.size).coerceAtLeast(2f)
        bars.forEachIndexed { index, level ->
            val h = (size.height * level).coerceAtLeast(4f)
            val x = index * (barWidth + gap)
            val y = (size.height - h) / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
            )
        }
    }
}
