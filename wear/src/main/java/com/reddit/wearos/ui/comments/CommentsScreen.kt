package com.reddit.wearos.ui.comments

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
        try {
            inputLauncher.launch(WearInputHelper.createInputIntent("Responder a u/$author..."))
        } catch (_: Exception) {
            try {
                inputLauncher.launch(WearInputHelper.createSpeechFallbackIntent("Responder a u/$author..."))
            } catch (_: Exception) {}
        }
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
    val depth = flatComment.depth.coerceAtMost(3)
    val isLastChild = flatComment.isLastChild
    val activeAncestors = flatComment.activeAncestors
    val treeLineColor = Color(0xFF6E6E80)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 2.dp, vertical = 0.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Líneas de árbol estilo Explorador de Archivos (Drilldown └── / ├── 100% Continuas)
        if (depth > 0) {
            val gutterWidth = (depth * 14).dp
            Canvas(
                modifier = Modifier
                    .width(gutterWidth)
                    .fillMaxHeight()
                    .padding(end = 2.dp)
            ) {
                val strokeWidthPx = 1.8.dp.toPx()
                val colStepPx = this.size.width / depth
                val cornerY = 16.dp.toPx()

                // 1. Columnas de ancestros (│) continuas de tope a tope sin cortes
                for (d in 0 until depth - 1) {
                    if (d < activeAncestors.size && activeAncestors[d]) {
                        val x = colStepPx * d + (colStepPx / 2f)
                        drawLine(
                            color = treeLineColor,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = strokeWidthPx,
                            cap = StrokeCap.Square
                        )
                    }
                }

                // 2. Rama conectora del nivel actual
                val currentX = colStepPx * (depth - 1) + (colStepPx / 2f)

                // Tramo vertical superior desde el padre hasta la altura del título
                drawLine(
                    color = treeLineColor,
                    start = Offset(currentX, 0f),
                    end = Offset(currentX, cornerY),
                    strokeWidth = strokeWidthPx,
                    cap = StrokeCap.Square
                )

                // Tramo horizontal que entra a la tarjeta del comentario
                drawLine(
                    color = treeLineColor,
                    start = Offset(currentX, cornerY),
                    end = Offset(size.width, cornerY),
                    strokeWidth = strokeWidthPx,
                    cap = StrokeCap.Square
                )

                // Si NO es el último hijo de este nivel, la rama continúa hacia abajo (├── T)
                if (!isLastChild) {
                    drawLine(
                        color = treeLineColor,
                        start = Offset(currentX, cornerY),
                        end = Offset(currentX, size.height),
                        strokeWidth = strokeWidthPx,
                        cap = StrokeCap.Square
                    )
                }
            }
        }

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
                // Cabecera: u/author en naranja sin icono
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

                // Barra inferior: Responder + Votación
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
}
