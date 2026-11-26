package com.stuchess.app.engine

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.github.junrar.Archive
import com.github.junrar.exception.UnsupportedRarV5Exception
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Properties
import java.util.zip.ZipInputStream

enum class BotArchiveFormat(val label: String) {
    TAR_GZ("TAR.GZ"),
    ZIP("ZIP"),
    RAR("RAR"),
    BUILT_IN("BUILT-IN")
}

enum class BotProfileKind {
    BUILT_IN,
    EXECUTABLE,
    NNUE_NETWORK,
    SOURCE_ONLY
}

data class BotEngineProfile(
    val id: String,
    val name: String,
    val archiveFormat: BotArchiveFormat,
    val kind: BotProfileKind,
    val executablePath: String? = null,
    val evalFilePath: String? = null,
    val sourceDirPath: String? = null,
    val sourceFileCount: Int = 0,
    val statusNote: String = ""
) {
    val isRunnable: Boolean
        get() = kind != BotProfileKind.SOURCE_ONLY

    companion object {
        const val BUILT_IN_ID = "builtin_stockfish"

        fun builtIn(): BotEngineProfile = BotEngineProfile(
            id = BUILT_IN_ID,
            name = "Stockfish (Built-in)",
            archiveFormat = BotArchiveFormat.BUILT_IN,
            kind = BotProfileKind.BUILT_IN,
            statusNote = "Bundled UCI engine"
        )
    }
}

data class BotImportResult(
    val profile: BotEngineProfile?,
    val message: String
)

class BotArchiveManager(
    private val botsRootDir: File,
    private val supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList()
) {
    constructor(context: Context) : this(
        botsRootDir = File(context.filesDir, "bots"),
        supportedAbis = Build.SUPPORTED_ABIS.toList()
    )

    fun listProfiles(): List<BotEngineProfile> {
        val profiles = mutableListOf(BotEngineProfile.builtIn())
        if (!botsRootDir.exists()) return profiles

        botsRootDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name.lowercase() }
            ?.forEach { dir ->
                readProfile(dir)?.let { profiles.add(it) }
            }
        return profiles
    }

    fun findProfile(id: String): BotEngineProfile {
        return listProfiles().firstOrNull { it.id == id } ?: BotEngineProfile.builtIn()
    }

    fun deleteProfile(id: String): Boolean {
        if (id == BotEngineProfile.BUILT_IN_ID) return false
        val target = File(botsRootDir, id)
        if (!target.exists()) return false
        return target.deleteRecursively()
    }

    fun importFromUri(context: Context, uri: Uri): BotImportResult {
        val displayName = resolveDisplayName(context, uri)
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "bot_${System.currentTimeMillis() % 10000}"

        val cacheFile = File(context.cacheDir, "bot_import_${System.currentTimeMillis()}.tmp")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return BotImportResult(null, "Unable to read selected archive")

            importArchiveFile(cacheFile, displayName)
        } catch (e: Exception) {
            BotImportResult(null, "Bot import failed: ${e.message ?: "unknown error"}")
        } finally {
            cacheFile.delete()
        }
    }

    fun importArchiveFile(archiveFile: File, originalFileName: String): BotImportResult {
        val format = detectArchiveFormat(archiveFile, originalFileName)
            ?: return BotImportResult(
                null,
                "Unsupported format. Please select a .tar.gz, .zip, or .rar archive."
            )

        val cleanName = stripArchiveExtension(originalFileName)
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .trim('_')
            .take(32)
            .ifBlank { "bot_${System.currentTimeMillis() % 10000}" }

        botsRootDir.mkdirs()
        val targetDir = File(botsRootDir, cleanName)
        if (targetDir.exists()) targetDir.deleteRecursively()
        targetDir.mkdirs()

        val extractedDir = File(targetDir, "content").apply { mkdirs() }

        return try {
            when (format) {
                BotArchiveFormat.ZIP -> extractZip(archiveFile, extractedDir)
                BotArchiveFormat.TAR_GZ -> extractTarGz(archiveFile, extractedDir)
                BotArchiveFormat.RAR -> extractRar(archiveFile, extractedDir)
                BotArchiveFormat.BUILT_IN -> error("Invalid archive format")
            }

            val profile = inspectAndBuildProfile(cleanName, format, extractedDir)
            if (profile.executablePath == null &&
                profile.evalFilePath == null &&
                profile.sourceFileCount == 0
            ) {
                targetDir.deleteRecursively()
                return BotImportResult(
                    null,
                    "No UCI binary, .nnue network, or C/C++ bot source found in archive"
                )
            }

            writeProfile(targetDir, profile)
            BotImportResult(profile, "Imported '${profile.name}': ${profile.statusNote}")
        } catch (e: UnsupportedRarV5Exception) {
            targetDir.deleteRecursively()
            BotImportResult(
                null,
                "RAR5 archives are not supported on-device. Re-pack as .zip or .tar.gz."
            )
        } catch (e: Exception) {
            targetDir.deleteRecursively()
            BotImportResult(null, "Import failed: ${e.message ?: "corrupted archive"}")
        }
    }

    private fun detectArchiveFormat(file: File, fileName: String): BotArchiveFormat? {
        val lower = fileName.lowercase()
        when {
            lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> return BotArchiveFormat.TAR_GZ
            lower.endsWith(".zip") -> return BotArchiveFormat.ZIP
            lower.endsWith(".rar") -> return BotArchiveFormat.RAR
        }
        val header = ByteArray(8)
        val read = FileInputStream(file).use { it.read(header) }
        if (read >= 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()) {
            return BotArchiveFormat.ZIP
        }
        if (read >= 2 && header[0] == 0x1F.toByte() && header[1] == 0x8B.toByte()) {
            return BotArchiveFormat.TAR_GZ
        }
        if (read >= 4 &&
            header[0] == 0x52.toByte() &&
            header[1] == 0x61.toByte() &&
            header[2] == 0x72.toByte() &&
            header[3] == 0x21.toByte()
        ) {
            return BotArchiveFormat.RAR
        }
        return null
    }

    private fun stripArchiveExtension(fileName: String): String {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".tar.gz") -> fileName.dropLast(7)
            lower.endsWith(".tgz") -> fileName.dropLast(4)
            lower.endsWith(".zip") -> fileName.dropLast(4)
            lower.endsWith(".rar") -> fileName.dropLast(4)
            else -> fileName.substringBeforeLast('.')
        }
    }

    private fun extractZip(archiveFile: File, destDir: File) {
        val canonicalDest = destDir.canonicalPath + File.separator
        ZipInputStream(BufferedInputStream(FileInputStream(archiveFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val outFile = safeResolve(destDir, canonicalDest, entry.name)
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { out -> zis.copyTo(out) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun extractTarGz(archiveFile: File, destDir: File) {
        val canonicalDest = destDir.canonicalPath + File.separator
        FileInputStream(archiveFile).use { fis ->
            GzipCompressorInputStream(BufferedInputStream(fis)).use { gzis ->
                TarArchiveInputStream(gzis).use { tis ->
                    var entry = tis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && !entry.isSymbolicLink && !entry.isLink) {
                            val outFile = safeResolve(destDir, canonicalDest, entry.name)
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { out -> tis.copyTo(out) }
                        }
                        entry = tis.nextEntry
                    }
                }
            }
        }
    }

    private fun extractRar(archiveFile: File, destDir: File) {
        val canonicalDest = destDir.canonicalPath + File.separator
        Archive(archiveFile).use { archive ->
            var header = archive.nextFileHeader()
            while (header != null) {
                if (!header.isDirectory) {
                    val normalized = header.fileName.replace('\\', '/')
                    val outFile = safeResolve(destDir, canonicalDest, normalized)
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { out -> archive.extractFile(header, out) }
                }
                header = archive.nextFileHeader()
            }
        }
    }

    private fun safeResolve(destDir: File, canonicalDest: String, entryName: String): File {
        val sanitized = entryName.replace('\\', '/').trimStart('/')
        val outFile = File(destDir, sanitized)
        val canonicalOut = outFile.canonicalPath
        if (!canonicalOut.startsWith(canonicalDest)) {
            throw SecurityException("Blocked invalid archive entry path: $entryName")
        }
        return outFile
    }

    private fun inspectAndBuildProfile(
        id: String,
        format: BotArchiveFormat,
        extractedDir: File
    ): BotEngineProfile {
        val allFiles = extractedDir.walkTopDown().filter { it.isFile }.toList()

        val elfCandidates = allFiles.mapNotNull { file ->
            val abi = detectElfAndroidAbi(file)
            if (abi != null) file to abi else null
        }
        val compatibleElf = elfCandidates.firstOrNull { (_, abi) -> abi in supportedAbis }
        compatibleElf?.first?.setExecutable(true, false)

        val nnueFile = allFiles
            .filter { it.name.lowercase().endsWith(".nnue") }
            .maxByOrNull { it.length() }

        val makefile = allFiles.firstOrNull {
            it.name == "Makefile" || it.name == "CMakeLists.txt"
        }
        val sourceFiles = allFiles.filter { f ->
            val ext = f.extension.lowercase()
            ext in SOURCE_EXTENSIONS || f.name == "Makefile" || f.name == "CMakeLists.txt"
        }
        val sourceDir = makefile?.parentFile ?: sourceFiles.firstOrNull()?.parentFile

        val (kind, status) = when {
            compatibleElf != null -> {
                val abiLabel = compatibleElf.second
                val extra = if (sourceFiles.isNotEmpty()) " + ${sourceFiles.size} src files" else ""
                BotProfileKind.EXECUTABLE to "$abiLabel binary$extra"
            }
            nnueFile != null -> {
                BotProfileKind.NNUE_NETWORK to "NNUE network (${nnueFile.name})"
            }
            sourceFiles.isNotEmpty() -> {
                val archNote = if (elfCandidates.isNotEmpty()) {
                    " (binary ABI mismatch: ${elfCandidates.first().second})"
                } else ""
                BotProfileKind.SOURCE_ONLY to "Source (${sourceFiles.size} files) · built-in engine$archNote"
            }
            else -> BotProfileKind.SOURCE_ONLY to "Empty archive"
        }

        return BotEngineProfile(
            id = id,
            name = id,
            archiveFormat = format,
            kind = kind,
            executablePath = compatibleElf?.first?.absolutePath,
            evalFilePath = nnueFile?.absolutePath,
            sourceDirPath = sourceDir?.absolutePath,
            sourceFileCount = sourceFiles.size,
            statusNote = status
        )
    }

    private fun detectElfAndroidAbi(file: File): String? {
        if (file.length() < 64) return null
        val header = ByteArray(20)
        val read = try {
            FileInputStream(file).use { it.read(header) }
        } catch (_: Exception) {
            return null
        }
        if (read < 20) return null
        if (header[0] != 0x7F.toByte() ||
            header[1] != 0x45.toByte() ||
            header[2] != 0x4C.toByte() ||
            header[3] != 0x46.toByte()
        ) {
            return null
        }
        val machine = (header[18].toInt() and 0xFF) or ((header[19].toInt() and 0xFF) shl 8)
        return when (machine) {
            183 -> "arm64-v8a"
            40 -> "armeabi-v7a"
            62 -> "x86_64"
            3 -> "x86"
            else -> null
        }
    }

    private fun writeProfile(botDir: File, profile: BotEngineProfile) {
        val props = Properties()
        props.setProperty("id", profile.id)
        props.setProperty("name", profile.name)
        props.setProperty("archiveFormat", profile.archiveFormat.name)
        props.setProperty("kind", profile.kind.name)
        props.setProperty("executablePath", profile.executablePath ?: "")
        props.setProperty("evalFilePath", profile.evalFilePath ?: "")
        props.setProperty("sourceDirPath", profile.sourceDirPath ?: "")
        props.setProperty("sourceFileCount", profile.sourceFileCount.toString())
        props.setProperty("statusNote", profile.statusNote)
        File(botDir, PROFILE_FILE_NAME).outputStream().use { out ->
            props.store(out, "stuchess bot profile")
        }
    }

    private fun readProfile(botDir: File): BotEngineProfile? {
        val metaFile = File(botDir, PROFILE_FILE_NAME)
        if (!metaFile.exists()) return null
        return try {
            val props = Properties()
            FileInputStream(metaFile).use { props.load(it) }
            val id = props.getProperty("id").orEmpty().ifBlank { botDir.name }
            val name = props.getProperty("name").orEmpty().ifBlank { id }
            val format = runCatching {
                BotArchiveFormat.valueOf(props.getProperty("archiveFormat", "ZIP"))
            }.getOrDefault(BotArchiveFormat.ZIP)
            val kind = runCatching {
                BotProfileKind.valueOf(props.getProperty("kind", "SOURCE_ONLY"))
            }.getOrDefault(BotProfileKind.SOURCE_ONLY)

            BotEngineProfile(
                id = id,
                name = name,
                archiveFormat = format,
                kind = kind,
                executablePath = props.getProperty("executablePath")?.ifBlank { null },
                evalFilePath = props.getProperty("evalFilePath")?.ifBlank { null },
                sourceDirPath = props.getProperty("sourceDirPath")?.ifBlank { null },
                sourceFileCount = props.getProperty("sourceFileCount")?.toIntOrNull() ?: 0,
                statusNote = props.getProperty("statusNote").orEmpty()
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private val SOURCE_EXTENSIONS = setOf("cpp", "cc", "cxx", "c", "h", "hpp", "rs", "go")
        private const val PROFILE_FILE_NAME = "profile.properties"
    }
}
