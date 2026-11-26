package com.stuchess.app.ui

import android.content.Context
import androidx.compose.ui.graphics.Color
import java.io.File

object AppPalette {
    val BgPrimary = Color(0xFF262421)
    val SurfacePrimary = Color(0xFF302E2B)
    val SurfaceElevated = Color(0xFF3A3834)
    val HairlineBorder = Color(0x14FFFFFF)
    val TextPrimary = Color(0xFFF0EEEA)
    val TextSecondary = Color(0xFFB3B0AA)
    val TextMuted = Color(0xFF83807A)
    val AccentPrimary = Color(0xFF81B64C)
    val AccentOnPrimary = Color(0xFF141B0D)
    val Danger = Color(0xFFCA3431)
    val Warning = Color(0xFFE68A2E)
    val HintArrow = Color(0xD181B64C)
}

data class BoardTheme(
    val id: String,
    val name: String,
    val lightSquare: Color,
    val darkSquare: Color,
    val lastMoveLight: Color,
    val lastMoveDark: Color,
    val selectedSquare: Color = Color(0x99F6F669)
)

object ThemeCatalog {
    val presets: List<BoardTheme> = listOf(
        BoardTheme(
            id = "green",
            name = "Green",
            lightSquare = Color(0xFFEBECD0),
            darkSquare = Color(0xFF739552),
            lastMoveLight = Color(0xFFF5F682),
            lastMoveDark = Color(0xFFB9CA43)
        ),
        BoardTheme(
            id = "walnut",
            name = "Walnut",
            lightSquare = Color(0xFFF0D9B5),
            darkSquare = Color(0xFFB58863),
            lastMoveLight = Color(0xFFF7EC74),
            lastMoveDark = Color(0xFFDAC34B)
        ),
        BoardTheme(
            id = "icy_sea",
            name = "Icy Sea",
            lightSquare = Color(0xFFDEE3E6),
            darkSquare = Color(0xFF8CA2AD),
            lastMoveLight = Color(0xFFC7E5F5),
            lastMoveDark = Color(0xFF8BBBD4)
        ),
        BoardTheme(
            id = "tournament",
            name = "Tournament",
            lightSquare = Color(0xFFEAE9D2),
            darkSquare = Color(0xFF4B7399),
            lastMoveLight = Color(0xFFD7E286),
            lastMoveDark = Color(0xFF6F9E8E)
        ),
        BoardTheme(
            id = "dark_slate",
            name = "Dark Slate",
            lightSquare = Color(0xFF9E9E9E),
            darkSquare = Color(0xFF4A4A4A),
            lastMoveLight = Color(0xFFB8B866),
            lastMoveDark = Color(0xFF7C7C38)
        )
    )

    val customSwatchPairs: List<Pair<Long, Long>> = listOf(
        0xFFEBECD0L to 0xFF739552L,
        0xFFF0D9B5L to 0xFFB58863L,
        0xFFEEEED2L to 0xFF567D46L,
        0xFFE8EDF9L to 0xFFB7C0D8L,
        0xFFF3E4D2L to 0xFFA75A44L,
        0xFFD8D9D8L to 0xFF6E7175L,
        0xFFEADBC8L to 0xFF8C583AL,
        0xFFECECD9L to 0xFF4F7A73L
    )

    fun resolveBoardTheme(
        themeId: String,
        customLightHex: Long,
        customDarkHex: Long
    ): BoardTheme {
        if (themeId == "custom") {
            return BoardTheme(
                id = "custom",
                name = "Custom",
                lightSquare = Color(customLightHex),
                darkSquare = Color(customDarkHex),
                lastMoveLight = Color(0xFFF5F682),
                lastMoveDark = Color(0xFFB9CA43)
            )
        }
        return presets.firstOrNull { it.id == themeId } ?: presets.first()
    }

    fun resolvePieceModel(context: Context, packId: String, pieceCode: String): Any {
        if (packId == "classic" || packId == "minimal") {
            return "file:///android_asset/pieces/$packId/$pieceCode.svg"
        }
        val packDir = File(File(context.filesDir, "pieces"), packId)
        for (ext in listOf("svg", "png", "webp")) {
            val candidate = File(packDir, "$pieceCode.$ext")
            if (candidate.exists()) return candidate
        }
        return "file:///android_asset/pieces/classic/$pieceCode.svg"
    }
}
