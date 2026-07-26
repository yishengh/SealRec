package com.yishenghuang.sealrec.core.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CrashRecoveryTest {

    @Test
    fun listIncomplete_onlyNonEmptyRaw() {
        val root = File("build/test-output/crash").also {
            it.deleteRecursively()
            it.mkdirs()
        }
        val dir = CrashRecovery.inProgressDir(root).also { it.mkdirs() }
        File(dir, "a.raw").writeBytes(ByteArray(10))
        File(dir, "empty.raw").writeBytes(ByteArray(0))
        File(dir, "x.txt").writeText("nope")

        val list = CrashRecovery.listIncomplete(root)
        assertEquals(1, list.size)
        assertTrue(list.first().name == "a.raw")
    }

    @Test
    fun computeRms_silenceIsLow() {
        val silence = ByteArray(200)
        val rms = SealEngine.computeRms(silence)
        assertTrue(rms < 0.01f)
    }
}
