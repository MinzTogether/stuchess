package com.stuchess.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.stuchess.app.engine.BotEngineProfile
import com.stuchess.app.engine.StockfishEngine
import com.stuchess.app.game.GameMode
import com.stuchess.app.game.NewGameConfig
import com.stuchess.app.game.PieceColor
import com.stuchess.app.game.TimeControl
import kotlin.math.roundToInt

@Composable
fun SettingsDialog(
    currentThemeName: String,
    currentPiecePackId: String,
    botProfileCount: Int,
    onOpenTheme: () -> Unit,
    onOpenAddSource: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppPalette.SurfacePrimary,
        titleContentColor = AppPalette.TextPrimary,
        textContentColor = AppPalette.TextSecondary,
        title = {
            Text("Settings", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsMenuRow(
                    icon = Icons.Rounded.Palette,
                    title = "Theme",
                    subtitle = "$currentThemeName · ${currentPiecePackId.replaceFirstChar { it.uppercase() }}",
                    onClick = onOpenTheme
                )
                SettingsMenuRow(
                    icon = Icons.Rounded.Code,
                    title = "Add Source",
                    subtitle = "$botProfileCount bot ${if (botProfileCount == 1) "profile" else "profiles"} · .tar.gz, .zip, .rar",
                    onClick = onOpenAddSource
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = AppPalette.TextSecondary)
            }
        }
    )
}

@Composable
private fun SettingsMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppPalette.SurfaceElevated)
            .border(1.dp, AppPalette.HairlineBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = AppPalette.TextPrimary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = AppPalette.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = AppPalette.TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = AppPalette.TextMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeDialog(
    selectedThemeId: String,
    customLightHex: Long,
    customDarkHex: Long,
    selectedPiecePackId: String,
    availablePiecePacks: List<String>,
    onSelectTheme: (String) -> Unit,
    onSelectCustomColors: (Long, Long) -> Unit,
    onSelectPiecePack: (String) -> Unit,
    onRequestImportZip: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val imageLoader = rememberChessImageLoader()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppPalette.SurfacePrimary,
        titleContentColor = AppPalette.TextPrimary,
        textContentColor = AppPalette.TextSecondary,
        title = {
            Text("Theme", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                SectionLabel("Board theme")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeCatalog.presets.forEach { theme ->
                        val selected = selectedThemeId == theme.id
                        BoardSwatchTile(
                            name = theme.name,
                            light = theme.lightSquare,
                            dark = theme.darkSquare,
                            selected = selected,
                            onClick = { onSelectTheme(theme.id) }
                        )
                    }
                }

                SectionLabel("Quick custom palette")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeCatalog.customSwatchPairs.forEach { (lightHex, darkHex) ->
                        val selected = selectedThemeId == "custom" &&
                            customLightHex == lightHex &&
                            customDarkHex == darkHex
                        BoardSwatchTile(
                            name = null,
                            light = Color(lightHex),
                            dark = Color(darkHex),
                            selected = selected,
                            onClick = { onSelectCustomColors(lightHex, darkHex) }
                        )
                    }
                }

                SectionLabel("Piece set")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availablePiecePacks.forEach { packId ->
                        val selected = selectedPiecePackId == packId
                        val previewModel = ThemeCatalog.resolvePieceModel(context, packId, "wn")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) AppPalette.AccentPrimary.copy(alpha = 0.2f)
                                    else AppPalette.SurfaceElevated
                                )
                                .border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) AppPalette.AccentPrimary else AppPalette.HairlineBorder,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectPiecePack(packId) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            AsyncImage(
                                model = previewModel,
                                imageLoader = imageLoader,
                                contentDescription = packId,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = packId.replaceFirstChar { it.uppercase() },
                                color = AppPalette.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onRequestImportZip,
                    border = BorderStroke(1.dp, AppPalette.HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FileUpload,
                        contentDescription = null,
                        tint = AppPalette.TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Piece Pack (.zip: wp..bk)", color = AppPalette.TextPrimary, fontSize = 12.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onBack) {
                Text("Back", color = AppPalette.TextSecondary)
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppPalette.AccentPrimary,
                    contentColor = AppPalette.AccentOnPrimary
                )
            ) {
                Text("Done", fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
fun AddSourceDialog(
    botProfiles: List<BotEngineProfile>,
    activeBotId: String,
    isImportingBot: Boolean,
    engineThreads: Int,
    engineHashMb: Int,
    onSelectBotProfile: (String) -> Unit,
    onRequestImportBotArchive: () -> Unit,
    onDeleteBotProfile: (String) -> Unit,
    onUpdateEngineResources: (Int, Int) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    val maxCores = remember { Runtime.getRuntime().availableProcessors().coerceAtLeast(1) }
    val maxHash = remember { if (StockfishEngine.is64BitDevice()) 128 else 48 }
    var threads by remember { mutableIntStateOf(engineThreads) }
    var hashMb by remember { mutableIntStateOf(engineHashMb) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppPalette.SurfacePrimary,
        titleContentColor = AppPalette.TextPrimary,
        textContentColor = AppPalette.TextSecondary,
        title = {
            Text("Add Source", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                SectionLabel("Bot engines & sources")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    botProfiles.forEach { profile ->
                        val selected = profile.id == activeBotId
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) AppPalette.AccentPrimary.copy(alpha = 0.16f)
                                    else AppPalette.SurfaceElevated
                                )
                                .border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) AppPalette.AccentPrimary else AppPalette.HairlineBorder,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectBotProfile(profile.id) }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Active",
                                            tint = AppPalette.AccentPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(
                                        text = profile.name,
                                        color = AppPalette.TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = profile.archiveFormat.label,
                                        color = AppPalette.TextSecondary,
                                        fontSize = 10.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(AppPalette.BgPrimary)
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                                Text(
                                    text = profile.statusNote,
                                    color = AppPalette.TextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (profile.id != BotEngineProfile.BUILT_IN_ID) {
                                IconButton(
                                    onClick = { onDeleteBotProfile(profile.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Delete bot",
                                        tint = AppPalette.TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onRequestImportBotArchive,
                    enabled = !isImportingBot,
                    border = BorderStroke(1.dp, AppPalette.HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FileUpload,
                        contentDescription = null,
                        tint = AppPalette.TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isImportingBot) "Importing archive..." else "Add Bot Source (.tar.gz, .zip, .rar)",
                        color = AppPalette.TextPrimary,
                        fontSize = 12.sp
                    )
                }

                SectionLabel("Engine resources")
                SliderRow(
                    label = "Threads",
                    valueText = "$threads",
                    value = threads.toFloat(),
                    range = 1f..maxCores.coerceAtMost(4).toFloat(),
                    onValueChange = {
                        threads = it.roundToInt().coerceIn(1, maxCores.coerceAtMost(4))
                        onUpdateEngineResources(threads, hashMb)
                    }
                )
                SliderRow(
                    label = "Hash RAM",
                    valueText = "${hashMb} MB",
                    value = hashMb.toFloat(),
                    range = 8f..maxHash.toFloat(),
                    onValueChange = {
                        hashMb = ((it / 8f).roundToInt() * 8).coerceIn(8, maxHash)
                        onUpdateEngineResources(threads, hashMb)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onBack) {
                Text("Back", color = AppPalette.TextSecondary)
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppPalette.AccentPrimary,
                    contentColor = AppPalette.AccentOnPrimary
                )
            ) {
                Text("Done", fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewGameDialog(
    currentConfig: NewGameConfig,
    botProfiles: List<BotEngineProfile>,
    onDismiss: () -> Unit,
    onStartGame: (NewGameConfig) -> Unit
) {
    var mode by remember { mutableStateOf(currentConfig.mode) }
    var humanColor by remember { mutableStateOf(currentConfig.humanColor) }
    var whiteBotId by remember { mutableStateOf(currentConfig.whiteBotId) }
    var blackBotId by remember { mutableStateOf(currentConfig.blackBotId) }
    var whiteElo by remember { mutableIntStateOf(currentConfig.whiteBotElo) }
    var blackElo by remember { mutableIntStateOf(currentConfig.blackBotElo) }
    var moveTimeMs by remember { mutableIntStateOf(currentConfig.botMoveTimeMs) }
    var botDelayMs by remember { mutableLongStateOf(currentConfig.botVsBotDelayMs) }
    var timeControl by remember { mutableStateOf(currentConfig.timeControl) }
    var autoFlip by remember { mutableStateOf(currentConfig.autoFlipHumanVsHuman) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppPalette.SurfacePrimary,
        titleContentColor = AppPalette.TextPrimary,
        textContentColor = AppPalette.TextSecondary,
        title = {
            Text("New Game", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                SectionLabel("Mode")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    GameMode.entries.forEach { option ->
                        val selected = mode == option
                        ChipButton(
                            label = when (option) {
                                GameMode.BOT_VS_HUMAN -> "Bot"
                                GameMode.BOT_VS_BOT -> "Bot vs Bot"
                                GameMode.HUMAN_VS_HUMAN -> "2 Players"
                            },
                            selected = selected,
                            onClick = { mode = option },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (mode == GameMode.BOT_VS_HUMAN) {
                    SectionLabel("Play as")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ChipButton(
                            label = "White",
                            selected = humanColor == PieceColor.WHITE,
                            onClick = { humanColor = PieceColor.WHITE },
                            modifier = Modifier.weight(1f)
                        )
                        ChipButton(
                            label = "Black",
                            selected = humanColor == PieceColor.BLACK,
                            onClick = { humanColor = PieceColor.BLACK },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (botProfiles.size > 1) {
                        val activeBotId = if (humanColor == PieceColor.WHITE) blackBotId else whiteBotId
                        SectionLabel("Bot engine")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            botProfiles.forEach { bot ->
                                ChipButton(
                                    label = bot.name,
                                    selected = activeBotId == bot.id,
                                    onClick = {
                                        if (humanColor == PieceColor.WHITE) blackBotId = bot.id
                                        else whiteBotId = bot.id
                                    }
                                )
                            }
                        }
                    }

                    val botElo = if (humanColor == PieceColor.WHITE) blackElo else whiteElo
                    SliderRow(
                        label = "Bot strength",
                        valueText = if (botElo >= 3190) "Max" else "$botElo Elo",
                        value = botElo.toFloat(),
                        range = 1320f..3190f,
                        onValueChange = {
                            val snapped = (it / 50f).roundToInt() * 50
                            val clamped = snapped.coerceIn(1320, 3190)
                            if (humanColor == PieceColor.WHITE) blackElo = clamped else whiteElo = clamped
                        }
                    )
                }

                if (mode == GameMode.BOT_VS_BOT) {
                    if (botProfiles.size > 1) {
                        SectionLabel("White engine")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            botProfiles.forEach { bot ->
                                ChipButton(
                                    label = bot.name,
                                    selected = whiteBotId == bot.id,
                                    onClick = { whiteBotId = bot.id }
                                )
                            }
                        }
                        SectionLabel("Black engine")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            botProfiles.forEach { bot ->
                                ChipButton(
                                    label = bot.name,
                                    selected = blackBotId == bot.id,
                                    onClick = { blackBotId = bot.id }
                                )
                            }
                        }
                    }

                    SliderRow(
                        label = "White Bot",
                        valueText = if (whiteElo >= 3190) "Max" else "$whiteElo Elo",
                        value = whiteElo.toFloat(),
                        range = 1320f..3190f,
                        onValueChange = { whiteElo = ((it / 50f).roundToInt() * 50).coerceIn(1320, 3190) }
                    )
                    SliderRow(
                        label = "Black Bot",
                        valueText = if (blackElo >= 3190) "Max" else "$blackElo Elo",
                        value = blackElo.toFloat(),
                        range = 1320f..3190f,
                        onValueChange = { blackElo = ((it / 50f).roundToInt() * 50).coerceIn(1320, 3190) }
                    )
                    SliderRow(
                        label = "Move pace delay",
                        valueText = "${botDelayMs}ms",
                        value = botDelayMs.toFloat(),
                        range = 0f..2000f,
                        onValueChange = { botDelayMs = ((it / 100f).roundToInt() * 100L).coerceIn(0L, 2000L) }
                    )
                }

                if (mode != GameMode.HUMAN_VS_HUMAN) {
                    SliderRow(
                        label = "Bot think time",
                        valueText = "${moveTimeMs}ms",
                        value = moveTimeMs.toFloat(),
                        range = 200f..2500f,
                        onValueChange = { moveTimeMs = ((it / 100f).roundToInt() * 100).coerceIn(200, 2500) }
                    )
                }

                if (mode == GameMode.HUMAN_VS_HUMAN) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Auto-flip board after each move", color = AppPalette.TextPrimary, fontSize = 13.sp)
                        Switch(checked = autoFlip, onCheckedChange = { autoFlip = it })
                    }
                }

                SectionLabel("Time control")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TimeControl.PRESETS.forEach { preset ->
                        ChipButton(
                            label = preset.label,
                            selected = timeControl.id == preset.id,
                            onClick = { timeControl = preset }
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = AppPalette.TextSecondary)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onStartGame(
                        NewGameConfig(
                            mode = mode,
                            humanColor = humanColor,
                            whiteBotId = whiteBotId,
                            blackBotId = blackBotId,
                            whiteBotElo = whiteElo,
                            blackBotElo = blackElo,
                            botMoveTimeMs = moveTimeMs,
                            botVsBotDelayMs = botDelayMs,
                            timeControl = timeControl,
                            autoFlipHumanVsHuman = autoFlip
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppPalette.AccentPrimary,
                    contentColor = AppPalette.AccentOnPrimary
                )
            ) {
                Text("Start", fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
private fun BoardSwatchTile(
    name: String?,
    light: Color,
    dark: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) AppPalette.AccentPrimary.copy(alpha = 0.18f) else AppPalette.SurfaceElevated)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) AppPalette.AccentPrimary else AppPalette.HairlineBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Column(modifier = Modifier.clip(RoundedCornerShape(4.dp))) {
            Row {
                Box(modifier = Modifier.size(14.dp).background(light))
                Box(modifier = Modifier.size(14.dp).background(dark))
            }
            Row {
                Box(modifier = Modifier.size(14.dp).background(dark))
                Box(modifier = Modifier.size(14.dp).background(light))
            }
        }
        if (name != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = name, color = AppPalette.TextPrimary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = AppPalette.TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun ChipButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) AppPalette.AccentPrimary else AppPalette.SurfaceElevated)
            .border(
                1.dp,
                if (selected) AppPalette.AccentPrimary else AppPalette.HairlineBorder,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (selected) AppPalette.AccentOnPrimary else AppPalette.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label, color = AppPalette.TextPrimary, fontSize = 13.sp)
            Text(
                text = valueText,
                color = AppPalette.TextSecondary,
                style = TextStyle(fontSize = 13.sp, fontFeatureSettings = "tnum")
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = AppPalette.AccentPrimary,
                activeTrackColor = AppPalette.AccentPrimary,
                inactiveTrackColor = AppPalette.SurfaceElevated
            )
        )
    }
}
