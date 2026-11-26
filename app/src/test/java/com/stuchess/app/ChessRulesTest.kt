package com.stuchess.app

import com.stuchess.app.game.ChessPosition
import com.stuchess.app.game.GameResult
import com.stuchess.app.game.PieceColor
import com.stuchess.app.game.PieceType
import com.stuchess.app.game.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessRulesTest {

    @Test
    fun initialPositionHasTwentyLegalMoves() {
        val pos = ChessPosition.initial()
        assertEquals(20, pos.legalMoves().size)
        assertEquals(ChessPosition.INITIAL_FEN, pos.toFen())
    }

    @Test
    fun scholarsMateProducesWhiteCheckmate() {
        var pos = ChessPosition.initial()
        for (uci in listOf("e2e4", "e7e5", "d1h5", "b8c6", "f1c4", "g8f6", "h5f7")) {
            pos = requireNotNull(pos.applyUci(uci)) { "Failed to apply $uci" }
        }
        assertEquals(GameResult.WHITE_WINS_CHECKMATE, pos.gameResult())
    }

    @Test
    fun enPassantRemovesCapturedPawn() {
        var pos = ChessPosition.initial()
        for (uci in listOf("e2e4", "a7a6", "e4e5", "d7d5")) {
            pos = requireNotNull(pos.applyUci(uci))
        }
        assertEquals(Square.fromNotation("d6"), pos.enPassantSquare)
        pos = requireNotNull(pos.applyUci("e5d6"))
        assertNull(pos.pieceAt(Square.fromNotation("d5")!!))
        assertEquals(PieceType.PAWN, pos.pieceAt(Square.fromNotation("d6")!!)?.type)
    }

    @Test
    fun kingSideCastlingMovesKingAndRook() {
        val pos = ChessPosition.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val after = pos.applyUci("e1g1")
        assertNotNull(after)
        assertEquals(PieceType.KING, after!!.pieceAt(Square.fromNotation("g1")!!)?.type)
        assertEquals(PieceType.ROOK, after.pieceAt(Square.fromNotation("f1")!!)?.type)
        assertNull(after.pieceAt(Square.fromNotation("h1")!!))
    }

    @Test
    fun promotionCreatesSelectedPiece() {
        val pos = ChessPosition.fromFen("4k3/P7/8/8/8/8/8/4K3 w - - 0 1")
        val after = pos.applyUci("a7a8n")
        assertNotNull(after)
        val promoted = after!!.pieceAt(Square.fromNotation("a8")!!)
        assertEquals(PieceColor.WHITE, promoted?.color)
        assertEquals(PieceType.KNIGHT, promoted?.type)
    }

    @Test
    fun stalemateIsDetected() {
        val pos = ChessPosition.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertTrue(pos.legalMoves().isEmpty())
        assertEquals(GameResult.DRAW_STALEMATE, pos.gameResult())
    }

    @Test
    fun materialBalanceReflectsCapturedQueen() {
        val equalPos = ChessPosition.initial()
        assertEquals(0, equalPos.materialBalanceCp())
        val blackUpQueen = ChessPosition.fromFen("4k3/8/8/8/8/8/8/4K2q w - - 0 1")
        assertEquals(-900, blackUpQueen.materialBalanceCp())
    }

    @Test
    fun fallbackMoveFindsMateInOne() {
        val pos = ChessPosition.fromFen("6k1/5ppp/8/8/8/8/4QPPP/6K1 w - - 0 1")
        val move = pos.chooseFallbackMove()
        assertNotNull(move)
        val after = pos.applyMove(move!!)
        assertNotNull(after)
        assertEquals(GameResult.WHITE_WINS_CHECKMATE, after!!.gameResult())
    }
}
