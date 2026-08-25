package com.reddit.wearos.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import coil.compose.AsyncImage
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.rotaryinput.rotaryWithScroll
import com.reddit.wearos.ui.components.RedditDownvoteIcon
import com.reddit.wearos.ui.components.RedditUpvoteIcon
import com.reddit.wearos.ui.components.WearVideoPlayer
import com.reddit.wearos.ui.feed.formatScore
import com.reddit.wearos.ui.theme.*
import com.reddit.wearos.util.RemotePhoneHelper

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun PostDetailScreen(
    viewModel: PostDetailViewModel,
    onViewComments: (String, String) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScalingLazyListState(
        initialCenterItemIndex = 0,
        initialCenterItemScrollOffset = 0
    )
    val context = LocalContext.current

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = scrollState) }
    ) {
        when (val state = uiState) {
            is PostDetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(indicatorColor = RedditOrange)
                }
            }
            is PostDetailUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = state.message, color = Color.Red, fontSize = 12.sp)
                }
            }
            is PostDetailUiState.Success -> {
                val post = state.post
                val videoUrl = post.getVideoUrl()

                ScalingLazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotaryWithScroll(scrollState),
                    state = scrollState,
                    autoCentering = AutoCenteringParams(itemIndex = 0, itemOffset = 0),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 42.dp, start = 8.dp, end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // CABECERA + TÍTULO INTEGRADO (Optimizado para pantalla circular)
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${post.subredditPrefixed} • u/${post.author}",
                                fontSize = 9.sp,
                                color = RedditOrange,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = post.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                textAlign = TextAlign.Center,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    // REPRODUCTOR DE VIDEO O IMAGEN
                    if (!videoUrl.isNullOrEmpty()) {
                        item {
                            WearVideoPlayer(
                                videoUrl = videoUrl,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                            )
                        }
                    } else if (post.url.endsWith(".jpg") || post.url.endsWith(".png") || post.url.endsWith(".jpeg") || (!post.thumbnail.isNullOrEmpty() && post.thumbnail.startsWith("http"))) {
                        item {
                            val imgUrl = if (post.url.endsWith(".jpg") || post.url.endsWith(".png") || post.url.endsWith(".jpeg")) post.url else post.thumbnail ?: ""
                            AsyncImage(
                                model = imgUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(125.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black)
                            )
                        }
                    }

                    // Texto del Post (selftext)
                    if (post.selfText.isNotEmpty()) {
                        item {
                            Text(
                                text = post.selfText,
                                fontSize = 11.sp,
                                color = TextPrimary,
                                lineHeight = 15.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Barra de Votación Reddit
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Reddit Vote Pill
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(DarkSurface)
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clickable {
                                            val dir = if (post.likes == true) 0 else 1
                                            viewModel.vote(dir)
                                        }
                                        .padding(3.dp)
                                ) {
                                    RedditUpvoteIcon(
                                        isVoted = post.likes == true,
                                        size = 14.dp
                                    )
                                }

                                Text(
                                    text = formatScore(post.score),
                                    fontSize = 11.sp,
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
                                        .clickable {
                                            val dir = if (post.likes == false) 0 else -1
                                            viewModel.vote(dir)
                                        }
                                        .padding(3.dp)
                                ) {
                                    RedditDownvoteIcon(
                                        isVoted = post.likes == false,
                                        size = 14.dp
                                    )
                                }
                            }

                            // Botón Guardar con Icono Material
                            CompactChip(
                                onClick = { viewModel.toggleSave() },
                                icon = {
                                    Icon(
                                        imageVector = if (post.saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = null,
                                        tint = if (post.saved) RedditOrange else Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = if (post.saved) "Guardado" else "Guardar",
                                        fontSize = 10.sp
                                    )
                                },
                                colors = ChipDefaults.secondaryChipColors()
                            )
                        }
                    }

                    // Botón para ver comentarios
                    item {
                        Chip(
                            onClick = { onViewComments(post.subreddit, post.id) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = "Ver comentarios (${post.numComments})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            colors = ChipDefaults.primaryChipColors(backgroundColor = RedditOrange),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Abrir en el teléfono Companion
                    item {
                        CompactChip(
                            onClick = {
                                RemotePhoneHelper.openPostOnPhone(context, post)
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = "Abrir en el móvil",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            },
                            colors = ChipDefaults.secondaryChipColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
