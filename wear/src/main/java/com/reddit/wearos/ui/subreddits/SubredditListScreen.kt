package com.reddit.wearos.ui.subreddits

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
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
            verticalArrangement = Arrangement.spacedBy(4.dp)
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

            // Feed Personal (Home)
            item {
                Chip(
                    onClick = {
                        repository.setSubreddit("home")
                        onSubredditSelected("home")
                    },
                    label = { Text("🏠 Feed Personal", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Popular y All
            item {
                Chip(
                    onClick = {
                        repository.setSubreddit("popular")
                        onSubredditSelected("popular")
                    },
                    label = { Text("🔥 r/popular", fontSize = 12.sp) },
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
                    label = { Text("🌐 r/all", fontSize = 12.sp) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (userSubreddits.isNotEmpty()) {
                item {
                    Text(
                        text = "Mis Subreddits",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }

                items(userSubreddits) { sub ->
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
}
