package com.stuchess.app

import com.stuchess.app.engine.BotArchiveFormat
import com.stuchess.app.engine.BotArchiveManager
import com.stuchess.app.engine.BotProfileKind
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BotArchiveManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun importsZipBotSourceArchive() {
        val botsDir = tempFolder.newFolder("bots_zip")
        val zipFile = tempFolder.newFile("custom-bot.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("custom-bot/src/Makefile"))
            zos.write("all:\n\techo ok\n".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("custom-bot/src/main.cpp"))
            zos.write("int main() { return 0; }\n".toByteArray())
            zos.closeEntry()
        }

        val manager = BotArchiveManager(botsDir, supportedAbis = listOf("arm64-v8a"))
        val result = manager.importArchiveFile(zipFile, "custom-bot.zip")
        assertNotNull(result.profile)
        assertEquals(BotArchiveFormat.ZIP, result.profile!!.archiveFormat)
        assertEquals(BotProfileKind.SOURCE_ONLY, result.profile!!.kind)
        assertEquals(2, result.profile!!.sourceFileCount)
        assertTrue(manager.listProfiles().any { it.id == "custom-bot" })
    }

    @Test
    fun importsTarGzWithArm64ElfBinaryAndSource() {
        val botsDir = tempFolder.newFolder("bots_targz")
        val tarGzFile = tempFolder.newFile("sf-universal.tar.gz")

        val fakeArm64Elf = ByteArray(128)
        fakeArm64Elf[0] = 0x7F
        fakeArm64Elf[1] = 'E'.code.toByte()
        fakeArm64Elf[2] = 'L'.code.toByte()
        fakeArm64Elf[3] = 'F'.code.toByte()
        fakeArm64Elf[18] = 183.toByte() // EM_AARCH64
        fakeArm64Elf[19] = 0

        FileOutputStream(tarGzFile).use { fos ->
            GzipCompressorOutputStream(fos).use { gzos ->
                TarArchiveOutputStream(gzos).use { tos ->
                    val binEntry = TarArchiveEntry("stockfish/stockfish-arm64")
                    binEntry.size = fakeArm64Elf.size.toLong()
                    tos.putArchiveEntry(binEntry)
                    tos.write(fakeArm64Elf)
                    tos.closeArchiveEntry()

                    val srcBytes = "int main() { return 0; }\n".toByteArray()
                    val srcEntry = TarArchiveEntry("stockfish/src/main.cpp")
                    srcEntry.size = srcBytes.size.toLong()
                    tos.putArchiveEntry(srcEntry)
                    tos.write(srcBytes)
                    tos.closeArchiveEntry()
                }
            }
        }

        val manager = BotArchiveManager(botsDir, supportedAbis = listOf("arm64-v8a"))
        val result = manager.importArchiveFile(tarGzFile, "sf-universal.tar.gz")
        assertNotNull(result.profile)
        assertEquals(BotArchiveFormat.TAR_GZ, result.profile!!.archiveFormat)
        assertEquals(BotProfileKind.EXECUTABLE, result.profile!!.kind)
        assertNotNull(result.profile!!.executablePath)
        assertTrue(File(result.profile!!.executablePath!!).exists())
    }

    @Test
    fun blocksZipSlipTraversal() {
        val botsDir = tempFolder.newFolder("bots_slip")
        val zipFile = tempFolder.newFile("evil.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("../../escaped.txt"))
            zos.write("bad".toByteArray())
            zos.closeEntry()
        }

        val manager = BotArchiveManager(botsDir, supportedAbis = listOf("arm64-v8a"))
        val result = manager.importArchiveFile(zipFile, "evil.zip")
        assertNull(result.profile)
        assertTrue(result.message.contains("invalid archive entry", ignoreCase = true))
    }
}
