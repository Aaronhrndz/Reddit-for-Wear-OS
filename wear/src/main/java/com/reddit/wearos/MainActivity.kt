package com.reddit.wearos

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.wear.ambient.AmbientModeSupport
import com.reddit.wearos.ui.aod.MinimalistAodScreen
import com.reddit.wearos.ui.navigation.AppNavigation
import com.reddit.wearos.ui.theme.RedditWearTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity(), AmbientModeSupport.AmbientCallbackProvider {

    private lateinit var app: RedditWearApp
    private lateinit var ambientController: AmbientModeSupport.AmbientController

    private var isAmbient by mutableStateOf(false)
    private var isBurnInProtection by mutableStateOf(false)

    private val ambientCallback = object : AmbientModeSupport.AmbientCallback() {
        override fun onEnterAmbient(ambientDetails: Bundle?) {
            super.onEnterAmbient(ambientDetails)
            isAmbient = true
            isBurnInProtection = ambientDetails?.getBoolean(AmbientModeSupport.EXTRA_BURN_IN_PROTECTION, false) ?: false
        }

        override fun onExitAmbient() {
            super.onExitAmbient()
            isAmbient = false
        }

        override fun onUpdateAmbient() {
            super.onUpdateAmbient()
            // Se ejecuta cada minuto en AOD
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
            RedditWearTheme {
                if (isAmbient) {
                    val currentSub by app.repository.currentSubreddit.collectAsState()
                    MinimalistAodScreen(
                        currentSubreddit = currentSub,
                        username = app.tokenStorage.getUsername(),
                        isBurnInProtection = isBurnInProtection
                    )
                } else {
                    AppNavigation(
                        authManager = app.authManager,
                        tokenStorage = app.tokenStorage,
                        repository = app.repository
                    )
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
