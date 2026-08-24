package com.reddit.wearos.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

@Composable
fun RedditWearTheme(
    colors: Colors? = null,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = colors ?: WearColorPalette,
        typography = WearTypography,
        content = content
    )
}
