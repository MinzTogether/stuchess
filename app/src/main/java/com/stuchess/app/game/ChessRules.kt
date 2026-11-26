package com.stuchess.app.game

import kotlin.math.abs
import kotlin.random.Random

enum class PieceColor {
    WHITE,
    BLACK;

    fun opposite(): PieceColor = if (this == WHITE) BLACK else WHITE
}

enum class PieceType(val fenChar: Char, val sanChar: Char, val materialValue: Int) {
    PAWN('p', ' ', 1),
    KNIGHT('n', 'N', 3),
    BISHOP('b', 'B', 3),
    ROOK('r', 'R', 5),
    QUEEN('q', 'Q', 9),
    KING('k', 'K', 0);

    companion object {
        fun fromFenChar(c: Char): PieceType? = when (c.lowercaseChar()) {
            'p' -> PAWN
            'n' -> KNIGHT
            'b' -> BISHOP
            'r' -> ROOK
            'q' -> QUEEN
            'k' -> KING
            else -> null
        }
    }
}

data class Piece(val color: PieceColor, val type: PieceType) {
    val code: String
        get() = "${if (color == PieceColor.WHITE) 'w' else 'b'}${type.fenChar}"

    val fenChar: Char
        get() = if (color == PieceColor.WHITE) type.fenChar.uppercaseChar() else type.fenChar
}

data class Square(val file: Int, val rank: Int) {
    val index: Int
        get() = rank * 8 + file

    val notation: String
        get() = "${'a' + file}${'1' + rank}"

    companion object {
        fun isValid(file: Int, rank: Int): Boolean = file in 0..7 && rank in 0..7

        fun fromIndex(index: Int): Square = Square(index % 8, index / 8)

        fun fromNotation(notation: String): Square? {
            if (notation.length != 2) return null
            val file = notation[0] - 'a'
            val rank = notation[1] - '1'
            return if (isValid(file, rank)) Square(file, rank) else null
        }
    }
}

data class ChessMove(
    val from: Square,
    val to: Square,
    val promotion: PieceType? = null
) {
    val uci: String
        get() = buildString {
            append(from.notation)
            append(to.notation)
            if (promotion != null) append(promotion.fenChar)
        }

    companion object {
        fun fromUci(uci: String): ChessMove? {
            if (uci.length < 4 || uci.length > 5) return null
            val from = Square.fromNotation(uci.substring(0, 2)) ?: return null
            val to = Square.fromNotation(uci.substring(2, 4)) ?: return null
            val promo = if (uci.length == 5) PieceType.fromFenChar(uci[4]) else null
            return ChessMove(from, to, promo)
        }
    }
}

enum class GameResult(val label: String, val isFinished: Boolean) {
    ONGOING("Ongoing", false),
    WHITE_WINS_CHECKMATE("White wins by checkmate", true),
    BLACK_WINS_CHECKMATE("Black wins by checkmate", true),
    WHITE_WINS_TIMEOUT("White wins on time", true),
    BLACK_WINS_TIMEOUT("Black wins on time", true),
    DRAW_STALEMATE("Draw by stalemate", true),
    DRAW_FIFTY_MOVE("Draw by 50-move rule", true),
    DRAW_THREEFOLD("Draw by threefold repetition", true),
    DRAW_INSUFFICIENT_MATERIAL("Draw by insufficient material", true)
}

data class ChessPosition(
    val board: Array<Piece?>,
    val sideToMove: PieceColor,
    val whiteKingSide: Boolean,
    val whiteQueenSide: Boolean,
    val blackKingSide: Boolean,
    val blackQueenSide: Boolean,
    val enPassantSquare: Square?,
    val halfMoveClock: Int,
    val fullMoveNumber: Int,
    val positionCounts: Map<String, Int> = emptyMap()
) {
    fun pieceAt(square: Square): Piece? = board[square.index]

    fun pieceAt(file: Int, rank: Int): Piece? =
        if (Square.isValid(file, rank)) board[rank * 8 + file] else null

    fun kingSquare(color: PieceColor): Square? {
        for (i in 0 until 64) {
            val p = board[i]
            if (p != null && p.color == color && p.type == PieceType.KING) {
                return Square.fromIndex(i)
            }
        }
        return null
    }

    fun isInCheck(color: PieceColor = sideToMove): Boolean {
        val king = kingSquare(color) ?: return false
        return isSquareAttackedBy(king, color.opposite())
    }

    fun isSquareAttackedBy(target: Square, attacker: PieceColor): Boolean {
        val pawnAttackRank = if (attacker == PieceColor.WHITE) target.rank - 1 else target.rank + 1
        for (df in intArrayOf(-1, 1)) {
            val p = pieceAt(target.file + df, pawnAttackRank)
            if (p != null && p.color == attacker && p.type == PieceType.PAWN) return true
        }

        val knightOffsets = arrayOf(
            intArrayOf(-2, -1), intArrayOf(-2, 1), intArrayOf(-1, -2), intArrayOf(-1, 2),
            intArrayOf(1, -2), intArrayOf(1, 2), intArrayOf(2, -1), intArrayOf(2, 1)
        )
        for (o in knightOffsets) {
            val p = pieceAt(target.file + o[0], target.rank + o[1])
            if (p != null && p.color == attacker && p.type == PieceType.KNIGHT) return true
        }

        for (df in -1..1) {
            for (dr in -1..1) {
                if (df == 0 && dr == 0) continue
                val p = pieceAt(target.file + df, target.rank + dr)
                if (p != null && p.color == attacker && p.type == PieceType.KING) return true
            }
        }

        val diagDirs = arrayOf(
            intArrayOf(-1, -1), intArrayOf(-1, 1), intArrayOf(1, -1), intArrayOf(1, 1)
        )
        for (d in diagDirs) {
            var f = target.file + d[0]
            var r = target.rank + d[1]
            while (Square.isValid(f, r)) {
                val p = board[r * 8 + f]
                if (p != null) {
                    if (p.color == attacker && (p.type == PieceType.BISHOP || p.type == PieceType.QUEEN)) {
                        return true
                    }
                    break
                }
                f += d[0]
                r += d[1]
            }
        }

        val orthoDirs = arrayOf(
            intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1)
        )
        for (d in orthoDirs) {
            var f = target.file + d[0]
            var r = target.rank + d[1]
            while (Square.isValid(f, r)) {
                val p = board[r * 8 + f]
                if (p != null) {
                    if (p.color == attacker && (p.type == PieceType.ROOK || p.type == PieceType.QUEEN)) {
                        return true
                    }
                    break
                }
                f += d[0]
                r += d[1]
            }
        }

        return false
    }

    fun legalMoves(): List<ChessMove> {
        val result = ArrayList<ChessMove>(40)
        for (i in 0 until 64) {
            val piece = board[i] ?: continue
            if (piece.color != sideToMove) continue
            val from = Square.fromIndex(i)
            val pseudo = pseudoLegalMovesFrom(from, piece)
            for (move in pseudo) {
                val next = applyUnchecked(move)
                if (!next.isInCheck(sideToMove)) {
                    result.add(move)
                }
            }
        }
        return result
    }

    fun legalMovesFrom(from: Square): List<ChessMove> {
        val piece = pieceAt(from) ?: return emptyList()
        if (piece.color != sideToMove) return emptyList()
        return pseudoLegalMovesFrom(from, piece).filter { move ->
            !applyUnchecked(move).isInCheck(sideToMove)
        }
    }

    private fun pseudoLegalMovesFrom(from: Square, piece: Piece): List<ChessMove> {
        val moves = ArrayList<ChessMove>(16)
        when (piece.type) {
            PieceType.PAWN -> generatePawnMoves(from, piece.color, moves)
            PieceType.KNIGHT -> {
                val offsets = arrayOf(
                    intArrayOf(-2, -1), intArrayOf(-2, 1), intArrayOf(-1, -2), intArrayOf(-1, 2),
                    intArrayOf(1, -2), intArrayOf(1, 2), intArrayOf(2, -1), intArrayOf(2, 1)
                )
                for (o in offsets) {
                    val f = from.file + o[0]
                    val r = from.rank + o[1]
                    if (!Square.isValid(f, r)) continue
                    val target = board[r * 8 + f]
                    if (target == null || target.color != piece.color) {
                        moves.add(ChessMove(from, Square(f, r)))
                    }
                }
            }
            PieceType.BISHOP -> addSlidingMoves(
                from, piece.color,
                arrayOf(intArrayOf(-1, -1), intArrayOf(-1, 1), intArrayOf(1, -1), intArrayOf(1, 1)),
                moves
            )
            PieceType.ROOK -> addSlidingMoves(
                from, piece.color,
                arrayOf(intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1)),
                moves
            )
            PieceType.QUEEN -> addSlidingMoves(
                from, piece.color,
                arrayOf(
                    intArrayOf(-1, -1), intArrayOf(-1, 1), intArrayOf(1, -1), intArrayOf(1, 1),
                    intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1)
                ),
                moves
            )
            PieceType.KING -> generateKingMoves(from, piece.color, moves)
        }
        return moves
    }

    private fun generatePawnMoves(from: Square, color: PieceColor, out: MutableList<ChessMove>) {
        val dir = if (color == PieceColor.WHITE) 1 else -1
        val startRank = if (color == PieceColor.WHITE) 1 else 6
        val promoRank = if (color == PieceColor.WHITE) 7 else 0
        val oneRank = from.rank + dir

        if (Square.isValid(from.file, oneRank) && board[oneRank * 8 + from.file] == null) {
            addPawnDestination(from, Square(from.file, oneRank), promoRank, out)
            val twoRank = from.rank + dir * 2
            if (from.rank == startRank && board[twoRank * 8 + from.file] == null) {
                out.add(ChessMove(from, Square(from.file, twoRank)))
            }
        }

        for (df in intArrayOf(-1, 1)) {
            val f = from.file + df
            if (!Square.isValid(f, oneRank)) continue
            val dest = Square(f, oneRank)
            val target = board[dest.index]
            if ((target != null && target.color != color) || dest == enPassantSquare) {
                addPawnDestination(from, dest, promoRank, out)
            }
        }
    }

    private fun addPawnDestination(
        from: Square,
        to: Square,
        promoRank: Int,
        out: MutableList<ChessMove>
    ) {
        if (to.rank == promoRank) {
            out.add(ChessMove(from, to, PieceType.QUEEN))
            out.add(ChessMove(from, to, PieceType.ROOK))
            out.add(ChessMove(from, to, PieceType.BISHOP))
            out.add(ChessMove(from, to, PieceType.KNIGHT))
        } else {
            out.add(ChessMove(from, to))
        }
    }

    private fun addSlidingMoves(
        from: Square,
        color: PieceColor,
        dirs: Array<IntArray>,
        out: MutableList<ChessMove>
    ) {
        for (d in dirs) {
            var f = from.file + d[0]
            var r = from.rank + d[1]
            while (Square.isValid(f, r)) {
                val target = board[r * 8 + f]
                if (target == null) {
                    out.add(ChessMove(from, Square(f, r)))
                } else {
                    if (target.color != color) {
                        out.add(ChessMove(from, Square(f, r)))
                    }
                    break
                }
                f += d[0]
                r += d[1]
            }
        }
    }

    private fun generateKingMoves(from: Square, color: PieceColor, out: MutableList<ChessMove>) {
        for (df in -1..1) {
            for (dr in -1..1) {
                if (df == 0 && dr == 0) continue
                val f = from.file + df
                val r = from.rank + dr
                if (!Square.isValid(f, r)) continue
                val target = board[r * 8 + f]
                if (target == null || target.color != color) {
                    out.add(ChessMove(from, Square(f, r)))
                }
            }
        }

        val enemy = color.opposite()
        val homeRank = if (color == PieceColor.WHITE) 0 else 7
        if (from.file == 4 && from.rank == homeRank && !isSquareAttackedBy(from, enemy)) {
            val canKingSide = if (color == PieceColor.WHITE) whiteKingSide else blackKingSide
            if (canKingSide &&
                pieceAt(5, homeRank) == null &&
                pieceAt(6, homeRank) == null &&
                pieceAt(7, homeRank) == Piece(color, PieceType.ROOK) &&
                !isSquareAttackedBy(Square(5, homeRank), enemy) &&
                !isSquareAttackedBy(Square(6, homeRank), enemy)
            ) {
                out.add(ChessMove(from, Square(6, homeRank)))
            }

            val canQueenSide = if (color == PieceColor.WHITE) whiteQueenSide else blackQueenSide
            if (canQueenSide &&
                pieceAt(3, homeRank) == null &&
                pieceAt(2, homeRank) == null &&
                pieceAt(1, homeRank) == null &&
                pieceAt(0, homeRank) == Piece(color, PieceType.ROOK) &&
                !isSquareAttackedBy(Square(3, homeRank), enemy) &&
                !isSquareAttackedBy(Square(2, homeRank), enemy)
            ) {
                out.add(ChessMove(from, Square(2, homeRank)))
            }
        }
    }

    fun applyMove(move: ChessMove): ChessPosition? {
        val matching = legalMovesFrom(move.from).firstOrNull { legal ->
            legal.to == move.to && (legal.promotion == move.promotion ||
                (move.promotion == null && legal.promotion == PieceType.QUEEN))
        } ?: return null
        return applyUnchecked(matching, updateRepetition = true)
    }

    fun applyUci(uci: String): ChessPosition? {
        val move = ChessMove.fromUci(uci) ?: return null
        return applyMove(move)
    }

    private fun applyUnchecked(move: ChessMove, updateRepetition: Boolean = false): ChessPosition {
        val nextBoard = board.copyOf()
        val moving = nextBoard[move.from.index]!!
        val captured = nextBoard[move.to.index]
        val isEnPassant = moving.type == PieceType.PAWN && move.to == enPassantSquare && captured == null

        nextBoard[move.from.index] = null
        nextBoard[move.to.index] = if (move.promotion != null) Piece(moving.color, move.promotion) else moving

        if (isEnPassant) {
            nextBoard[move.from.rank * 8 + move.to.file] = null
        }

        if (moving.type == PieceType.KING && abs(move.to.file - move.from.file) == 2) {
            val rank = move.from.rank
            if (move.to.file == 6) {
                nextBoard[rank * 8 + 5] = nextBoard[rank * 8 + 7]
                nextBoard[rank * 8 + 7] = null
            } else if (move.to.file == 2) {
                nextBoard[rank * 8 + 3] = nextBoard[rank * 8 + 0]
                nextBoard[rank * 8 + 0] = null
            }
        }

        var wKs = whiteKingSide
        var wQs = whiteQueenSide
        var bKs = blackKingSide
        var bQs = blackQueenSide

        if (moving.type == PieceType.KING) {
            if (moving.color == PieceColor.WHITE) {
                wKs = false; wQs = false
            } else {
                bKs = false; bQs = false
            }
        }
        if (move.from == Square(0, 0) || move.to == Square(0, 0)) wQs = false
        if (move.from == Square(7, 0) || move.to == Square(7, 0)) wKs = false
        if (move.from == Square(0, 7) || move.to == Square(0, 7)) bQs = false
        if (move.from == Square(7, 7) || move.to == Square(7, 7)) bKs = false

        val nextEp = if (moving.type == PieceType.PAWN && abs(move.to.rank - move.from.rank) == 2) {
            Square(move.from.file, (move.from.rank + move.to.rank) / 2)
        } else {
            null
        }

        val nextHalfMove = if (moving.type == PieceType.PAWN || captured != null || isEnPassant) 0 else halfMoveClock + 1
        val nextFullMove = if (sideToMove == PieceColor.BLACK) fullMoveNumber + 1 else fullMoveNumber

        val candidate = ChessPosition(
            board = nextBoard,
            sideToMove = sideToMove.opposite(),
            whiteKingSide = wKs,
            whiteQueenSide = wQs,
            blackKingSide = bKs,
            blackQueenSide = bQs,
            enPassantSquare = nextEp,
            halfMoveClock = nextHalfMove,
            fullMoveNumber = nextFullMove,
            positionCounts = positionCounts
        )

        if (!updateRepetition) return candidate
        val key = candidate.positionKey()
        val updatedCounts = HashMap(positionCounts)
        updatedCounts[key] = (updatedCounts[key] ?: 0) + 1
        return candidate.copy(positionCounts = updatedCounts)
    }

    fun moveToSan(move: ChessMove): String {
        val piece = pieceAt(move.from) ?: return move.uci
        val next = applyUnchecked(move)
        val suffix = when {
            next.legalMoves().isEmpty() && next.isInCheck(next.sideToMove) -> "#"
            next.isInCheck(next.sideToMove) -> "+"
            else -> ""
        }

        if (piece.type == PieceType.KING && abs(move.to.file - move.from.file) == 2) {
            val castle = if (move.to.file == 6) "O-O" else "O-O-O"
            return castle + suffix
        }

        val isCapture = pieceAt(move.to) != null ||
            (piece.type == PieceType.PAWN && move.to == enPassantSquare)

        val sb = StringBuilder()
        if (piece.type == PieceType.PAWN) {
            if (isCapture) {
                sb.append('a' + move.from.file)
                sb.append('x')
            }
            sb.append(move.to.notation)
            if (move.promotion != null) {
                sb.append('=')
                sb.append(move.promotion.sanChar)
            }
        } else {
            sb.append(piece.type.sanChar)
            val ambiguous = legalMoves().filter { other ->
                other.from != move.from &&
                    other.to == move.to &&
                    pieceAt(other.from) == piece
            }
            if (ambiguous.isNotEmpty()) {
                val sameFile = ambiguous.any { it.from.file == move.from.file }
                val sameRank = ambiguous.any { it.from.rank == move.from.rank }
                if (!sameFile) {
                    sb.append('a' + move.from.file)
                } else if (!sameRank) {
                    sb.append('1' + move.from.rank)
                } else {
                    sb.append(move.from.notation)
                }
            }
            if (isCapture) sb.append('x')
            sb.append(move.to.notation)
        }
        sb.append(suffix)
        return sb.toString()
    }

    fun isMoveSacrifice(move: ChessMove): Boolean {
        val moving = pieceAt(move.from) ?: return false
        if (moving.type == PieceType.PAWN || moving.type == PieceType.KING) return false
        val capturedValue = pieceAt(move.to)?.type?.materialValue ?: 0
        if (moving.type.materialValue <= capturedValue) return false
        val next = applyUnchecked(move)
        val attackedByEnemy = next.isSquareAttackedBy(move.to, moving.color.opposite())
        if (!attackedByEnemy) return false
        val defendedByOwn = next.isSquareAttackedBy(move.to, moving.color)
        if (!defendedByOwn) return true
        val enemyAttackers = next.minAttackerValue(move.to, moving.color.opposite())
        return enemyAttackers != null && enemyAttackers < moving.type.materialValue
    }

    private fun minAttackerValue(target: Square, attacker: PieceColor): Int? {
        var lowest: Int? = null
        val savedSide = copy(sideToMove = attacker)
        for (i in 0 until 64) {
            val p = board[i] ?: continue
            if (p.color != attacker) continue
            val from = Square.fromIndex(i)
            val pseudo = savedSide.pseudoLegalMovesFrom(from, p)
            if (pseudo.any { it.to == target }) {
                val value = if (p.type == PieceType.KING) 100 else p.type.materialValue
                if (lowest == null || value < lowest) lowest = value
            }
        }
        return lowest
    }

    fun gameResult(): GameResult {
        val moves = legalMoves()
        if (moves.isEmpty()) {
            return if (isInCheck(sideToMove)) {
                if (sideToMove == PieceColor.WHITE) GameResult.BLACK_WINS_CHECKMATE
                else GameResult.WHITE_WINS_CHECKMATE
            } else {
                GameResult.DRAW_STALEMATE
            }
        }
        if (halfMoveClock >= 100) return GameResult.DRAW_FIFTY_MOVE
        if ((positionCounts[positionKey()] ?: 0) >= 3) return GameResult.DRAW_THREEFOLD
        if (isInsufficientMaterial()) return GameResult.DRAW_INSUFFICIENT_MATERIAL
        return GameResult.ONGOING
    }

    private fun isInsufficientMaterial(): Boolean {
        val minors = ArrayList<Pair<Piece, Square>>()
        for (i in 0 until 64) {
            val p = board[i] ?: continue
            when (p.type) {
                PieceType.PAWN, PieceType.ROOK, PieceType.QUEEN -> return false
                PieceType.BISHOP, PieceType.KNIGHT -> minors.add(p to Square.fromIndex(i))
                PieceType.KING -> Unit
            }
        }
        if (minors.size <= 1) return true
        if (minors.size == 2) {
            val (p1, sq1) = minors[0]
            val (p2, sq2) = minors[1]
            if (p1.type == PieceType.BISHOP && p2.type == PieceType.BISHOP) {
                val color1 = (sq1.file + sq1.rank) % 2
                val color2 = (sq2.file + sq2.rank) % 2
                return color1 == color2
            }
        }
        return false
    }

    fun positionKey(): String {
        val sb = StringBuilder(64)
        for (rank in 7 downTo 0) {
            var empty = 0
            for (file in 0..7) {
                val p = board[rank * 8 + file]
                if (p == null) {
                    empty++
                } else {
                    if (empty > 0) {
                        sb.append(empty)
                        empty = 0
                    }
                    sb.append(p.fenChar)
                }
            }
            if (empty > 0) sb.append(empty)
            if (rank > 0) sb.append('/')
        }
        sb.append(' ')
        sb.append(if (sideToMove == PieceColor.WHITE) 'w' else 'b')
        sb.append(' ')
        val castling = buildString {
            if (whiteKingSide) append('K')
            if (whiteQueenSide) append('Q')
            if (blackKingSide) append('k')
            if (blackQueenSide) append('q')
        }
        sb.append(castling.ifEmpty { "-" })
        sb.append(' ')
        sb.append(enPassantSquare?.notation ?: "-")
        return sb.toString()
    }

    fun materialBalanceCp(): Int {
        var score = 0
        for (i in 0 until 64) {
            val p = board[i] ?: continue
            val value = p.type.materialValue * 100
            score += if (p.color == PieceColor.WHITE) value else -value
        }
        return score
    }

    fun chooseFallbackMove(): ChessMove? {
        val moves = legalMoves()
        if (moves.isEmpty()) return null
        val mover = sideToMove
        var bestMove = moves.first()
        var bestScore = Int.MIN_VALUE

        for (move in moves) {
            val next = applyUnchecked(move)
            val replyMoves = next.legalMoves()
            var score = 0
            if (replyMoves.isEmpty()) {
                score = if (next.isInCheck(next.sideToMove)) 100_000 else 0
            } else {
                val captured = pieceAt(move.to)
                val moving = pieceAt(move.from)
                if (captured != null) {
                    score += captured.type.materialValue * 120 - (moving?.type?.materialValue ?: 0) * 10
                }
                if (move.promotion != null) {
                    score += move.promotion.materialValue * 100
                }
                if (next.isInCheck(next.sideToMove)) {
                    score += 45
                }
                if (move.to.file in 2..5 && move.to.rank in 2..5) {
                    score += 18
                }
                if (next.isSquareAttackedBy(move.to, mover.opposite()) && moving != null) {
                    score -= moving.type.materialValue * 35
                }
            }
            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
        }
        return bestMove
    }

    fun swapOrMovePiece(from: Square, to: Square): ChessPosition {
        if (from == to) return this
        val nextBoard = board.copyOf()
        val temp = nextBoard[to.index]
        nextBoard[to.index] = nextBoard[from.index]
        nextBoard[from.index] = temp

        val wKingHome = nextBoard[4] == Piece(PieceColor.WHITE, PieceType.KING)
        val bKingHome = nextBoard[60] == Piece(PieceColor.BLACK, PieceType.KING)
        val base = ChessPosition(
            board = nextBoard,
            sideToMove = sideToMove,
            whiteKingSide = wKingHome && nextBoard[7] == Piece(PieceColor.WHITE, PieceType.ROOK),
            whiteQueenSide = wKingHome && nextBoard[0] == Piece(PieceColor.WHITE, PieceType.ROOK),
            blackKingSide = bKingHome && nextBoard[63] == Piece(PieceColor.BLACK, PieceType.ROOK),
            blackQueenSide = bKingHome && nextBoard[56] == Piece(PieceColor.BLACK, PieceType.ROOK),
            enPassantSquare = null,
            halfMoveClock = 0,
            fullMoveNumber = 1
        )
        return base.copy(positionCounts = mapOf(base.positionKey() to 1))
    }

    fun withSideToMove(color: PieceColor): ChessPosition {
        val base = copy(
            sideToMove = color,
            enPassantSquare = null,
            halfMoveClock = 0,
            fullMoveNumber = 1
        )
        return base.copy(positionCounts = mapOf(base.positionKey() to 1))
    }

    fun validateForPlay(): String? {
        var whiteKings = 0
        var blackKings = 0
        for (i in 0 until 64) {
            val p = board[i] ?: continue
            val rank = i / 8
            if (p.type == PieceType.KING) {
                if (p.color == PieceColor.WHITE) whiteKings++ else blackKings++
            }
            if (p.type == PieceType.PAWN && (rank == 0 || rank == 7)) {
                return "Pawns cannot start on rank 1 or rank 8"
            }
        }
        if (whiteKings != 1 || blackKings != 1) {
            return "Board must have exactly one White king and one Black king"
        }
        if (isInCheck(sideToMove.opposite())) {
            return "Opponent king cannot start in check"
        }
        if (legalMoves().isEmpty()) {
            return "Starting side has no legal moves"
        }
        return null
    }

    fun toFen(): String = "${positionKey()} $halfMoveClock $fullMoveNumber"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChessPosition) return false
        return board.contentEquals(other.board) &&
            sideToMove == other.sideToMove &&
            whiteKingSide == other.whiteKingSide &&
            whiteQueenSide == other.whiteQueenSide &&
            blackKingSide == other.blackKingSide &&
            blackQueenSide == other.blackQueenSide &&
            enPassantSquare == other.enPassantSquare &&
            halfMoveClock == other.halfMoveClock &&
            fullMoveNumber == other.fullMoveNumber
    }

    override fun hashCode(): Int {
        var result = board.contentHashCode()
        result = 31 * result + sideToMove.hashCode()
        result = 31 * result + whiteKingSide.hashCode()
        result = 31 * result + whiteQueenSide.hashCode()
        result = 31 * result + blackKingSide.hashCode()
        result = 31 * result + blackQueenSide.hashCode()
        result = 31 * result + (enPassantSquare?.hashCode() ?: 0)
        result = 31 * result + halfMoveClock
        result = 31 * result + fullMoveNumber
        return result
    }

    companion object {
        const val INITIAL_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        fun initial(): ChessPosition = fromFen(INITIAL_FEN)

        fun randomUnrestricted(random: Random = Random.Default): ChessPosition {
            val pawnSquares = ArrayList<Int>(16).apply {
                for (f in 0..7) add(1 * 8 + f)
                for (f in 0..7) add(6 * 8 + f)
            }
            val majorSquares = ArrayList<Int>(16).apply {
                for (f in 0..7) add(0 * 8 + f)
                for (f in 0..7) add(7 * 8 + f)
            }
            val pawns = List(8) { Piece(PieceColor.WHITE, PieceType.PAWN) } +
                List(8) { Piece(PieceColor.BLACK, PieceType.PAWN) }
            val majors = listOf(
                Piece(PieceColor.WHITE, PieceType.ROOK),
                Piece(PieceColor.WHITE, PieceType.KNIGHT),
                Piece(PieceColor.WHITE, PieceType.BISHOP),
                Piece(PieceColor.WHITE, PieceType.QUEEN),
                Piece(PieceColor.WHITE, PieceType.KING),
                Piece(PieceColor.WHITE, PieceType.BISHOP),
                Piece(PieceColor.WHITE, PieceType.KNIGHT),
                Piece(PieceColor.WHITE, PieceType.ROOK),
                Piece(PieceColor.BLACK, PieceType.ROOK),
                Piece(PieceColor.BLACK, PieceType.KNIGHT),
                Piece(PieceColor.BLACK, PieceType.BISHOP),
                Piece(PieceColor.BLACK, PieceType.QUEEN),
                Piece(PieceColor.BLACK, PieceType.KING),
                Piece(PieceColor.BLACK, PieceType.BISHOP),
                Piece(PieceColor.BLACK, PieceType.KNIGHT),
                Piece(PieceColor.BLACK, PieceType.ROOK)
            )

            repeat(500) {
                val board = arrayOfNulls<Piece>(64)
                val shuffledPawns = pawns.shuffled(random)
                val shuffledMajors = majors.shuffled(random)
                for (idx in pawnSquares.indices) {
                    board[pawnSquares[idx]] = shuffledPawns[idx]
                }
                for (idx in majorSquares.indices) {
                    board[majorSquares[idx]] = shuffledMajors[idx]
                }
                val base = ChessPosition(
                    board = board,
                    sideToMove = PieceColor.WHITE,
                    whiteKingSide = false,
                    whiteQueenSide = false,
                    blackKingSide = false,
                    blackQueenSide = false,
                    enPassantSquare = null,
                    halfMoveClock = 0,
                    fullMoveNumber = 1
                )
                val candidate = base.copy(positionCounts = mapOf(base.positionKey() to 1))
                if (candidate.validateForPlay() == null) {
                    return candidate
                }
            }
            return initial()
        }



        fun fromFen(fen: String): ChessPosition {
            val parts = fen.trim().split(Regex("\\s+"))
            require(parts.size >= 4) { "Invalid FEN string: $fen" }
            val board = arrayOfNulls<Piece>(64)
            val ranks = parts[0].split('/')
            require(ranks.size == 8) { "Invalid FEN board ranks: $fen" }

            for (rIdx in 0..7) {
                val rank = 7 - rIdx
                var file = 0
                for (ch in ranks[rIdx]) {
                    if (ch.isDigit()) {
                        file += ch - '0'
                    } else {
                        val color = if (ch.isUpperCase()) PieceColor.WHITE else PieceColor.BLACK
                        val type = PieceType.fromFenChar(ch)
                            ?: throw IllegalArgumentException("Invalid FEN piece: $ch")
                        board[rank * 8 + file] = Piece(color, type)
                        file++
                    }
                }
            }

            val side = if (parts[1] == "b") PieceColor.BLACK else PieceColor.WHITE
            val castling = parts[2]
            val ep = if (parts[3] == "-") null else Square.fromNotation(parts[3])
            val half = parts.getOrNull(4)?.toIntOrNull() ?: 0
            val full = parts.getOrNull(5)?.toIntOrNull() ?: 1

            val base = ChessPosition(
                board = board,
                sideToMove = side,
                whiteKingSide = 'K' in castling,
                whiteQueenSide = 'Q' in castling,
                blackKingSide = 'k' in castling,
                blackQueenSide = 'q' in castling,
                enPassantSquare = ep,
                halfMoveClock = half,
                fullMoveNumber = full
            )
            return base.copy(positionCounts = mapOf(base.positionKey() to 1))
        }
    }
}
