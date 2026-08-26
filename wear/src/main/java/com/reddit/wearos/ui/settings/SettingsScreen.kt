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

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun SettingsScreen(
    tokenStorage: TokenStorage,
    repository: RedditRepository,
    onOpenInbox: () -> Unit,
    onOpenSaved: () -> Unit,
    onOpenProfile: () -> Unit = {},
    onLogout: () -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScalingLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isLoggedIn by tokenStorage.isLoggedIn.collectAsState()
    var username by remember { mutableStateOf(tokenStorage.getUsername()) }
    var userAvatarUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            scope.launch {
                try {
                    val me = repository.getMe()
                    if (me != null) {
                        if (!me.name.isNullOrEmpty()) {
                            username = me.name
                            tokenStorage.setUsername(me.name)
                        }
                        if (!me.iconImg.isNullOrEmpty()) {
                            userAvatarUrl = me.iconImg.substringBefore("?").replace("&amp;", "&")
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = scrollState) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .rotaryWithScroll(scrollState),
            state = scrollState,
            autoCentering = null,
            contentPadding = PaddingValues(top = 26.dp, bottom = 20.dp, start = 10.dp, end = 10.dp),
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

            // Estado de la cuenta (Pulsar para abrir Perfil)
            item {
                val currentName = username ?: tokenStorage.getUsername()
                val displayText = if (isLoggedIn) {
                    if (!currentName.isNullOrEmpty()) "u/$currentName" else "Sesión activa"
                } else {
                    "Modo Invitado"
                }
                Chip(
                    onClick = { if (isLoggedIn) onOpenProfile() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.primaryChipColors(
                        backgroundColor = if (isLoggedIn) RedditOrange else Color(0xFF24242E),
                        contentColor = Color.White
                    ),
                    icon = {
                        if (!userAvatarUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = userAvatarUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                            )
                        } else if (!currentName.isNullOrEmpty()) {
                            val initial = currentName.firstOrNull()?.uppercase() ?: "?"
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initial,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    label = {
                        Text(
                            text = displayText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = if (isLoggedIn) "Ver perfil e historial ➔" else "Sin cuenta vinculada",
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                )
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
                val deviceName = "${android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${android.os.Build.MODEL}"
                Text(
                    text = "Reddit for Wear OS v1.1.0\n$deviceName",
                    fontSize = 9.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}
