package com.reddit.wearos.ui.subreddits

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val currentSort by repository.currentSort.collectAsState()

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
            // SECCIÓN: ORDENAR PUBLICACIONES
            item {
                Text(
                    text = "Ordenar Feed",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = RedditOrange,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CompactChip(
                        onClick = {
                            repository.setSort("hot")
                            onSubredditSelected(repository.currentSubreddit.value)
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Whatshot,
                                contentDescription = null,
                                tint = if (currentSort == "hot") Color.White else TextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                        },
                        label = { Text("Hot", fontSize = 10.sp, fontWeight = if (currentSort == "hot") FontWeight.Bold else FontWeight.Normal) },
                        colors = if (currentSort == "hot") ChipDefaults.primaryChipColors(backgroundColor = RedditOrange) else ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.weight(1f)
                    )
                    CompactChip(
                        onClick = {
                            repository.setSort("new")
                            onSubredditSelected(repository.currentSubreddit.value)
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = if (currentSort == "new") Color.White else TextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                        },
                        label = { Text("New", fontSize = 10.sp, fontWeight = if (currentSort == "new") FontWeight.Bold else FontWeight.Normal) },
                        colors = if (currentSort == "new") ChipDefaults.primaryChipColors(backgroundColor = RedditOrange) else ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CompactChip(
                        onClick = {
                            repository.setSort("rising")
                            onSubredditSelected(repository.currentSubreddit.value)
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = if (currentSort == "rising") Color.White else TextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                        },
                        label = { Text("Rising", fontSize = 10.sp, fontWeight = if (currentSort == "rising") FontWeight.Bold else FontWeight.Normal) },
                        colors = if (currentSort == "rising") ChipDefaults.primaryChipColors(backgroundColor = RedditOrange) else ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.weight(1f)
                    )
                    CompactChip(
                        onClick = {
                            repository.setSort("top")
                            onSubredditSelected(repository.currentSubreddit.value)
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = if (currentSort == "top") Color.White else TextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                        },
                        label = { Text("Top", fontSize = 10.sp, fontWeight = if (currentSort == "top") FontWeight.Bold else FontWeight.Normal) },
                        colors = if (currentSort == "top") ChipDefaults.primaryChipColors(backgroundColor = RedditOrange) else ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // SECCIÓN: FEEDS PRINCIPALES
            item {
                Text(
                    text = "Feeds Principales",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = RedditOrange,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                )
            }

            item {
                Chip(
                    onClick = {
                        repository.setSubreddit("home")
                        onSubredditSelected("home")
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("Feed Personal", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
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
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Whatshot,
                            contentDescription = null,
                            tint = RedditOrange,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("r/popular", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
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
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("r/all", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    secondaryLabel = { Text("Todo Reddit", fontSize = 9.sp, color = TextSecondary) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Sección: Mis Subreddits Suscritos
            if (userSubreddits.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = RedditOrange,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Mis Suscripciones (${userSubreddits.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RedditOrange
                        )
                    }
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Comunidades Destacadas",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                }
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
