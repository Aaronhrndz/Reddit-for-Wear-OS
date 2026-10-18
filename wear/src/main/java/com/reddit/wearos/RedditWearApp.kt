package com.reddit.wearos

import android.app.Application
import com.reddit.wearos.auth.RedditAuthManager
import com.reddit.wearos.auth.TokenStorage
import com.reddit.wearos.data.RedditRepository
import com.reddit.wearos.data.network.NetworkClient
import com.reddit.wearos.data.network.RedditApiService

import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

class RedditWearApp : Application(), ImageLoaderFactory {

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

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15) // Max 15% RAM para imágenes en reloj
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(25L * 1024 * 1024) // 25 MB en disco flash
                    .build()
            }
            .respectCacheHeaders(false) // Cachear agresivamente para evitar uso de radio
            .build()
    }
}
