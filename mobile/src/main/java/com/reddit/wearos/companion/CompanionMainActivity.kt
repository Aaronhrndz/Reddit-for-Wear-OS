package com.reddit.wearos.companion

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.security.MessageDigest
import java.security.SecureRandom
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable

class CompanionMainActivity : ComponentActivity(), MessageClient.OnMessageReceivedListener {

    private lateinit var syncManager: WatchSyncManager

    private var loginStatus by mutableStateOf("Listo para conectar tu cuenta")
    private var connectedWatchesCount by mutableStateOf(0)
    private var watchInstalledVersion by mutableStateOf<String?>(null)
    private var watchUpdateStatus by mutableStateOf<String?>(null)
    private var isSyncing by mutableStateOf(false)
    private var isWebLoginOpen by mutableStateOf(false)
    private var redditUsername by mutableStateOf("Aaronhrnndz")
    private var clientIdInput by mutableStateOf("pA-pE4qfVj5W_A")
    private var tokenOrCookieInput by mutableStateOf("")

    private var oauthVerifier: String? = null
    private var oauthState: String? = null

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0"
        const val DEFAULT_CLIENT_ID = "pA-pE4qfVj5W_A"
    }

    override fun onResume() {
        super.onResume()
        Wearable.getMessageClient(this).addListener(this)
        lifecycleScope.launch {
            syncManager.requestWatchVersion()
        }
    }

    override fun onPause() {
        super.onPause()
        Wearable.getMessageClient(this).removeListener(this)
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            "/reddit/version_response" -> {
                watchInstalledVersion = String(event.data, Charsets.UTF_8).trim()
            }
            "/reddit/update_status" -> {
                watchUpdateStatus = String(event.data, Charsets.UTF_8).trim()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        syncManager = WatchSyncManager(this)

        val prefs = getSharedPreferences("companion_prefs", Context.MODE_PRIVATE)
        redditUsername = prefs.getString("saved_username", "Aaronhrnndz") ?: "Aaronhrnndz"
        clientIdInput = prefs.getString("saved_client_id", DEFAULT_CLIENT_ID) ?: DEFAULT_CLIENT_ID
        val savedSession = prefs.getString("saved_session_data", "") ?: ""

        // Recuperar automáticamente cookies activas del WebView (incluyendo reddit_session de 1 año)
        val webViewCookies = extractAllRedditCookies()
        if (webViewCookies.contains("reddit_session=")) {
            tokenOrCookieInput = webViewCookies
            prefs.edit().putString("saved_session_data", webViewCookies).apply()
            loginStatus = "✅ Sesión persistente (reddit_session) activa en el móvil"
        } else if (savedSession.isNotEmpty()) {
            tokenOrCookieInput = savedSession
        }

        checkConnectedWatches(autoSyncIfReady = tokenOrCookieInput.contains("reddit_session="))
        handleAuthRedirect(intent)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFF4500),
                    background = Color(0xFF0F0F0F),
                    surface = Color(0xFF1A1A1A)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isWebLoginOpen) {
                        RedditWebLoginView(
                            username = redditUsername,
                            onSessionCaptured = { sessionToken, user ->
                                isWebLoginOpen = false
                                if (user.isNotBlank()) {
                                    redditUsername = user
                                    prefs.edit().putString("saved_username", user).apply()
                                }
                                tokenOrCookieInput = sessionToken
                                prefs.edit().putString("saved_session_data", sessionToken).apply()
                                copyToClipboard(sessionToken)
                                syncSessionToWatch(sessionToken, redditUsername)
                            },
                            onCancel = { isWebLoginOpen = false }
                        )
                    } else {
                        CompanionScreen(
                            username = redditUsername,
                            onUsernameChange = {
                                redditUsername = it
                                prefs.edit().putString("saved_username", it).apply()
                            },
                            clientId = clientIdInput,
                            onClientIdChange = {
                                clientIdInput = it
                                prefs.edit().putString("saved_client_id", it).apply()
                            },
                            tokenInput = tokenOrCookieInput,
                            onTokenInputChange = { tokenOrCookieInput = it },
                            loginStatus = loginStatus,
                            connectedWatches = connectedWatchesCount,
                            watchVersion = watchInstalledVersion,
                            watchUpdateStatus = watchUpdateStatus,
                            isSyncing = isSyncing,
                            onStartOAuthLogin = { startOAuth2Flow(clientIdInput) },
                            onStartLogin = { isWebLoginOpen = true },
                            onCopyToClipboard = {
                                if (tokenOrCookieInput.isNotBlank()) {
                                    copyToClipboard(tokenOrCookieInput)
                                } else {
                                    Toast.makeText(this@CompanionMainActivity, "No hay token para copiar", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onSyncManualToken = {
                                val currentCookies = extractAllRedditCookies()
                                val effectiveToken = if (currentCookies.contains("reddit_session=")) {
                                    currentCookies
                                } else {
                                    tokenOrCookieInput.trim()
                                }
                                if (effectiveToken.isNotBlank()) {
                                    tokenOrCookieInput = effectiveToken
                                    prefs.edit().putString("saved_session_data", effectiveToken).apply()
                                    copyToClipboard(effectiveToken)
                                    syncSessionToWatch(effectiveToken, redditUsername)
                                } else {
                                    Toast.makeText(this@CompanionMainActivity, "Introduce un token o inicia sesión primero", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onLogout = { performLogout() },
                            onRefreshWatches = {
                                checkConnectedWatches(autoSyncIfReady = false)
                                lifecycleScope.launch { syncManager.requestWatchVersion() }
                            },
                            onTriggerWatchUpdate = { wearApkUrl ->
                                lifecycleScope.launch {
                                    watchUpdateStatus = "Enviando orden de actualización al reloj..."
                                    val sent = syncManager.triggerWatchUpdate(wearApkUrl)
                                    if (!sent) {
                                        watchUpdateStatus = "No se detectó reloj conectado por Bluetooth"
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthRedirect(intent)
    }

    private fun handleAuthRedirect(intent: Intent?) {
        val data = intent?.data ?: return
        val isRedditWearScheme = data.scheme == "redditwear" && data.host == "auth"
        val isLocalhost = (data.scheme == "http" || data.scheme == "https") && data.host == "localhost"
        if (isRedditWearScheme || isLocalhost) {
            val code = data.getQueryParameter("code")
            if (!code.isNullOrEmpty()) {
                val effectiveRedirect = if (isLocalhost) "http://localhost:8080" else "redditwear://auth"
                exchangeOAuthCode(code, effectiveRedirect)
            }
        }
    }

    private fun generateRandomString(length: Int = 32): String {
        val random = SecureRandom()
        val bytes = ByteArray(length)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val md = MessageDigest.getInstance("SHA-256")
        md.update(bytes, 0, bytes.size)
        val digest = md.digest()
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun startOAuth2Flow(customClientId: String) {
        val verifier = generateRandomString(32)
        val state = generateRandomString(16)
        oauthVerifier = verifier
        oauthState = state
        val challenge = generateCodeChallenge(verifier)
        val effectiveClientId = customClientId.trim().ifEmpty { DEFAULT_CLIENT_ID }
        val redirectUri = "redditwear://auth"
        val scopes = "identity read mysubreddits vote save history"

        val authUrl = Uri.parse("https://www.reddit.com/api/v1/authorize.compact").buildUpon()
            .appendQueryParameter("client_id", effectiveClientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("state", state)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("duration", "permanent")
            .appendQueryParameter("scope", scopes)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

        val intent = Intent(Intent.ACTION_VIEW, authUrl)
        startActivity(intent)
    }

    private fun exchangeOAuthCode(code: String, redirectUri: String = "redditwear://auth") {
        isSyncing = true
        loginStatus = "Intercambiando código OAuth 2.0 permanente con Reddit..."
        val verifier = oauthVerifier ?: ""
        val effectiveClientId = clientIdInput.trim().ifEmpty { DEFAULT_CLIENT_ID }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = HttpClient(OkHttp) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

                val credentials = Base64.encodeToString("$effectiveClientId:".toByteArray(), Base64.NO_WRAP)
                val responseText: String = client.submitForm(
                    url = "https://www.reddit.com/api/v1/access_token",
                    formParameters = Parameters.build {
                        append("grant_type", "authorization_code")
                        append("code", code)
                        append("redirect_uri", redirectUri)
                        if (verifier.isNotEmpty()) {
                            append("code_verifier", verifier)
                        }
                    }
                ) {
                    header("Authorization", "Basic $credentials")
                    header("User-Agent", "android:com.reddit.wearos:v1.4.0 (by /u/$redditUsername)")
                }.bodyAsText()

                val json = Json { ignoreUnknownKeys = true }
                val jsonElement = json.parseToJsonElement(responseText).jsonObject
                val accessToken = jsonElement["access_token"]?.jsonPrimitive?.content ?: ""
                val refreshToken = jsonElement["refresh_token"]?.jsonPrimitive?.contentOrNull
                val expiresIn = jsonElement["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L

                if (accessToken.isNotEmpty()) {
                    var detectedUsername = redditUsername
                    try {
                        val meJson = client.get("https://oauth.reddit.com/api/v1/me") {
                            header("Authorization", "Bearer $accessToken")
                            header("User-Agent", "android:com.reddit.wearos:v1.4.0 (by /u/$redditUsername)")
                        }.bodyAsText()
                        val meParsed = json.parseToJsonElement(meJson).jsonObject
                        val parsedName = meParsed["name"]?.jsonPrimitive?.contentOrNull
                        if (!parsedName.isNullOrEmpty()) {
                            detectedUsername = parsedName
                            withContext(Dispatchers.Main) {
                                redditUsername = parsedName
                                getSharedPreferences("companion_prefs", Context.MODE_PRIVATE)
                                    .edit().putString("saved_username", parsedName).apply()
                            }
                        }
                    } catch (_: Exception) {}

                    withContext(Dispatchers.Main) {
                        tokenOrCookieInput = accessToken
                        copyToClipboard(accessToken)
                    }

                    val payload = AuthSyncPayload(
                        clientId = effectiveClientId,
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresInSeconds = expiresIn,
                        username = detectedUsername
                    )

                    val syncSuccess = syncManager.sendAuthToWatch(payload)
                    withContext(Dispatchers.Main) {
                        isSyncing = false
                        loginStatus = if (syncSuccess) {
                            "✅ Sesión permanente vinculada (u/$detectedUsername) en tu Galaxy Watch"
                        } else {
                            "✅ Token OAuth permanente obtenido. Conecta el reloj vía Bluetooth para sincronizar."
                        }
                        Toast.makeText(this@CompanionMainActivity, "OAuth permanente configurado con éxito", Toast.LENGTH_LONG).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isSyncing = false
                        loginStatus = "Error en respuesta OAuth: $responseText"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isSyncing = false
                    loginStatus = "Error al intercambiar token: ${e.localizedMessage}"
                }
            }
        }
    }

    private fun performLogout() {
        val cm = CookieManager.getInstance()
        cm.removeAllCookies {
            cm.flush()
        }
        getSharedPreferences("companion_prefs", Context.MODE_PRIVATE)
            .edit().remove("saved_session_data").apply()
        tokenOrCookieInput = ""
        loginStatus = "Sesión cerrada en el móvil"
        Toast.makeText(this, "Sesión cerrada y cookies eliminadas", Toast.LENGTH_SHORT).show()
    }

    private fun copyToClipboard(text: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Reddit Token", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Token copiado al portapapeles", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    private fun checkConnectedWatches(autoSyncIfReady: Boolean = false) {
        lifecycleScope.launch {
            val nodes = syncManager.getConnectedWatches()
            connectedWatchesCount = nodes.size
            if (autoSyncIfReady && nodes.isNotEmpty() && tokenOrCookieInput.isNotBlank()) {
                syncSessionToWatch(tokenOrCookieInput, redditUsername)
            }
        }
    }

    private fun syncSessionToWatch(sessionData: String, username: String) {
        isSyncing = true
        loginStatus = "Sincronizando sesión persistente de u/$username con Galaxy Watch..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Asegurar que siempre incluimos reddit_session de CookieManager si existe
                val webCookies = extractAllRedditCookies()
                val combined = if (webCookies.isNotEmpty()) "$sessionData; $webCookies" else sessionData
                val sessionMatch = Regex("""reddit_session=([^;\s]+)""").find(combined)
                val tokenV2Match = Regex("""token_v2=([^;\s]+)""").find(combined)?.groupValues?.get(1)?.trim()?.takeIf { !isLoidJwt(it) }

                val permanentCookie = sessionMatch?.let { "reddit_session=${it.groupValues[1].trim()}" }
                val effectiveAccess = when {
                    permanentCookie != null -> permanentCookie
                    tokenV2Match != null -> tokenV2Match
                    !isLoidJwt(sessionData) -> sessionData.trim()
                    else -> ""
                }

                val payload = AuthSyncPayload(
                    clientId = "cookie_session",
                    accessToken = effectiveAccess,
                    refreshToken = permanentCookie,
                    expiresInSeconds = 31536000L,
                    username = username
                )

                val syncSuccess = syncManager.sendAuthToWatch(payload)
                withContext(Dispatchers.Main) {
                    isSyncing = false
                    if (syncSuccess) {
                        val modeLabel = if (permanentCookie != null) "persistente (1 año)" else "activa"
                        loginStatus = "✅ u/$username sincronizado con sesión $modeLabel en tu Galaxy Watch"
                        Toast.makeText(this@CompanionMainActivity, "u/$username conectado en el reloj (Sesión Persistente)", Toast.LENGTH_LONG).show()
                    } else {
                        loginStatus = "Sesión persistente guardada. Conecta tu reloj por Bluetooth y pulsa Reenviar."
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isSyncing = false
                    loginStatus = "Error al sincronizar: ${e.localizedMessage}"
                }
            }
        }
    }
}

private fun isLoidJwt(raw: String): Boolean {
    val clean = raw.trim().removePrefix("Bearer ")
    if (!clean.startsWith("eyJ")) return false
    return try {
        val seg = clean.split(".")
        if (seg.size == 3) {
            val p = String(Base64.decode(seg[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)
            Regex(""""sub"\s*:\s*"loid"""").containsMatchIn(p)
        } else false
    } catch (_: Exception) { false }
}

fun extractAllRedditCookies(): String {
    return try {
        val cm = CookieManager.getInstance()
        val c1 = cm.getCookie("https://www.reddit.com") ?: ""
        val c2 = cm.getCookie("https://old.reddit.com") ?: ""
        val c3 = cm.getCookie("https://oauth.reddit.com") ?: ""
        val c4 = cm.getCookie("https://accounts.reddit.com") ?: ""
        val combined = listOf(c1, c2, c3, c4).filter { it.isNotEmpty() }.joinToString("; ")

        val parts = mutableListOf<String>()
        Regex("""reddit_session=([^;\s]+)""").find(combined)?.let {
            val v = it.groupValues[1].trim()
            if (v.isNotEmpty() && v != "deleted") parts.add("reddit_session=$v")
        }
        Regex("""token_v2=([^;\s]+)""").find(combined)?.let {
            val v = it.groupValues[1].trim()
            if (v.length > 50 && !isLoidJwt(v)) parts.add("token_v2=$v")
        }
        Regex("""loid=([^;\s]+)""").find(combined)?.let {
            val v = it.groupValues[1].trim()
            if (v.isNotEmpty()) parts.add("loid=$v")
        }
        if (parts.isNotEmpty()) parts.joinToString("; ") else combined
    } catch (_: Exception) {
        ""
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RedditWebLoginView(
    username: String,
    onSessionCaptured: (String, String) -> Unit,
    onCancel: () -> Unit
) {
    var detectedUser by remember { mutableStateOf(username) }
    var hasPersistentCookie by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = Color(0xFF1E1E1E),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Login Reddit u/$detectedUser", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        Text(
                            text = if (hasPersistentCookie) "✅ Cookie reddit_session (1 año) lista" else "Inicia sesión abajo (Remember Me activo)",
                            fontSize = 11.sp,
                            color = if (hasPersistentCookie) Color(0xFF4CAF50) else Color(0xFFAAAAAA)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val cm = CookieManager.getInstance()
                                cm.removeAllCookies { cm.flush() }
                                hasPersistentCookie = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF442222)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFFF8888), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Limpiar", fontSize = 11.sp, color = Color(0xFFFF8888))
                        }
                        Button(
                            onClick = onCancel,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Volver", fontSize = 11.sp)
                        }
                    }
                }
            }

            AndroidView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        isFocusable = true
                        isFocusableInTouchMode = true
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                CookieManager.getInstance().flush()
                                val cookies = extractAllRedditCookies()
                                hasPersistentCookie = cookies.contains("reddit_session=")

                                view?.evaluateJavascript(
                                    """
                                    (function() {
                                        try {
                                            // 1. Marcar siempre "remember me" (rem) para que reddit_session dure 1 año
                                            var rems = document.querySelectorAll('input[name="rem"], #rem-login-main, input[type="checkbox"]');
                                            for (var k = 0; k < rems.length; k++) {
                                                rems[k].checked = true;
                                            }
                                            // 2. Cerrar banners de cookies
                                            var btns = document.querySelectorAll('button, a');
                                            for (var i = 0; i < btns.length; i++) {
                                                var text = (btns[i].innerText || '').toLowerCase();
                                                if (text.includes('accept') || text.includes('agree') || text.includes('aceptar') || text.includes('got it')) {
                                                    btns[i].click();
                                                }
                                            }
                                            var overlays = document.querySelectorAll('#cookie-banner, .cookie-banner, [id*="cookie"], [class*="cookie"], [class*="overlay"], [class*="backdrop"]');
                                            for (var j = 0; j < overlays.length; j++) {
                                                if (overlays[j].tagName !== 'BODY' && overlays[j].tagName !== 'HTML') {
                                                    overlays[j].style.display = 'none';
                                                    overlays[j].style.pointerEvents = 'none';
                                                }
                                            }
                                            // 3. Detectar usuario logueado en old.reddit.com
                                            var userLink = document.querySelector('.user a');
                                            if (userLink && userLink.innerText && !userLink.innerText.toLowerCase().includes('want to join')) {
                                                return userLink.innerText.trim();
                                            }
                                        } catch(e) {}
                                        return "";
                                    })();
                                    """.trimIndent()
                                ) { result ->
                                    val cleanName = result?.trim()?.removeSurrounding("\"") ?: ""
                                    if (cleanName.isNotEmpty() && cleanName != "null" && !cleanName.contains(" ")) {
                                        detectedUser = cleanName
                                    }
                                }
                            }
                        }
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            javaScriptCanOpenWindowsAutomatically = true
                        }
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        loadUrl("https://old.reddit.com/login")
                    }
                }
            )

            // Botón manual de confirmación
            Surface(
                color = Color(0xFF1A1A1A),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        CookieManager.getInstance().flush()
                        val token = extractAllRedditCookies()
                        onSessionCaptured(token, detectedUser)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hasPersistentCookie) Color(0xFF2E7D32) else Color(0xFFFF4500)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (hasPersistentCookie) "✅ Sincronizar Sesión Persistente con el Reloj" else "Copiar y Sincronizar con el Reloj",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun CompanionScreen(
    username: String,
    onUsernameChange: (String) -> Unit,
    clientId: String,
    onClientIdChange: (String) -> Unit,
    tokenInput: String,
    onTokenInputChange: (String) -> Unit,
    loginStatus: String,
    connectedWatches: Int,
    watchVersion: String?,
    watchUpdateStatus: String?,
    isSyncing: Boolean,
    onStartOAuthLogin: () -> Unit,
    onStartLogin: () -> Unit,
    onCopyToClipboard: () -> Unit,
    onSyncManualToken: () -> Unit,
    onLogout: () -> Unit,
    onRefreshWatches: () -> Unit,
    onTriggerWatchUpdate: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    var showAdvanced by remember { mutableStateOf(false) }
    val hasActiveSession = tokenInput.contains("reddit_session=") || tokenInput.length > 40

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Cabecera limpia
        Text(
            text = "Reddit for Wear OS",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Sincronización automática con tu Galaxy Watch",
            fontSize = 13.sp,
            color = Color(0xFF9E9E9E)
        )

        // 1. Tarjeta de Estado del Reloj + Sesión
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1E))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (connectedWatches > 0) Icons.Default.CheckCircle else Icons.Default.Watch,
                            contentDescription = null,
                            tint = if (connectedWatches > 0) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (connectedWatches > 0) "Galaxy Watch conectado" else "Buscando reloj Bluetooth…",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                            Text(
                                text = if (hasActiveSession) "Cuenta activa: u/$username" else "Sin sesión iniciada",
                                fontSize = 12.sp,
                                color = if (hasActiveSession) Color(0xFF4CAF50) else Color(0xFFAAAAAA)
                            )
                        }
                    }
                    IconButton(onClick = onRefreshWatches) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refrescar relojes",
                            tint = Color.White
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF2C2C34))

                Text(
                    text = loginStatus,
                    fontSize = 13.sp,
                    color = if (loginStatus.contains("✅")) Color(0xFF81C784) else Color(0xFFCCCCCC)
                )
            }
        }

        // 2. Acciones Principales (1 Toque)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1E))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onSyncManualToken,
                    enabled = !isSyncing && hasActiveSession,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4500)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSyncing) "Sincronizando con el reloj…" else "Sincronizar con el Reloj Ahora",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onStartLogin,
                        enabled = !isSyncing,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (hasActiveSession) "Cambiar Cuenta" else "Iniciar Sesión",
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }

                    if (hasActiveSession) {
                        OutlinedButton(
                            onClick = onLogout,
                            enabled = !isSyncing,
                            modifier = Modifier.height(46.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Cerrar sesión",
                                tint = Color(0xFFFF8A80),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Salir", fontSize = 13.sp, color = Color(0xFFFF8A80))
                        }
                    }
                }
            }
        }

        // 3. Tarjeta de Actualizaciones (Móvil + Reloj)
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val currentVer = remember {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.6.1"
            } catch (_: Exception) {
                "1.6.1"
            }
        }
        var releaseInfo by remember { mutableStateOf<CompanionReleaseInfo?>(null) }
        var isCheckingUpdate by remember { mutableStateOf(false) }
        var isInstallingMobile by remember { mutableStateOf(false) }
        var updateLabel by remember { mutableStateOf("Comprobando actualizaciones en GitHub…") }

        LaunchedEffect(Unit) {
            isCheckingUpdate = true
            val info = CompanionUpdateChecker.checkLatestRelease(currentVer)
            isCheckingUpdate = false
            releaseInfo = info
            updateLabel = when {
                info == null -> "Error de red al consultar GitHub"
                info.isUpdateAvailable -> "⚡ Nueva versión ${info.latestTag} disponible"
                else -> "✅ Última versión en GitHub: ${info.latestTag}"
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1E))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Actualizaciones (Móvil y Reloj)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Text(
                            text = updateLabel,
                            fontSize = 12.sp,
                            color = if (releaseInfo?.isUpdateAvailable == true) Color(0xFFFFB74D) else Color(0xFF81C784)
                        )
                    }
                    IconButton(
                        onClick = {
                            onRefreshWatches()
                            if (!isCheckingUpdate) {
                                scope.launch {
                                    isCheckingUpdate = true
                                    updateLabel = "Buscando en GitHub Releases…"
                                    val info = CompanionUpdateChecker.checkLatestRelease(currentVer)
                                    isCheckingUpdate = false
                                    releaseInfo = info
                                    updateLabel = when {
                                        info == null -> "Error de red al consultar GitHub"
                                        info.isUpdateAvailable -> "⚡ Nueva versión ${info.latestTag} disponible"
                                        else -> "✅ Última versión en GitHub: ${info.latestTag}"
                                    }
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Comprobar actualizaciones", tint = Color.White)
                    }
                }

                HorizontalDivider(color = Color(0xFF2C2C34))

                val info = releaseInfo
                val watchVerLabel = watchVersion?.let { "v$it" } ?: if (connectedWatches > 0) "v$currentVer (conectado)" else "No conectado"
                val watchHasUpdate = info != null && watchVersion != null &&
                    CompanionUpdateChecker.isNewerVersion(info.latestVersion, watchVersion.removePrefix("v"))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "📱 Móvil: v$currentVer",
                        fontSize = 12.sp,
                        color = Color(0xFFCCCCCC)
                    )
                    Text(
                        text = "⌚ Reloj: $watchVerLabel",
                        fontSize = 12.sp,
                        color = if (watchHasUpdate) Color(0xFFFFB74D) else Color(0xFFCCCCCC)
                    )
                }

                if (!watchUpdateStatus.isNullOrEmpty()) {
                    Text(
                        text = "⌚ $watchUpdateStatus",
                        fontSize = 12.sp,
                        color = Color(0xFFFFB74D)
                    )
                }

                if (info != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val url = info.mobileApkUrl
                                if (!url.isNullOrEmpty() && !isInstallingMobile) {
                                    scope.launch {
                                        isInstallingMobile = true
                                        MobileApkInstaller.downloadAndInstallMobileApk(
                                            context = context,
                                            apkUrl = url,
                                            onStatus = { st -> updateLabel = st }
                                        )
                                        isInstallingMobile = false
                                    }
                                } else {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseHtmlUrl)))
                                }
                            },
                            enabled = !isInstallingMobile,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (info.isUpdateAvailable) Color(0xFFFF4500) else Color(0xFF2C2C34)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (info.isUpdateAvailable) "Actualizar Móvil" else "Reinstalar Móvil",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Button(
                            onClick = {
                                val url = info.wearApkUrl
                                if (!url.isNullOrEmpty()) {
                                    onTriggerWatchUpdate(url)
                                } else {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseHtmlUrl)))
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (watchHasUpdate) Color(0xFFFF4500) else Color(0xFF2C2C34)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (watchHasUpdate) "Actualizar Reloj" else "Actualizar Reloj",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // 4. Sección Avanzada Plegable (Oculta por defecto para no recargar la UI)
        TextButton(onClick = { showAdvanced = !showAdvanced }) {
            Text(
                text = if (showAdvanced) "▲ Ocultar opciones avanzadas" else "▼ Opciones avanzadas (Token / Usuario)",
                fontSize = 12.sp,
                color = Color(0xFF888888)
            )
        }

        if (showAdvanced) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF16161A))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Usuario de Reddit", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                    OutlinedTextField(
                        value = username,
                        onValueChange = onUsernameChange,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFF4500),
                            unfocusedBorderColor = Color(0xFF444444)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Cookie / Token de Sesión", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                        if (tokenInput.isNotEmpty()) {
                            TextButton(onClick = onCopyToClipboard) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copiar", fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = onTokenInputChange,
                        minLines = 2,
                        maxLines = 4,
                        placeholder = { Text("reddit_session=...", fontSize = 11.sp, color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFF4500),
                            unfocusedBorderColor = Color(0xFF444444)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}


