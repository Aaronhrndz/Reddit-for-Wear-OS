package com.reddit.wearos.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.reddit.wearos.MainActivity
import com.reddit.wearos.RedditWearApp
import com.reddit.wearos.data.model.formatRelativeTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.future

class SavedTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val app = application as? RedditWearApp

        return serviceScope.future {
            val savedItems = try {
                val username = app?.tokenStorage?.getUsername()?.takeIf { it.isNotBlank() && it != "me" }
                    ?: app?.repository?.getMe()?.name
                    ?: "me"
                val (posts, _) = app?.repository?.getSavedPosts(username = username) ?: Pair(emptyList(), null)
                posts.take(3)
            } catch (_: Exception) {
                emptyList()
            }

            val openAppAction = ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(packageName)
                        .setClassName(MainActivity::class.java.name)
                        .build()
                )
                .build()

            val textHeader = LayoutElementBuilders.Text.Builder()
                .setText("🔖 MIS GUARDADOS")
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(DimensionBuilders.sp(11f))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setColor(ColorBuilders.argb(0xFFFF4500.toInt()))
                        .build()
                )
                .build()

            val column = LayoutElementBuilders.Column.Builder()
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setClickable(
                            ModifiersBuilders.Clickable.Builder()
                                .setOnClick(openAppAction)
                                .build()
                        )
                        .setPadding(
                            ModifiersBuilders.Padding.Builder()
                                .setStart(DimensionBuilders.dp(14f))
                                .setEnd(DimensionBuilders.dp(14f))
                                .setTop(DimensionBuilders.dp(12f))
                                .setBottom(DimensionBuilders.dp(12f))
                                .build()
                        )
                        .build()
                )
                .addContent(textHeader)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(5f)).build())

            if (savedItems.isEmpty()) {
                val emptyText = LayoutElementBuilders.Text.Builder()
                    .setText("Toca para ver tus guardados...")
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(11f))
                            .setColor(ColorBuilders.argb(0xFFAAAAAA.toInt()))
                            .build()
                    )
                    .build()
                column.addContent(emptyText)
            } else {
                savedItems.forEachIndexed { index, post ->
                    val timeAgo = formatRelativeTime(post.createdUtc)
                    val cleanTitle = if (post.title.length > 42) post.title.take(39) + "…" else post.title

                    val itemCol = LayoutElementBuilders.Column.Builder()
                        .setWidth(DimensionBuilders.expand())
                        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
                        .addContent(
                            LayoutElementBuilders.Text.Builder()
                                .setText("r/${post.subreddit}" + if (timeAgo.isNotEmpty()) " • $timeAgo" else "")
                                .setFontStyle(
                                    LayoutElementBuilders.FontStyle.Builder()
                                        .setSize(DimensionBuilders.sp(8f))
                                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                                        .setColor(ColorBuilders.argb(0xFFFF4500.toInt()))
                                        .build()
                                )
                                .setMaxLines(1)
                                .build()
                        )
                        .addContent(
                            LayoutElementBuilders.Text.Builder()
                                .setText(cleanTitle)
                                .setFontStyle(
                                    LayoutElementBuilders.FontStyle.Builder()
                                        .setSize(DimensionBuilders.sp(10f))
                                        .setColor(ColorBuilders.argb(0xFFFFFFFF.toInt()))
                                        .build()
                                )
                                .setMaxLines(1)
                                .build()
                        )
                        .build()

                    column.addContent(itemCol)
                    if (index < savedItems.size - 1) {
                        column.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(4f)).build())
                    }
                }
            }

            val timeline = TimelineBuilders.Timeline.Builder()
                .addTimelineEntry(
                    TimelineBuilders.TimelineEntry.Builder()
                        .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(column.build()).build())
                        .build()
                )
                .build()

            TileBuilders.Tile.Builder()
                .setResourcesVersion("1")
                .setTileTimeline(timeline)
                .setFreshnessIntervalMillis(15 * 60 * 1000L)
                .build()
        }
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion("1")
            .build()
        return Futures.immediateFuture(resources)
    }
}
