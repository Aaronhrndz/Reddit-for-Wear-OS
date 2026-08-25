package com.reddit.wearos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.TimeText

/**
 * Componente TimeText con gradiente translúcido negro superior para evitar colisiones
 * y solapamiento entre la hora del reloj y el texto en scroll.
 */
@Composable
fun RedditTimeText(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black,
                        Color.Black.copy(alpha = 0.88f),
                        Color.Transparent
                    )
                )
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        TimeText()
    }
}
