package com.reddit.wearos.ui.subreddits

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.rotaryinput.rotaryWithScroll
import com.reddit.wearos.data.RedditRepository
import com.reddit.wearos.ui.theme.RedditOrange
import com.reddit.wearos.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun SubredditListScreen(
    repository: RedditRepository,
    onSubredditSelected: (String) -> Unit
) {
    val scrollState = rememberScalingLazyListState()
    val scope = rememberCoroutineScope()
    var userSubreddits by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val defaultSuggested = listOf(
        "technology", "espanol", "AskReddit", "gaming", "science", "worldnews", "todayilearned", "aww"
    )

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val subs = repository.getUserSubreddits()
                if (subs.isNotEmpty()) {
                    userSubreddits = subs.map { it.displayName }
                }
            } catch (_: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = scrollState) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .rotaryWithScroll(scrollState),
            state = scrollState,
            contentPadding = PaddingValues(top = 28.dp, bottom = 40.dp, start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            item {
                Text(
                    text = "Feeds y Comunidades",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = RedditOrange,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            // Feeds Principales
            item {
                Chip(
                    onClick = {
                        repository.setSubreddit("home")
                        onSubredditSelected("home")
                    },
                    label = { Text("🏠 Feed Personal", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    secondaryLabel = { Text("Tus suscripciones", fontSize = 9.sp, color = TextSecondary) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Chip(
                    onClick = {
                        repository.setSubreddit("popular")
                        onSubredditSelected("popular")
                    },
                    label = { Text("🔥 r/popular", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    secondaryLabel = { Text("Tendencias globales", fontSize = 9.sp, color = TextSecondary) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Chip(
                    onClick = {
                        repository.setSubreddit("all")
                        onSubredditSelected("all")
                    },
                    label = { Text("🌐 r/all", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    secondaryLabel = { Text("Todo Reddit", fontSize = 9.sp, color = TextSecondary) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Sección: Mis Subreddits Suscritos
            if (userSubreddits.isNotEmpty()) {
                item {
                    Text(
                        text = "⭐ Mis Suscripciones (${userSubreddits.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = RedditOrange,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }

                items(userSubreddits) { sub ->
                    Chip(
                        onClick = {
                            repository.setSubreddit(sub)
                            onSubredditSelected(sub)
                        },
                        label = { Text("r/$sub", fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            indicatorColor = RedditOrange
                        )
                    }
                }
            }

            // Sección: Comunidades Sugeridas
            item {
                Text(
                    text = "💡 Comunidades Destacadas",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                )
            }

            items(defaultSuggested.filter { it !in userSubreddits }) { sub ->
                Chip(
                    onClick = {
                        repository.setSubreddit(sub)
                        onSubredditSelected(sub)
                    },
                    label = { Text("r/$sub", fontSize = 12.sp) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
