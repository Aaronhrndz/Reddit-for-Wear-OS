package com.reddit.wearos.ui.components

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.reddit.wearos.LocalIsAmbient
import com.reddit.wearos.ui.theme.RedditOrange
import kotlinx.coroutines.delay

@Composable
fun WearVideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier,
    fallbackUrl: String? = null,
    posterUrl: String? = null,
    autoPlay: Boolean = false
) {
    var hasStartedPlayback by remember { mutableStateOf(autoPlay) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(true) }

    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    var playerRef by remember { mutableStateOf<ExoPlayer?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }
    var urlIndex by remember { mutableIntStateOf(0) }

    val isAmbient = LocalIsAmbient.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val candidates = remember(videoUrl, fallbackUrl) {
        listOf(videoUrl.replace("&amp;", "&"), fallbackUrl?.replace("&amp;", "&"))
            .filterNotNull()
            .distinct()
    }

    // Pausar vídeo inmediatamente al entrar en Always-On Display (AOD) para no drenar batería
    LaunchedEffect(isAmbient) {
        if (isAmbient) {
            try {
                playerRef?.playWhenReady = false
            } catch (_: Exception) {}
        }
    }

    // Pausar vídeo cuando la actividad pasa a segundo plano (ON_PAUSE / ON_STOP)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                try {
                    playerRef?.playWhenReady = false
                } catch (_: Exception) {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Limpieza al desmontar o al reintentar / cambiar de URL candidata
    DisposableEffect(retryTrigger, urlIndex) {
        onDispose {
            try {
                playerRef?.release()
            } catch (_: Exception) {}
            playerRef = null
        }
    }

    // Aplicar mute/unmute
    LaunchedEffect(isMuted) {
        playerRef?.volume = if (isMuted) 0f else 1f
    }

    // Ticker para progreso de reproducción (se detiene en AOD o pausa)
    LaunchedEffect(isPlaying, hasStartedPlayback, hasError, isAmbient) {
        while (hasStartedPlayback && isPlaying && !hasError && !isAmbient) {
            try {
                playerRef?.let { p ->
                    currentPositionMs = p.currentPosition.coerceAtLeast(0)
                    durationMs = p.duration.coerceAtLeast(0)
                }
            } catch (_: Exception) {}
            delay(250)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeColor.Black),
        contentAlignment = Alignment.Center
    ) {
        if (!hasStartedPlayback) {
            // Estado Inicial: Poster / Thumbnail con Botón Play Grande en el centro (NO AUTOPLAY)
            if (!posterUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ComposeColor(0x33000000))
                    .clickable {
                        hasStartedPlayback = true
                        isBuffering = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(RedditOrange),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Reproducir Video",
                        tint = ComposeColor.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        } else if (!hasError) {
            // key(): fuerza recreación completa del PlayerView/ExoPlayer en retry o cambio de URL
            key(retryTrigger, urlIndex) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShutterBackgroundColor(Color.BLACK)

                            val exo = ExoPlayer.Builder(ctx).build().apply {
                                setAudioAttributes(
                                    androidx.media3.common.AudioAttributes.Builder()
                                        .setUsage(C.USAGE_MEDIA)
                                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                                        .build(),
                                    true
                                )
                                volume = if (isMuted) 0f else 1f
                                repeatMode = Player.REPEAT_MODE_ONE
                                addListener(object : Player.Listener {
                                    override fun onPlaybackStateChanged(playbackState: Int) {
                                        if (playbackState == Player.STATE_READY) {
                                            isBuffering = false
                                            durationMs = duration.coerceAtLeast(0)
                                        } else if (playbackState == Player.STATE_BUFFERING) {
                                            isBuffering = true
                                        } else if (playbackState == Player.STATE_IDLE) {
                                            isBuffering = false
                                            isPlaying = false
                                        }
                                    }

                                    override fun onIsPlayingChanged(isNowPlaying: Boolean) {
                                        isPlaying = isNowPlaying
                                    }

                                    override fun onPlayerError(error: PlaybackException) {
                                        if (urlIndex < candidates.lastIndex) {
                                            // Probar siguiente URL candidata (HLS -> MP4 fallback)
                                            urlIndex++
                                        } else {
                                            isBuffering = false
                                            isPlaying = false
                                            hasError = true
                                        }
                                    }
                                })
                                setMediaItem(MediaItem.fromUri(candidates[urlIndex]))
                                playWhenReady = true
                                prepare()
                            }
                            player = exo
                            playerRef = exo
                        }
                    },
                    update = { view ->
                        view.player?.volume = if (isMuted) 0f else 1f
                        view.keepScreenOn = isPlaying && !isAmbient
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(hasStartedPlayback) {
                            detectTapGestures {
                                try {
                                    playerRef?.let { p ->
                                        p.playWhenReady = !p.playWhenReady
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                )
            }

            // Indicador de Buffering
            if (isBuffering && !hasError) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    indicatorColor = RedditOrange,
                    strokeWidth = 2.5.dp
                )
            }

            // Overlay de Play grande al pausar
            if (!isPlaying && !isBuffering && !hasError) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(ComposeColor(0xCC000000))
                        .clickable {
                            try {
                                playerRef?.playWhenReady = true
                            } catch (_: Exception) {}
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Reproducir",
                        tint = ComposeColor.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Botón de Mute / Sonido
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(ComposeColor(0x99000000))
                    .clickable { isMuted = !isMuted }
                    .padding(5.dp)
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (isMuted) "Silenciado" else "Sonido activado",
                    tint = ComposeColor.White,
                    modifier = Modifier.size(14.dp)
                )
            }

            // Barra de progreso y Seek
            if (!isBuffering && durationMs > 0) {
                val progress = (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(ComposeColor(0xAA000000))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .pointerInput(durationMs) {
                                detectTapGestures { offset ->
                                    val newFraction = (offset.x / size.width).coerceIn(0f, 1f)
                                    val targetMs = (newFraction * durationMs).toLong()
                                    try {
                                        playerRef?.seekTo(targetMs)
                                        currentPositionMs = targetMs
                                    } catch (_: Exception) {}
                                }
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(ComposeColor(0x55FFFFFF))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(RedditOrange)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(currentPositionMs),
                            fontSize = 8.sp,
                            color = ComposeColor.White
                        )
                        Text(
                            text = formatTime(durationMs),
                            fontSize = 8.sp,
                            color = ComposeColor(0xFFAAAAAA)
                        )
                    }
                }
            }
        } else {
            // Vista de Error amigable con botón de reintentar
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .clickable {
                        hasError = false
                        isBuffering = true
                        urlIndex = 0
                        retryTrigger++
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reintentar",
                    tint = RedditOrange,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text("Reintentar video", fontSize = 10.sp, color = ComposeColor.White)
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0L).toInt()
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return String.format("%d:%02d", minutes, seconds)
}
