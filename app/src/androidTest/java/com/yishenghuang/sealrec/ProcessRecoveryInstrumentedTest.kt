package com.yishenghuang.sealrec

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yishenghuang.sealrec.core.audio.*
import com.yishenghuang.sealrec.core.pipeline.*
import com.yishenghuang.sealrec.core.verify.IntegrityStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Two separate instrumentation processes, with adb force-stop between them. */
@RunWith(AndroidJUnit4::class)
class ProcessRecoveryInstrumentedTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SealRecApp
    private val phase get() = InstrumentationRegistry.getArguments().getString("crashPhase")
    @Test fun prepare() = runBlocking<Unit> {
        assumeTrue(phase == "prepare")
        val raw = File(CrashRecovery.inProgressDir(app.filesDir).also { it.mkdirs() }, "process_test.raw")
        check(!raw.exists())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val engine = SealEngine(scope, audioConfig = AudioConfig(16000), captureFactory = {
            object : PcmCapture {
                override val bufferSizeBytes = 320
                override fun start() = Unit
                override fun stop() = Unit
                override fun read(buffer: ByteArray): Int { Thread.sleep(10); buffer.fill(0); return buffer.size }
            }
        })
        engine.start(raw)
        withTimeout(10000) { while (engine.elapsedMs.value < 500) delay(20) }
        assertTrue(raw.length() > 0)
        // Intentionally no pause, stop, or close: the next command terminates the process.
    }

    @Test fun recover() = runBlocking<Unit> {
        assumeTrue(phase == "recover")
        val raw = app.repository.listIncompleteRaws().single { it.name == "process_test.raw" }
        assertEquals(16000, CrashRecovery.readFormat(raw).sampleRate)
        val expected = raw.length() * 1000 / 32000
        val id = app.repository.repairIncomplete(raw)
        val entity = app.repository.observeRecordings().first().single { it.id == id }
        assertEquals(expected, entity.durationMs)
        assertTrue(expected >= 500)
        assertEquals(IntegrityStatus.Intact, app.repository.verifyRecording(id)!!.status)
        assertFalse(raw.exists())
        app.repository.moveToTrash(id)
        app.repository.purgeFromTrash(id)
    }
}
