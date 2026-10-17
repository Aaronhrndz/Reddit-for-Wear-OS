package com.reddit.wearos.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Smartphone
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
    currentUsername: String? = null,
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
                viewModel.editComment(editId, text)
            } else if (!targetId.isNullOrEmpty()) {
                viewModel.submitComment(targetId, text)
            }
        }
    )

    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = scrollState) }
    ) {
        when (val state = uiState) {
                is PostDetailUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(indicatorColor = RedditOrange)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Cargando post…", fontSize = 10.sp, color = TextSecondary)
                        }
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
                        contentPadding = PaddingValues(top = 20.dp, bottom = 42.dp, start = 6.dp, end = 6.dp),
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
                            val timeAgo = com.reddit.wearos.data.model.formatRelativeTime(post.createdUtc)
                            val metaText = if (timeAgo.isNotEmpty()) {
                                "${post.subredditPrefixed} • $timeAgo • u/${post.author}"
                            } else {
                                "${post.subredditPrefixed} • u/${post.author}"
                            }
                            Text(
                                text = metaText,
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

                    // REPRODUCTOR DE VIDEO O CARRUSEL DE IMÁGENES
                    if (!videoUrl.isNullOrEmpty()) {
                        item {
                            WearVideoPlayer(
                                videoUrl = videoUrl,
                                fallbackUrl = post.getVideoUrls().getOrNull(1),
                                posterUrl = post.getThumbnailUrl(),
                                autoPlay = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(155.dp)
                                    .padding(vertical = 4.dp)
                            )
                        }
                    } else {
                        val galleryUrls = post.getGalleryImageUrls()
                        if (galleryUrls.isNotEmpty()) {
                            item {
                                PostImageCarousel(imageUrls = galleryUrls)
                            }
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
                                    imageVector = Icons.Default.Smartphone,
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
                    if (state.commentsLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        indicatorColor = RedditOrange,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Cargando comentarios…", fontSize = 9.sp, color = TextSecondary)
                                }
                            }
                        }
                    } else if (state.comments.isEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Sin comentarios",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } else {
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
                                    text = "Comentarios (${state.comments.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                        }
                        items(state.comments) { flatComment ->
                            val isOwnComment = !currentUsername.isNullOrBlank() &&
                                flatComment.comment.author.equals(currentUsername, ignoreCase = true)
                            InlineCommentCard(
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
fun PostImageCarousel(
    imageUrls: List<String>
) {
    var currentIndex by remember(imageUrls) { mutableStateOf(0) }
    val safeIndex = currentIndex.coerceIn(0, (imageUrls.size - 1).coerceAtLeast(0))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(145.dp)
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageUrls[safeIndex],
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .clickable(enabled = imageUrls.size > 1) {
                    currentIndex = (safeIndex + 1) % imageUrls.size
                }
        )

        if (imageUrls.size > 1) {
            // Botón Anterior (Izquierda)
            if (safeIndex > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 4.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xAA000000))
                        .clickable { currentIndex = safeIndex - 1 },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Anterior",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Botón Siguiente (Derecha)
            if (safeIndex < imageUrls.size - 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xAA000000))
                        .clickable { currentIndex = safeIndex + 1 },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Siguiente",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Indicador de página inferior (ej. 1 / 5)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xBB000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "${safeIndex + 1} / ${imageUrls.size}",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun InlineCommentCard(
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
                // Cabecera: u/author en naranja + tiempo relativo + acciones propias (Editar/Borrar)
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
                                        text = "¿Borrar?",
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Borrar",
                                        tint = Color(0xFFFF8A80),
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }

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

