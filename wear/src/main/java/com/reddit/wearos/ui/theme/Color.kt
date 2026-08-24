package com.reddit.wearos.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors

val RedditOrange = Color(0xFFFF4500)
val RedditOrangeDark = Color(0xFFD63B00)
val RedditUpvote = Color(0xFFFF5722)
val RedditDownvote = Color(0xFF7193FF)
val RedditBlue = Color(0xFF0079D3)

val DarkBackground = Color(0xFF000000)
val DarkSurface = Color(0xFF232326) // Fondo de tarjeta ligeramente más claro para contraste OLED
val DarkSurfaceVariant = Color(0xFF2D2D32)
val TextPrimary = Color(0xFFEDEDED)
val TextSecondary = Color(0xFFAAAAAA)
val TextMuted = Color(0xFF707070)

internal val WearColorPalette: Colors = Colors(
    primary = RedditOrange,
    primaryVariant = RedditOrangeDark,
    secondary = RedditBlue,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    onError = Color.White
)
