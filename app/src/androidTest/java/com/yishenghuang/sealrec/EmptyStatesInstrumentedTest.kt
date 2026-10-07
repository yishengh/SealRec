package com.yishenghuang.sealrec

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.yishenghuang.sealrec.core.audio.PlaybackState
import com.yishenghuang.sealrec.ui.LibraryScreen
import com.yishenghuang.sealrec.ui.TrashScreen
import com.yishenghuang.sealrec.ui.theme.SealRecTheme
import org.junit.Rule
import org.junit.Test

class EmptyStatesInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    @Test fun emptyLibraryHasReadableGuidance() {
        compose.setContent {
            SealRecTheme { LibraryScreen(emptyList(), PlaybackState(), true,
                onPlayToggle = {}, onSeek = {}, onRename = { _, _ -> }, onVerify = {}, onExport = {}, onShare = {}, onDelete = {}) }
        }
        compose.onNodeWithText("No recordings yet").assertIsDisplayed()
    }
    @Test fun emptyTrashHasNoDestructiveAction() {
        compose.setContent { SealRecTheme { TrashScreen(emptyList(), {}, {}, {}, {}) } }
        compose.onNodeWithText("Recycle bin is empty").assertIsDisplayed()
        compose.onNodeWithText("Empty recycle bin").assertDoesNotExist()
    }
}
