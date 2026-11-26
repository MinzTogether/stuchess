package com.stuchess.app

import com.stuchess.app.engine.EvalScore
import com.stuchess.app.engine.MoveClassification
import com.stuchess.app.engine.MoveClassifier
import com.stuchess.app.game.PieceColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveClassifierTest {

    @Test
    fun zeroCentipawnsIsFiftyPercentWinProbability() {
        assertEquals(50.0, MoveClassifier.cpToWinPercent(0), 0.001)
    }

    @Test
    fun largeWinDropClassifiesAsBlunder() {
        val cls = MoveClassifier.classify(
            mover = PieceColor.WHITE,
            evalBefore = EvalScore(cp = 120),
            evalAfter = EvalScore(cp = -350),
            secondBestEvalBefore = EvalScore(cp = 80),
            playedMoveUci = "g1f3",
            bestMoveUci = "d2d4",
            isSacrifice = false
        )
        assertEquals(MoveClassification.BLUNDER, cls)
    }

    @Test
    fun onlyGoodMoveClassifiesAsGreat() {
        val cls = MoveClassifier.classify(
            mover = PieceColor.WHITE,
            evalBefore = EvalScore(cp = 250),
            evalAfter = EvalScore(cp = 250),
            secondBestEvalBefore = EvalScore(cp = -100),
            playedMoveUci = "e4e5",
            bestMoveUci = "e4e5",
            isSacrifice = false
        )
        assertEquals(MoveClassification.GREAT, cls)
    }

    @Test
    fun soundSacrificeClassifiesAsBrilliant() {
        val cls = MoveClassifier.classify(
            mover = PieceColor.WHITE,
            evalBefore = EvalScore(cp = 80),
            evalAfter = EvalScore(cp = 90),
            secondBestEvalBefore = EvalScore(cp = 20),
            playedMoveUci = "f3g5",
            bestMoveUci = "f3g5",
            isSacrifice = true
        )
        assertEquals(MoveClassification.BRILLIANT, cls)
    }

    @Test
    fun accuracyDropsWithAverageLoss() {
        val high = MoveClassifier.calculateAccuracy(listOf(0.2, 0.5, 0.1))
        val low = MoveClassifier.calculateAccuracy(listOf(18.0, 22.0, 15.0))
        assertTrue(high > 95.0)
        assertTrue(low < 55.0)
    }

    @Test
    fun checkmateEvalBarSnapsToExtremes() {
        val whiteMate = EvalScore(mate = 1)
        val blackMate = EvalScore(mate = -1)
        assertEquals(0.97f, whiteMate.toBarFraction(), 0.001f)
        assertEquals(0.03f, blackMate.toBarFraction(), 0.001f)
        assertEquals("M1", whiteMate.formatDisplay())
        assertEquals("-M1", blackMate.formatDisplay())
    }
}
