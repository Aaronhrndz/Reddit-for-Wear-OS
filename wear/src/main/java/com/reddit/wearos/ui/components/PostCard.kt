package com.reddit.wearos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.CardDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import com.reddit.wearos.data.model.RedditPost
import com.reddit.wearos.ui.theme.*
import androidx.compose.foundation.shape.CircleShape

private val avatarColors = listOf(
    Color(0xFFE53935), Color(0xFF8E24AA), Color(0xFF3949AB),
    Color(0xFF039BE5), Color(0xFF00897B), Color(0xFF43A047),
    Color(0xFFFDD835), Color(0xFFFF6F00), Color(0xFF6D4C41),
    Color(0xFF546E7A), Color(0xFFD81B60), Color(0xFF5E35B1)
)

fun avatarColorForName(name: String): Color {
    val hash = name.fold(0) { acc, c -> acc * 31 + c.code }
    return avatarColors[(hash and 0x7FFFFFFF) % avatarColors.size]
}

fun formatScore(score: Int): String {
    return when {
        score >= 1000000 -> String.format("%.1fM", score / 1000000.0)
        score >= 1000 -> String.format("%.1fk", score / 1000.0)
        else -> score.toString()
    }
}

@Composable
fun PostCard(
    post: RedditPost,
    onClick: () -> Unit,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
        backgroundPainter = CardDefaults.cardBackgroundPainter(
            startBackgroundColor = DarkSurface,
            endBackgroundColor = DarkSurface
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val initial = post.author.firstOrNull()?.uppercase() ?: "?"
                    val avatarColor = avatarColorForName(post.author)
                    Box(
                        modifier = Modifier
                            .size(15.dp)
                            .clip(CircleShape)
                            .background(avatarColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    val subText = if (post.subredditPrefixed.isNotEmpty()) post.subredditPrefixed else "r/${post.subreddit}"
                    val timeAgo = com.reddit.wearos.data.model.formatRelativeTime(post.createdUtc)
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = RedditOrange, fontWeight = FontWeight.Bold)) {
                                append(subText)
                            }
                            withStyle(SpanStyle(color = TextSecondary)) {
                                if (timeAgo.isNotEmpty()) {
                                    append(" • $timeAgo")
                                }
                                append(" • u/${post.author}")
                            }
                        },
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (post.hasVideo()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(RedditOrange)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(9.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "VIDEO",
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = post.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp
            )

            val previewUrl = post.getThumbnailUrl()
            val galleryUrls = post.getGalleryImageUrls()
            if (!previewUrl.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                ) {
                    AsyncImage(
                        model = previewUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (post.hasVideo()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x44000000)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else if (galleryUrls.size > 1) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xBB000000))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "1/${galleryUrls.size}",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x88000000))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            val dotsCount = galleryUrls.size.coerceAtMost(7)
                            repeat(dotsCount) { idx ->
                                Box(
                                    modifier = Modifier
                                        .size(if (idx == 0) 5.dp else 3.5.dp)
                                        .clip(CircleShape)
                                        .background(if (idx == 0) RedditOrange else Color(0x99FFFFFF))
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF141416))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clickable { onUpvote() }
                            .padding(2.dp)
                    ) {
                        RedditUpvoteIcon(
                            isVoted = post.likes == true,
                            size = 12.dp
                        )
                    }

                    Text(
                        text = formatScore(post.score),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (post.likes) {
                            true -> RedditUpvote
                            false -> RedditDownvote
                            else -> TextPrimary
                        },
                        modifier = Modifier.padding(horizontal = 3.dp)
                    )

                    Box(
                        modifier = Modifier
                            .clickable { onDownvote() }
                            .padding(2.dp)
                    ) {
                        RedditDownvoteIcon(
                            isVoted = post.likes == false,
                            size = 12.dp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF141416))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Comentarios",
                        tint = TextSecondary,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${post.numComments}",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
