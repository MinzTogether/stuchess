package com.stuchess.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.outlined.Lightbulb
import java.util.Locale
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.stuchess.app.engine.MoveClassification
import com.stuchess.app.game.GameMode
import com.stuchess.app.game.GameUiState
import com.stuchess.app.game.GameViewModel
import com.stuchess.app.game.MoveRecord
import com.stuchess.app.game.PieceColor

@Composable
fun GameScreen(viewModel: GameViewModel) {
    val state by viewModel.uiState.collectAsState()
    var showNewGameDialog by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAddSourceDialog by remember { mutableStateOf(false) }

    val zipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importPiecePackZip(uri)
        }
    }

    val botArchiveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importBotArchive(uri)
        }
    }

    val boardTheme = remember(
        state.selectedBoardThemeId,
        state.customLightSquareHex,
        state.customDarkSquareHex
    ) {
        ThemeCatalog.resolveBoardTheme(
            state.selectedBoardThemeId,
            state.customLightSquareHex,
            state.customDarkSquareHex
        )
    }

    val topColor = if (state.boardFlipped) PieceColor.WHITE else PieceColor.BLACK
    val bottomColor = if (state.boardFlipped) PieceColor.BLACK else PieceColor.WHITE

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(AppPalette.BgPrimary)
            .safeDrawingPadding()
    ) {
        val isLandscape = maxWidth > maxHeight

        if (isLandscape) {
            val boardEdge = min(maxHeight - 16.dp, maxWidth * 0.54f)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    EvalBar(
                        evalScore = state.displayedMoveRecord?.evalAfter ?: state.currentEval,
                        boardFlipped = state.boardFlipped,
                        barWidth = 22.dp,
                        modifier = Modifier.height(boardEdge)
                    )
                    ChessBoard(
                        position = state.displayedPosition,
                        boardTheme = boardTheme,
                        piecePackId = state.selectedPiecePackId,
                        boardFlipped = state.boardFlipped,
                        selectedSquare = state.selectedSquare,
                        legalTargets = state.legalTargets,
                        lastMove = state.displayedMoveRecord?.move,
                        lastMoveClassification = state.displayedMoveRecord?.classification,
                        hintMove = if (state.isViewingLive) state.hintMove else null,
                        showHints = state.showHints,
                        pendingPromotion = state.pendingPromotion,
                        isInteractive = state.isViewingLive,
                        onSquareTap = viewModel::onSquareTapped,
                        onPieceDrag = viewModel::onPieceDragged,
                        onPromotionSelected = viewModel::completePromotion,
                        onPromotionDismiss = viewModel::cancelPromotion,
                        modifier = Modifier.size(boardEdge)
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    TopStatusStrip(state = state)

                    PlayerBar(
                        name = playerLabel(state, topColor),
                        subtitle = playerSubtitle(state, topColor),
                        timeMs = if (topColor == PieceColor.WHITE) state.whiteTimeMs else state.blackTimeMs,
                        hasClock = state.config.timeControl.initialMs > 0L,
                        isActiveTurn = !state.gameResult.isFinished && state.position.sideToMove == topColor,
                        accuracy = if (topColor == PieceColor.WHITE) state.whiteAccuracy else state.blackAccuracy
                    )

                    EvaluationBanner(state = state)

                    LandscapeMoveList(
                        moves = state.moveRecords,
                        activePly = state.viewingPly ?: state.moveRecords.size,
                        onSelectPly = viewModel::viewHistoryAt,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )

                    if (state.config.mode == GameMode.BOT_VS_BOT) {
                        BotVsBotControlRow(
                            isPaused = state.isBotVsBotPaused,
                            onTogglePause = viewModel::toggleBotVsBotPause,
                            onTakeOverWhite = { viewModel.takeOverFromBot(PieceColor.WHITE) },
                            onTakeOverBlack = { viewModel.takeOverFromBot(PieceColor.BLACK) }
                        )
                    }

                    PlayerBar(
                        name = playerLabel(state, bottomColor),
                        subtitle = playerSubtitle(state, bottomColor),
                        timeMs = if (bottomColor == PieceColor.WHITE) state.whiteTimeMs else state.blackTimeMs,
                        hasClock = state.config.timeControl.initialMs > 0L,
                        isActiveTurn = !state.gameResult.isFinished && state.position.sideToMove == bottomColor,
                        accuracy = if (bottomColor == PieceColor.WHITE) state.whiteAccuracy else state.blackAccuracy
                    )

                    ActionToolbar(
                        state = state,
                        onNewGame = { showNewGameDialog = true },
                        onFlip = viewModel::toggleBoardFlip,
                        onToggleHints = viewModel::toggleHints,
                        onTakeback = viewModel::takeback,
                        onOpenSettings = { showSettingsMenu = true },
                        onStepBack = {
                            val current = state.viewingPly ?: state.moveRecords.size
                            viewModel.viewHistoryAt((current - 1).coerceAtLeast(0))
                        },
                        onStepForward = {
                            val current = state.viewingPly ?: state.moveRecords.size
                            viewModel.viewHistoryAt(current + 1)
                        }
                    )
                }
            }
        } else {
            val boardWidth = min(maxWidth - 46.dp, maxHeight * 0.56f)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                TopStatusStrip(state = state)

                EvaluationBanner(state = state)

                PlayerBar(
                    name = playerLabel(state, topColor),
                    subtitle = playerSubtitle(state, topColor),
                    timeMs = if (topColor == PieceColor.WHITE) state.whiteTimeMs else state.blackTimeMs,
                    hasClock = state.config.timeControl.initialMs > 0L,
                    isActiveTurn = !state.gameResult.isFinished && state.position.sideToMove == topColor,
                    accuracy = if (topColor == PieceColor.WHITE) state.whiteAccuracy else state.blackAccuracy
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    EvalBar(
                        evalScore = state.displayedMoveRecord?.evalAfter ?: state.currentEval,
                        boardFlipped = state.boardFlipped,
                        barWidth = 20.dp,
                        modifier = Modifier.height(boardWidth)
                    )
                    ChessBoard(
                        position = state.displayedPosition,
                        boardTheme = boardTheme,
                        piecePackId = state.selectedPiecePackId,
                        boardFlipped = state.boardFlipped,
                        selectedSquare = state.selectedSquare,
                        legalTargets = state.legalTargets,
                        lastMove = state.displayedMoveRecord?.move,
                        lastMoveClassification = state.displayedMoveRecord?.classification,
                        hintMove = if (state.isViewingLive) state.hintMove else null,
                        showHints = state.showHints,
                        pendingPromotion = state.pendingPromotion,
                        isInteractive = state.isViewingLive,
                        onSquareTap = viewModel::onSquareTapped,
                        onPieceDrag = viewModel::onPieceDragged,
                        onPromotionSelected = viewModel::completePromotion,
                        onPromotionDismiss = viewModel::cancelPromotion,
                        modifier = Modifier.size(boardWidth)
                    )
                }

                PlayerBar(
                    name = playerLabel(state, bottomColor),
                    subtitle = playerSubtitle(state, bottomColor),
                    timeMs = if (bottomColor == PieceColor.WHITE) state.whiteTimeMs else state.blackTimeMs,
                    hasClock = state.config.timeControl.initialMs > 0L,
                    isActiveTurn = !state.gameResult.isFinished && state.position.sideToMove == bottomColor,
                    accuracy = if (bottomColor == PieceColor.WHITE) state.whiteAccuracy else state.blackAccuracy
                )

                PortraitMoveStrip(
                    moves = state.moveRecords,
                    activePly = state.viewingPly ?: state.moveRecords.size,
                    onSelectPly = viewModel::viewHistoryAt
                )

                if (state.config.mode == GameMode.BOT_VS_BOT) {
                    BotVsBotControlRow(
                        isPaused = state.isBotVsBotPaused,
                        onTogglePause = viewModel::toggleBotVsBotPause,
                        onTakeOverWhite = { viewModel.takeOverFromBot(PieceColor.WHITE) },
                        onTakeOverBlack = { viewModel.takeOverFromBot(PieceColor.BLACK) }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                ActionToolbar(
                    state = state,
                    onNewGame = { showNewGameDialog = true },
                    onFlip = viewModel::toggleBoardFlip,
                    onToggleHints = viewModel::toggleHints,
                    onTakeback = viewModel::takeback,
                    onOpenSettings = { showSettingsMenu = true },
                    onStepBack = {
                        val current = state.viewingPly ?: state.moveRecords.size
                        viewModel.viewHistoryAt((current - 1).coerceAtLeast(0))
                    },
                    onStepForward = {
                        val current = state.viewingPly ?: state.moveRecords.size
                        viewModel.viewHistoryAt(current + 1)
                    }
                )
            }
        }
    }

    if (showNewGameDialog) {
        NewGameDialog(
            currentConfig = state.config,
            botProfiles = state.botProfiles,
            onDismiss = { showNewGameDialog = false },
            onStartGame = { config ->
                showNewGameDialog = false
                viewModel.startNewGame(config)
            }
        )
    }

    if (showSettingsMenu) {
        SettingsDialog(
            currentThemeName = boardTheme.name,
            currentPiecePackId = state.selectedPiecePackId,
            botProfileCount = state.botProfiles.size,
            onOpenTheme = {
                showSettingsMenu = false
                showThemeDialog = true
            },
            onOpenAddSource = {
                showSettingsMenu = false
                showAddSourceDialog = true
            },
            onDismiss = { showSettingsMenu = false }
        )
    }

    if (showThemeDialog) {
        ThemeDialog(
            selectedThemeId = state.selectedBoardThemeId,
            customLightHex = state.customLightSquareHex,
            customDarkHex = state.customDarkSquareHex,
            selectedPiecePackId = state.selectedPiecePackId,
            availablePiecePacks = state.availablePiecePackIds,
            onSelectTheme = viewModel::selectBoardTheme,
            onSelectCustomColors = viewModel::setCustomBoardColors,
            onSelectPiecePack = viewModel::selectPiecePack,
            onRequestImportZip = { zipLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
            onBack = {
                showThemeDialog = false
                showSettingsMenu = true
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showAddSourceDialog) {
        val activeBotId = if (state.config.mode == GameMode.BOT_VS_HUMAN && state.config.humanColor == PieceColor.BLACK) {
            state.config.whiteBotId
        } else {
            state.config.blackBotId
        }
        AddSourceDialog(
            botProfiles = state.botProfiles,
            activeBotId = activeBotId,
            isImportingBot = state.isImportingBot,
            engineThreads = state.engineThreads,
            engineHashMb = state.engineHashMb,
            onSelectBotProfile = viewModel::selectActiveBot,
            onRequestImportBotArchive = { botArchiveLauncher.launch(arrayOf("*/*")) },
            onDeleteBotProfile = viewModel::deleteBotProfile,
            onUpdateEngineResources = viewModel::updateEngineResources,
            onBack = {
                showAddSourceDialog = false
                showSettingsMenu = true
            },
            onDismiss = { showAddSourceDialog = false }
        )
    }
}

@Composable
private fun TopStatusStrip(state: GameUiState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
    ) {
        Text(
            text = state.config.mode.label,
            color = AppPalette.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        val rightText = when {
            state.gameResult.isFinished -> state.gameResult.label
            state.isEngineThinking -> "Stockfish thinking..."
            state.statusMessage != null -> state.statusMessage
            state.engineNotice != null -> "No engine binary"
            !state.isViewingLive -> "Viewing move ${state.viewingPly}/${state.moveRecords.size}"
            else -> if (state.position.sideToMove == PieceColor.WHITE) "White to move" else "Black to move"
        }
        Text(
            text = rightText,
            color = if (state.gameResult.isFinished) AppPalette.AccentPrimary else AppPalette.TextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EvaluationBanner(state: GameUiState) {
    val record = state.displayedMoveRecord
    val classification = record?.classification

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AppPalette.SurfacePrimary)
            .border(1.dp, AppPalette.HairlineBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        if (state.engineNotice != null) {
            Text(
                text = state.engineNotice,
                color = AppPalette.Warning,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        } else if (record != null && classification != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(classification.colorHex))
                ) {
                    Text(
                        text = classification.badge,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${record.san} is ${classification.label}",
                    color = AppPalette.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val bestHint = if (state.showHints && record.bestMoveBefore != null &&
                classification in listOf(
                    MoveClassification.INACCURACY,
                    MoveClassification.MISTAKE,
                    MoveClassification.BLUNDER
                )
            ) "Best: ${record.bestMoveBefore}" else (record.evalAfter ?: state.currentEval).formatDisplay()

            Text(
                text = bestHint,
                color = AppPalette.TextSecondary,
                style = TextStyle(fontSize = 12.sp, fontFeatureSettings = "tnum")
            )
        } else {
            val hintText = if (state.showHints && state.hintMove != null && state.isViewingLive) {
                "Hint: ${state.hintMove.uci}"
            } else {
                "Eval ready"
            }
            Text(
                text = hintText,
                color = AppPalette.TextSecondary,
                fontSize = 12.sp
            )
            Text(
                text = state.currentEval.formatDisplay(),
                color = AppPalette.TextPrimary,
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
            )
        }
    }
}

@Composable
private fun PlayerBar(
    name: String,
    subtitle: String,
    timeMs: Long,
    hasClock: Boolean,
    isActiveTurn: Boolean,
    accuracy: Double?
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isActiveTurn) AppPalette.AccentPrimary else AppPalette.TextMuted)
            )
            Text(
                text = name,
                color = AppPalette.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    color = AppPalette.TextMuted,
                    style = TextStyle(fontSize = 12.sp, fontFeatureSettings = "tnum")
                )
            }
            if (accuracy != null) {
                Text(
                    text = String.format(Locale.US, "%.1f%%", accuracy),
                    color = AppPalette.TextSecondary,
                    style = TextStyle(fontSize = 11.sp, fontFeatureSettings = "tnum"),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AppPalette.SurfaceElevated)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }

        if (hasClock) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isActiveTurn) AppPalette.SurfaceElevated else AppPalette.SurfacePrimary)
                    .border(
                        1.dp,
                        if (isActiveTurn) AppPalette.AccentPrimary.copy(alpha = 0.6f) else AppPalette.HairlineBorder,
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = formatClock(timeMs),
                    color = if (timeMs in 1..15_000) AppPalette.Danger else AppPalette.TextPrimary,
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum"
                    )
                )
            }
        }
    }
}

@Composable
private fun PortraitMoveStrip(
    moves: List<MoveRecord>,
    activePly: Int,
    onSelectPly: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(moves.size) {
        if (moves.isNotEmpty()) {
            listState.animateScrollToItem(moves.lastIndex)
        }
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
    ) {
        itemsIndexed(moves) { index, record ->
            val ply = index + 1
            val selected = ply == activePly
            val moveNumberPrefix = if (record.mover == PieceColor.WHITE) "${(index / 2) + 1}. " else ""
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) AppPalette.SurfaceElevated else AppPalette.SurfacePrimary)
                    .border(
                        1.dp,
                        if (selected) AppPalette.AccentPrimary else AppPalette.HairlineBorder,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { onSelectPly(ply) }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "$moveNumberPrefix${record.san}",
                    color = AppPalette.TextPrimary,
                    style = TextStyle(fontSize = 12.sp, fontFeatureSettings = "tnum")
                )
                record.classification?.let { cls ->
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(cls.colorHex))
                    )
                }
            }
        }
    }
}

@Composable
private fun LandscapeMoveList(
    moves: List<MoveRecord>,
    activePly: Int,
    onSelectPly: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val pairs = remember(moves) { moves.chunked(2) }
    val listState = rememberLazyListState()
    LaunchedEffect(pairs.size) {
        if (pairs.isNotEmpty()) {
            listState.animateScrollToItem(pairs.lastIndex)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AppPalette.SurfacePrimary)
            .border(1.dp, AppPalette.HairlineBorder, RoundedCornerShape(8.dp))
    ) {
        if (pairs.isEmpty()) {
            Text(
                text = "Moves will appear here",
                color = AppPalette.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(pairs) { rowIdx, pair ->
                    val whiteMove = pair[0]
                    val blackMove = pair.getOrNull(1)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "${rowIdx + 1}.",
                            color = AppPalette.TextMuted,
                            style = TextStyle(fontSize = 12.sp, fontFeatureSettings = "tnum"),
                            modifier = Modifier.width(28.dp)
                        )
                        MoveCell(
                            record = whiteMove,
                            selected = whiteMove.ply == activePly,
                            onClick = { onSelectPly(whiteMove.ply) },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        if (blackMove != null) {
                            MoveCell(
                                record = blackMove,
                                selected = blackMove.ply == activePly,
                                onClick = { onSelectPly(blackMove.ply) },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoveCell(
    record: MoveRecord,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(if (selected) AppPalette.SurfaceElevated else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(
            text = record.san,
            color = AppPalette.TextPrimary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
        record.classification?.let { cls ->
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Color(cls.colorHex))
            )
        }
    }
}

@Composable
private fun BotVsBotControlRow(
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onTakeOverWhite: () -> Unit,
    onTakeOverBlack: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        CompactControlPill(
            label = if (isPaused) "Resume" else "Pause",
            icon = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
            accent = isPaused,
            onClick = onTogglePause,
            modifier = Modifier.weight(1f)
        )
        CompactControlPill(
            label = "Play White",
            icon = null,
            accent = false,
            onClick = onTakeOverWhite,
            modifier = Modifier.weight(1f)
        )
        CompactControlPill(
            label = "Play Black",
            icon = null,
            accent = false,
            onClick = onTakeOverBlack,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CompactControlPill(
    label: String,
    icon: ImageVector?,
    accent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (accent) AppPalette.AccentPrimary else AppPalette.SurfacePrimary)
            .border(1.dp, AppPalette.HairlineBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp, horizontal = 8.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (accent) AppPalette.AccentOnPrimary else AppPalette.TextPrimary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = label,
            color = if (accent) AppPalette.AccentOnPrimary else AppPalette.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun ActionToolbar(
    state: GameUiState,
    onNewGame: () -> Unit,
    onFlip: () -> Unit,
    onToggleHints: () -> Unit,
    onTakeback: () -> Unit,
    onOpenSettings: () -> Unit,
    onStepBack: () -> Unit,
    onStepForward: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppPalette.SurfacePrimary)
            .border(1.dp, AppPalette.HairlineBorder, RoundedCornerShape(10.dp))
            .padding(6.dp)
    ) {
        ToolbarAction(Icons.Rounded.Add, "New", false, onNewGame, Modifier.weight(1f))
        ToolbarAction(Icons.Rounded.Undo, "Undo", false, onTakeback, Modifier.weight(1f))
        ToolbarAction(
            icon = if (state.showHints) Icons.Rounded.Lightbulb else Icons.Outlined.Lightbulb,
            label = "Hint",
            active = state.showHints,
            onClick = onToggleHints,
            modifier = Modifier.weight(1f)
        )
        ToolbarAction(Icons.Rounded.SwapVert, "Flip", false, onFlip, Modifier.weight(1f))
        ToolbarAction(Icons.Rounded.Settings, "Settings", false, onOpenSettings, Modifier.weight(1f))
        ToolbarAction(Icons.Rounded.ChevronLeft, "Prev", false, onStepBack, Modifier.weight(0.8f))
        ToolbarAction(Icons.Rounded.ChevronRight, "Next", false, onStepForward, Modifier.weight(0.8f))
    }
}

@Composable
private fun ToolbarAction(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) AppPalette.AccentPrimary.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) AppPalette.AccentPrimary else AppPalette.TextPrimary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = if (active) AppPalette.AccentPrimary else AppPalette.TextSecondary,
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

private fun playerLabel(state: GameUiState, color: PieceColor): String = when (state.config.mode) {
    GameMode.HUMAN_VS_HUMAN -> if (color == PieceColor.WHITE) "White" else "Black"
    GameMode.BOT_VS_BOT -> if (color == PieceColor.WHITE) "Stockfish White" else "Stockfish Black"
    GameMode.BOT_VS_HUMAN -> if (color == state.config.humanColor) "You" else "Stockfish"
}

private fun playerSubtitle(state: GameUiState, color: PieceColor): String = when (state.config.mode) {
    GameMode.HUMAN_VS_HUMAN -> ""
    GameMode.BOT_VS_BOT -> {
        val elo = if (color == PieceColor.WHITE) state.config.whiteBotElo else state.config.blackBotElo
        if (elo >= 3190) "Max" else "$elo"
    }
    GameMode.BOT_VS_HUMAN -> {
        if (color == state.config.humanColor) ""
        else {
            val elo = if (color == PieceColor.WHITE) state.config.whiteBotElo else state.config.blackBotElo
            if (elo >= 3190) "Max" else "$elo"
        }
    }
}

private fun formatClock(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (ms in 1..19_999L) {
        val tenths = (ms % 1000L) / 100L
        String.format(Locale.US, "%d:%02d.%d", minutes, seconds, tenths)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
