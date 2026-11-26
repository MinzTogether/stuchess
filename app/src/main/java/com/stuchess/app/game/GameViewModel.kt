package com.stuchess.app.game

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stuchess.app.engine.AnalysisLine
import com.stuchess.app.engine.BotArchiveManager
import com.stuchess.app.engine.BotEngineProfile
import com.stuchess.app.engine.BotProfileKind
import com.stuchess.app.engine.EngineAvailability
import com.stuchess.app.engine.EvalScore
import com.stuchess.app.engine.MoveClassifier
import com.stuchess.app.engine.StockfishEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("stuchess_prefs", Context.MODE_PRIVATE)
    private val botArchiveManager = BotArchiveManager(application)
    private val evalEngine = StockfishEngine(application)
    private val whiteBotEngine = StockfishEngine(application)
    private val blackBotEngine = StockfishEngine(application)

    private val _uiState = MutableStateFlow(loadInitialUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var engineJob: Job? = null
    private var clockJob: Job? = null
    private var lastPreMoveLines: List<AnalysisLine> = emptyList()

    init {
        viewModelScope.launch {
            initializeEngines()
            startClockLoop()
            scheduleEngineWork()
        }
    }

    private fun loadInitialUiState(): GameUiState {
        val defaultThreads = StockfishEngine.defaultThreads()
        val defaultHash = StockfishEngine.defaultHashMb()
        return GameUiState(
            selectedBoardThemeId = prefs.getString("board_theme_id", "green") ?: "green",
            customLightSquareHex = prefs.getLong("custom_light_hex", 0xFFEBECD0),
            customDarkSquareHex = prefs.getLong("custom_dark_hex", 0xFF739552),
            selectedPiecePackId = prefs.getString("piece_pack_id", "classic") ?: "classic",
            availablePiecePackIds = discoverPiecePacks(),
            botProfiles = botArchiveManager.listProfiles(),
            showHints = prefs.getBoolean("show_hints", true),
            engineThreads = prefs.getInt("engine_threads", defaultThreads),
            engineHashMb = prefs.getInt("engine_hash_mb", defaultHash)
        )
    }

    private suspend fun initializeEngines() {
        val status = evalEngine.start()
        val notice = when (status) {
            EngineAvailability.READY -> null
            EngineAvailability.MISSING_BINARY ->
                "Stockfish binary not bundled yet. Using material balance & fallback bot."
            EngineAvailability.ERROR ->
                "Engine error: ${evalEngine.lastError ?: "unknown"}"
        }
        _uiState.update { it.copy(engineAvailability = status, engineNotice = notice) }
    }

    private suspend fun ensureBotEngineReady(engine: StockfishEngine, profileId: String): EngineAvailability {
        val profile = botArchiveManager.findProfile(profileId)

        if (profile.kind == BotProfileKind.EXECUTABLE && !profile.executablePath.isNullOrBlank()) {
            val directStatus = engine.start(
                customExecutable = File(profile.executablePath),
                evalFilePath = profile.evalFilePath
            )
            if (directStatus == EngineAvailability.READY) {
                return EngineAvailability.READY
            }
            val directError = engine.lastError
            val fallbackStatus = startBuiltInWithOptionalEval(engine, profile.evalFilePath)
            if (fallbackStatus == EngineAvailability.READY) {
                _uiState.update {
                    it.copy(
                        statusMessage = directError ?: "Running '${profile.name}' via built-in Stockfish"
                    )
                }
                return EngineAvailability.READY
            }
            return fallbackStatus
        }

        val status = startBuiltInWithOptionalEval(engine, profile.evalFilePath)
        if (status == EngineAvailability.READY && profile.kind == BotProfileKind.SOURCE_ONLY) {
            _uiState.update {
                it.copy(statusMessage = "'${profile.name}' source active via built-in Stockfish")
            }
        } else if (status != EngineAvailability.READY && engine.lastError != null) {
            _uiState.update { it.copy(statusMessage = engine.lastError) }
        }
        return status
    }

    private suspend fun startBuiltInWithOptionalEval(
        engine: StockfishEngine,
        evalFilePath: String?
    ): EngineAvailability {
        if (!evalFilePath.isNullOrBlank()) {
            val withEval = engine.start(customExecutable = null, evalFilePath = evalFilePath)
            if (withEval == EngineAvailability.READY) return EngineAvailability.READY
        }
        return engine.start(customExecutable = null, evalFilePath = null)
    }

    private fun discoverPiecePacks(): List<String> {
        val builtIn = listOf("classic", "minimal")
        val customDir = File(getApplication<Application>().filesDir, "pieces")
        val imported = customDir.listFiles()
            ?.filter { it.isDirectory && hasAllPieceFiles(it) }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
        return builtIn + imported
    }

    private fun hasAllPieceFiles(dir: File): Boolean {
        val existing = dir.listFiles()?.map { it.nameWithoutExtension.lowercase() }?.toSet() ?: emptySet()
        return REQUIRED_PIECE_CODES.all { it in existing }
    }

    fun startNewGame(config: NewGameConfig) {
        engineJob?.cancel()
        evalEngine.requestStopSearch()
        whiteBotEngine.requestStopSearch()
        blackBotEngine.requestStopSearch()
        lastPreMoveLines = emptyList()

        val initialPos = ChessPosition.initial()
        val flip = config.mode == GameMode.BOT_VS_HUMAN && config.humanColor == PieceColor.BLACK

        _uiState.update {
            it.copy(
                config = config,
                position = initialPos,
                positionHistory = listOf(initialPos),
                moveRecords = emptyList(),
                viewingPly = null,
                selectedSquare = null,
                legalTargets = emptyList(),
                pendingPromotion = null,
                boardFlipped = flip,
                hintMove = null,
                hintEval = null,
                currentEval = EvalScore(cp = 20),
                isEngineThinking = false,
                isBotVsBotPaused = false,
                whiteTimeMs = config.timeControl.initialMs,
                blackTimeMs = config.timeControl.initialMs,
                gameResult = GameResult.ONGOING,
                statusMessage = null
            )
        }

        engineJob = viewModelScope.launch {
            if (evalEngine.availability == EngineAvailability.READY) {
                evalEngine.newGame()
            }
            when (config.mode) {
                GameMode.HUMAN_VS_HUMAN -> {
                    whiteBotEngine.shutdown()
                    blackBotEngine.shutdown()
                }
                GameMode.BOT_VS_HUMAN -> {
                    if (config.humanColor == PieceColor.WHITE) {
                        whiteBotEngine.shutdown()
                        if (ensureBotEngineReady(blackBotEngine, config.blackBotId) == EngineAvailability.READY) {
                            blackBotEngine.newGame()
                        }
                    } else {
                        blackBotEngine.shutdown()
                        if (ensureBotEngineReady(whiteBotEngine, config.whiteBotId) == EngineAvailability.READY) {
                            whiteBotEngine.newGame()
                        }
                    }
                }
                GameMode.BOT_VS_BOT -> {
                    if (ensureBotEngineReady(whiteBotEngine, config.whiteBotId) == EngineAvailability.READY) {
                        whiteBotEngine.newGame()
                    }
                    if (ensureBotEngineReady(blackBotEngine, config.blackBotId) == EngineAvailability.READY) {
                        blackBotEngine.newGame()
                    }
                }
            }
            runEngineTurn(evaluatePreviousPly = null)
        }
    }

    fun selectActiveBot(profileId: String) {
        val profile = botArchiveManager.findProfile(profileId)
        _uiState.update { cur ->
            val updatedConfig = when (cur.config.mode) {
                GameMode.BOT_VS_HUMAN -> {
                    if (cur.config.humanColor == PieceColor.WHITE) {
                        cur.config.copy(blackBotId = profile.id)
                    } else {
                        cur.config.copy(whiteBotId = profile.id)
                    }
                }
                GameMode.BOT_VS_BOT -> {
                    cur.config.copy(whiteBotId = profile.id, blackBotId = profile.id)
                }
                GameMode.HUMAN_VS_HUMAN -> {
                    cur.config.copy(whiteBotId = profile.id, blackBotId = profile.id)
                }
            }
            cur.copy(
                config = updatedConfig,
                statusMessage = "Selected bot '${profile.name}'"
            )
        }
        val state = _uiState.value
        if (!state.gameResult.isFinished && state.config.mode != GameMode.HUMAN_VS_HUMAN) {
            scheduleEngineWork()
        }
    }

    fun onSquareTapped(square: Square) {
        val state = _uiState.value
        if (!state.isViewingLive || state.gameResult.isFinished) return
        if (!isHumanTurn(state)) return

        val selected = state.selectedSquare
        if (selected != null) {
            val candidateMoves = state.legalTargets.filter { it.to == square }
            if (candidateMoves.isNotEmpty()) {
                handleUserMoveCandidates(selected, square, candidateMoves)
                return
            }
        }

        val piece = state.position.pieceAt(square)
        if (piece != null && piece.color == state.position.sideToMove) {
            val legal = state.position.legalMovesFrom(square)
            _uiState.update { it.copy(selectedSquare = square, legalTargets = legal) }
        } else {
            _uiState.update { it.copy(selectedSquare = null, legalTargets = emptyList()) }
        }
    }

    fun onPieceDragged(from: Square, to: Square) {
        val state = _uiState.value
        if (!state.isViewingLive || state.gameResult.isFinished) return
        if (!isHumanTurn(state)) return
        val candidates = state.position.legalMovesFrom(from).filter { it.to == to }
        if (candidates.isNotEmpty()) {
            handleUserMoveCandidates(from, to, candidates)
        } else {
            _uiState.update { it.copy(selectedSquare = null, legalTargets = emptyList()) }
        }
    }

    private fun handleUserMoveCandidates(from: Square, to: Square, candidates: List<ChessMove>) {
        if (candidates.size > 1 && candidates.any { it.promotion != null }) {
            _uiState.update {
                it.copy(
                    pendingPromotion = PendingPromotion(from, to, it.position.sideToMove),
                    selectedSquare = null,
                    legalTargets = emptyList()
                )
            }
        } else {
            commitMove(candidates.first(), preLines = lastPreMoveLines)
        }
    }

    fun completePromotion(pieceType: PieceType) {
        val pending = _uiState.value.pendingPromotion ?: return
        _uiState.update { it.copy(pendingPromotion = null) }
        commitMove(ChessMove(pending.from, pending.to, pieceType), preLines = lastPreMoveLines)
    }

    fun cancelPromotion() {
        _uiState.update { it.copy(pendingPromotion = null) }
    }

    private fun commitMove(move: ChessMove, preLines: List<AnalysisLine>) {
        val state = _uiState.value
        val beforePos = state.position
        val san = beforePos.moveToSan(move)
        val isSacrifice = beforePos.isMoveSacrifice(move)
        val nextPos = beforePos.applyMove(move) ?: return
        val result = nextPos.gameResult()
        val mover = beforePos.sideToMove

        val increment = state.config.timeControl.incrementMs
        val updatedWhiteMs = if (state.config.timeControl.initialMs > 0 && mover == PieceColor.WHITE) {
            state.whiteTimeMs + increment
        } else state.whiteTimeMs
        val updatedBlackMs = if (state.config.timeControl.initialMs > 0 && mover == PieceColor.BLACK) {
            state.blackTimeMs + increment
        } else state.blackTimeMs

        val nextFlip = if (state.config.mode == GameMode.HUMAN_VS_HUMAN && state.config.autoFlipHumanVsHuman) {
            nextPos.sideToMove == PieceColor.BLACK
        } else state.boardFlipped

        val immediateEval: EvalScore? = when {
            result == GameResult.WHITE_WINS_CHECKMATE -> EvalScore(mate = 1)
            result == GameResult.BLACK_WINS_CHECKMATE -> EvalScore(mate = -1)
            result.isFinished -> EvalScore(cp = 0)
            evalEngine.availability != EngineAvailability.READY -> EvalScore(cp = nextPos.materialBalanceCp())
            else -> null
        }

        val newRecord = MoveRecord(
            ply = state.moveRecords.size + 1,
            mover = mover,
            move = move,
            san = san,
            fenAfter = nextPos.toFen(),
            evalAfter = immediateEval,
            bestMoveBefore = preLines.firstOrNull()?.pv?.firstOrNull()
        )

        val recordIndex = state.moveRecords.size
        val evalBefore = preLines.firstOrNull()?.score ?: state.currentEval
        val secondBestBefore = preLines.getOrNull(1)?.score
        val bestMoveBefore = preLines.firstOrNull()?.pv?.firstOrNull()

        _uiState.update {
            it.copy(
                position = nextPos,
                positionHistory = it.positionHistory + nextPos,
                moveRecords = it.moveRecords + newRecord,
                viewingPly = null,
                selectedSquare = null,
                legalTargets = emptyList(),
                boardFlipped = nextFlip,
                hintMove = null,
                hintEval = null,
                currentEval = immediateEval ?: it.currentEval,
                whiteTimeMs = updatedWhiteMs,
                blackTimeMs = updatedBlackMs,
                gameResult = result
            )
        }

        lastPreMoveLines = emptyList()
        scheduleEngineWork(
            evaluatePreviousPly = PreviousMoveContext(
                recordIndex = recordIndex,
                mover = mover,
                playedMoveUci = move.uci,
                evalBefore = evalBefore,
                secondBestBefore = secondBestBefore,
                bestMoveBefore = bestMoveBefore,
                isSacrifice = isSacrifice,
                fenAfter = nextPos.toFen(),
                sideAfter = nextPos.sideToMove
            )
        )
    }

    private data class PreviousMoveContext(
        val recordIndex: Int,
        val mover: PieceColor,
        val playedMoveUci: String,
        val evalBefore: EvalScore,
        val secondBestBefore: EvalScore?,
        val bestMoveBefore: String?,
        val isSacrifice: Boolean,
        val fenAfter: String,
        val sideAfter: PieceColor
    )

    private fun scheduleEngineWork(evaluatePreviousPly: PreviousMoveContext? = null) {
        engineJob?.cancel()
        evalEngine.requestStopSearch()
        whiteBotEngine.requestStopSearch()
        blackBotEngine.requestStopSearch()

        engineJob = viewModelScope.launch {
            runEngineTurn(evaluatePreviousPly)
        }
    }

    private suspend fun runEngineTurn(evaluatePreviousPly: PreviousMoveContext?) {
        if (evaluatePreviousPly != null && evalEngine.availability == EngineAvailability.READY) {
            evaluateMoveAndClassify(evaluatePreviousPly)
        }

        val latest = _uiState.value
        if (latest.gameResult.isFinished) {
            _uiState.update { it.copy(isEngineThinking = false) }
            return
        }

        when (latest.config.mode) {
            GameMode.HUMAN_VS_HUMAN -> {
                if (evalEngine.availability == EngineAvailability.READY) {
                    computeHintAndPreEval(latest.position)
                }
            }
            GameMode.BOT_VS_HUMAN -> {
                if (isHumanTurn(latest)) {
                    if (evalEngine.availability == EngineAvailability.READY) {
                        computeHintAndPreEval(latest.position)
                    }
                } else {
                    val isWhiteBot = latest.position.sideToMove == PieceColor.WHITE
                    val botEngine = if (isWhiteBot) whiteBotEngine else blackBotEngine
                    val botId = if (isWhiteBot) latest.config.whiteBotId else latest.config.blackBotId
                    if (ensureBotEngineReady(botEngine, botId) == EngineAvailability.READY) {
                        runBotMove(
                            engine = botEngine,
                            position = latest.position,
                            elo = if (isWhiteBot) latest.config.whiteBotElo else latest.config.blackBotElo,
                            moveTimeMs = latest.config.botMoveTimeMs
                        )
                    } else {
                        runFallbackBotMove(latest.position)
                    }
                }
            }
            GameMode.BOT_VS_BOT -> {
                if (latest.isBotVsBotPaused) {
                    _uiState.update { it.copy(isEngineThinking = false) }
                    return
                }
                if (latest.moveRecords.isNotEmpty() && latest.config.botVsBotDelayMs > 0) {
                    delay(latest.config.botVsBotDelayMs)
                }
                val current = _uiState.value
                if (current.isBotVsBotPaused || current.gameResult.isFinished) return

                val isWhiteTurn = current.position.sideToMove == PieceColor.WHITE
                val botEngine = if (isWhiteTurn) whiteBotEngine else blackBotEngine
                val botId = if (isWhiteTurn) current.config.whiteBotId else current.config.blackBotId
                if (ensureBotEngineReady(botEngine, botId) == EngineAvailability.READY) {
                    val elo = if (isWhiteTurn) current.config.whiteBotElo else current.config.blackBotElo
                    runBotMove(
                        engine = botEngine,
                        position = current.position,
                        elo = elo,
                        moveTimeMs = current.config.botMoveTimeMs
                    )
                } else {
                    runFallbackBotMove(current.position)
                }
            }
        }
    }

    private suspend fun runFallbackBotMove(position: ChessPosition) {
        _uiState.update { it.copy(isEngineThinking = true) }
        delay(250L)
        val fallback = withContext(Dispatchers.Default) {
            position.chooseFallbackMove()
        }
        _uiState.update { it.copy(isEngineThinking = false) }
        if (fallback != null) {
            commitMove(fallback, preLines = emptyList())
        }
    }

    private suspend fun evaluateMoveAndClassify(ctx: PreviousMoveContext) {
        val state = _uiState.value
        val resultAfter = _uiState.value.gameResult
        val evalAfter: EvalScore = if (resultAfter == GameResult.WHITE_WINS_CHECKMATE) {
            EvalScore(mate = 1)
        } else if (resultAfter == GameResult.BLACK_WINS_CHECKMATE) {
            EvalScore(mate = -1)
        } else if (resultAfter.isFinished) {
            EvalScore(cp = 0)
        } else {
            evalEngine.configure(
                threads = state.engineThreads,
                hashMb = state.engineHashMb,
                limitStrength = false,
                multiPv = 2
            )
            val analysis = evalEngine.search(
                fen = ctx.fenAfter,
                sideToMove = ctx.sideAfter,
                depth = 10,
                onLineUpdate = { line ->
                    if (line.multiPv == 1) {
                        _uiState.update { cur -> cur.copy(currentEval = line.score) }
                    }
                }
            )
            lastPreMoveLines = analysis.lines
            analysis.lines.firstOrNull()?.score ?: state.currentEval
        }

        val classification = MoveClassifier.classify(
            mover = ctx.mover,
            evalBefore = ctx.evalBefore,
            evalAfter = evalAfter,
            secondBestEvalBefore = ctx.secondBestBefore,
            playedMoveUci = ctx.playedMoveUci,
            bestMoveUci = ctx.bestMoveBefore,
            isSacrifice = ctx.isSacrifice
        )
        val loss = MoveClassifier.winPercentLoss(ctx.mover, ctx.evalBefore, evalAfter)

        _uiState.update { current ->
            if (ctx.recordIndex !in current.moveRecords.indices) return@update current
            val updated = current.moveRecords.toMutableList()
            updated[ctx.recordIndex] = updated[ctx.recordIndex].copy(
                evalAfter = evalAfter,
                classification = classification,
                winPercentLoss = loss
            )
            val bestReply = lastPreMoveLines.firstOrNull()?.pv?.firstOrNull()?.let { ChessMove.fromUci(it) }
            current.copy(
                moveRecords = updated,
                currentEval = evalAfter,
                hintMove = bestReply,
                hintEval = evalAfter
            )
        }
    }

    private suspend fun computeHintAndPreEval(position: ChessPosition) {
        if (lastPreMoveLines.isNotEmpty()) {
            val best = lastPreMoveLines.firstOrNull()
            _uiState.update {
                it.copy(
                    hintMove = best?.pv?.firstOrNull()?.let { uci -> ChessMove.fromUci(uci) },
                    hintEval = best?.score,
                    currentEval = best?.score ?: it.currentEval,
                    isEngineThinking = false
                )
            }
            return
        }

        val state = _uiState.value
        _uiState.update { it.copy(isEngineThinking = true) }
        evalEngine.configure(
            threads = state.engineThreads,
            hashMb = state.engineHashMb,
            limitStrength = false,
            multiPv = 2
        )
        val search = evalEngine.search(
            fen = position.toFen(),
            sideToMove = position.sideToMove,
            depth = 10,
            onLineUpdate = { line ->
                if (line.multiPv == 1) {
                    val liveHint = line.pv.firstOrNull()?.let { uci -> ChessMove.fromUci(uci) }
                    _uiState.update { cur ->
                        cur.copy(
                            currentEval = line.score,
                            hintEval = line.score,
                            hintMove = liveHint ?: cur.hintMove
                        )
                    }
                }
            }
        )
        lastPreMoveLines = search.lines
        val bestLine = search.lines.firstOrNull()
        val hintMove = (bestLine?.pv?.firstOrNull() ?: search.bestMove)?.let { ChessMove.fromUci(it) }
        _uiState.update {
            it.copy(
                hintMove = hintMove,
                hintEval = bestLine?.score,
                currentEval = bestLine?.score ?: it.currentEval,
                isEngineThinking = false
            )
        }
    }

    private suspend fun runBotMove(
        engine: StockfishEngine,
        position: ChessPosition,
        elo: Int,
        moveTimeMs: Int
    ) {
        val state = _uiState.value
        _uiState.update { it.copy(isEngineThinking = true) }

        val perEngineHash = if (state.config.mode == GameMode.BOT_VS_BOT) {
            (state.engineHashMb / 2).coerceAtLeast(8)
        } else {
            state.engineHashMb
        }
        val perEngineThreads = if (state.config.mode == GameMode.BOT_VS_BOT) {
            1
        } else {
            state.engineThreads
        }

        engine.configure(
            threads = perEngineThreads,
            hashMb = perEngineHash,
            limitStrength = elo < 3190,
            elo = elo,
            multiPv = 1
        )
        val search = engine.search(
            fen = position.toFen(),
            sideToMove = position.sideToMove,
            moveTimeMs = moveTimeMs
        )
        _uiState.update { it.copy(isEngineThinking = false) }

        val chosen = search.bestMove?.let { ChessMove.fromUci(it) }
            ?: position.chooseFallbackMove()
            ?: return

        val preLines = if (lastPreMoveLines.isNotEmpty()) lastPreMoveLines else search.lines
        commitMove(chosen, preLines = preLines)
    }

    private fun startClockLoop() {
        clockJob?.cancel()
        clockJob = viewModelScope.launch {
            while (isActive) {
                delay(100L)
                val state = _uiState.value
                if (state.config.timeControl.initialMs <= 0L) continue
                if (state.gameResult.isFinished || state.moveRecords.isEmpty()) continue
                if (state.config.mode == GameMode.BOT_VS_BOT && state.isBotVsBotPaused) continue

                if (state.position.sideToMove == PieceColor.WHITE) {
                    val remaining = (state.whiteTimeMs - 100L).coerceAtLeast(0L)
                    _uiState.update {
                        it.copy(
                            whiteTimeMs = remaining,
                            gameResult = if (remaining == 0L) GameResult.BLACK_WINS_TIMEOUT else it.gameResult
                        )
                    }
                } else {
                    val remaining = (state.blackTimeMs - 100L).coerceAtLeast(0L)
                    _uiState.update {
                        it.copy(
                            blackTimeMs = remaining,
                            gameResult = if (remaining == 0L) GameResult.WHITE_WINS_TIMEOUT else it.gameResult
                        )
                    }
                }
            }
        }
    }

    private fun isHumanTurn(state: GameUiState): Boolean = when (state.config.mode) {
        GameMode.HUMAN_VS_HUMAN -> true
        GameMode.BOT_VS_BOT -> false
        GameMode.BOT_VS_HUMAN -> state.position.sideToMove == state.config.humanColor
    }

    fun toggleHints() {
        val next = !_uiState.value.showHints
        prefs.edit().putBoolean("show_hints", next).apply()
        _uiState.update { it.copy(showHints = next) }
    }

    fun toggleBoardFlip() {
        _uiState.update { it.copy(boardFlipped = !it.boardFlipped) }
    }

    fun takeback() {
        val state = _uiState.value
        if (state.moveRecords.isEmpty()) return
        engineJob?.cancel()
        evalEngine.requestStopSearch()
        whiteBotEngine.requestStopSearch()
        blackBotEngine.requestStopSearch()
        lastPreMoveLines = emptyList()

        val pliesToDrop = if (state.config.mode == GameMode.BOT_VS_HUMAN &&
            state.position.sideToMove == state.config.humanColor &&
            state.moveRecords.size >= 2
        ) 2 else 1

        val newHistory = state.positionHistory.dropLast(pliesToDrop)
        val newRecords = state.moveRecords.dropLast(pliesToDrop)
        val restoredPos = newHistory.last()

        _uiState.update {
            it.copy(
                position = restoredPos,
                positionHistory = newHistory,
                moveRecords = newRecords,
                viewingPly = null,
                selectedSquare = null,
                legalTargets = emptyList(),
                pendingPromotion = null,
                hintMove = null,
                currentEval = newRecords.lastOrNull()?.evalAfter ?: EvalScore(cp = 20),
                isEngineThinking = false,
                gameResult = restoredPos.gameResult()
            )
        }
        scheduleEngineWork()
    }

    fun toggleBotVsBotPause() {
        val state = _uiState.value
        if (state.config.mode != GameMode.BOT_VS_BOT) return
        val nextPaused = !state.isBotVsBotPaused
        _uiState.update { it.copy(isBotVsBotPaused = nextPaused) }
        if (nextPaused) {
            engineJob?.cancel()
            evalEngine.requestStopSearch()
            whiteBotEngine.requestStopSearch()
            blackBotEngine.requestStopSearch()
            _uiState.update { it.copy(isEngineThinking = false) }
        } else {
            scheduleEngineWork()
        }
    }

    fun takeOverFromBot(asColor: PieceColor) {
        engineJob?.cancel()
        evalEngine.requestStopSearch()
        whiteBotEngine.requestStopSearch()
        blackBotEngine.requestStopSearch()
        if (asColor == PieceColor.WHITE) whiteBotEngine.shutdown() else blackBotEngine.shutdown()
        lastPreMoveLines = emptyList()

        _uiState.update {
            it.copy(
                config = it.config.copy(mode = GameMode.BOT_VS_HUMAN, humanColor = asColor),
                boardFlipped = asColor == PieceColor.BLACK,
                isBotVsBotPaused = false,
                isEngineThinking = false,
                statusMessage = "Took over as ${if (asColor == PieceColor.WHITE) "White" else "Black"}"
            )
        }
        scheduleEngineWork()
    }

    fun viewHistoryAt(ply: Int?) {
        val maxPly = _uiState.value.moveRecords.size
        val normalized = if (ply == null || ply >= maxPly) null else ply.coerceAtLeast(0)
        _uiState.update {
            it.copy(
                viewingPly = normalized,
                selectedSquare = null,
                legalTargets = emptyList()
            )
        }
    }

    fun selectBoardTheme(themeId: String) {
        prefs.edit().putString("board_theme_id", themeId).apply()
        _uiState.update { it.copy(selectedBoardThemeId = themeId) }
    }

    fun setCustomBoardColors(lightHex: Long, darkHex: Long) {
        prefs.edit()
            .putString("board_theme_id", "custom")
            .putLong("custom_light_hex", lightHex)
            .putLong("custom_dark_hex", darkHex)
            .apply()
        _uiState.update {
            it.copy(
                selectedBoardThemeId = "custom",
                customLightSquareHex = lightHex,
                customDarkSquareHex = darkHex
            )
        }
    }

    fun selectPiecePack(packId: String) {
        prefs.edit().putString("piece_pack_id", packId).apply()
        _uiState.update { it.copy(selectedPiecePackId = packId) }
    }

    fun updateEngineResources(threads: Int, hashMb: Int) {
        prefs.edit()
            .putInt("engine_threads", threads)
            .putInt("engine_hash_mb", hashMb)
            .apply()
        _uiState.update { it.copy(engineThreads = threads, engineHashMb = hashMb) }
    }

    fun importBotArchive(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImportingBot = true, statusMessage = "Importing bot archive...") }
            val result = withContext(Dispatchers.IO) {
                botArchiveManager.importFromUri(getApplication(), uri)
            }
            val updatedProfiles = botArchiveManager.listProfiles()
            _uiState.update {
                it.copy(
                    botProfiles = updatedProfiles,
                    isImportingBot = false,
                    statusMessage = result.message
                )
            }
        }
    }

    fun deleteBotProfile(profileId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                botArchiveManager.deleteProfile(profileId)
            }
            val updatedProfiles = botArchiveManager.listProfiles()
            _uiState.update { cur ->
                val fixedConfig = cur.config.copy(
                    whiteBotId = if (cur.config.whiteBotId == profileId) BotEngineProfile.BUILT_IN_ID else cur.config.whiteBotId,
                    blackBotId = if (cur.config.blackBotId == profileId) BotEngineProfile.BUILT_IN_ID else cur.config.blackBotId
                )
                cur.copy(
                    config = fixedConfig,
                    botProfiles = updatedProfiles,
                    statusMessage = "Removed bot '$profileId'"
                )
            }
        }
    }

    fun importPiecePackZip(uri: Uri) {
        viewModelScope.launch {
            val resultMessage = withContext(Dispatchers.IO) {
                try {
                    val app = getApplication<Application>()
                    val rawName = uri.lastPathSegment
                        ?.substringAfterLast('/')
                        ?.substringBeforeLast('.')
                        ?.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                        ?.take(24)
                        ?.ifBlank { null }
                        ?: "custom_${System.currentTimeMillis() % 10000}"

                    val rootPiecesDir = File(app.filesDir, "pieces").apply { mkdirs() }
                    val targetDir = File(rootPiecesDir, rawName)
                    if (targetDir.exists()) targetDir.deleteRecursively()
                    targetDir.mkdirs()

                    val canonicalTarget = targetDir.canonicalPath + File.separator
                    val extractedCodes = HashSet<String>()

                    app.contentResolver.openInputStream(uri)?.use { stream ->
                        ZipInputStream(stream).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                if (!entry.isDirectory) {
                                    val baseName = File(entry.name).name.lowercase()
                                    val code = baseName.substringBeforeLast('.')
                                    val ext = baseName.substringAfterLast('.', "")
                                    if (code in REQUIRED_PIECE_CODES && ext in ALLOWED_IMAGE_EXTS) {
                                        val outFile = File(targetDir, "$code.$ext")
                                        if (!outFile.canonicalPath.startsWith(canonicalTarget)) {
                                            throw SecurityException("Blocked invalid zip entry path")
                                        }
                                        outFile.outputStream().use { out -> zis.copyTo(out) }
                                        extractedCodes.add(code)
                                    }
                                }
                                zis.closeEntry()
                                entry = zis.nextEntry
                            }
                        }
                    } ?: return@withContext "Unable to read selected zip file"

                    if (extractedCodes.size < REQUIRED_PIECE_CODES.size) {
                        targetDir.deleteRecursively()
                        val missing = (REQUIRED_PIECE_CODES - extractedCodes).sorted().joinToString(", ")
                        return@withContext "Zip missing pieces: $missing"
                    }

                    prefs.edit().putString("piece_pack_id", rawName).apply()
                    "Imported piece pack '$rawName'"
                } catch (e: Exception) {
                    "Import failed: ${e.message ?: "invalid zip"}"
                }
            }

            val updatedPacks = discoverPiecePacks()
            val selected = prefs.getString("piece_pack_id", _uiState.value.selectedPiecePackId)
                ?: _uiState.value.selectedPiecePackId
            _uiState.update {
                it.copy(
                    availablePiecePackIds = updatedPacks,
                    selectedPiecePackId = selected,
                    statusMessage = resultMessage
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        clockJob?.cancel()
        engineJob?.cancel()
        evalEngine.shutdown()
        whiteBotEngine.shutdown()
        blackBotEngine.shutdown()
    }

    companion object {
        private val REQUIRED_PIECE_CODES = setOf(
            "wp", "wn", "wb", "wr", "wq", "wk",
            "bp", "bn", "bb", "br", "bq", "bk"
        )
        private val ALLOWED_IMAGE_EXTS = setOf("svg", "png", "webp")
    }
}
