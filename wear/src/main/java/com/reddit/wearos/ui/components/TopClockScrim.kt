package com.reddit.wearos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Máscara de degradado negro superior que cubre el texto en scroll
 * justo por debajo del reloj (TimeText) para evitar que se mezclen o colisionen.
 */
@Composable
fun TopClockScrim(
    modifier: Modifier = Modifier,
    height: Dp = 22.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black,
                        Color.Black.copy(alpha = 0.8f),
                        Color.Transparent
                    )
                )
            )
    )
}
