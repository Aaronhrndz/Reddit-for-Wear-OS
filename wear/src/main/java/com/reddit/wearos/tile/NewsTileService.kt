package com.reddit.wearos.tile

import androidx.wear.protolayout.*
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.reddit.wearos.MainActivity
import com.reddit.wearos.RedditWearApp
import com.reddit.wearos.data.model.RedditPost
import com.reddit.wearos.data.model.formatRelativeTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.future

class NewsTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val app = application as? RedditWearApp

        return serviceScope.future {
            val newsPosts: List<RedditPost> = try {
                val (posts, _) = app?.repository?.getFeed(subreddit = "worldnews", sort = "hot") ?: Pair(emptyList(), null)
                posts.take(4)
            } catch (_: Exception) {
                emptyList()
            }

            val rootLayout = buildFullCircleLayout(
                headerTitle = "⚡ NOTICIAS",
                posts = newsPosts
            )

            val timeline = TimelineBuilders.Timeline.Builder()
                .addTimelineEntry(
                    TimelineBuilders.TimelineEntry.Builder()
                        .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(rootLayout).build())
                        .build()
                )
                .build()

            TileBuilders.Tile.Builder()
                .setResourcesVersion("1")
                .setTileTimeline(timeline)
                .setFreshnessIntervalMillis(60 * 60 * 1000L)
                .build()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion("1")
                .build()
        )
    }

    private fun buildLaunchAction(post: RedditPost?): ActionBuilders.LaunchAction {
        val launchIntent = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(packageName)
            .setClassName(MainActivity::class.java.name)

        if (post != null) {
            launchIntent.addKeyToExtraMapping(
                "post_id",
                ActionBuilders.AndroidStringExtra.Builder().setValue(post.id).build()
            )
            launchIntent.addKeyToExtraMapping(
                "subreddit",
                ActionBuilders.AndroidStringExtra.Builder().setValue(post.subreddit).build()
            )
        }

        return ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(launchIntent.build())
            .build()
    }

    private fun buildFullCircleLayout(
        headerTitle: String,
        posts: List<RedditPost>
    ): LayoutElementBuilders.LayoutElement {
        val refreshClickable = ModifiersBuilders.Clickable.Builder()
            .setId("refresh_news_tile")
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .build()

        if (posts.isEmpty()) {
            return LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.expand())
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setClickable(refreshClickable)
                        .build()
                )
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText("↻ Toca para cargar noticias")
                        .setFontStyle(
                            LayoutElementBuilders.FontStyle.Builder()
                                .setSize(DimensionBuilders.sp(13f))
                                .setColor(ColorBuilders.argb(0xFFAAAAAA.toInt()))
                                .build()
                        )
                        .build()
                )
                .build()
        }

        val mainColumn = LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.wrap())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

        val headerBadge = LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(refreshClickable)
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(ColorBuilders.argb(0x33FF4500.toInt()))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(14f)).build())
                            .build()
                    )
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setStart(DimensionBuilders.dp(14f))
                            .setEnd(DimensionBuilders.dp(14f))
                            .setTop(DimensionBuilders.dp(4f))
                            .setBottom(DimensionBuilders.dp(4f))
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText("$headerTitle  ↻")
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(11f))
                            .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                            .setColor(ColorBuilders.argb(0xFFFF5722.toInt()))
                            .build()
                    )
                    .setMaxLines(1)
                    .build()
            )
            .build()

        mainColumn.addContent(headerBadge)
        mainColumn.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(6f)).build())

        posts.forEachIndexed { idx, post ->
            val scoreStr = if (post.score >= 1000) String.format("%.1fk", post.score / 1000.0) else "${post.score}"
            val timeAgo = formatRelativeTime(post.createdUtc)
            val curveInset = when (idx) {
                0 -> 20f
                1 -> 4f
                2 -> 4f
                else -> 22f
            }

            val cardBox = LayoutElementBuilders.Row.Builder()
                .setWidth(DimensionBuilders.expand())
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setPadding(
                            ModifiersBuilders.Padding.Builder()
                                .setStart(DimensionBuilders.dp(curveInset))
                                .setEnd(DimensionBuilders.dp(curveInset))
                                .build()
                        )
                        .build()
                )
                .addContent(
                    LayoutElementBuilders.Column.Builder()
                        .setWidth(DimensionBuilders.expand())
                        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
                        .setModifiers(
                            ModifiersBuilders.Modifiers.Builder()
                                .setClickable(
                                    ModifiersBuilders.Clickable.Builder()
                                        .setId("news_${post.id}")
                                        .setOnClick(buildLaunchAction(post))
                                        .build()
                                )
                                .setBackground(
                                    ModifiersBuilders.Background.Builder()
                                        .setColor(ColorBuilders.argb(0xFF222227.toInt()))
                                        .setCorner(ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(16f)).build())
                                        .build()
                                )
                                .setPadding(
                                    ModifiersBuilders.Padding.Builder()
                                        .setStart(DimensionBuilders.dp(12f))
                                        .setEnd(DimensionBuilders.dp(12f))
                                        .setTop(DimensionBuilders.dp(7f))
                                        .setBottom(DimensionBuilders.dp(7f))
                                        .build()
                                )
                                .build()
                        )
                        .addContent(
                            LayoutElementBuilders.Text.Builder()
                                .setText(post.title)
                                .setFontStyle(
                                    LayoutElementBuilders.FontStyle.Builder()
                                        .setSize(DimensionBuilders.sp(12f))
                                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                                        .setColor(ColorBuilders.argb(0xFFFFFFFF.toInt()))
                                        .build()
                                )
                                .setMaxLines(1)
                                .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE)
                                .build()
                        )
                        .addContent(
                            LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(2f)).build()
                        )
                        .addContent(
                            LayoutElementBuilders.Text.Builder()
                                .setText("▲ $scoreStr • r/${post.subreddit}" + if (timeAgo.isNotEmpty()) " • $timeAgo" else "")
                                .setFontStyle(
                                    LayoutElementBuilders.FontStyle.Builder()
                                        .setSize(DimensionBuilders.sp(10f))
                                        .setColor(ColorBuilders.argb(0xFFFF6E40.toInt()))
                                        .build()
                                )
                                .setMaxLines(1)
                                .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE)
                                .build()
                        )
                        .build()
                )
                .build()

            mainColumn.addContent(cardBox)

            if (idx < posts.size - 1) {
                mainColumn.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(5f)).build())
            }
        }

        return LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setStart(DimensionBuilders.dp(6f))
                            .setEnd(DimensionBuilders.dp(6f))
                            .build()
                    )
                    .build()
            )
            .addContent(mainColumn.build())
            .build()
    }
}
