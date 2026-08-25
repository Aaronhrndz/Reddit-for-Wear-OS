package com.reddit.wearos.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import coil.compose.AsyncImage
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.rotaryinput.rotaryWithScroll
import com.reddit.wearos.data.model.RedditPost
import com.reddit.wearos.ui.components.RedditDownvoteIcon
import com.reddit.wearos.ui.components.RedditUpvoteIcon
import com.reddit.wearos.ui.theme.*

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.wear.compose.foundation.lazy.ScalingLazyListState

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel,
    onPostClick: (RedditPost) -> Unit,
    onOpenSubreddits: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScalingLazyListState(initialCenterItemIndex = 1)

    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = scrollState) }
    ) {
        when (val state = uiState) {
                is FeedUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(indicatorColor = RedditOrange)
                    }
                }
                is FeedUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.message,
                                color = Color.Red,
                                fontSize = 12.sp,
                                maxLines = 3,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Chip(
                                onClick = { viewModel.loadFeed(refresh = true) },
                                label = { Text("Reintentar") },
                                colors = ChipDefaults.primaryChipColors(backgroundColor = RedditOrange)
                            )
                        }
                    }
                }
                is FeedUiState.Success -> {
                    ScalingLazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .rotaryWithScroll(scrollState),
                        state = scrollState,
                        contentPadding = PaddingValues(top = 18.dp, bottom = 42.dp, start = 8.dp, end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                    // PANEL SUPERIOR DE CONTROL (Visible al deslizar hacia arriba)
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Selector de Feeds Naranja Oficial Reddit (#FF4500)
                            Chip(
                                onClick = onOpenSubreddits,
                                icon = {
                                    val icon = when (state.subreddit.lowercase()) {
                                        "home", "principal" -> Icons.Default.Home
                                        "popular" -> Icons.Default.Whatshot
                                        "all" -> Icons.Default.Public
                                        else -> Icons.Default.Public
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    val title = when (state.subreddit.lowercase()) {
                                        "home", "principal" -> "Personal"
                                        "popular" -> "r/popular"
                                        "all" -> "r/all"
                                        else -> "r/${state.subreddit}"
                                    }
                                    Text(
                                        text = title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color.White
                                    )
                                },
                                secondaryLabel = {
                                    Text(
                                        text = state.sort.uppercase(),
                                        fontSize = 8.sp,
                                        color = Color(0xFFEEEEEE),
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                colors = ChipDefaults.primaryChipColors(
                                    backgroundColor = RedditOrange,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            // Botón de Refrescar Circular sin borde
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(DarkSurface)
                                    .clickable {
                                        if (!state.isRefreshing) {
                                            viewModel.loadFeed(refresh = true)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (state.isRefreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        indicatorColor = RedditOrange,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refrescar",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Botón de Ajustes Circular sin borde
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(DarkSurface)
                                    .clickable { onOpenSettings() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Ajustes",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // LISTA DE PUBLICACIONES
                    itemsIndexed(state.posts) { index, post ->
                        if (index >= state.posts.size - 3 && state.hasMore && !state.isLoadingMore) {
                            LaunchedEffect(Unit) {
                                viewModel.loadFeed(refresh = false)
                            }
                        }
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

                    if (state.posts.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Sin publicaciones",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    if (state.isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    indicatorColor = RedditOrange
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PostCard(
    post: RedditPost,
    onClick: () -> Unit,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
        backgroundPainter = CardDefaults.cardBackgroundPainter(
            startBackgroundColor = DarkSurface,
            endBackgroundColor = DarkSurface
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header: Subreddit (Naranja) + Autor + Badge Video
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val subText = if (post.subredditPrefixed.isNotEmpty()) post.subredditPrefixed else "r/${post.subreddit}"
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = RedditOrange, fontWeight = FontWeight.Bold)) {
                            append(subText)
                        }
                        withStyle(SpanStyle(color = TextSecondary)) {
                            append(" • u/${post.author}")
                        }
                    },
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (post.hasVideo()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(RedditOrange)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(9.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "VIDEO",
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Título compacto y legible
            Text(
                text = post.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp
            )

            // Imagen o Preview de Alta Resolución si existe
            val previewUrl = post.getThumbnailUrl()
            if (!previewUrl.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                ) {
                    AsyncImage(
                        model = previewUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (post.hasVideo()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x44000000)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Footer con píldora de votar a la IZQUIERDA y comentarios a la DERECHA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reddit Vote Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF141416))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clickable { onUpvote() }
                            .padding(2.dp)
                    ) {
                        RedditUpvoteIcon(
                            isVoted = post.likes == true,
                            size = 12.dp
                        )
                    }

                    Text(
                        text = formatScore(post.score),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (post.likes) {
                            true -> RedditUpvote
                            false -> RedditDownvote
                            else -> TextPrimary
                        },
                        modifier = Modifier.padding(horizontal = 3.dp)
                    )

                    Box(
                        modifier = Modifier
                            .clickable { onDownvote() }
                            .padding(2.dp)
                    ) {
                        RedditDownvoteIcon(
                            isVoted = post.likes == false,
                            size = 12.dp
                        )
                    }
                }

                // Comentarios con Icono Material
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF141416))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Comentarios",
                        tint = TextSecondary,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${post.numComments}",
                        fontSize = 9.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

fun formatScore(score: Int): String {
    return when {
        score >= 1_000_000 -> String.format("%.1fM", score / 1_000_000.0)
        score >= 1_000 -> String.format("%.1fk", score / 1_000.0)
        else -> score.toString()
    }
}
