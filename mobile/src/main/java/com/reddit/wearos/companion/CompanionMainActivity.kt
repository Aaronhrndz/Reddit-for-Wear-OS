package com.reddit.wearos.companion

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
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
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
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

    private fun checkConnectedWatches() {
        lifecycleScope.launch {
            val nodes = syncManager.getConnectedWatches()
            connectedWatchesCount = nodes.size
        }
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
                        loginStatus = "u/$username sincronizado con éxito en tu Galaxy Watch"
                        Toast.makeText(this@CompanionMainActivity, "u/$username conectado en el reloj", Toast.LENGTH_LONG).show()
                    } else {
                        loginStatus = "Sesión obtenida. Conecta tu reloj por Bluetooth para sincronizar."
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RedditWebLoginView(
    username: String,
    onSessionCaptured: (String, String) -> Unit,
    onCancel: () -> Unit
) {
    fun extractSessionToken(): String {
        val cm = CookieManager.getInstance()
        val c1 = cm.getCookie("https://www.reddit.com") ?: ""
        val c2 = cm.getCookie("https://old.reddit.com") ?: ""
        val c3 = cm.getCookie("https://oauth.reddit.com") ?: ""
        val c4 = cm.getCookie("https://accounts.reddit.com") ?: ""
        val combined = listOf(c1, c2, c3, c4).filter { it.isNotEmpty() }.joinToString("; ")
        return combined
    }

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
                    Text("Login Reddit u/$username", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val cm = CookieManager.getInstance()
                                cm.removeAllCookies { cm.flush() }
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
                        webViewClient = object : WebViewClient() {}
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            javaScriptCanOpenWindowsAutomatically = true
                            userAgentString = CompanionMainActivity.USER_AGENT
                        }
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        loadUrl("https://www.reddit.com/login")
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
                        val token = extractSessionToken()
                        onSessionCaptured(token, username)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4500)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copiar y Sincronizar con el Reloj", fontWeight = FontWeight.Bold)
                }
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
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (connectedWatches > 0) Icons.Default.CheckCircle else Icons.Default.Watch,
                            contentDescription = null,
                            tint = if (connectedWatches > 0) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (connectedWatches > 0) "$connectedWatches reloj conectado" else "Buscando reloj vía Bluetooth...",
                            fontSize = 12.sp,
                            color = if (connectedWatches > 0) Color(0xFF4CAF50) else Color(0xFFFF9800)
                        )
                    }
                }
                IconButton(onClick = onRefreshWatches) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refrescar",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
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
                    color = if (loginStatus.contains("éxito") || loginStatus.contains("sincronizado")) Color(0xFF4CAF50) else Color(0xFFCCCCCC)
                )
            }
        }

        // BOTONES DE LOGIN Y LOGOUT
        Button(
            onClick = onStartLogin,
            enabled = !isSyncing,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4500)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Iniciar Sesión (Login Web)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Button(
            onClick = onLogout,
            enabled = !isSyncing,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C34)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = null,
                tint = Color(0xFFFF6B6B),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Cerrar Sesión / Limpiar Cuenta",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF6B6B)
            )
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
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copiar Todo", fontSize = 11.sp, color = Color.White)
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
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sincronizar Este Token con el Reloj", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

