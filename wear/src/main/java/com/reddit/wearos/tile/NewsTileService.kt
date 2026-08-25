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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.future

class NewsTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val app = application as? RedditWearApp

        return serviceScope.future {
            val headlines = try {
                val (posts, _) = app?.repository?.getFeed(subreddit = "worldnews", sort = "hot") ?: Pair(emptyList(), null)
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
                .setText("⚡ TITULARES FLASH")
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
                        .build()
                )
                .addContent(textHeader)
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(4f)).build())

            if (headlines.isEmpty()) {
                val emptyText = LayoutElementBuilders.Text.Builder()
                    .setText("Toca para abrir noticias...")
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(12f))
                            .setColor(ColorBuilders.argb(0xFFAAAAAA.toInt()))
                            .build()
                    )
                    .build()
                column.addContent(emptyText)
            } else {
                headlines.forEachIndexed { index, post ->
                    val scoreStr = if (post.score >= 1000) "${post.score / 1000}k" else "${post.score}"
                    val cleanTitle = if (post.title.length > 45) post.title.take(42) + "..." else post.title

                    val itemRow = LayoutElementBuilders.Row.Builder()
                        .setWidth(DimensionBuilders.expand())
                        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                        .addContent(
                            LayoutElementBuilders.Text.Builder()
                                .setText("▲$scoreStr ")
                                .setFontStyle(
                                    LayoutElementBuilders.FontStyle.Builder()
                                        .setSize(DimensionBuilders.sp(9f))
                                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                                        .setColor(ColorBuilders.argb(0xFFFF4500.toInt()))
                                        .build()
                                )
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

                    column.addContent(itemRow)
                    if (index < headlines.size - 1) {
                        column.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(3f)).build())
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
                .setFreshnessIntervalMillis(15 * 60 * 1000L) // Refrescar cada 15 min
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
