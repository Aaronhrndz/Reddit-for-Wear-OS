package com.reddit.wearos.ui.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.reddit.wearos.data.model.RedditPost
import com.reddit.wearos.ui.feed.PostCard
import com.reddit.wearos.ui.theme.RedditOrange
import com.reddit.wearos.ui.theme.TextPrimary
import com.reddit.wearos.ui.theme.TextSecondary

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun SavedScreen(
    viewModel: SavedViewModel,
    onPostClick: (RedditPost) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = scrollState) }
    ) {
        when (val state = uiState) {
            is SavedUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(indicatorColor = RedditOrange)
                }
            }
            is SavedUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(text = state.message, color = Color.Red, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        CompactChip(
                            onClick = { viewModel.loadSavedPosts() },
                            label = { Text("Reintentar") }
                        )
                    }
                }
            }
            is SavedUiState.Success -> {
                if (state.posts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "No tienes nada guardado", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                } else {
                    ScalingLazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .rotaryWithScroll(scrollState),
                        state = scrollState,
                        contentPadding = PaddingValues(top = 26.dp, bottom = 38.dp, start = 8.dp, end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = null,
                                    tint = RedditOrange,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Guardados",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RedditOrange
                                )
                            }
                        }

                        items(state.posts) { post ->
                            PostCard(
                                post = post,
                                onClick = { onPostClick(post) },
                                onUpvote = {
                                    val newDir = if (post.likes == true) 0 else 1
                                    viewModel.vote(post, newDir)
                                },
                                onDownvote = {
                                    val newDir = if (post.likes == false) 0 else -1
                                    viewModel.vote(post, newDir)
                                }
                            )
                        }

                        if (state.hasMore) {
                            item {
                                CompactChip(
                                    onClick = { viewModel.loadMore() },
                                    label = { Text("Cargar más", fontSize = 10.sp) },
                                    colors = ChipDefaults.secondaryChipColors(),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
