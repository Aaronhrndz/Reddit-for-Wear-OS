package com.reddit.wearos.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Article
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.rotaryinput.rotaryWithScroll
import com.reddit.wearos.data.RedditRepository
import com.reddit.wearos.data.model.RedditPost
import com.reddit.wearos.data.model.RedditSubreddit
import com.reddit.wearos.ui.components.PostCard
import com.reddit.wearos.ui.theme.DarkSurface
import com.reddit.wearos.ui.theme.RedditOrange
import com.reddit.wearos.ui.theme.TextPrimary
import com.reddit.wearos.ui.theme.TextSecondary
import kotlinx.coroutines.launch

enum class SearchTab {
    FEEDS, POSTS
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun SearchScreen(
    initialQuery: String = "",
    repository: RedditRepository,
    onSubredditClick: (String) -> Unit,
    onPostClick: (RedditPost) -> Unit
) {
    val scrollState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    var currentQuery by remember { mutableStateOf(initialQuery) }
    var selectedTab by remember { mutableStateOf(SearchTab.FEEDS) }

    var isLoading by remember { mutableStateOf(false) }
    var subredditsResult by remember { mutableStateOf<List<RedditSubreddit>>(emptyList()) }
    var postsResult by remember { mutableStateOf<List<RedditPost>>(emptyList()) }

    fun performSearch(query: String) {
        if (query.isBlank()) return
        isLoading = true
        scope.launch {
            try {
                if (selectedTab == SearchTab.FEEDS) {
                    subredditsResult = repository.searchSubreddits(query.trim())
                } else {
                    val (posts, _) = repository.searchPosts(query = query.trim())
                    postsResult = posts
                }
            } catch (_: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedTab) {
        if (currentQuery.isNotBlank()) {
            performSearch(currentQuery)
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
            // 1. CAMPO DE TEXTO PARA ABRIR TECLADO NATIVO
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkSurface)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = RedditOrange,
                        modifier = Modifier.size(16.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        if (currentQuery.isEmpty()) {
                            Text(
                                text = "Buscar en Reddit...",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }

                        BasicTextField(
                            value = currentQuery,
                            onValueChange = { currentQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(RedditOrange),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    keyboardController?.hide()
                                    performSearch(currentQuery)
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (currentQuery.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF333333))
                                .clickable {
                                    currentQuery = ""
                                    subredditsResult = emptyList()
                                    postsResult = emptyList()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Limpiar",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            // 2. BOTONES DE PESTAÑAS: FEEDS vs POSTS
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CompactChip(
                        onClick = { selectedTab = SearchTab.FEEDS },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Explore,
                                contentDescription = null,
                                tint = if (selectedTab == SearchTab.FEEDS) Color.White else TextSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                        },
                        label = {
                            Text(
                                "Feeds",
                                fontSize = 10.sp,
                                fontWeight = if (selectedTab == SearchTab.FEEDS) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = if (selectedTab == SearchTab.FEEDS)
                            ChipDefaults.primaryChipColors(backgroundColor = RedditOrange)
                        else
                            ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.weight(1f)
                    )

                    CompactChip(
                        onClick = { selectedTab = SearchTab.POSTS },
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.Article,
                                contentDescription = null,
                                tint = if (selectedTab == SearchTab.POSTS) Color.White else TextSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                        },
                        label = {
                            Text(
                                "Posts",
                                fontSize = 10.sp,
                                fontWeight = if (selectedTab == SearchTab.POSTS) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = if (selectedTab == SearchTab.POSTS)
                            ChipDefaults.primaryChipColors(backgroundColor = RedditOrange)
                        else
                            ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 3. SPINNER DE CARGA
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

            // 4. RESULTADOS DE FEEDS / SUBREDDITS
            if (!isLoading && selectedTab == SearchTab.FEEDS) {
                if (subredditsResult.isEmpty() && currentQuery.isNotEmpty()) {
                    item {
                        Text(
                            text = "Sin resultados para \"$currentQuery\"",
                            fontSize = 10.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    items(subredditsResult) { sub ->
                        Chip(
                            onClick = { onSubredditClick(sub.displayName) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Explore,
                                    contentDescription = null,
                                    tint = RedditOrange,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = "r/${sub.displayName}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            secondaryLabel = {
                                val subCount = sub.subscribers
                                val countStr = if (subCount != null && subCount > 0) {
                                    if (subCount >= 1000000) "${subCount / 1000000}M miembros"
                                    else if (subCount >= 1000) "${subCount / 1000}k miembros"
                                    else "$subCount miembros"
                                } else "Comunidad de Reddit"
                                Text(countStr, fontSize = 8.sp, color = TextSecondary)
                            },
                            colors = ChipDefaults.secondaryChipColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 5. RESULTADOS DE POSTS
            if (!isLoading && selectedTab == SearchTab.POSTS) {
                if (postsResult.isEmpty() && currentQuery.isNotEmpty()) {
                    item {
                        Text(
                            text = "No se encontraron publicaciones",
                            fontSize = 10.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    items(postsResult) { post ->
                        PostCard(
                            post = post,
                            onClick = { onPostClick(post) },
                            onUpvote = { scope.launch { repository.vote(post, 1) } },
                            onDownvote = { scope.launch { repository.vote(post, -1) } }
                        )
                    }
                }
            }
        }
    }
}
