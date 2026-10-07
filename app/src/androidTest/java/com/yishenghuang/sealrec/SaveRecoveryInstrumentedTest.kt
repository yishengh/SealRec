package com.yishenghuang.sealrec

import androidx.test.platform.app.InstrumentationRegistry
import com.yishenghuang.sealrec.core.pipeline.CrashRecovery
import com.yishenghuang.sealrec.core.wav.WavFormat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SaveRecoveryInstrumentedTest {
    private val repo get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SealRecApp).repository

    @Test fun recoversAfterWavCommitWithoutWritingAnotherCopy() = runBlocking<Unit> {
        val raw = repo.newRawFile().also { it.writeBytes(ByteArray(6400)) }
        CrashRecovery.saveFormat(raw, WavFormat(16000))
        val wav = repo.newWavFile()
        CrashRecovery.repairAndSeal(raw, wav, deleteSource = false)
        val id = repo.repairIncomplete(raw)
        try {
            val entry = repo.observeRecordings().first().single { it.id == id }
            assertEquals(wav.absolutePath, entry.filePath)
            assertEquals(200L, entry.durationMs)
            assertFalse(raw.exists())
        } finally { repo.moveToTrash(id); repo.purgeFromTrash(id) }
    }

    @Test fun recoversAfterDatabaseCommitWithoutDuplicatingRow() = runBlocking<Unit> {
        val raw = repo.newRawFile().also { it.writeBytes(ByteArray(6400)) }
        CrashRecovery.saveFormat(raw, WavFormat(16000))
        val wav = repo.newWavFile()
        val seal = CrashRecovery.repairAndSeal(raw, wav, deleteSource = false)
        val id = repo.registerSealedFile(wav, 200, null, seal.deviceTimeUtcMs)
        try {
            assertEquals(id, repo.repairIncomplete(raw))
            assertEquals(1, repo.observeRecordings().first().count { it.filePath == wav.absolutePath })
            assertFalse(raw.exists())
        } finally { repo.moveToTrash(id); repo.purgeFromTrash(id) }
    }

    @Test fun removesOnlyJournaledPartialCopyBeforeRepair() = runBlocking<Unit> {
        val raw = repo.newRawFile().also { it.writeBytes(ByteArray(6400)) }
        CrashRecovery.saveFormat(raw, WavFormat(16000))
        val abandoned = repo.newWavFile()
        CrashRecovery.markDestination(raw, abandoned)
        val partial = File(abandoned.parentFile, abandoned.name + ".pending").also { it.writeBytes(ByteArray(1000)) }
        val id = repo.repairIncomplete(raw)
        try {
            assertFalse(partial.exists())
            assertFalse(raw.exists())
            assertEquals(200L, repo.observeRecordings().first().single { it.id == id }.durationMs)
        } finally { repo.moveToTrash(id); repo.purgeFromTrash(id) }
    }

    @Test fun renameRefusesCaseInsensitiveConflictInTrash() = runBlocking<Unit> {
        suspend fun recording(): Long {
            val raw = repo.newRawFile().also { it.writeBytes(ByteArray(6400)) }
            CrashRecovery.saveFormat(raw, WavFormat(16000))
            return repo.repairIncomplete(raw)
        }
        val first = recording()
        val second = recording()
        try {
            val name = repo.observeRecordings().first().single { it.id == second }.fileName
            repo.moveToTrash(second)
            assertFalse(repo.renameRecording(first, name.uppercase(java.util.Locale.ROOT)))
        } finally {
            repo.moveToTrash(first); repo.purgeFromTrash(first)
            repo.moveToTrash(second); repo.purgeFromTrash(second)
        }
    }
}
