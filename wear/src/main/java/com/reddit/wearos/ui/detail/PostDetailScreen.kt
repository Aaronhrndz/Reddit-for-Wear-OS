package com.reddit.wearos.ui.detail

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
    onViewComments: ((String, String) -> Unit)? = null,
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

    fun launchReply(thingId: String, author: String?) {
        replyingToThingId = thingId
        replyingToAuthor = author
        val label = if (author != null) "Responder a u/$author..." else "Comentar en el post..."
        inputLauncher.launch(WearInputHelper.createInputIntent(label))
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
                    contentPadding = PaddingValues(top = 24.dp, bottom = 42.dp, start = 10.dp, end = 10.dp),
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

                    // Texto del Post (selftext) inmediatamente después de la foto / video
                    if (post.selfText.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurface)
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = post.selfText,
                                    fontSize = 11.sp,
                                    color = TextPrimary,
                                    lineHeight = 15.sp
                                )
                            }
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

                    // Botones de acción secundarios: Comentar y Abrir en el móvil
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CompactChip(
                                onClick = {
                                    val postFullName = if (post.name.isNotEmpty()) post.name else "t3_${post.id}"
                                    launchReply(postFullName, null)
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Reply,
                                        contentDescription = "Comentar",
                                        tint = RedditOrange,
                                        modifier = Modifier.size(13.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = "Comentar",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                colors = ChipDefaults.secondaryChipColors(),
                                modifier = Modifier.weight(1f)
                            )

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
                                        text = "Móvil",
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                },
                                colors = ChipDefaults.secondaryChipColors(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // SECCIÓN DE COMENTARIOS DIRECTOS
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ChatBubbleOutline,
                                contentDescription = null,
                                tint = RedditOrange,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Comentarios (${post.numComments})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
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
    val treeLineColor = Color(0xFF707080)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Líneas de árbol estilo Explorador de Archivos (Treeview Drilldown └──)
        if (depth > 0) {
            val gutterWidth = (depth * 16).dp
            Canvas(
                modifier = Modifier
                    .width(gutterWidth)
                    .fillMaxHeight()
                    .padding(end = 4.dp)
            ) {
                val strokeWidthPx = 1.8.dp.toPx()
                val colStepPx = this.size.width / depth

                // Columnas de ancestros continuas (│)
                for (d in 0 until depth - 1) {
                    val x = colStepPx * d + (colStepPx / 2f)
                    drawLine(
                        color = treeLineColor,
                        start = Offset(x, 0f),
                        end = Offset(x, this.size.height),
                        strokeWidth = strokeWidthPx,
                        cap = StrokeCap.Round
                    )
                }

                // Rama actual conectora en L (└──)
                val currentX = colStepPx * (depth - 1) + (colStepPx / 2f)
                val cornerY = 13.dp.toPx()
                val path = Path().apply {
                    moveTo(currentX, 0f)
                    lineTo(currentX, cornerY - 3.dp.toPx())
                    quadraticTo(
                        currentX, cornerY,
                        currentX + 3.dp.toPx(), cornerY
                    )
                    lineTo(this@Canvas.size.width, cornerY)
                }

                drawPath(
                    path = path,
                    color = treeLineColor,
                    style = Stroke(
                        width = strokeWidthPx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }

        // Caja de contenido del comentario
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .padding(horizontal = 7.dp, vertical = 5.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Cabecera con conector de respuesta y autor
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (depth > 0) {
                        Text(
                            text = "↳ ",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = RedditOrange
                        )
                    }
                    Text(
                        text = "u/${comment.author}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = RedditOrange
                    )
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
}

