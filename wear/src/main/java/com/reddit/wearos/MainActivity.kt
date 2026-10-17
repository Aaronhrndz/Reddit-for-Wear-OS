package com.reddit.wearos

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.fragment.app.FragmentActivity
import androidx.wear.ambient.AmbientModeSupport
import com.reddit.wearos.ui.navigation.AppNavigation
import com.reddit.wearos.ui.theme.RedditWearTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

val LocalIsAmbient = compositionLocalOf { false }

class MainActivity : FragmentActivity(), AmbientModeSupport.AmbientCallbackProvider {

    private lateinit var app: RedditWearApp
    private lateinit var ambientController: AmbientModeSupport.AmbientController

    private var isAmbient by mutableStateOf(false)

    private val ambientCallback = object : AmbientModeSupport.AmbientCallback() {
        override fun onEnterAmbient(ambientDetails: Bundle?) {
            super.onEnterAmbient(ambientDetails)
            isAmbient = true
        }

        override fun onExitAmbient() {
            super.onExitAmbient()
            isAmbient = false
        }

        override fun onUpdateAmbient() {
            super.onUpdateAmbient()
        }
    }

    override fun getAmbientCallback(): AmbientModeSupport.AmbientCallback {
        return ambientCallback
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        app = application as RedditWearApp
        ambientController = AmbientModeSupport.attach(this)

        handleAuthIntent(intent)
        val initialPostId = intent?.getStringExtra("post_id")
        val initialSubreddit = intent?.getStringExtra("subreddit")

        setContent {
            RedditWearTheme {
                CompositionLocalProvider(LocalIsAmbient provides isAmbient) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .drawWithContent {
                                drawContent()
                                // Al entrar en Always On Display, transforma 100% de la UI a Blanco y Negro puro
                                if (isAmbient) {
                                    drawRect(
                                        color = Color.Black,
                                        blendMode = BlendMode.Color
                                    )
                                }
                            }
                            .alpha(if (isAmbient) 0.82f else 1f)
                    ) {
                        AppNavigation(
                            authManager = app.authManager,
                            tokenStorage = app.tokenStorage,
                            repository = app.repository,
                            initialPostId = initialPostId,
                            initialSubreddit = initialSubreddit
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "redditwear" && uri.host == "auth") {
            val code = uri.getQueryParameter("code")
            val error = uri.getQueryParameter("error")
            if (error == null && !code.isNullOrEmpty()) {
                val clientId = app.tokenStorage.getClientId()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        app.authManager.exchangeCodeForToken(clientId, code)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }
}
