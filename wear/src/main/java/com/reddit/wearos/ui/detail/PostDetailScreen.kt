package com.reddit.wearos.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import coil.compose.AsyncImage
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.rotaryinput.rotaryWithScroll
import com.reddit.wearos.ui.comments.FlatComment
import com.reddit.wearos.ui.components.ClickableRedditText
import com.reddit.wearos.ui.components.RedditDownvoteIcon
import com.reddit.wearos.ui.components.RedditUpvoteIcon
import com.reddit.wearos.ui.components.ReplyDialog
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
                viewModel.submitComment(targetId, text)
            }
        }
    )

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
                    scalingParams = ScalingLazyColumnDefaults.scalingParams(
                        edgeScale = 1f,
                        edgeAlpha = 1f,
                        minTransitionArea = 0f,
                        maxTransitionArea = 0f
                    ),
                    contentPadding = PaddingValues(top = 24.dp, bottom = 42.dp, start = 6.dp, end = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    // CABECERA + TÍTULO INTEGRADO (Optimizado para pantalla circular)
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 4.dp),
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
                                    .padding(vertical = 4.dp)
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
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black)
                            )
                        }
                    }

                    // Texto del Post con Links Navegables
                    if (post.selfText.isNotEmpty()) {
                        item {
                            ClickableRedditText(
                                text = post.selfText,
                                fontSize = 11.sp,
                                color = TextPrimary,
                                lineHeight = 15.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Barra de Votación Reddit
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
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
                                    imageVector = Icons.AutoMirrored.Filled.Reply,
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        )
                    }

                    // SECCIÓN DE COMENTARIOS INLINE
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 4.dp),
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
    val isLastChild = flatComment.isLastChild
    val hasReplies = flatComment.hasReplies
    val passThroughColumns = flatComment.passThroughColumns
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
                val colStepPx = 14.dp.toPx()
                val cornerY = 16.dp.toPx()

                // 1. Columnas de ancestros pass-through (d de 0 hasta depth - 2)
                for (d in 0 until depth - 1) {
                    if (d < passThroughColumns.size && passThroughColumns[d]) {
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

                // 2. Columna de la rama propia (columna depth - 1)
                val currentCol = depth - 1
                val currentX = colStepPx * currentCol + (colStepPx / 2f)

                // Tramo vertical desde el tope (y=0) hasta cornerY
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

                // Si NO es el último hijo o si TIENE respuestas debajo, continúa hacia abajo conectando con sus hijos/hermanos (├── T)
                // Si es el último hijo Y no tiene respuestas, finaliza limpiamente en L (└──)
                if (!isLastChild || hasReplies) {
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

                // Texto del comentario con links clicables hacia Samsung Internet
                ClickableRedditText(
                    text = comment.body,
                    fontSize = 11.sp,
                    color = TextPrimary,
                    lineHeight = 14.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Barra inferior con botón Responder en Pildorita destacada y Barra de votación en Pildorita
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botón Responder en Pildorita de alto contraste y borde
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF282834))
                            .border(1.dp, Color(0xFF404052), RoundedCornerShape(14.dp))
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

                    // Votación en Pildorita
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF282834))
                            .border(1.dp, Color(0xFF404052), RoundedCornerShape(14.dp))
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
