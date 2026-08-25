package com.reddit.wearos.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    onOpenInbox: () -> Unit,
    onOpenSaved: () -> Unit,
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = if (isLoggedIn) RedditOrange else TextSecondary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            val currentName = username ?: tokenStorage.getUsername()
                            val displayText = if (isLoggedIn) {
                                if (!currentName.isNullOrEmpty()) "u/$currentName" else "Sesión activa"
                            } else {
                                "Modo Invitado"
                            }
                            Text(
                                text = displayText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (isLoggedIn) "Cuenta vinculada" else "Sin cuenta vinculada",
                                fontSize = 9.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // Bandeja de Mensajes
            if (isLoggedIn) {
                item {
                    Chip(
                        onClick = onOpenInbox,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Mail,
                                contentDescription = null,
                                tint = RedditOrange,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Mensajes", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        secondaryLabel = { Text("Bandeja de entrada", fontSize = 9.sp) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Chip(
                        onClick = onOpenSaved,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = null,
                                tint = RedditOrange,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Guardados", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        secondaryLabel = { Text("Posts y comentarios", fontSize = 9.sp) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
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
                        icon = {
                            Icon(
                                imageVector = Icons.Default.ExitToApp,
                                contentDescription = null,
                                tint = Color.Red,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Cerrar Sesión", fontSize = 11.sp) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Chip(
                        onClick = {
                            RemotePhoneHelper.openPhoneCompanion(context)
                            onLogout()
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Abrir Login en Móvil", fontSize = 11.sp) },
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
