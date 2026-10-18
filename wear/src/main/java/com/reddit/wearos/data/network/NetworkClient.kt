package com.reddit.wearos.data.network

import com.reddit.wearos.auth.RedditAuthManager
import com.reddit.wearos.auth.TokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.Protocol

object NetworkClient {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    fun createAuthHttpClient(): HttpClient {
        return HttpClient(OkHttp) {
            engine {
                config {
                    protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                    retryOnConnectionFailure(true)
                }
            }
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
                socketTimeoutMillis = 15000
            }
            install(Logging) {
                level = LogLevel.NONE
            }
        }
    }

    fun createRedditApiHttpClient(tokenStorage: TokenStorage, authManager: RedditAuthManager): HttpClient {
        return HttpClient(OkHttp) {
            engine {
                config {
                    protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                    retryOnConnectionFailure(true)
                }
            }
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 15000
                socketTimeoutMillis = 15000
            }
        }
    }
}
