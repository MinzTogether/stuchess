package com.stuchess.app.engine

import android.content.Context
import android.os.Build
import com.stuchess.app.game.PieceColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

enum class EngineAvailability {
    READY,
    MISSING_BINARY,
    ERROR
}

data class AnalysisLine(
    val multiPv: Int,
    val depth: Int,
    val score: EvalScore,
    val pv: List<String>
)

data class SearchResult(
    val bestMove: String?,
    val lines: List<AnalysisLine>
)

class StockfishEngine(private val context: Context) {
    private val commandMutex = Mutex()
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private var activeExecutablePath: String? = null
    private var activeEvalFilePath: String? = null

    @Volatile
    var availability: EngineAvailability = EngineAvailability.MISSING_BINARY
        private set

    @Volatile
    var lastError: String? = null
        private set

    fun builtInBinaryPath(): File = File(context.applicationInfo.nativeLibraryDir, "libstockfish.so")

    suspend fun start(
        customExecutable: File? = null,
        evalFilePath: String? = null
    ): EngineAvailability = commandMutex.withLock {
        withContext(Dispatchers.IO) {
            val targetBinary = customExecutable ?: builtInBinaryPath()
            if (process?.isAlive == true &&
                availability == EngineAvailability.READY &&
                activeExecutablePath == targetBinary.absolutePath &&
                activeEvalFilePath == evalFilePath
            ) {
                return@withContext EngineAvailability.READY
            }

            stopInternal()

            if (!targetBinary.exists()) {
                availability = EngineAvailability.MISSING_BINARY
                lastError = "Missing ${targetBinary.name} in ${targetBinary.parent}"
                return@withContext availability
            }
            if (!targetBinary.canExecute()) {
                targetBinary.setExecutable(true, false)
            }

            try {
                val pb = ProcessBuilder(targetBinary.absolutePath)
                pb.redirectErrorStream(true)
                val proc = pb.start()
                process = proc
                writer = BufferedWriter(OutputStreamWriter(proc.outputStream))
                reader = BufferedReader(InputStreamReader(proc.inputStream))

                sendRaw("uci")
                val uciOk = readUntil { it == "uciok" }
                if (!uciOk || !proc.isAlive) {
                    throw IllegalStateException("Engine exited before uciok")
                }
                if (!evalFilePath.isNullOrBlank()) {
                    sendRaw("setoption name EvalFile value $evalFilePath")
                }
                sendRaw("isready")
                val readyOk = readUntil { it == "readyok" }
                if (!readyOk || !proc.isAlive) {
                    throw IllegalStateException("Engine exited before readyok")
                }

                activeExecutablePath = targetBinary.absolutePath
                activeEvalFilePath = evalFilePath
                availability = EngineAvailability.READY
                lastError = null
                availability
            } catch (e: Exception) {
                availability = EngineAvailability.ERROR
                val rawMessage = e.message ?: "Failed to start UCI engine"
                lastError = if (customExecutable != null && rawMessage.contains("Permission denied", ignoreCase = true)) {
                    "Android blocked external binary; using built-in engine"
                } else {
                    rawMessage
                }
                stopInternal()
                availability
            }
        }
    }

    suspend fun configure(
        threads: Int,
        hashMb: Int,
        limitStrength: Boolean = false,
        elo: Int = 1500,
        multiPv: Int = 1
    ) = commandMutex.withLock {
        withContext(Dispatchers.IO) {
            if (process?.isAlive != true) return@withContext
            val safeThreads = threads.coerceIn(1, Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
            val maxHash = if (is64BitDevice()) 256 else 64
            val safeHash = hashMb.coerceIn(8, maxHash)
            val safeElo = elo.coerceIn(1320, 3190)

            sendRaw("setoption name Threads value $safeThreads")
            sendRaw("setoption name Hash value $safeHash")
            sendRaw("setoption name MultiPV value ${multiPv.coerceIn(1, 3)}")
            sendRaw("setoption name UCI_LimitStrength value $limitStrength")
            if (limitStrength) {
                sendRaw("setoption name UCI_Elo value $safeElo")
            }
            sendRaw("isready")
            val readyOk = readUntil { it == "readyok" }
            if (!readyOk || process?.isAlive != true) {
                availability = EngineAvailability.ERROR
                lastError = "Engine stopped responding during configure"
                stopInternal()
            }
        }
    }

    suspend fun newGame() = commandMutex.withLock {
        withContext(Dispatchers.IO) {
            if (process?.isAlive != true) return@withContext
            sendRaw("ucinewgame")
            sendRaw("isready")
            val readyOk = readUntil { it == "readyok" }
            if (!readyOk || process?.isAlive != true) {
                availability = EngineAvailability.ERROR
                lastError = "Engine stopped responding during ucinewgame"
                stopInternal()
            }
        }
    }

    suspend fun search(
        fen: String,
        sideToMove: PieceColor,
        moveTimeMs: Int? = null,
        depth: Int? = null,
        onLineUpdate: ((AnalysisLine) -> Unit)? = null
    ): SearchResult = commandMutex.withLock {
        withContext(Dispatchers.IO) {
            if (process?.isAlive != true) {
                return@withContext SearchResult(null, emptyList())
            }
            try {
                sendRaw("position fen $fen")
                val goCmd = when {
                    depth != null -> "go depth $depth"
                    moveTimeMs != null -> "go movetime $moveTimeMs"
                    else -> "go movetime 500"
                }
                sendRaw(goCmd)

                val bestByPv = LinkedHashMap<Int, AnalysisLine>()
                var bestMove: String? = null
                val r = reader ?: return@withContext SearchResult(null, emptyList())

                while (true) {
                    val line = r.readLine() ?: break
                    if (line.startsWith("info ") && line.contains(" score ") && line.contains(" pv ")) {
                        parseInfoLine(line, sideToMove)?.let { parsed ->
                            bestByPv[parsed.multiPv] = parsed
                            onLineUpdate?.invoke(parsed)
                        }
                    } else if (line.startsWith("bestmove ")) {
                        val tokens = line.split(' ')
                        val candidate = tokens.getOrNull(1)
                        if (candidate != null && candidate != "(none)") {
                            bestMove = candidate
                        }
                        break
                    }
                }
                if (bestMove == null && process?.isAlive != true) {
                    availability = EngineAvailability.ERROR
                    lastError = "Engine terminated during search"
                    stopInternal()
                }
                SearchResult(bestMove, bestByPv.values.sortedBy { it.multiPv })
            } catch (e: Exception) {
                availability = EngineAvailability.ERROR
                lastError = e.message ?: "Engine search error"
                stopInternal()
                SearchResult(null, emptyList())
            }
        }
    }

    fun requestStopSearch() {
        try {
            writer?.apply {
                write("stop\n")
                flush()
            }
        } catch (_: Exception) {
        }
    }

    fun shutdown() {
        stopInternal()
    }

    private fun stopInternal() {
        try {
            writer?.apply {
                write("quit\n")
                flush()
            }
        } catch (_: Exception) {
        }
        try {
            process?.destroy()
        } catch (_: Exception) {
        }
        process = null
        writer = null
        reader = null
        activeExecutablePath = null
        activeEvalFilePath = null
    }

    private fun sendRaw(command: String) {
        val w = writer ?: return
        w.write(command)
        w.newLine()
        w.flush()
    }

    private fun readUntil(predicate: (String) -> Boolean): Boolean {
        val r = reader ?: return false
        while (true) {
            val line = r.readLine() ?: return false
            if (predicate(line.trim())) return true
        }
    }

    private fun parseInfoLine(line: String, sideToMove: PieceColor): AnalysisLine? {
        val tokens = line.split(Regex("\\s+"))
        var depth = 0
        var multiPv = 1
        var cp: Int? = null
        var mate: Int? = null
        var pvStart = -1

        var i = 0
        while (i < tokens.size) {
            when (tokens[i]) {
                "depth" -> depth = tokens.getOrNull(i + 1)?.toIntOrNull() ?: depth
                "multipv" -> multiPv = tokens.getOrNull(i + 1)?.toIntOrNull() ?: multiPv
                "score" -> {
                    val kind = tokens.getOrNull(i + 1)
                    val raw = tokens.getOrNull(i + 2)?.toIntOrNull()
                    if (raw != null) {
                        val normalized = if (sideToMove == PieceColor.WHITE) raw else -raw
                        if (kind == "cp") cp = normalized
                        if (kind == "mate") mate = normalized
                    }
                }
                "pv" -> {
                    pvStart = i + 1
                    break
                }
            }
            i++
        }

        if (pvStart == -1 || (cp == null && mate == null)) return null
        val pv = tokens.subList(pvStart, tokens.size)
        return AnalysisLine(multiPv, depth, EvalScore(cp = cp, mate = mate), pv)
    }

    companion object {
        fun is64BitDevice(): Boolean = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()

        fun defaultThreads(): Int = if (is64BitDevice()) 2 else 1

        fun defaultHashMb(): Int = if (is64BitDevice()) 32 else 16
    }
}
