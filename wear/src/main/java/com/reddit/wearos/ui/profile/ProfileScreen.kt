package com.reddit.wearos.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import coil.compose.AsyncImage
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.rotaryinput.rotaryWithScroll
import com.reddit.wearos.data.RedditRepository
import com.reddit.wearos.data.model.RedditComment
import com.reddit.wearos.data.model.RedditPost
import com.reddit.wearos.data.model.RedditUser
import com.reddit.wearos.ui.components.PostCard
import com.reddit.wearos.ui.theme.DarkSurface
import com.reddit.wearos.ui.theme.RedditOrange
import com.reddit.wearos.ui.theme.TextPrimary
import com.reddit.wearos.ui.theme.TextSecondary
import kotlinx.coroutines.launch

enum class ProfileTab {
    POSTS, COMMENTS
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun ProfileScreen(
    repository: RedditRepository,
    onPostClick: (RedditPost) -> Unit
) {
    val scrollState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf<RedditUser?>(null) }
    var selectedTab by remember { mutableStateOf(ProfileTab.POSTS) }
    var userPosts by remember { mutableStateOf<List<RedditPost>>(emptyList()) }
    var userComments by remember { mutableStateOf<List<RedditComment>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val me = repository.getMe()
                user = me
                val username = me?.name ?: "aaronhrndz"
                val (posts, _) = repository.getUserSubmitted(username)
                userPosts = posts
                userComments = repository.getUserComments(username)
            } catch (_: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = scrollState) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .rotaryWithScroll(scrollState),
            state = scrollState,
            contentPadding = PaddingValues(top = 22.dp, bottom = 40.dp, start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. CABECERA DE PERFIL: AVATAR Y NICKNAME
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val avatarUrl = user?.iconImg?.replace("&amp;", "&")
                    if (!avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "Avatar",
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2A2A2A)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(RedditOrange),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "u/${user?.name ?: "aaronhrndz"}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // 2. DESGLOSE DE KARMA
            item {
                Card(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    backgroundPainter = CardDefaults.cardBackgroundPainter(
                        startBackgroundColor = DarkSurface,
                        endBackgroundColor = DarkSurface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${user?.totalKarma ?: (user?.linkKarma ?: 0) + (user?.commentKarma ?: 0)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = RedditOrange
                            )
                            Text("Total", fontSize = 8.sp, color = TextSecondary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${user?.linkKarma ?: 0}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text("Posts", fontSize = 8.sp, color = TextSecondary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${user?.commentKarma ?: 0}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text("Comentarios", fontSize = 8.sp, color = TextSecondary)
                        }
                    }
                }
            }

            // 3. SELECTOR DE HISTORIAL: POSTS vs COMENTARIOS
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CompactChip(
                        onClick = { selectedTab = ProfileTab.POSTS },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Article,
                                contentDescription = null,
                                tint = if (selectedTab == ProfileTab.POSTS) Color.White else TextSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                        },
                        label = {
                            Text(
                                "Mis Posts (${userPosts.size})",
                                fontSize = 9.sp,
                                fontWeight = if (selectedTab == ProfileTab.POSTS) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = if (selectedTab == ProfileTab.POSTS)
                            ChipDefaults.primaryChipColors(backgroundColor = RedditOrange)
                        else
                            ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.weight(1f)
                    )

                    CompactChip(
                        onClick = { selectedTab = ProfileTab.COMMENTS },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = if (selectedTab == ProfileTab.COMMENTS) Color.White else TextSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                        },
                        label = {
                            Text(
                                "Comentarios (${userComments.size})",
                                fontSize = 9.sp,
                                fontWeight = if (selectedTab == ProfileTab.COMMENTS) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = if (selectedTab == ProfileTab.COMMENTS)
                            ChipDefaults.primaryChipColors(backgroundColor = RedditOrange)
                        else
                            ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 4. ESTADO DE CARGA
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            indicatorColor = RedditOrange
                        )
                    }
                }
            }

            // 5. LISTADO DE MIS POSTS
            if (!isLoading && selectedTab == ProfileTab.POSTS) {
                if (userPosts.isEmpty()) {
                    item {
                        Text(
                            text = "No has publicado posts aún",
                            fontSize = 10.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    items(userPosts) { post ->
                        PostCard(
                            post = post,
                            onClick = { onPostClick(post) },
                            onUpvote = { scope.launch { repository.vote(post, 1) } },
                            onDownvote = { scope.launch { repository.vote(post, -1) } }
                        )
                    }
                }
            }

            // 6. LISTADO DE MIS COMENTARIOS
            if (!isLoading && selectedTab == ProfileTab.COMMENTS) {
                if (userComments.isEmpty()) {
                    item {
                        Text(
                            text = "No has hecho comentarios aún",
                            fontSize = 10.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    items(userComments) { comment ->
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            backgroundPainter = CardDefaults.cardBackgroundPainter(
                                startBackgroundColor = DarkSurface,
                                endBackgroundColor = DarkSurface
                            )
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Comentario",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RedditOrange
                                    )
                                    Text(
                                        text = "▲ ${comment.score}",
                                        fontSize = 9.sp,
                                        color = TextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = comment.body,
                                    fontSize = 10.sp,
                                    color = TextPrimary,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
