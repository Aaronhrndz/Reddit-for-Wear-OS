package com.reddit.wearos.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Feed : Screen("feed")
    object Subreddits : Screen("subreddits")
    object Settings : Screen("settings")
    object Inbox : Screen("inbox")
    object PostDetail : Screen("post_detail/{postId}/{subreddit}") {
        fun createRoute(postId: String, subreddit: String) = "post_detail/$postId/$subreddit"
    }
    object Comments : Screen("comments/{postId}/{subreddit}") {
        fun createRoute(postId: String, subreddit: String) = "comments/$postId/$subreddit"
    }
}
