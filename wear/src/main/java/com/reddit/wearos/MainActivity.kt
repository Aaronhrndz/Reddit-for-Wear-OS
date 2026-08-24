package com.reddit.wearos

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.fragment.app.FragmentActivity
import androidx.wear.ambient.AmbientModeSupport
import androidx.wear.compose.material.Colors
import com.reddit.wearos.ui.navigation.AppNavigation
import com.reddit.wearos.ui.theme.RedditWearTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

val LocalIsAmbient = compositionLocalOf { false }

private val AmbientMonochromePalette = Colors(
    primary = Color.White,
    primaryVariant = Color(0xFFAAAAAA),
    secondary = Color(0xFFCCCCCC),
    background = Color.Black,
    surface = Color(0xFF1E1E1E),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFFAAAAAA),
    onError = Color.White
)

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

        setContent {
            RedditWearTheme(
                colors = if (isAmbient) AmbientMonochromePalette else null
            ) {
                CompositionLocalProvider(LocalIsAmbient provides isAmbient) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .alpha(if (isAmbient) 0.85f else 1f)
                    ) {
                        AppNavigation(
                            authManager = app.authManager,
                            tokenStorage = app.tokenStorage,
                            repository = app.repository
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
