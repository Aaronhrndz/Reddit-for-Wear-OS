package com.reddit.wearos.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.reddit.wearos.ui.comments.TreeLineSegment

@Composable
fun TreeLineCanvas(
    segments: List<TreeLineSegment>,
    modifier: Modifier = Modifier
) {
    if (segments.isEmpty()) return

    val treeLineColor = Color(0xFF6E6E80)
    val colStep = 10.dp
    val gutterWidth = (segments.size * 10).dp

    Canvas(
        modifier = modifier
            .width(gutterWidth)
            .fillMaxHeight()
            .padding(end = 2.dp)
    ) {
        val strokeWidthPx = 1.8.dp.toPx()
        val colStepPx = colStep.toPx()
        val cornerY = 16.dp.toPx()

        for (col in segments.indices) {
            val seg = segments[col]
            val x = colStepPx * col + (colStepPx / 2f)

            // Segmento vertical superior (y=0 hasta cornerY)
            if (seg.hasTop) {
                drawLine(
                    color = treeLineColor,
                    start = Offset(x, 0f),
                    end = Offset(x, cornerY),
                    strokeWidth = strokeWidthPx,
                    cap = StrokeCap.Square
                )
            }

            // Segmento vertical inferior (cornerY hasta bottom)
            if (seg.hasBottom) {
                drawLine(
                    color = treeLineColor,
                    start = Offset(x, cornerY),
                    end = Offset(x, size.height),
                    strokeWidth = strokeWidthPx,
                    cap = StrokeCap.Square
                )
            }

            // Segmento horizontal hacia la tarjeta
            if (seg.hasHoriz) {
                drawLine(
                    color = treeLineColor,
                    start = Offset(x, cornerY),
                    end = Offset(size.width, cornerY),
                    strokeWidth = strokeWidthPx,
                    cap = StrokeCap.Square
                )
            }
        }
    }
}
