package com.stuchess.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stuchess.app.engine.EvalScore

@Composable
fun EvalBar(
    evalScore: EvalScore,
    boardFlipped: Boolean,
    modifier: Modifier = Modifier,
    barWidth: Dp = 22.dp
) {
    val targetWhiteFraction = evalScore.toBarFraction()
    val animatedWhiteFraction by animateFloatAsState(
        targetValue = targetWhiteFraction,
        animationSpec = tween(durationMillis = 280),
        label = "eval_bar_fraction"
    )

    val whiteColor = Color(0xFFECEAE4)
    val blackColor = Color(0xFF3E3C39)
    val topIsBlack = !boardFlipped
    val label = evalScore.formatDisplay()
    val whiteWinning = evalScore.toWinPercentForWhite() >= 50.0

    Box(
        modifier = modifier
            .width(barWidth)
            .clip(RoundedCornerShape(4.dp))
            .background(if (topIsBlack) blackColor else whiteColor)
            .border(1.dp, AppPalette.HairlineBorder, RoundedCornerShape(4.dp))
    ) {
        if (topIsBlack) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(animatedWhiteFraction)
                    .align(Alignment.BottomCenter)
                    .background(whiteColor)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(1f - animatedWhiteFraction)
                    .align(Alignment.BottomCenter)
                    .background(blackColor)
            )
        }

        val labelAlignment = when {
            whiteWinning && topIsBlack -> Alignment.BottomCenter
            whiteWinning && !topIsBlack -> Alignment.TopCenter
            !whiteWinning && topIsBlack -> Alignment.TopCenter
            else -> Alignment.BottomCenter
        }
        val textColor = if (whiteWinning) Color(0xFF262421) else Color(0xFFF0EEEA)

        Text(
            text = label,
            color = textColor,
            style = TextStyle(
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum"
            ),
            maxLines = 1,
            modifier = Modifier
                .align(labelAlignment)
                .padding(vertical = 3.dp)
        )
    }
}
