package com.yishenghuang.sealrec

import android.Manifest
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yishenghuang.sealrec.core.audio.RecordingPlayer
import com.yishenghuang.sealrec.core.pipeline.SealEngineState
import com.yishenghuang.sealrec.core.verify.IntegrityStatus
import com.yishenghuang.sealrec.core.wav.WavIO
import com.yishenghuang.sealrec.service.SealRecordService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Run only on the dedicated emulator started with -no-audio. */
@RunWith(AndroidJUnit4::class)
class CoreFlowInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private suspend fun awaitCondition(condition: () -> Boolean) = withTimeout(10000) {
        while (!condition()) delay(25)
    }

    @Test fun emulatorRecordingFocusBackgroundSaveLibraryPlaybackRenameShareTrash() = runBlocking {
        assumeTrue(Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish"))
        // Require an explicit runner argument in addition to the emulator check.
        assumeTrue(InstrumentationRegistry.getArguments().getString("silentEmulator") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = compose.activity
        val app = context.application as SealRecApp
        fun grant(permission: String) {
            if (Build.VERSION.SDK_INT >= 28) {
                instrumentation.uiAutomation.grantRuntimePermission(context.packageName, permission)
            } else {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(
                    instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} $permission")
                ).use { it.readBytes() }
            }
        }
        grant(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) grant(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT <= 28) {
            grant(Manifest.permission.READ_EXTERNAL_STORAGE)
            grant(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val player = RecordingPlayer(context, scope)
        var savedId: Long? = null
        try {
            instrumentation.runOnMainSync { SealRecordService.start(context); SealRecordService.start(context) }
            awaitCondition { app.engine.value?.state?.value == SealEngineState.Recording }
            val engine = app.engine.value!!
            awaitCondition { engine.elapsedMs.value >= 700 }
            instrumentation.runOnMainSync { SealRecordService.pause(context) }
            awaitCondition { engine.state.value == SealEngineState.Paused }
            val paused = engine.elapsedMs.value
            delay(200)
            assertEquals(paused, engine.elapsedMs.value)
            instrumentation.runOnMainSync { SealRecordService.resume(context) }
            awaitCondition { engine.elapsedMs.value > paused + 500 }

            // A competing focus owner pauses capture; regaining focus must not auto-record.
            val manager = context.getSystemService(AudioManager::class.java)
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                .setOnAudioFocusChangeListener { }.build()
            instrumentation.runOnMainSync { assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, manager.requestAudioFocus(request)) }
            awaitCondition { engine.state.value == SealEngineState.Paused }
            manager.abandonAudioFocusRequest(request)
            delay(200)
            assertEquals(SealEngineState.Paused, engine.state.value)
            instrumentation.runOnMainSync { SealRecordService.resume(context) }
            awaitCondition { engine.state.value == SealEngineState.Recording }

            // Lock/background with the microphone foreground service still running.
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_SLEEP").close()
            val beforeLock = engine.elapsedMs.value
            awaitCondition { engine.elapsedMs.value > beforeLock + 1000 }
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
            instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
            instrumentation.runOnMainSync { SealRecordService.stop(context); SealRecordService.stop(context) }
            awaitCondition { !app.recordingSessionActive.value }
            val saved = withTimeout(10000) { app.repository.observeRecordings().first { it.isNotEmpty() }.first() }
            savedId = saved.id
            assertTrue(saved.durationMs >= 2000)
            assertEquals(IntegrityStatus.Intact, app.repository.verifyRecording(saved.id)!!.status)
            assertEquals(saved.durationMs, WavIO.inspect(File(saved.filePath)).durationMs.toLong())

            player.play(saved.id, File(saved.filePath))
            awaitCondition { player.state.value.isPlaying }
            player.pause()
            awaitCondition { !player.state.value.isPlaying }
            player.seekTo(500)
            awaitCondition { player.state.value.positionMs == 500 }
            player.resume()
            awaitCondition { player.state.value.isPlaying }
            awaitCondition { !player.state.value.isPlaying }
            assertEquals(player.state.value.durationMs, player.state.value.positionMs)
            player.resume()
            awaitCondition { player.state.value.isPlaying && player.state.value.positionMs < player.state.value.durationMs }
            player.stopAndWait()

            assertTrue(app.repository.renameRecording(saved.id, "Test_${saved.id}.wav"))
            assertTrue(app.repository.renameRecording(saved.id, "test_${saved.id}.wav"))
            assertFalse(app.repository.renameRecording(saved.id, " "))
            val uris = app.repository.shareUris(saved.id)
            assertEquals(2, uris.size)
            uris.forEach { assertTrue(it.scheme == "content"); context.contentResolver.openInputStream(it)!!.use { stream -> assertTrue(stream.read() >= 0) } }
            val renamed = app.repository.observeRecordings().first().single { it.id == saved.id }
            val exported = app.repository.exportToMusic(File(renamed.filePath))!!
            var repeatedExport: android.net.Uri? = null
            try {
                context.contentResolver.openInputStream(exported)!!.use { stream ->
                    val magic = ByteArray(4)
                    assertEquals(4, stream.read(magic))
                    assertEquals("RIFF", String(magic))
                }
                repeatedExport = app.repository.exportToMusic(File(renamed.filePath))!!
                assertNotEquals(exported, repeatedExport)
                assertArrayEquals(
                    context.contentResolver.openInputStream(exported)!!.use { it.readBytes() },
                    context.contentResolver.openInputStream(repeatedExport)!!.use { it.readBytes() }
                )
            } finally {
                context.contentResolver.delete(exported, null, null)
                repeatedExport?.let { context.contentResolver.delete(it, null, null) }
                val downloads = if (Build.VERSION.SDK_INT >= 29) android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    else android.provider.MediaStore.Files.getContentUri("external")
                val base = File(renamed.filePath).nameWithoutExtension
                context.contentResolver.query(downloads, arrayOf("_id"), "_display_name IN (?, ?)", arrayOf("$base.json", "$base (1).json"), null)?.use { rows ->
                    assertEquals(if (repeatedExport == null) 1 else 2, rows.count)
                    while (rows.moveToNext()) {
                        val jsonUri = android.content.ContentUris.withAppendedId(downloads, rows.getLong(0))
                        try {
                            val json = context.contentResolver.openInputStream(jsonUri)!!.bufferedReader().use { it.readText() }
                            assertEquals(renamed.fileName, org.json.JSONObject(json).getString("fileName"))
                        } finally { context.contentResolver.delete(jsonUri, null, null) }
                    }
                }
            }
            app.repository.moveToTrash(saved.id)
            assertTrue(app.repository.observeTrash().first().any { it.id == saved.id })
            app.repository.restoreFromTrash(saved.id)
            assertTrue(app.repository.observeRecordings().first().any { it.id == saved.id })
            app.repository.moveToTrash(saved.id)
            app.repository.purgeFromTrash(saved.id)
            assertFalse(app.repository.observeTrash().first().any { it.id == saved.id })
            savedId = null
        } finally {
            player.stopAndWait(); scope.cancel()
            savedId?.let { app.repository.moveToTrash(it); app.repository.purgeFromTrash(it) }
            if (app.recordingSessionActive.value) instrumentation.runOnMainSync { SealRecordService.cancel(context) }
        }
    }
}
