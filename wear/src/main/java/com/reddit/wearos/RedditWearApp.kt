package com.reddit.wearos

import android.app.Application
import com.reddit.wearos.auth.RedditAuthManager
import com.reddit.wearos.auth.TokenStorage
import com.reddit.wearos.data.RedditRepository
import com.reddit.wearos.data.network.NetworkClient
import com.reddit.wearos.data.network.RedditApiService

class RedditWearApp : Application() {

    lateinit var tokenStorage: TokenStorage
        private set

    lateinit var authManager: RedditAuthManager
        private set

    lateinit var repository: RedditRepository
        private set

    override fun onCreate() {
        super.onCreate()

        tokenStorage = TokenStorage(this)
        val authHttpClient = NetworkClient.createAuthHttpClient()
        authManager = RedditAuthManager(this, tokenStorage, authHttpClient)

        val apiHttpClient = NetworkClient.createRedditApiHttpClient(tokenStorage, authManager)
        val apiService = RedditApiService(apiHttpClient, tokenStorage, authManager)
        repository = RedditRepository(apiService)

        // Programar comprobación periódica diaria ultra battery-friendly de actualizaciones
        com.reddit.wearos.util.UpdateJobService.schedule(this)
    }
}
