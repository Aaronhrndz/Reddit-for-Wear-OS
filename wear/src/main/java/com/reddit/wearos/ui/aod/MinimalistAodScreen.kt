package com.reddit.wearos.ui.aod

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MinimalistAodScreen(
    currentSubreddit: String = "home",
    username: String? = null,
    isBurnInProtection: Boolean = true
) {
    val currentTime = remember {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    // Offset sutil para protección anti-burn-in de pantallas AMOLED
    val offsetMod = if (isBurnInProtection) {
        val min = (System.currentTimeMillis() / 60000 % 6).toInt()
        when (min) {
            0 -> Modifier.offset(x = 0.dp, y = 0.dp)
            1 -> Modifier.offset(x = 2.dp, y = 1.dp)
            2 -> Modifier.offset(x = (-2).dp, y = 2.dp)
            3 -> Modifier.offset(x = 1.dp, y = (-2).dp)
            4 -> Modifier.offset(x = (-1).dp, y = (-1).dp)
            else -> Modifier.offset(x = 0.dp, y = 0.dp)
        }
    } else Modifier

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = offsetMod.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo Snoo Minimalista en Contorno (AOD Battery Friendly <3% OPR)
            MinimalistSnooOutline(modifier = Modifier.size(24.dp))

            Spacer(modifier = Modifier.height(6.dp))

            // Reloj Digital Minimalista AOD
            Text(
                text = currentTime,
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                color = Color(0xFFEEEEEE),
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Contexto sutil (Reddit / Subreddit)
            val subLabel = when (currentSubreddit.lowercase()) {
                "home", "principal" -> if (!username.isNullOrEmpty()) "u/$username" else "Reddit Feed"
                "popular" -> "r/popular"
                "all" -> "r/all"
                else -> "r/$currentSubreddit"
            }

            Text(
                text = subLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF777777),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun MinimalistSnooOutline(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Cabeza Snoo
        drawCircle(
            color = Color(0xFFFF4500),
            radius = w * 0.38f,
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Ojo izquierdo
        drawCircle(
            color = Color(0xFFFF4500),
            radius = 1.5.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(w * 0.40f, h * 0.48f)
        )

        // Ojo derecho
        drawCircle(
            color = Color(0xFFFF4500),
            radius = 1.5.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(w * 0.60f, h * 0.48f)
        )

        // Antena
        val antennaPath = Path().apply {
            moveTo(w * 0.50f, h * 0.12f)
            lineTo(w * 0.50f, h * 0.04f)
            lineTo(w * 0.65f, h * 0.02f)
        }
        drawPath(
            path = antennaPath,
            color = Color(0xFFFF4500),
            style = Stroke(width = 1.5.dp.toPx())
        )
        drawCircle(
            color = Color(0xFFFF4500),
            radius = 2.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(w * 0.65f, h * 0.02f)
        )
    }
}
