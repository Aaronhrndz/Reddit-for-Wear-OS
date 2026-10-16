package com.reddit.wearos.ui.comments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.rotaryinput.rotaryWithScroll
import com.reddit.wearos.ui.components.ClickableRedditText
import com.reddit.wearos.ui.components.RedditDownvoteIcon
import com.reddit.wearos.ui.components.RedditUpvoteIcon
import com.reddit.wearos.ui.components.ReplyDialog
import com.reddit.wearos.ui.feed.formatScore
import com.reddit.wearos.ui.theme.*

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun CommentsScreen(
    viewModel: CommentsViewModel,
    postId: String = "",
    subreddit: String = "",
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScalingLazyListState()

    var replyingToThingId by remember { mutableStateOf<String?>(null) }
    var replyingToAuthor by remember { mutableStateOf<String?>(null) }
    var showReplyDialog by remember { mutableStateOf(false) }

    fun launchReply(thingId: String, author: String) {
        replyingToThingId = thingId
        replyingToAuthor = author
        showReplyDialog = true
    }

    // Modal de Entrada de Texto con Teclado
    ReplyDialog(
        show = showReplyDialog,
        author = replyingToAuthor ?: "usuario",
        onDismiss = {
            showReplyDialog = false
            replyingToThingId = null
            replyingToAuthor = null
        },
        onSend = { text ->
            val targetId = replyingToThingId
            if (!targetId.isNullOrEmpty()) {
                viewModel.submitComment(postId, subreddit, targetId, text)
            }
        }
    )

    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = scrollState) }
    ) {
        when (val state = uiState) {
            is CommentsUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(indicatorColor = RedditOrange)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Cargando comentarios…", fontSize = 10.sp, color = TextSecondary)
                    }
                }
            }
            is CommentsUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = state.message, color = Color.Red, fontSize = 12.sp)
                }
            }
            is CommentsUiState.Success -> {
                if (state.comments.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "Sin comentarios", color = TextSecondary, fontSize = 12.sp)
                    }
                } else {
                    ScalingLazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .rotaryWithScroll(scrollState),
                        state = scrollState,
                        autoCentering = null,
                        scalingParams = ScalingLazyColumnDefaults.scalingParams(
                            edgeScale = 1f,
                            edgeAlpha = 1f,
                            minTransitionArea = 0f,
                            maxTransitionArea = 0f
                        ),
                        contentPadding = PaddingValues(top = 22.dp, bottom = 42.dp, start = 6.dp, end = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        items(state.comments) { flatComment ->
                            CommentCard(
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
fun CommentCard(
    flatComment: FlatComment,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit,
    onReply: () -> Unit
) {
    val comment = flatComment.comment

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 2.dp, vertical = 0.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Líneas de árbol continuas precalculadas con 0 errores matemáticos
        com.reddit.wearos.ui.components.TreeLineCanvas(
            segments = flatComment.segments
        )

        // Tarjeta de contenido del comentario (con margen vertical interno para no cortar las líneas)
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .padding(horizontal = 7.dp, vertical = 5.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Cabecera: u/author en naranja + tiempo relativo
                val commentTimeAgo = com.reddit.wearos.data.model.formatRelativeTime(comment.createdUtc)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "u/${comment.author}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = RedditOrange
                    )
                    if (commentTimeAgo.isNotEmpty()) {
                        Text(
                            text = " • $commentTimeAgo",
                            fontSize = 8.sp,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Texto del comentario con enlaces interactivos a Samsung Internet
                ClickableRedditText(
                    text = comment.body,
                    fontSize = 11.sp,
                    color = TextPrimary,
                    lineHeight = 14.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Barra inferior: Responder en Pildorita destacada + Votación en Pildorita
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botón Responder en Pildorita plana sin bordes con fondo ligeramente más claro
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF353542))
                            .clickable { onReply() }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = "Responder",
                            tint = RedditOrange,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Responder",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    // Votación en Pildorita plana sin bordes con fondo ligeramente más claro
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF353542))
                            .padding(horizontal = 5.dp, vertical = 3.dp)
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
}
