package com.reddit.wearos.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.reddit.wearos.data.model.RedditPost
import java.util.concurrent.Executors

object RemotePhoneHelper {

    fun openPhoneCompanion(context: Context) {
        val helper = RemoteActivityHelper(context, Executors.newSingleThreadExecutor())
        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse("redditwear://auth"))

        helper.startRemoteActivity(intent)
        Toast.makeText(context, "Abriendo en tu teléfono...", Toast.LENGTH_SHORT).show()
    }

    fun openPostOnPhone(context: Context, post: RedditPost) {
        val helper = RemoteActivityHelper(context, Executors.newSingleThreadExecutor())
        val url = if (post.permalink.isNotEmpty()) {
            "https://reddit.com${post.permalink}"
        } else {
            "https://reddit.com/comments/${post.id}"
        }

        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse(url))

        helper.startRemoteActivity(intent)
        Toast.makeText(context, "Abriendo en tu teléfono...", Toast.LENGTH_SHORT).show()
    }

    fun openUrlOnPhone(context: Context, url: String) {
        val helper = RemoteActivityHelper(context, Executors.newSingleThreadExecutor())
        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse(url))

        helper.startRemoteActivity(intent)
        Toast.makeText(context, "Abriendo descarga en tu teléfono...", Toast.LENGTH_SHORT).show()
    }
}
