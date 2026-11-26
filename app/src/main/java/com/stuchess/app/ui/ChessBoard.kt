package com.stuchess.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import com.stuchess.app.engine.MoveClassification
import com.stuchess.app.game.ChessMove
import com.stuchess.app.game.ChessPosition
import com.stuchess.app.game.PendingPromotion
import com.stuchess.app.game.Piece
import com.stuchess.app.game.PieceType
import com.stuchess.app.game.Square
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun rememberChessImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember(context) {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }
}


@Composable
fun MoveClassificationBadge(
    classification: MoveClassification,
    size: Dp,
    fontSize: TextUnit,
    modifier: Modifier = Modifier
) {
    val bgColor = Color(classification.colorHex)
    val textColor = Color(classification.badgeTextColorHex)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor)
            .border(1.5.dp, Color(0xCC1E1C1A), CircleShape)
            .padding(1.dp)
            .border(0.75.dp, Color(0x40FFFFFF), CircleShape)
    ) {
        Text(
            text = classification.badge,
            color = textColor,
            style = TextStyle(
                fontSize = fontSize,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = fontSize
            ),
            maxLines = 1
        )
    }
}

@Composable
fun ChessBoard(
    position: ChessPosition,
    boardTheme: BoardTheme,
    piecePackId: String,
    boardFlipped: Boolean,
    selectedSquare: Square?,
    legalTargets: List<ChessMove>,
    lastMove: ChessMove?,
    lastMoveClassification: MoveClassification?,
    hintMove: ChessMove?,
    showHints: Boolean,
    pendingPromotion: PendingPromotion?,
    isInteractive: Boolean,
    allowFreePiecePlacement: Boolean = false,
    onSquareTap: (Square) -> Unit,
    onPieceDrag: (Square, Square) -> Unit,
    onPromotionSelected: (PieceType) -> Unit,
    onPromotionDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = rememberChessImageLoader()
    val density = LocalDensity.current

    var draggingFrom by remember(position) { mutableStateOf<Square?>(null) }
    var dragOffset by remember(position) { mutableStateOf(Offset.Zero) }

    val legalTargetSet = remember(legalTargets) { legalTargets.map { it.to }.toSet() }
    val checkedKingSquare = remember(position) {
        if (position.isInCheck(position.sideToMove)) position.kingSquare(position.sideToMove) else null
    }

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, AppPalette.HairlineBorder, RoundedCornerShape(6.dp))
    ) {
        val boardSizePx = with(density) { maxWidth.toPx() }
        val squareSizePx = boardSizePx / 8f
        val squareSizeDp = maxWidth / 8f

        fun offsetToSquare(offset: Offset): Square? {
            val col = (offset.x / squareSizePx).toInt()
            val row = (offset.y / squareSizePx).toInt()
            if (col !in 0..7 || row !in 0..7) return null
            val file = if (boardFlipped) 7 - col else col
            val rank = if (boardFlipped) row else 7 - row
            return Square(file, rank)
        }

        fun squareToColRow(square: Square): Pair<Int, Int> {
            val col = if (boardFlipped) 7 - square.file else square.file
            val row = if (boardFlipped) square.rank else 7 - square.rank
            return col to row
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(position, boardFlipped, isInteractive, boardSizePx) {
                    if (!isInteractive) return@pointerInput
                    detectTapGestures { pos ->
                        offsetToSquare(pos)?.let { onSquareTap(it) }
                    }
                }
                .pointerInput(position, boardFlipped, isInteractive, allowFreePiecePlacement, boardSizePx) {
                    if (!isInteractive) return@pointerInput
                    detectDragGestures(
                        onDragStart = { startPos ->
                            val sq = offsetToSquare(startPos)
                            val piece = sq?.let { position.pieceAt(it) }
                            val canDrag = piece != null && (allowFreePiecePlacement || piece.color == position.sideToMove)
                            if (sq != null && canDrag) {
                                draggingFrom = sq
                                dragOffset = startPos
                                onSquareTap(sq)
                            }
                        },
                        onDrag = { change, _ ->
                            if (draggingFrom != null) {
                                change.consume()
                                dragOffset = change.position
                            }
                        },
                        onDragEnd = {
                            val from = draggingFrom
                            val to = offsetToSquare(dragOffset)
                            draggingFrom = null
                            if (from != null && to != null && from != to) {
                                onPieceDrag(from, to)
                            }
                        },
                        onDragCancel = {
                            draggingFrom = null
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val sqSize = size.width / 8f
                for (row in 0..7) {
                    for (col in 0..7) {
                        val file = if (boardFlipped) 7 - col else col
                        val rank = if (boardFlipped) row else 7 - row
                        val sq = Square(file, rank)
                        val isLight = (file + rank) % 2 != 0
                        var bgColor = if (isLight) boardTheme.lightSquare else boardTheme.darkSquare

                        if (lastMove != null && (sq == lastMove.from || sq == lastMove.to)) {
                            bgColor = if (isLight) boardTheme.lastMoveLight else boardTheme.lastMoveDark
                        }

                        drawRect(
                            color = bgColor,
                            topLeft = Offset(col * sqSize, row * sqSize),
                            size = Size(sqSize, sqSize)
                        )

                        if (sq == selectedSquare) {
                            drawRect(
                                color = boardTheme.selectedSquare,
                                topLeft = Offset(col * sqSize, row * sqSize),
                                size = Size(sqSize, sqSize)
                            )
                        }

                        if (sq == checkedKingSquare) {
                            drawRect(
                                color = Color(0x88CA3431),
                                topLeft = Offset(col * sqSize, row * sqSize),
                                size = Size(sqSize, sqSize)
                            )
                        }

                        if (sq in legalTargetSet) {
                            val center = Offset(col * sqSize + sqSize / 2f, row * sqSize + sqSize / 2f)
                            val hasPiece = position.pieceAt(sq) != null
                            if (hasPiece) {
                                drawCircle(
                                    color = Color(0x40000000),
                                    radius = sqSize * 0.43f,
                                    center = center,
                                    style = Stroke(width = sqSize * 0.09f)
                                )
                            } else {
                                drawCircle(
                                    color = Color(0x38000000),
                                    radius = sqSize * 0.16f,
                                    center = center
                                )
                            }
                        }
                    }
                }

                if (!allowFreePiecePlacement && showHints && hintMove != null) {
                    drawHintArrow(
                        from = hintMove.from,
                        to = hintMove.to,
                        boardFlipped = boardFlipped,
                        sqSize = sqSize,
                        color = AppPalette.HintArrow
                    )
                }
            }

            for (row in 0..7) {
                val rank = if (boardFlipped) row else 7 - row
                val isLight = ((if (boardFlipped) 7 else 0) + rank) % 2 != 0
                val coordColor = if (isLight) boardTheme.darkSquare else boardTheme.lightSquare
                Text(
                    text = "${rank + 1}",
                    color = coordColor,
                    style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier
                        .offset(x = 2.dp, y = squareSizeDp * row + 1.dp)
                )
            }
            for (col in 0..7) {
                val file = if (boardFlipped) 7 - col else col
                val isLight = (file + (if (boardFlipped) 7 else 0)) % 2 != 0
                val coordColor = if (isLight) boardTheme.darkSquare else boardTheme.lightSquare
                Text(
                    text = "${'a' + file}",
                    color = coordColor,
                    style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier
                        .offset(
                            x = squareSizeDp * (col + 1) - 8.dp,
                            y = squareSizeDp * 8 - 12.dp
                        )
                )
            }

            for (i in 0 until 64) {
                val piece = position.board[i] ?: continue
                val sq = Square.fromIndex(i)
                if (sq == draggingFrom) continue
                val (col, row) = squareToColRow(sq)
                val model = remember(piecePackId, piece.code) {
                    ThemeCatalog.resolvePieceModel(context, piecePackId, piece.code)
                }
                AsyncImage(
                    model = model,
                    imageLoader = imageLoader,
                    contentDescription = piece.code,
                    modifier = Modifier
                        .size(squareSizeDp)
                        .offset(x = squareSizeDp * col, y = squareSizeDp * row)
                        .padding(2.dp)
                )
            }

            if (!allowFreePiecePlacement && lastMove != null && lastMoveClassification != null) {
                val (col, row) = squareToColRow(lastMove.to)
                val badgeSize = (squareSizeDp * 0.44f).coerceIn(20.dp, 28.dp)
                val badgeFont = if (lastMoveClassification.badge.length > 1) 10.sp else 12.sp
                MoveClassificationBadge(
                    classification = lastMoveClassification,
                    size = badgeSize,
                    fontSize = badgeFont,
                    modifier = Modifier.offset(
                        x = (squareSizeDp * (col + 1) - badgeSize + 3.dp).coerceAtMost(squareSizeDp * 8 - badgeSize),
                        y = (squareSizeDp * row - 3.dp).coerceAtLeast(0.dp)
                    )
                )
            }

            draggingFrom?.let { fromSq ->
                position.pieceAt(fromSq)?.let { piece ->
                    val model = remember(piecePackId, piece.code) {
                        ThemeCatalog.resolvePieceModel(context, piecePackId, piece.code)
                    }
                    val liftedSizePx = squareSizePx * 1.14f
                    AsyncImage(
                        model = model,
                        imageLoader = imageLoader,
                        contentDescription = piece.code,
                        modifier = Modifier
                            .size(squareSizeDp * 1.14f)
                            .offset {
                                IntOffset(
                                    x = (dragOffset.x - liftedSizePx / 2f).roundToInt(),
                                    y = (dragOffset.y - liftedSizePx / 2f).roundToInt()
                                )
                            }
                    )
                }
            }

            if (pendingPromotion != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x99000000))
                        .clickable { onPromotionDismiss() }
                ) {
                    Surface(
                        color = AppPalette.SurfacePrimary,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppPalette.HairlineBorder)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(12.dp)
                        ) {
                            listOf(
                                PieceType.QUEEN,
                                PieceType.ROOK,
                                PieceType.BISHOP,
                                PieceType.KNIGHT
                            ).forEach { promoType ->
                                val piece = Piece(pendingPromotion.color, promoType)
                                val model = ThemeCatalog.resolvePieceModel(context, piecePackId, piece.code)
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AppPalette.SurfaceElevated)
                                        .clickable { onPromotionSelected(promoType) }
                                        .padding(4.dp)
                                ) {
                                    AsyncImage(
                                        model = model,
                                        imageLoader = imageLoader,
                                        contentDescription = promoType.name,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawHintArrow(
    from: Square,
    to: Square,
    boardFlipped: Boolean,
    sqSize: Float,
    color: Color
) {
    val fromCol = if (boardFlipped) 7 - from.file else from.file
    val fromRow = if (boardFlipped) from.rank else 7 - from.rank
    val toCol = if (boardFlipped) 7 - to.file else to.file
    val toRow = if (boardFlipped) to.rank else 7 - to.rank

    val start = Offset(fromCol * sqSize + sqSize / 2f, fromRow * sqSize + sqSize / 2f)
    val end = Offset(toCol * sqSize + sqSize / 2f, toRow * sqSize + sqSize / 2f)

    val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
    val headLength = sqSize * 0.36f
    val headHalfWidth = sqSize * 0.24f
    val shaftEnd = Offset(
        x = end.x - (headLength * 0.72f * cos(angle)).toFloat(),
        y = end.y - (headLength * 0.72f * sin(angle)).toFloat()
    )

    drawLine(
        color = color,
        start = start,
        end = shaftEnd,
        strokeWidth = sqSize * 0.18f,
        cap = StrokeCap.Round
    )

    val leftWing = Offset(
        x = end.x - (headLength * cos(angle) - headHalfWidth * sin(angle)).toFloat(),
        y = end.y - (headLength * sin(angle) + headHalfWidth * cos(angle)).toFloat()
    )
    val rightWing = Offset(
        x = end.x - (headLength * cos(angle) + headHalfWidth * sin(angle)).toFloat(),
        y = end.y - (headLength * sin(angle) - headHalfWidth * cos(angle)).toFloat()
    )

    val path = Path().apply {
        moveTo(end.x, end.y)
        lineTo(leftWing.x, leftWing.y)
        lineTo(rightWing.x, rightWing.y)
        close()
    }
    drawPath(path = path, color = color)
}
