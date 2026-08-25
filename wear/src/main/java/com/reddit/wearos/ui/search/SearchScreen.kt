package com.reddit.wearos.ui.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.rotaryinput.rotaryWithScroll
import com.reddit.wearos.data.RedditRepository
import com.reddit.wearos.data.model.RedditPost
import com.reddit.wearos.data.model.RedditSubreddit
import com.reddit.wearos.ui.components.PostCard
import com.reddit.wearos.ui.theme.RedditOrange
import com.reddit.wearos.ui.theme.TextSecondary
import kotlinx.coroutines.launch

enum class SearchTab {
    FEEDS, POSTS
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun SearchScreen(
    initialQuery: String = "",
    repository: RedditRepository,
    onSubredditClick: (String) -> Unit,
    onPostClick: (RedditPost) -> Unit
) {
    val scrollState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    val scope = rememberCoroutineScope()
    var currentQuery by remember { mutableStateOf(initialQuery) }
    var selectedTab by remember { mutableStateOf(SearchTab.FEEDS) }

    var isLoading by remember { mutableStateOf(false) }
    var subredditsResult by remember { mutableStateOf<List<RedditSubreddit>>(emptyList()) }
    var postsResult by remember { mutableStateOf<List<RedditPost>>(emptyList()) }

    fun performSearch(query: String) {
        if (query.isBlank()) return
        isLoading = true
        scope.launch {
            try {
                if (selectedTab == SearchTab.FEEDS) {
                    subredditsResult = repository.searchSubreddits(query.trim())
                } else {
                    val (posts, _) = repository.searchPosts(query = query.trim())
                    postsResult = posts
                }
            } catch (_: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(currentQuery, selectedTab) {
        if (currentQuery.isNotBlank()) {
            performSearch(currentQuery)
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                currentQuery = spoken.trim()
            }
        }
    }

    fun launchVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Buscar en Reddit...")
        }
        try {
            voiceLauncher.launch(intent)
        } catch (_: Exception) {}
    }

    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = scrollState) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .rotaryWithScroll(scrollState),
            state = scrollState,
            contentPadding = PaddingValues(top = 24.dp, bottom = 40.dp, start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Chip(
                    onClick = { launchVoiceSearch() },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = RedditOrange,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = if (currentQuery.isNotEmpty()) currentQuery else "Buscar...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    secondaryLabel = {
                        Text("Toca para dictar por voz", fontSize = 8.sp, color = TextSecondary)
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CompactChip(
                        onClick = { selectedTab = SearchTab.FEEDS },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Explore,
                                contentDescription = null,
                                tint = if (selectedTab == SearchTab.FEEDS) Color.White else TextSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                        },
                        label = {
                            Text(
                                "Feeds",
                                fontSize = 10.sp,
                                fontWeight = if (selectedTab == SearchTab.FEEDS) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = if (selectedTab == SearchTab.FEEDS)
                            ChipDefaults.primaryChipColors(backgroundColor = RedditOrange)
                        else
                            ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.weight(1f)
                    )

                    CompactChip(
                        onClick = { selectedTab = SearchTab.POSTS },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Article,
                                contentDescription = null,
                                tint = if (selectedTab == SearchTab.POSTS) Color.White else TextSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                        },
                        label = {
                            Text(
                                "Posts",
                                fontSize = 10.sp,
                                fontWeight = if (selectedTab == SearchTab.POSTS) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = if (selectedTab == SearchTab.POSTS)
                            ChipDefaults.primaryChipColors(backgroundColor = RedditOrange)
                        else
                            ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            indicatorColor = RedditOrange
                        )
                    }
                }
            }

            if (!isLoading && selectedTab == SearchTab.FEEDS) {
                if (subredditsResult.isEmpty() && currentQuery.isNotEmpty()) {
                    item {
                        Text(
                            text = "Sin resultados para \"$currentQuery\"",
                            fontSize = 10.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    items(subredditsResult) { sub ->
                        Chip(
                            onClick = { onSubredditClick(sub.displayName) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Explore,
                                    contentDescription = null,
                                    tint = RedditOrange,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = "r/${sub.displayName}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            secondaryLabel = {
                                val subCount = sub.subscribers
                                val countStr = if (subCount != null && subCount > 0) {
                                    if (subCount >= 1000000) "${subCount / 1000000}M miembros"
                                    else if (subCount >= 1000) "${subCount / 1000}k miembros"
                                    else "$subCount miembros"
                                } else "Comunidad de Reddit"
                                Text(countStr, fontSize = 8.sp, color = TextSecondary)
                            },
                            colors = ChipDefaults.secondaryChipColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (!isLoading && selectedTab == SearchTab.POSTS) {
                if (postsResult.isEmpty() && currentQuery.isNotEmpty()) {
                    item {
                        Text(
                            text = "No se encontraron publicaciones",
                            fontSize = 10.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    items(postsResult) { post ->
                        PostCard(
                            post = post,
                            onClick = { onPostClick(post) },
                            onUpvote = { scope.launch { repository.vote(post, 1) } },
                            onDownvote = { scope.launch { repository.vote(post, -1) } }
                        )
                    }
                }
            }
        }
    }
}
