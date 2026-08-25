package com.reddit.wearos.ui.detail

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import coil.compose.AsyncImage
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.rotaryinput.rotaryWithScroll
import com.reddit.wearos.ui.comments.FlatComment
import com.reddit.wearos.ui.components.RedditDownvoteIcon
import com.reddit.wearos.ui.components.RedditUpvoteIcon
import com.reddit.wearos.ui.components.WearVideoPlayer
import com.reddit.wearos.ui.feed.formatScore
import com.reddit.wearos.ui.theme.*
import com.reddit.wearos.util.RemotePhoneHelper
import com.reddit.wearos.util.WearInputHelper

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

    var replyingToThingId by remember { mutableStateOf<String?>(null) }
    var replyingToAuthor by remember { mutableStateOf<String?>(null) }

    val inputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val replyText = WearInputHelper.extractText(result.data)
            val targetId = replyingToThingId
            if (!replyText.isNullOrEmpty() && !targetId.isNullOrEmpty()) {
                viewModel.submitComment(targetId, replyText)
            }
        }
        replyingToThingId = null
        replyingToAuthor = null
    }

    fun launchReply(thingId: String, author: String) {
        replyingToThingId = thingId
        replyingToAuthor = author
        inputLauncher.launch(WearInputHelper.createInputIntent("Responder a u/$author..."))
    }

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
                    autoCentering = null,
                    contentPadding = PaddingValues(top = 24.dp, bottom = 42.dp, start = 8.dp, end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // CABECERA + TÍTULO INTEGRADO (Optimizado para pantalla circular)
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 2.dp),
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
                                lineHeight = 15.sp,
                                modifier = Modifier.fillMaxWidth()
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

                    // Botón para Responder al Post Principal
                    item {
                        Chip(
                            onClick = {
                                val targetId = if (post.name.isNotEmpty()) post.name else "t3_${post.id}"
                                launchReply(targetId, post.author)
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Reply,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = "Responder al post",
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

                    // SECCIÓN DE COMENTARIOS INLINE
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ChatBubbleOutline,
                                contentDescription = null,
                                tint = RedditOrange,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Comentarios (${post.numComments})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }

                    if (state.commentsLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    indicatorColor = RedditOrange,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else if (state.comments.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Sin comentarios",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } else {
                        items(state.comments) { flatComment ->
                            InlineCommentCard(
                                flatComment = flatComment,
                                onUpvote = {
                                    val newDir = if (flatComment.comment.likes == true) 0 else 1
                                    viewModel.voteComment(flatComment.comment, newDir)
                                },
                                onDownvote = {
                                    val newDir = if (flatComment.comment.likes == false) 0 else -1
                                    viewModel.voteComment(flatComment.comment, newDir)
                                },
                                onReply = {
                                    val targetId = if (flatComment.comment.id.startsWith("t1_")) flatComment.comment.id else "t1_${flatComment.comment.id}"
                                    launchReply(targetId, flatComment.comment.author)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InlineCommentCard(
    flatComment: FlatComment,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit,
    onReply: () -> Unit
) {
    val comment = flatComment.comment
    val depth = flatComment.depth.coerceAtMost(3)

    // Sangría limpia y escalonada por nivel de jerarquía
    val startIndent = when (depth) {
        0 -> 0.dp
        1 -> 12.dp
        2 -> 22.dp
        else -> 30.dp
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startIndent, end = 2.dp, top = 2.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .then(
                if (depth > 0) {
                    Modifier.drawBehind {
                        drawLine(
                            color = Color(0xFF4A4A58),
                            start = Offset(0f, 6.dp.toPx()),
                            end = Offset(0f, size.height - 6.dp.toPx()),
                            strokeWidth = 2.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                } else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Cabecera: u/author en naranja
            Text(
                text = "u/${comment.author}",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = RedditOrange
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Texto del comentario
            Text(
                text = comment.body,
                fontSize = 11.sp,
                color = TextPrimary,
                lineHeight = 14.sp
            )

            Spacer(modifier = Modifier.height(3.dp))

            // Barra inferior con botón Responder y Barra de votación
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botón Responder
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E1E1E))
                        .clickable { onReply() }
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Reply,
                        contentDescription = "Responder",
                        tint = RedditOrange,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Responder",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }

                // Votación
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E1E1E))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clickable { onUpvote() }
                            .padding(1.dp)
                    ) {
                        RedditUpvoteIcon(
                            isVoted = comment.likes == true,
                            size = 11.dp
                        )
                    }

                    Text(
                        text = formatScore(comment.score),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (comment.likes) {
                            true -> RedditUpvote
                            false -> RedditDownvote
                            else -> TextPrimary
                        },
                        modifier = Modifier.padding(horizontal = 3.dp)
                    )

                    Box(
                        modifier = Modifier
                            .clickable { onDownvote() }
                            .padding(1.dp)
                    ) {
                        RedditDownvoteIcon(
                            isVoted = comment.likes == false,
                            size = 11.dp
                        )
                    }
                }
            }
        }
    }
}
