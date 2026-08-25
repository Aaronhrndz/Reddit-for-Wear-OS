package com.reddit.wearos.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.reddit.wearos.ui.inbox.InboxScreen
import com.reddit.wearos.ui.inbox.InboxViewModel
import com.reddit.wearos.ui.settings.SettingsScreen
import com.reddit.wearos.ui.subreddits.SubredditListScreen

@Composable
fun AppNavigation(
    authManager: RedditAuthManager,
    tokenStorage: TokenStorage,
    repository: RedditRepository
) {
    val navController = rememberSwipeDismissableNavController()
    val feedViewModel = remember { FeedViewModel(repository) }
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
                },
                onOpenSearch = {
                    navController.navigate(Screen.Search.route)
                }
            )
        }

        composable(Screen.Search.route) {
            com.reddit.wearos.ui.search.SearchScreen(
                repository = repository,
                onSubredditClick = { sub ->
                    repository.setSubreddit(sub)
                    navController.popBackStack(Screen.Feed.route, inclusive = false)
                },
                onPostClick = { post ->
                    navController.navigate(Screen.PostDetail.createRoute(post.id, post.subreddit))
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
                onOpenInbox = {
                    navController.navigate(Screen.Inbox.route)
                },
                onOpenSaved = {
                    navController.navigate(Screen.Saved.route)
                },
                onOpenProfile = {
                    navController.navigate(Screen.Profile.route)
                },
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

        composable(Screen.Profile.route) {
            com.reddit.wearos.ui.profile.ProfileScreen(
                repository = repository,
                onPostClick = { post ->
                    navController.navigate(Screen.PostDetail.createRoute(post.id, post.subreddit))
                }
            )
        }

        composable(Screen.Saved.route) {
            val username = tokenStorage.getUsername() ?: "Aaronhrnndz"
            val savedViewModel = remember { com.reddit.wearos.ui.saved.SavedViewModel(repository, username) }
            com.reddit.wearos.ui.saved.SavedScreen(
                viewModel = savedViewModel,
                onPostClick = { post ->
                    navController.navigate(Screen.PostDetail.createRoute(post.id, post.subreddit))
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Inbox.route) {
            val inboxViewModel = remember { InboxViewModel(repository) }
            InboxScreen(
                viewModel = inboxViewModel,
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
                postId = postId,
                subreddit = subreddit,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
