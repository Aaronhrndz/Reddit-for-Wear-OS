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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reddit.wearos.R
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
import com.reddit.wearos.util.GitHubReleaseInfo
import com.reddit.wearos.util.RemotePhoneHelper
import com.reddit.wearos.util.UpdateChecker
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

    val currentVer = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.6.0"
        } catch (_: Exception) {
            "1.6.0"
        }
    }
    var releaseInfo by remember { mutableStateOf<GitHubReleaseInfo?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateStatusText by remember { mutableStateOf("Comprobar en GitHub") }
    var isBetaChannel by remember { mutableStateOf(UpdateChecker.isBetaChannel(context)) }
    var tapCount by remember { mutableStateOf(0) }
    var showDevDialog by remember { mutableStateOf(false) }

    val checkUpdateAction = {
        scope.launch {
            isCheckingUpdate = true
            updateStatusText = "Buscando en GitHub…"
            val checked = UpdateChecker.checkLatestRelease(currentVer, isBeta = isBetaChannel)
            isCheckingUpdate = false
            releaseInfo = checked
            updateStatusText = when {
                checked == null -> "Sin conexión a GitHub"
                checked.isUpdateAvailable -> "⚡ Toca para instalar ${checked.latestTag}"
                else -> "✅ Estás en la última (v$currentVer${if (isBetaChannel) " Beta" else ""})"
            }
        }
    }

    LaunchedEffect(Unit) {
        isCheckingUpdate = true
        val info = UpdateChecker.checkLatestRelease(currentVer, isBeta = isBetaChannel)
        isCheckingUpdate = false
        releaseInfo = info
        updateStatusText = when {
            info == null -> "Toca para comprobar"
            info.isUpdateAvailable -> "⚡ Nueva ${info.latestTag} disponible"
            else -> "✅ Al día (v$currentVer${if (isBetaChannel) " Beta" else ""})"
        }
    }

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
                        label = { Text(stringResource(R.string.settings_messages), fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        secondaryLabel = { Text(stringResource(R.string.settings_inbox), fontSize = 9.sp) },
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
                        label = { Text(stringResource(R.string.settings_saved), fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        secondaryLabel = { Text(stringResource(R.string.settings_saved_sub), fontSize = 9.sp) },
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
                        label = { Text(stringResource(R.string.logout), fontSize = 11.sp) },
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
                        label = { Text(stringResource(R.string.settings_open_mobile), fontSize = 11.sp) },
                        colors = ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Botón de Actualizaciones OTA (GitHub Releases)
            item {
                val hasNew = releaseInfo?.isUpdateAvailable == true
                Chip(
                    onClick = {
                        val info = releaseInfo
                        if (info != null && info.isUpdateAvailable && !isCheckingUpdate) {
                            val targetUrl = info.wearApkUrl
                            if (!targetUrl.isNullOrEmpty()) {
                                scope.launch {
                                    isCheckingUpdate = true
                                    com.reddit.wearos.util.ApkSelfInstaller.downloadAndInstallFromUrl(
                                        context = context,
                                        apkUrl = targetUrl,
                                        onStatus = { status -> updateStatusText = status }
                                    )
                                    isCheckingUpdate = false
                                }
                            } else {
                                RemotePhoneHelper.openUrlOnPhone(context, info.releaseHtmlUrl)
                            }
                        } else if (!isCheckingUpdate) {
                            checkUpdateAction()
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = if (hasNew) Color.White else RedditOrange,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = if (hasNew) "Instalar ${releaseInfo?.latestTag}" else "Actualizaciones",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = updateStatusText,
                            fontSize = 9.sp,
                            color = if (hasNew) Color.White.copy(alpha = 0.9f) else TextSecondary
                        )
                    },
                    colors = if (hasNew) {
                        ChipDefaults.primaryChipColors(backgroundColor = RedditOrange)
                    } else {
                        ChipDefaults.secondaryChipColors()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Opciones de desarrollador desbloqueadas
            if (isBetaChannel || showDevDialog) {
                item {
                    ToggleChip(
                        checked = isBetaChannel,
                        onCheckedChange = { next ->
                            isBetaChannel = next
                            UpdateChecker.setBetaChannel(context, next)
                            checkUpdateAction()
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.dev_beta_title),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        },
                        secondaryLabel = {
                            Text(
                                text = stringResource(R.string.dev_beta_desc),
                                fontSize = 9.sp,
                                color = if (isBetaChannel) RedditOrange else TextSecondary
                            )
                        },
                        toggleControl = {
                            Switch(
                                checked = isBetaChannel,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = RedditOrange,
                                    checkedTrackColor = RedditOrange.copy(alpha = 0.5f)
                                )
                            )
                        },
                        colors = ToggleChipDefaults.toggleChipColors(
                            checkedStartBackgroundColor = Color(0xFF26262B),
                            checkedEndBackgroundColor = Color(0xFF26262B),
                            uncheckedStartBackgroundColor = Color(0xFF1C1C20),
                            uncheckedEndBackgroundColor = Color(0xFF1C1C20)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Info de versión (7-tap Easter Egg)
            item {
                val deviceName = "${android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${android.os.Build.MODEL}"
                val isVerBeta = currentVer.contains("beta", ignoreCase = true)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 16.dp)
                        .clickable {
                            tapCount++
                            if (tapCount >= 7) {
                                tapCount = 0
                                showDevDialog = true
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.dev_unlocked),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else if (tapCount >= 3) {
                                val remaining = 7 - tapCount
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.dev_steps_remaining, remaining),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${stringResource(R.string.settings_version_title)} v$currentVer",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isVerBeta || isBetaChannel) RedditOrange else Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = deviceName,
                        fontSize = 10.sp,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}
