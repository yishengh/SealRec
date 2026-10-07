package com.yishenghuang.sealrec

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yishenghuang.sealrec.core.audio.*
import com.yishenghuang.sealrec.core.crypto.KeystoreManager
import com.yishenghuang.sealrec.core.pipeline.*
import com.yishenghuang.sealrec.core.verify.*
import com.yishenghuang.sealrec.core.wav.WavIO
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RecordingLifecycleTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun directory() = File(context.cacheDir, "synthetic_${UUID.randomUUID()}").also { it.mkdirs() }
    private class SyntheticCapture(private val failAfter: Int = Int.MAX_VALUE) : PcmCapture {
        override val bufferSizeBytes = 320
        private var reads = 0
        override fun start() { reads = 0 }
        override fun read(buffer: ByteArray): Int {
            Thread.sleep(2)
            if (reads++ >= failAfter) return -3
            buffer.fill(0) // Explicit synthetic silence, never host audio.
            return buffer.size
        }
        override fun stop() = Unit
    }

    @Test fun lengthLimitPausesWithoutDroppingWrittenFrames() = runBlocking<Unit> {
        val dir = directory()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val engine = SealEngine(scope, audioConfig = AudioConfig(16000), captureFactory = { SyntheticCapture() }, maxPcmBytes = 9600)
        val raw = File(dir, "limit.raw")
        try {
            engine.start(raw)
            withTimeout(5000) { while (!engine.error.value) delay(10) }
            assertEquals(9600L, raw.length())
            assertEquals(300L, engine.elapsedMs.value)
            engine.stopAndSeal(File(dir, "limit.wav"))
        } finally { engine.close(); scope.cancel(); dir.deleteRecursively() }
    }

    @Test fun lowStorageRefusesCaptureBeforeOpeningMicrophone() = runBlocking<Unit> {
        val dir = directory()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var opened = false
        val engine = SealEngine(scope, captureFactory = { opened = true; SyntheticCapture() }, availableBytes = { 0L })
        try {
            try { engine.start(File(dir, "full.raw")); fail() } catch (_: IllegalStateException) { }
            assertFalse(opened)
            assertEquals(SealEngineState.Idle, engine.state.value)
        } finally { engine.close(); scope.cancel(); dir.deleteRecursively() }
    }

    @Test fun startPauseResumeSaveAndVerifyForEveryQuality() = runBlocking<Unit> {
        for (rate in listOf(16000, 44100, 48000)) {
            val dir = directory()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val key = KeystoreManager("test_${UUID.randomUUID()}")
            val engine = SealEngine(scope, key, AudioConfig(rate), { SyntheticCapture() })
            val raw = File(dir, "record.raw")
            try {
                engine.start(raw)
                withTimeout(5000) { while (engine.elapsedMs.value < 200) delay(10) }
                engine.pause()
                val pausedBytes = raw.length()
                val pausedTime = engine.elapsedMs.value
                delay(50)
                assertEquals(pausedBytes, raw.length())
                assertEquals(pausedTime, engine.elapsedMs.value)
                engine.start(raw)
                withTimeout(5000) { while (engine.elapsedMs.value <= pausedTime + 200) delay(10) }
                val wav = File(dir, "saved.wav")
                engine.stopAndSeal(wav)
                assertEquals(SealEngineState.Idle, engine.state.value)
                assertTrue(raw.exists()) // Await database acknowledgement before deleting raw.
                val layout = WavIO.inspect(wav)
                assertEquals(rate, layout.format.sampleRate)
                assertEquals(raw.length() * 1000 / (rate * 2), layout.durationMs.toLong())
                assertEquals(IntegrityStatus.Intact, VerifyUseCase(key).verify(wav).status)
                engine.acknowledgeSaved()
                assertFalse(raw.exists())
            } finally { engine.close(); scope.cancel(); dir.deleteRecursively() }
        }
    }

    @Test fun readFailurePausesAndCanSaveCapturedAudio() = runBlocking<Unit> {
        val dir = directory()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val engine = SealEngine(scope, audioConfig = AudioConfig(16000), captureFactory = { SyntheticCapture(30) })
        try {
            engine.start(File(dir, "failure.raw"))
            withTimeout(5000) { while (!engine.error.value) delay(10) }
            assertEquals(SealEngineState.Paused, engine.state.value)
            val wav = File(dir, "saved.wav")
            engine.stopAndSeal(wav)
            assertEquals(300, WavIO.inspect(wav).durationMs)
        } finally { engine.close(); scope.cancel(); dir.deleteRecursively() }
    }

    @Test fun failedSaveRetainsRecoverableSourceAndFormat() = runBlocking<Unit> {
        val dir = directory()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val engine = SealEngine(scope, audioConfig = AudioConfig(48000), captureFactory = { SyntheticCapture() })
        val raw = File(dir, "capture.raw")
        try {
            engine.start(raw)
            withTimeout(5000) { while (engine.elapsedMs.value < 200) delay(10) }
            val occupied = File(dir, "existing.wav").also { it.writeText("preserve") }
            try { engine.stopAndSeal(occupied); fail("Must refuse overwrite") } catch (_: IllegalArgumentException) { }
            engine.close()
            assertTrue(raw.length() > 0)
            assertEquals(48000, CrashRecovery.readFormat(raw).sampleRate)
            assertEquals("preserve", occupied.readText())
            val repaired = File(dir, "repaired.wav")
            CrashRecovery.repairAndSeal(raw, repaired)
            assertEquals(48000, WavIO.inspect(repaired).format.sampleRate)
            assertEquals(IntegrityStatus.Intact, VerifyUseCase().verify(repaired).status)
        } finally { engine.close(); scope.cancel(); dir.deleteRecursively() }
    }

    @Test fun startFailureClosesCaptureAndPreservesState() = runBlocking<Unit> {
        val dir = directory()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var closed = false
        val engine = SealEngine(scope, captureFactory = { object : PcmCapture {
            override val bufferSizeBytes = 320
            override fun start() { throw SecurityException("Test permission denial") }
            override fun read(buffer: ByteArray) = 0
            override fun stop() { closed = true }
        } })
        try {
            try { engine.start(File(dir, "denied.raw")); fail() } catch (_: SecurityException) { }
            assertTrue(closed)
            assertEquals(SealEngineState.Paused, engine.state.value)
        } finally { engine.close(); scope.cancel(); dir.deleteRecursively() }
    }

    @Test fun recoverySalvagesCompleteFramesWithoutOverwritingSourceEarly() {
        val dir = directory()
        try {
            val raw = File(dir, "partial.raw").also { it.writeBytes(ByteArray(3201)) }
            CrashRecovery.saveFormat(raw, com.yishenghuang.sealrec.core.wav.WavFormat(16000))
            val wav = File(dir, "recovered.wav")
            CrashRecovery.repairAndSeal(raw, wav, deleteSource = false)
            assertEquals(3201L, raw.length())
            assertEquals(3200, WavIO.inspect(wav).dataSize)
            assertEquals(100, WavIO.inspect(wav).durationMs)
            assertEquals(IntegrityStatus.Intact, VerifyUseCase().verify(wav).status)
        } finally { dir.deleteRecursively() }
    }
}
