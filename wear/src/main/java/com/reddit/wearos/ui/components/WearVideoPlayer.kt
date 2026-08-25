package com.reddit.wearos.ui.components

import android.media.MediaPlayer
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.reddit.wearos.ui.theme.RedditOrange
import kotlinx.coroutines.delay

@Composable
fun WearVideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true
) {
    var isPlaying by remember { mutableStateOf(autoPlay) }
    var isBuffering by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(true) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }

    var currentPositionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }

    // Ticker para actualizar la barra de progreso / línea de tiempo
    LaunchedEffect(isPlaying, isBuffering) {
        while (isPlaying && !isBuffering && !hasError) {
            videoViewRef?.let { vv ->
                currentPositionMs = vv.currentPosition
                durationMs = vv.duration.coerceAtLeast(0)
            }
            delay(250)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (!hasError) {
            AndroidView(
                factory = { context ->
                    VideoView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setVideoURI(Uri.parse(videoUrl))
                        setOnPreparedListener { mp ->
                            mediaPlayerRef = mp
                            mp.isLooping = true
                            durationMs = mp.duration.coerceAtLeast(0)
                            if (isMuted) {
                                mp.setVolume(0f, 0f)
                            }
                            isBuffering = false
                            if (isPlaying) {
                                start()
                            }
                        }
                        setOnErrorListener { _, _, _ ->
                            hasError = true
                            isBuffering = false
                            true
                        }
                        videoViewRef = this
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        videoViewRef?.let { vv ->
                            if (vv.isPlaying) {
                                vv.pause()
                                isPlaying = false
                            } else {
                                vv.start()
                                isPlaying = true
                            }
                        }
                    }
            )

            // Indicador de Carga / Buffering
            if (isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    indicatorColor = RedditOrange
                )
            }

            // Overlay de Play grande al pausar
            if (!isPlaying && !isBuffering) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC000000))
                        .clickable {
                            videoViewRef?.start()
                            isPlaying = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Reproducir",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Botón de Mute / Sonido con Icono Material
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000))
                    .clickable {
                        isMuted = !isMuted
                        mediaPlayerRef?.let { mp ->
                            val vol = if (isMuted) 0f else 1f
                            mp.setVolume(vol, vol)
                        }
                    }
                    .padding(5.dp)
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = if (isMuted) "Silenciado" else "Sonido activado",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }

            // LÍNEA DE TIEMPO / BARRA DE PROGRESO INFERIOR (Interactiva con Seek)
            if (!isBuffering && durationMs > 0) {
                val progress = (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0x99000000))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    // Barra táctil para adelantar / retroceder vídeo
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .pointerInput(durationMs) {
                                detectTapGestures { offset ->
                                    val newFraction = (offset.x / size.width).coerceIn(0f, 1f)
                                    val targetMs = (newFraction * durationMs).toInt()
                                    videoViewRef?.seekTo(targetMs)
                                    currentPositionMs = targetMs
                                }
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // Pista de fondo
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0x55FFFFFF))
                        )
                        // Progreso activo naranja
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(RedditOrange)
                        )
                    }

                    // Etiquetas de Tiempo (ej: 0:12 / 0:45)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(currentPositionMs),
                            fontSize = 8.sp,
                            color = Color.White
                        )
                        Text(
                            text = formatTime(durationMs),
                            fontSize = 8.sp,
                            color = Color(0xFFAAAAAA)
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = Color.Red,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text("Error de video", fontSize = 10.sp, color = Color.Red)
            }
        }
    }
}

private fun formatTime(ms: Int): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return String.format("%d:%02d", minutes, seconds)
}
