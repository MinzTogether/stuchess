package com.stuchess.app.engine

import com.stuchess.app.game.PieceColor
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp

enum class MoveClassification(
    val label: String,
    val badge: String,
    val colorHex: Long,
    val badgeTextColorHex: Long
) {
    BOOK("Book", "B", 0xFFD5A47D, 0xFF1F1B16),
    BRILLIANT("Brilliant", "!!", 0xFF26C2A3, 0xFFFFFFFF),
    GREAT("Great", "!", 0xFF5C9CE6, 0xFFFFFFFF),
    BEST("Best", "★", 0xFF81C744, 0xFFFFFFFF),
    EXCELLENT("Excellent", "✓", 0xFF96D153, 0xFF141B0D),
    GOOD("Good", "○", 0xFF95C47B, 0xFF141B0D),
    INACCURACY("Inaccuracy", "?!", 0xFFF7C631, 0xFF1F1B12),
    MISTAKE("Mistake", "?", 0xFFFF922B, 0xFFFFFFFF),
    BLUNDER("Blunder", "??", 0xFFFA412D, 0xFFFFFFFF)
}

data class EvalScore(
    val cp: Int? = null,
    val mate: Int? = null
) {
    fun toWinPercentForWhite(): Double {
        if (mate != null) {
            return if (mate > 0) 100.0 else 0.0
        }
        val centipawns = (cp ?: 0).coerceIn(-10000, 10000)
        return MoveClassifier.cpToWinPercent(centipawns)
    }

    fun toWinPercentFor(color: PieceColor): Double {
        val whiteWin = toWinPercentForWhite()
        return if (color == PieceColor.WHITE) whiteWin else 100.0 - whiteWin
    }

    fun toBarFraction(): Float {
        return (toWinPercentForWhite() / 100.0).toFloat().coerceIn(0.03f, 0.97f)
    }

    fun formatDisplay(): String {
        if (mate != null) {
            val sign = if (mate < 0) "-" else ""
            return "${sign}M${abs(mate)}"
        }
        val pawns = (cp ?: 0) / 100.0
        return if (pawns >= 0.0) String.format(Locale.US, "+%.1f", pawns) else String.format(Locale.US, "%.1f", pawns)
    }
}

object MoveClassifier {
    fun cpToWinPercent(cp: Int): Double {
        return 50.0 + 50.0 * (2.0 / (1.0 + exp(-0.00368208 * cp)) - 1.0)
    }

    fun winPercentLoss(mover: PieceColor, evalBefore: EvalScore, evalAfter: EvalScore): Double {
        val beforeWin = evalBefore.toWinPercentFor(mover)
        val afterWin = evalAfter.toWinPercentFor(mover)
        return (beforeWin - afterWin).coerceAtLeast(0.0)
    }

    fun classify(
        mover: PieceColor,
        evalBefore: EvalScore,
        evalAfter: EvalScore,
        secondBestEvalBefore: EvalScore?,
        playedMoveUci: String,
        bestMoveUci: String?,
        isSacrifice: Boolean,
        isBook: Boolean = false
    ): MoveClassification {
        if (isBook) return MoveClassification.BOOK

        val beforeWin = evalBefore.toWinPercentFor(mover)
        val afterWin = evalAfter.toWinPercentFor(mover)
        val loss = (beforeWin - afterWin).coerceAtLeast(0.0)
        val isEngineBest = bestMoveUci != null && playedMoveUci.equals(bestMoveUci, ignoreCase = true)

        if (isSacrifice && loss <= 2.0 && beforeWin < 88.0 && afterWin >= 45.0) {
            return MoveClassification.BRILLIANT
        }

        if (isEngineBest && secondBestEvalBefore != null) {
            val secondWin = secondBestEvalBefore.toWinPercentFor(mover)
            val gap = beforeWin - secondWin
            if (gap >= 15.0 && beforeWin < 92.0) {
                return MoveClassification.GREAT
            }
        }

        if (isEngineBest || loss <= 0.5) {
            return MoveClassification.BEST
        }

        return when {
            loss <= 2.0 -> MoveClassification.EXCELLENT
            loss <= 5.0 -> MoveClassification.GOOD
            loss <= 10.0 -> MoveClassification.INACCURACY
            loss <= 20.0 -> MoveClassification.MISTAKE
            else -> MoveClassification.BLUNDER
        }
    }

    fun calculateAccuracy(losses: List<Double>): Double {
        if (losses.isEmpty()) return 100.0
        val avgLoss = losses.average()
        val raw = 103.1668 * exp(-0.04354 * avgLoss) - 3.1669
        return raw.coerceIn(0.0, 100.0)
    }
}
