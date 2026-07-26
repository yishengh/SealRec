package com.yishenghuang.sealrec.ui.layout

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Readable column width on tablets / foldables. */
val SealContentMaxWidth: Dp = 720.dp

/** Treat as tablet / expanded when width is at least this many dp. */
private const val ExpandedWidthDp = 600

@Composable
fun isExpandedWidth(): Boolean =
    LocalConfiguration.current.screenWidthDp >= ExpandedWidthDp

/**
 * Centers content and caps width so forms/lists don't stretch edge-to-edge on tablets.
 */
fun Modifier.sealContentColumn(maxWidth: Dp = SealContentMaxWidth): Modifier =
    this
        .fillMaxWidth()
        .wrapContentWidth(align = Alignment.CenterHorizontally)
        .widthIn(max = maxWidth)
        .fillMaxWidth()
