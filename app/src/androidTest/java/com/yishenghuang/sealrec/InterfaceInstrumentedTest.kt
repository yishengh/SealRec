package com.yishenghuang.sealrec

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yishenghuang.sealrec.data.AppLanguage
import com.yishenghuang.sealrec.data.NightModeOption
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class InterfaceInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun navigationLanguageThemeAndAccessibleControls() {
        val app = compose.activity.application as SealRecApp
        try {
            compose.onNodeWithContentDescription("Record").assertIsDisplayed()
            compose.onNodeWithText("Library").performClick()
            compose.onNodeWithText("Local sealed files · offline verify anytime").assertIsDisplayed()
            compose.onNode(hasText("Verify") and SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Tab)).performClick()
            compose.onNodeWithText("Choose file").assertIsDisplayed()
            compose.onNodeWithText("Settings").performClick()
            compose.onNodeWithText("简体中文").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("语言").fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText("设置") and hasClickAction()).assertIsSelected()
            compose.onNodeWithText("English").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Language").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Dark", substring = false).performScrollTo().performClick()
            compose.onNode(hasText("Settings") and hasClickAction()).assertIsSelected()
            compose.onNodeWithText("About SealRec").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()
            compose.onNodeWithText("Record", substring = false).performClick()
            compose.onNodeWithContentDescription("Record").assertIsDisplayed()
            compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
                File(app.cacheDir, "interface-dark.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        } finally {
            runBlocking {
                app.settingsRepository.setLanguage(AppLanguage.English)
                app.settingsRepository.setNightMode(NightModeOption.FollowSystem)
            }
        }
    }
}
