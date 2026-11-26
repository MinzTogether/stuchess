package com.stuchess.app.game

import com.stuchess.app.engine.BotEngineProfile
import com.stuchess.app.engine.EngineAvailability
import com.stuchess.app.engine.EvalScore
import com.stuchess.app.engine.MoveClassification

enum class GameMode(val label: String) {
    BOT_VS_HUMAN("Bot vs Human"),
    BOT_VS_BOT("Bot vs Bot"),
    HUMAN_VS_HUMAN("Human vs Human")
}

enum class BoardSetupMode(val label: String) {
    STANDARD("Standard"),
    CUSTOM("Custom"),
    RANDOM("Random")
}

data class TimeControl(
    val id: String,
    val label: String,
    val initialMs: Long,
    val incrementMs: Long
) {
    companion object {
        val UNLIMITED = TimeControl("unlimited", "Unlimited", 0L, 0L)
        val PRESETS = listOf(
            UNLIMITED,
            TimeControl("bullet_1_1", "1+1", 60_000L, 1_000L),
            TimeControl("blitz_3_2", "3+2", 180_000L, 2_000L),
            TimeControl("blitz_5_0", "5+0", 300_000L, 0L),
            TimeControl("rapid_10_0", "10+0", 600_000L, 0L),
            TimeControl("rapid_15_10", "15+10", 900_000L, 10_000L)
        )
    }
}

data class NewGameConfig(
    val mode: GameMode = GameMode.BOT_VS_HUMAN,
    val setupMode: BoardSetupMode = BoardSetupMode.STANDARD,
    val humanColor: PieceColor = PieceColor.WHITE,
    val whiteBotId: String = BotEngineProfile.BUILT_IN_ID,
    val blackBotId: String = BotEngineProfile.BUILT_IN_ID,
    val whiteBotElo: Int = 1500,
    val blackBotElo: Int = 1600,
    val botMoveTimeMs: Int = 600,
    val botVsBotDelayMs: Long = 500L,
    val timeControl: TimeControl = TimeControl.PRESETS[4],
    val autoFlipHumanVsHuman: Boolean = false
)

data class MoveRecord(
    val ply: Int,
    val mover: PieceColor,
    val move: ChessMove,
    val san: String,
    val fenAfter: String,
    val evalAfter: EvalScore? = null,
    val classification: MoveClassification? = null,
    val bestMoveBefore: String? = null,
    val winPercentLoss: Double? = null
)

data class PendingPromotion(
    val from: Square,
    val to: Square,
    val color: PieceColor
)

data class GameUiState(
    val config: NewGameConfig = NewGameConfig(),
    val isCustomizingBoard: Boolean = false,
    val position: ChessPosition = ChessPosition.initial(),
    val positionHistory: List<ChessPosition> = listOf(ChessPosition.initial()),
    val moveRecords: List<MoveRecord> = emptyList(),
    val viewingPly: Int? = null,
    val selectedSquare: Square? = null,
    val legalTargets: List<ChessMove> = emptyList(),
    val pendingPromotion: PendingPromotion? = null,
    val boardFlipped: Boolean = false,
    val showHints: Boolean = true,
    val hintMove: ChessMove? = null,
    val hintEval: EvalScore? = null,
    val currentEval: EvalScore = EvalScore(cp = 20),
    val isEngineThinking: Boolean = false,
    val isBotVsBotPaused: Boolean = false,
    val engineAvailability: EngineAvailability = EngineAvailability.READY,
    val engineNotice: String? = null,
    val whiteTimeMs: Long = TimeControl.PRESETS[4].initialMs,
    val blackTimeMs: Long = TimeControl.PRESETS[4].initialMs,
    val gameResult: GameResult = GameResult.ONGOING,
    val selectedBoardThemeId: String = "green",
    val customLightSquareHex: Long = 0xFFEBECD0,
    val customDarkSquareHex: Long = 0xFF739552,
    val selectedPiecePackId: String = "classic",
    val availablePiecePackIds: List<String> = listOf("classic", "minimal"),
    val botProfiles: List<BotEngineProfile> = listOf(BotEngineProfile.builtIn()),
    val isImportingBot: Boolean = false,
    val engineThreads: Int = 2,
    val engineHashMb: Int = 32,
    val statusMessage: String? = null
) {
    val displayedPosition: ChessPosition
        get() = if (viewingPly != null && viewingPly in positionHistory.indices) {
            positionHistory[viewingPly]
        } else {
            position
        }

    val displayedMoveRecord: MoveRecord?
        get() {
            val ply = viewingPly ?: moveRecords.size
            return if (ply in 1..moveRecords.size) moveRecords[ply - 1] else null
        }

    val isViewingLive: Boolean
        get() = viewingPly == null || viewingPly == moveRecords.size

    val completedFullMoves: Int
        get() = moveRecords.size / 2

    val totalFullMoves: Int
        get() = (moveRecords.size + 1) / 2

    val viewingFullMoveNumber: Int?
        get() = viewingPly?.let { if (it == 0) 0 else (it + 1) / 2 }

    val whiteAccuracy: Double?
        get() {
            val losses = moveRecords.filter { it.mover == PieceColor.WHITE }.mapNotNull { it.winPercentLoss }
            return if (losses.isEmpty()) null else com.stuchess.app.engine.MoveClassifier.calculateAccuracy(losses)
        }

    val blackAccuracy: Double?
        get() {
            val losses = moveRecords.filter { it.mover == PieceColor.BLACK }.mapNotNull { it.winPercentLoss }
            return if (losses.isEmpty()) null else com.stuchess.app.engine.MoveClassifier.calculateAccuracy(losses)
        }
}
