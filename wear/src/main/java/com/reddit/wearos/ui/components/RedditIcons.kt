package com.reddit.wearos.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun RedditUpvoteIcon(
    isVoted: Boolean,
    activeColor: Color = Color(0xFFFF4500),
    inactiveColor: Color = Color(0xFF888888),
    size: Dp = 14.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.12f)
            lineTo(w * 0.90f, h * 0.58f)
            lineTo(w * 0.65f, h * 0.58f)
            lineTo(w * 0.65f, h * 0.90f)
            lineTo(w * 0.35f, h * 0.90f)
            lineTo(w * 0.35f, h * 0.58f)
            lineTo(w * 0.10f, h * 0.58f)
            close()
        }

        if (isVoted) {
            drawPath(path = path, color = activeColor, style = Fill)
        } else {
            drawPath(
                path = path,
                color = inactiveColor,
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

@Composable
fun RedditDownvoteIcon(
    isVoted: Boolean,
    activeColor: Color = Color(0xFF7193FF),
    inactiveColor: Color = Color(0xFF888888),
    size: Dp = 14.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.88f)
            lineTo(w * 0.90f, h * 0.42f)
            lineTo(w * 0.65f, h * 0.42f)
            lineTo(w * 0.65f, h * 0.10f)
            lineTo(w * 0.35f, h * 0.10f)
            lineTo(w * 0.35f, h * 0.42f)
            lineTo(w * 0.10f, h * 0.42f)
            close()
        }

        if (isVoted) {
            drawPath(path = path, color = activeColor, style = Fill)
        } else {
            drawPath(
                path = path,
                color = inactiveColor,
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

@Composable
fun RedditBookmarkIcon(
    isSaved: Boolean,
    activeColor: Color = Color(0xFFFF4500),
    inactiveColor: Color = Color(0xFFCCCCCC),
    size: Dp = 14.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        val path = Path().apply {
            moveTo(w * 0.18f, h * 0.08f)
            lineTo(w * 0.82f, h * 0.08f)
            lineTo(w * 0.82f, h * 0.92f)
            lineTo(w * 0.50f, h * 0.65f)
            lineTo(w * 0.18f, h * 0.92f)
            close()
        }

        if (isSaved) {
            drawPath(path = path, color = activeColor, style = Fill)
        } else {
            drawPath(
                path = path,
                color = inactiveColor,
                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}
