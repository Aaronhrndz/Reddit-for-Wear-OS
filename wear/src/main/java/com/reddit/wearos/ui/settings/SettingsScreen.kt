package com.reddit.wearos.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.rotaryinput.rotaryWithScroll
import com.reddit.wearos.auth.TokenStorage
import com.reddit.wearos.data.RedditRepository
import com.reddit.wearos.ui.theme.RedditOrange
import com.reddit.wearos.ui.theme.TextPrimary
import com.reddit.wearos.ui.theme.TextSecondary
import com.reddit.wearos.util.RemotePhoneHelper
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun SettingsScreen(
    tokenStorage: TokenStorage,
    repository: RedditRepository,
    onLogout: () -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScalingLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isLoggedIn by tokenStorage.isLoggedIn.collectAsState()
    var username by remember { mutableStateOf(tokenStorage.getUsername()) }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn && username.isNullOrEmpty()) {
            scope.launch {
                try {
                    val me = repository.getMe()
                    if (!me?.name.isNullOrEmpty()) {
                        username = me?.name
                        tokenStorage.setUsername(me?.name)
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = scrollState) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .rotaryWithScroll(scrollState),
            state = scrollState,
            contentPadding = PaddingValues(top = 28.dp, bottom = 40.dp, start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = "Ajustes de Reddit",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = RedditOrange
                )
            }

            // Estado de la cuenta
            item {
                Card(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val currentName = username ?: tokenStorage.getUsername()
                        val displayText = if (isLoggedIn) {
                            if (!currentName.isNullOrEmpty()) "👤 Conectado como\nu/$currentName" else "👤 Conectado (Sesión activa)"
                        } else {
                            "👤 Modo Invitado\n(Sin cuenta vinculada)"
                        }

                        Text(
                            text = displayText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Botón Cerrar Sesión / Vincular
            item {
                if (isLoggedIn) {
                    Chip(
                        onClick = {
                            tokenStorage.clearTokens()
                            onLogout()
                        },
                        label = { Text("🚪 Cerrar Sesión", fontSize = 11.sp) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Chip(
                        onClick = {
                            RemotePhoneHelper.openPhoneCompanion(context)
                            onLogout()
                        },
                        label = { Text("📱 Abrir Login en Móvil", fontSize = 11.sp) },
                        colors = ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Info de versión
            item {
                Text(
                    text = "Reddit for WearOS v1.0.0\nGalaxy Watch Ultra",
                    fontSize = 9.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}
