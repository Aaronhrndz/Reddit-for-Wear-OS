package com.reddit.wearos.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

    var pullDistance by remember { mutableStateOf(0f) }

    // NestedScroll para capturar el gesto Pull to Refresh cuando estamos arriba del todo
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Si estamos en el primer ítem y el usuario desliza hacia abajo (available.y > 0)
                if (scrollState.centerItemIndex <= 1 && available.y > 0) {
                    pullDistance = (pullDistance + available.y * 0.5f).coerceIn(0f, 150f)
                    return Offset(0f, available.y * 0.3f)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (scrollState.centerItemIndex <= 1 && available.y > 0) {
                    pullDistance = (pullDistance + available.y * 0.5f).coerceIn(0f, 150f)
                    return available
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (pullDistance > 60f) {
                    viewModel.loadFeed(refresh = true)
                }
                pullDistance = 0f
                return super.onPreFling(available)
            }
        }
    }

    Scaffold(
        timeText = { TimeText() },
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
                        .padding(16.dp),
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
                        Spacer(modifier = Modifier.height(8.dp))
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
                        .nestedScroll(nestedScrollConnection)
                        .rotaryWithScroll(scrollState),
                    state = scrollState,
                    contentPadding = PaddingValues(top = 18.dp, bottom = 44.dp, start = 8.dp, end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // PANTALLA / PANEL SUPERIOR DE CONTROL (Visible solo al deslizar hacia arriba)
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Indicador de Pull to Refresh o estado de recarga
                            if (state.isRefreshing || pullDistance > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (state.isRefreshing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            indicatorColor = RedditOrange
                                        )
                                    } else {
                                        Text(
                                            text = if (pullDistance > 60f) "⬆️ Suelta para refrescar" else "⬇️ Desliza para refrescar",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (pullDistance > 60f) RedditOrange else TextSecondary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Selector de Feeds con NARANJA OFICIAL REDDIT (#FF4500)
                                Chip(
                                    onClick = onOpenSubreddits,
                                    label = {
                                        val title = when (state.subreddit.lowercase()) {
                                            "home", "principal" -> "🏠 Personal"
                                            "popular" -> "🔥 r/popular"
                                            "all" -> "🌐 r/all"
                                            else -> "r/${state.subreddit}"
                                        }
                                        Text(
                                            text = title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color.White
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

                                Spacer(modifier = Modifier.width(8.dp))

                                // Botón grande de Ajustes
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(DarkSurface)
                                        .border(BorderStroke(1.dp, Color(0xFF38383E)), CircleShape)
                                        .clickable { onOpenSettings() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("⚙️", fontSize = 16.sp)
                                }
                            }
                        }
                    }

                    // LISTA DE PUBLICACIONES (Ocupa 100% de la pantalla)
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

                    // Pie de lista con acceso a Ajustes
                    item {
                        Chip(
                            onClick = onOpenSettings,
                            label = {
                                Text(
                                    text = "⚙️ Ajustes de la App",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            colors = ChipDefaults.secondaryChipColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
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
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, Color(0xFF38383E)), RoundedCornerShape(14.dp)),
        backgroundPainter = CardDefaults.cardBackgroundPainter(
            startBackgroundColor = DarkSurface,
            endBackgroundColor = DarkSurface
        )
    ) {
        Column(modifier = Modifier.padding(9.dp)) {
            // Header: Subreddit + Autor + Badge Video
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${post.subredditPrefixed} • u/${post.author}",
                    fontSize = 9.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (post.hasVideo()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(RedditOrange)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "▶ VIDEO",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Título
            Text(
                text = post.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // Thumbnail si existe
            if (!post.thumbnail.isNullOrEmpty() && post.thumbnail.startsWith("http")) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    AsyncImage(
                        model = post.thumbnail,
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
                            Text("▶", fontSize = 20.sp, color = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

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
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF141416))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clickable { onUpvote() }
                            .padding(4.dp)
                    ) {
                        RedditUpvoteIcon(
                            isVoted = post.likes == true,
                            size = 13.dp
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
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Box(
                        modifier = Modifier
                            .clickable { onDownvote() }
                            .padding(4.dp)
                    ) {
                        RedditDownvoteIcon(
                            isVoted = post.likes == false,
                            size = 13.dp
                        )
                    }
                }

                // Comentarios a la DERECHA
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF141416))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "💬 ${post.numComments}",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
