package com.reddit.wearos.ui.comments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reddit.wearos.R
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
    currentUsername: String? = null,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScalingLazyListState()

    var replyingToThingId by remember { mutableStateOf<String?>(null) }
    var replyingToAuthor by remember { mutableStateOf<String?>(null) }
    var editingCommentId by remember { mutableStateOf<String?>(null) }
    var editingInitialText by remember { mutableStateOf("") }
    var showReplyDialog by remember { mutableStateOf(false) }

    fun launchReply(thingId: String, author: String) {
        editingCommentId = null
        editingInitialText = ""
        replyingToThingId = thingId
        replyingToAuthor = author
        showReplyDialog = true
    }

    fun launchEdit(commentId: String, currentBody: String) {
        replyingToThingId = null
        editingCommentId = commentId
        editingInitialText = currentBody
        showReplyDialog = true
    }

    // Modal de Entrada de Texto con Teclado (Responder o Editar)
    ReplyDialog(
        show = showReplyDialog,
        author = replyingToAuthor ?: "usuario",
        initialText = editingInitialText,
        dialogTitle = if (editingCommentId != null) "Editar comentario" else null,
        onDismiss = {
            showReplyDialog = false
            replyingToThingId = null
            replyingToAuthor = null
            editingCommentId = null
            editingInitialText = ""
        },
        onSend = { text ->
            val editId = editingCommentId
            val targetId = replyingToThingId
            if (!editId.isNullOrEmpty()) {
                viewModel.editComment(postId, subreddit, editId, text)
            } else if (!targetId.isNullOrEmpty()) {
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
                        Text(stringResource(R.string.loading_comments), fontSize = 10.sp, color = TextSecondary)
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
                        Text(text = stringResource(R.string.comments_empty), color = TextSecondary, fontSize = 12.sp)
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
                            val isOwnComment = !currentUsername.isNullOrBlank() &&
                                flatComment.comment.author.equals(currentUsername, ignoreCase = true)
                            CommentCard(
                                flatComment = flatComment,
                                isOwnComment = isOwnComment,
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
                                },
                                onEdit = {
                                    launchEdit(flatComment.comment.id, flatComment.comment.body)
                                },
                                onDelete = {
                                    viewModel.deleteComment(flatComment.comment.id)
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
    isOwnComment: Boolean = false,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val comment = flatComment.comment
    var confirmDelete by remember { mutableStateOf(false) }

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
                // Cabecera: u/author en naranja + tiempo relativo + Editar/Borrar
                val commentTimeAgo = com.reddit.wearos.data.model.formatRelativeTime(comment.createdUtc)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
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

                    if (isOwnComment) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF353542))
                                    .clickable { onEdit() }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Editar",
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (confirmDelete) Color(0xFFB71C1C) else Color(0xFF3A2323))
                                    .clickable {
                                        if (confirmDelete) {
                                            confirmDelete = false
                                            onDelete()
                                        } else {
                                            confirmDelete = true
                                        }
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                if (confirmDelete) {
                                    Text(
                                        text = stringResource(R.string.delete_confirm_title),
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.delete_comment),
                                        tint = Color(0xFFFF8A80),
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                        }
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
