package com.reddit.wearos.companion

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
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

class CompanionMainActivity : ComponentActivity() {

    private lateinit var syncManager: WatchSyncManager

    private var loginStatus by mutableStateOf("Listo para conectar tu cuenta")
    private var connectedWatchesCount by mutableStateOf(0)
    private var isSyncing by mutableStateOf(false)
    private var isWebLoginOpen by mutableStateOf(false)
    private var redditUsername by mutableStateOf("aaronhrndz")
    private var tokenOrCookieInput by mutableStateOf("")

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        syncManager = WatchSyncManager(this)

        checkConnectedWatches()

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
                                tokenOrCookieInput = sessionToken
                                copyToClipboard(sessionToken)
                                syncSessionToWatch(sessionToken, user)
                            },
                            onCancel = { isWebLoginOpen = false }
                        )
                    } else {
                        CompanionScreen(
                            username = redditUsername,
                            onUsernameChange = { redditUsername = it },
                            tokenInput = tokenOrCookieInput,
                            onTokenInputChange = { tokenOrCookieInput = it },
                            loginStatus = loginStatus,
                            connectedWatches = connectedWatchesCount,
                            isSyncing = isSyncing,
                            onStartLogin = { isWebLoginOpen = true },
                            onCopyToClipboard = {
                                if (tokenOrCookieInput.isNotBlank()) {
                                    copyToClipboard(tokenOrCookieInput)
                                } else {
                                    Toast.makeText(this@CompanionMainActivity, "No hay token para copiar", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onSyncManualToken = {
                                if (tokenOrCookieInput.isNotBlank()) {
                                    copyToClipboard(tokenOrCookieInput.trim())
                                    syncSessionToWatch(tokenOrCookieInput.trim(), redditUsername)
                                } else {
                                    Toast.makeText(this@CompanionMainActivity, "Introduce un token o sesión", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onLogout = { performLogout() },
                            onRefreshWatches = { checkConnectedWatches() }
                        )
                    }
                }
            }
        }
    }

    private fun performLogout() {
        val cm = CookieManager.getInstance()
        cm.removeAllCookies {
            cm.flush()
        }
        tokenOrCookieInput = ""
        loginStatus = "Sesión cerrada en el móvil"
        Toast.makeText(this, "🚪 Sesión cerrada y cookies eliminadas", Toast.LENGTH_SHORT).show()
    }

    private fun copyToClipboard(text: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Reddit Token", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "📋 Token copiado al portapapeles", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    private fun checkConnectedWatches() {
        lifecycleScope.launch {
            val nodes = syncManager.getConnectedWatches()
            connectedWatchesCount = nodes.size
        }
    }

    private var oauthVerifier: String? = null
    private var oauthState: String? = null

    private fun generateRandomString(length: Int = 32): String {
        val random = java.security.SecureRandom()
        val bytes = ByteArray(length)
        random.nextBytes(bytes)
        return android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val md = java.security.MessageDigest.getInstance("SHA-256")
        md.update(bytes, 0, bytes.size)
        val digest = md.digest()
        return android.util.Base64.encodeToString(digest, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun syncSessionToWatch(sessionData: String, username: String) {
        isSyncing = true
        loginStatus = "Sincronizando sesión de u/$username con Galaxy Watch..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val payload = AuthSyncPayload(
                    clientId = "cookie_session",
                    accessToken = sessionData,
                    refreshToken = null,
                    expiresInSeconds = 31536000L,
                    username = username
                )

                val syncSuccess = syncManager.sendAuthToWatch(payload)
                withContext(Dispatchers.Main) {
                    isSyncing = false
                    if (syncSuccess) {
                        loginStatus = "✅ ¡u/$username sincronizado con éxito en tu Galaxy Watch!"
                        Toast.makeText(this@CompanionMainActivity, "u/$username conectado en el reloj", Toast.LENGTH_LONG).show()
                    } else {
                        loginStatus = "✅ Sesión obtenida. Conecta tu reloj por Bluetooth para sincronizar."
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isSyncing = false
                    loginStatus = "❌ Error al sincronizar: ${e.localizedMessage}"
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RedditWebLoginView(
    username: String,
    onSessionCaptured: (String, String) -> Unit,
    onCancel: () -> Unit
) {
    var currentCookies by remember { mutableStateOf("") }

    fun extractSessionToken(): String {
        val cm = CookieManager.getInstance()
        val c1 = cm.getCookie("https://www.reddit.com") ?: ""
        val c2 = cm.getCookie("https://old.reddit.com") ?: ""
        val c3 = cm.getCookie("https://oauth.reddit.com") ?: ""
        val c4 = cm.getCookie("https://accounts.reddit.com") ?: ""
        val combined = listOf(c1, c2, c3, c4).filter { it.isNotEmpty() }.joinToString("; ")
        return combined.ifEmpty { currentCookies }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Login Reddit u/$username", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            val cm = CookieManager.getInstance()
                            cm.removeAllCookies { cm.flush() }
                            currentCookies = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF442222)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("🧹 Limpiar", fontSize = 10.sp, color = Color(0xFFFF8888))
                    }
                    Button(
                        onClick = onCancel,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text("Volver", fontSize = 10.sp)
                    }
                }
            }

            AndroidView(
                modifier = Modifier.weight(1f),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        settings.userAgentString = CompanionMainActivity.USER_AGENT

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                currentCookies = extractSessionToken()
                            }

                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                currentCookies = extractSessionToken()
                            }
                        }
                        loadUrl("https://www.reddit.com/login")
                    }
                }
            )

            // Botón manual de confirmación
            Button(
                onClick = {
                    val token = extractSessionToken()
                    onSessionCaptured(token, username)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4500)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("📋 Copiar y Sincronizar con el Reloj", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CompanionScreen(
    username: String,
    onUsernameChange: (String) -> Unit,
    tokenInput: String,
    onTokenInputChange: (String) -> Unit,
    loginStatus: String,
    connectedWatches: Int,
    isSyncing: Boolean,
    onStartLogin: () -> Unit,
    onCopyToClipboard: () -> Unit,
    onSyncManualToken: () -> Unit,
    onLogout: () -> Unit,
    onRefreshWatches: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Reddit for Wear OS",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Sincronizador Oficial de Cuenta",
            fontSize = 14.sp,
            color = Color(0xFFAAAAAA)
        )

        // Estado del Reloj
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Galaxy Watch", fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        text = if (connectedWatches > 0) "✅ $connectedWatches reloj conectado" else "⚠️ Buscando reloj vía Bluetooth...",
                        fontSize = 12.sp,
                        color = if (connectedWatches > 0) Color(0xFF4CAF50) else Color(0xFFFF9800)
                    )
                }
                IconButton(onClick = onRefreshWatches) {
                    Text("🔄", fontSize = 18.sp)
                }
            }
        }

        // Campo de Usuario
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Tu Usuario de Reddit", fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(6.dp))
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
            }
        }

        // Estado de Sesión
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Estado de la Cuenta", fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = loginStatus,
                    fontSize = 13.sp,
                    color = if (loginStatus.startsWith("✅")) Color(0xFF4CAF50) else Color(0xFFCCCCCC)
                )
            }
        }

        // BOTONES DE LOGIN Y LOGOUT
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onStartLogin,
                enabled = !isSyncing,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4500)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "🚀 Login Web",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Button(
                onClick = onLogout,
                enabled = !isSyncing,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C34)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "🚪 Cerrar Sesión",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF6B6B)
                )
            }
        }

        // Tarjeta Token con visualización completa y botón copiar
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Token / Sesión Capturada", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                    if (tokenInput.isNotEmpty()) {
                        Button(
                            onClick = onCopyToClipboard,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("📋 Copiar Todo", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = onTokenInputChange,
                    minLines = 3,
                    maxLines = 6,
                    placeholder = { Text("Pega tu Bearer token o sesión aquí...", fontSize = 11.sp, color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFF4500),
                        unfocusedBorderColor = Color(0xFF444444)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onSyncManualToken,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4500))
                ) {
                    Text("📥 Sincronizar Este Token con el Reloj", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

