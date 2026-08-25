package com.reddit.wearos.ui.comments

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Reply
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.reddit.wearos.ui.components.RedditDownvoteIcon
import com.reddit.wearos.ui.components.RedditUpvoteIcon
import com.reddit.wearos.ui.feed.formatScore
import com.reddit.wearos.ui.theme.*
import com.reddit.wearos.util.WearInputHelper

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

    val inputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val replyText = WearInputHelper.extractText(result.data)
            val targetId = replyingToThingId
            if (!replyText.isNullOrEmpty() && !targetId.isNullOrEmpty()) {
                viewModel.submitComment(postId, subreddit, targetId, replyText)
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
            is CommentsUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(indicatorColor = RedditOrange)
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
                        contentPadding = PaddingValues(top = 28.dp, bottom = 40.dp, start = 6.dp, end = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
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
    val indent = (flatComment.depth.coerceAtMost(3) * 5).dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent, end = 2.dp, top = 2.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .padding(horizontal = 7.dp, vertical = 5.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Cabecera: u/author a la izquierda, indicador de Hilo/Tree a la derecha
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "u/${comment.author}",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = RedditOrange
                )

                if (flatComment.depth > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF222222))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = when (flatComment.depth) {
                                1 -> "└─ hilo 1"
                                2 -> "└── hilo 2"
                                3 -> "└─── hilo 3"
                                else -> "└─ L${flatComment.depth}"
                            },
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium,
                            color = RedditBlue
                        )
                    }
                }
            }

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
