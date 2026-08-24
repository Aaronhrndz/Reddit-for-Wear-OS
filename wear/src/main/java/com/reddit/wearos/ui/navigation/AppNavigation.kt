package com.reddit.wearos.ui.navigation

import androidx.compose.runtime.Composable
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.reddit.wearos.auth.LoginScreen
import com.reddit.wearos.auth.RedditAuthManager
import com.reddit.wearos.auth.TokenStorage
import com.reddit.wearos.data.RedditRepository
import com.reddit.wearos.ui.comments.CommentsScreen
import com.reddit.wearos.ui.comments.CommentsViewModel
import com.reddit.wearos.ui.detail.PostDetailScreen
import com.reddit.wearos.ui.detail.PostDetailViewModel
import com.reddit.wearos.ui.feed.FeedScreen
import com.reddit.wearos.ui.feed.FeedViewModel
import com.reddit.wearos.ui.settings.SettingsScreen
import com.reddit.wearos.ui.subreddits.SubredditListScreen

@Composable
fun AppNavigation(
    authManager: RedditAuthManager,
    tokenStorage: TokenStorage,
    repository: RedditRepository
) {
    val navController = rememberSwipeDismissableNavController()
    val startDestination = if (tokenStorage.getAccessToken() != null) Screen.Feed.route else Screen.Login.route

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                authManager = authManager,
                tokenStorage = tokenStorage,
                onLoginSuccess = {
                    navController.navigate(Screen.Feed.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onContinueGuest = {
                    navController.navigate(Screen.Feed.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Feed.route) {
            val feedViewModel = FeedViewModel(repository)
            FeedScreen(
                viewModel = feedViewModel,
                onPostClick = { post ->
                    navController.navigate(Screen.PostDetail.createRoute(post.id, post.subreddit))
                },
                onOpenSubreddits = {
                    navController.navigate(Screen.Subreddits.route)
                },
                onOpenSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Subreddits.route) {
            SubredditListScreen(
                repository = repository,
                onSubredditSelected = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                tokenStorage = tokenStorage,
                repository = repository,
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Feed.route) { inclusive = true }
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.PostDetail.route) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId") ?: ""
            val subreddit = backStackEntry.arguments?.getString("subreddit") ?: ""
            val detailViewModel = PostDetailViewModel(repository)
            detailViewModel.loadPost(postId, subreddit)

            PostDetailScreen(
                viewModel = detailViewModel,
                onViewComments = { sub, id ->
                    navController.navigate(Screen.Comments.createRoute(id, sub))
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Comments.route) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId") ?: ""
            val subreddit = backStackEntry.arguments?.getString("subreddit") ?: ""
            val commentsViewModel = CommentsViewModel(repository)
            commentsViewModel.loadComments(postId, subreddit)

            CommentsScreen(
                viewModel = commentsViewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
