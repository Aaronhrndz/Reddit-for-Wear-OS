package com.reddit.wearos.ui.components

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import com.reddit.wearos.ui.theme.RedditOrange
import kotlinx.coroutines.delay

@Composable
fun WearVideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier,
    audioUrl: String? = null,
    posterUrl: String? = null,
    autoPlay: Boolean = false
) {
    val context = LocalContext.current
    var hasStartedPlayback by remember { mutableStateOf(autoPlay) }
    var isPlaying by remember { mutableStateOf(autoPlay) }
    var isBuffering by remember { mutableStateOf(autoPlay) }
    var hasError by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(true) }

    var currentPositionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }

    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }

    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var audioPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var surfaceRef by remember { mutableStateOf<Surface?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    val cleanUrl = remember(videoUrl) {
        videoUrl.replace("&amp;", "&")
    }

    // Ticker para progreso de reproducción
    LaunchedEffect(isPlaying, isBuffering, hasError, hasStartedPlayback) {
        while (hasStartedPlayback && isPlaying && !isBuffering && !hasError) {
            try {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        currentPositionMs = mp.currentPosition
                        durationMs = mp.duration.coerceAtLeast(0)
                    }
                }
            } catch (_: Exception) {}
            delay(250)
        }
    }

    // Limpieza al desmontar o reintentar
    DisposableEffect(retryTrigger) {
        onDispose {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.reset()
                mediaPlayer?.release()
            } catch (_: Exception) {}
            try {
                audioPlayer?.stop()
                audioPlayer?.reset()
                audioPlayer?.release()
            } catch (_: Exception) {}
            mediaPlayer = null
            audioPlayer = null
            surfaceRef?.release()
            surfaceRef = null
            textureViewRef = null
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black),
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
                    .background(Color(0x33000000))
                    .clickable {
                        hasStartedPlayback = true
                        isPlaying = true
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
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        } else if (!hasError) {
            AndroidView(
                factory = { ctx ->
                    val tv = TextureView(ctx)
                    tv.layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    textureViewRef = tv

                    tv.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                        if (videoWidth > 0 && videoHeight > 0) {
                            applyVideoAspectRatio(view as TextureView, videoWidth, videoHeight)
                        }
                    }

                    tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                            val s = Surface(st)
                            surfaceRef = s

                            try {
                                val mp = MediaPlayer().apply {
                                    setSurface(s)
                                    setAudioAttributes(
                                        AudioAttributes.Builder()
                                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                                            .setUsage(AudioAttributes.USAGE_MEDIA)
                                            .build()
                                    )
                                    isLooping = true

                                    val headers = mapOf("User-Agent" to "android:com.reddit.wearos:v1.0.0 (WearOS client)")
                                    setDataSource(ctx, Uri.parse(cleanUrl), headers)

                                    setOnVideoSizeChangedListener { _, w, h ->
                                        if (w > 0 && h > 0) {
                                            videoWidth = w
                                            videoHeight = h
                                            tv.post {
                                                applyVideoAspectRatio(tv, w, h)
                                            }
                                        }
                                    }

                                    setOnPreparedListener { player ->
                                        isBuffering = false
                                        durationMs = player.duration.coerceAtLeast(0)
                                        if (player.videoWidth > 0 && player.videoHeight > 0) {
                                            videoWidth = player.videoWidth
                                            videoHeight = player.videoHeight
                                            tv.post {
                                                applyVideoAspectRatio(tv, player.videoWidth, player.videoHeight)
                                            }
                                        }
                                        val vol = if (isMuted) 0f else 1f
                                        player.setVolume(vol, vol)
                                        if (isPlaying) {
                                            player.start()
                                        }
                                    }

                                    setOnInfoListener { _, what, _ ->
                                        when (what) {
                                            MediaPlayer.MEDIA_INFO_BUFFERING_START -> isBuffering = true
                                            MediaPlayer.MEDIA_INFO_BUFFERING_END -> isBuffering = false
                                            MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> isBuffering = false
                                        }
                                        true
                                    }

                                    setOnErrorListener { _, _, _ ->
                                        hasError = true
                                        isBuffering = false
                                        true
                                    }
                                    prepareAsync()
                                }
                                mediaPlayer = mp

                                if (!audioUrl.isNullOrEmpty()) {
                                    try {
                                        val ap = MediaPlayer().apply {
                                            setAudioAttributes(
                                                AudioAttributes.Builder()
                                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                                    .build()
                                            )
                                            isLooping = true
                                            setDataSource(ctx, Uri.parse(audioUrl), mapOf("User-Agent" to "android:com.reddit.wearos:v1.0.0 (WearOS client)"))
                                            val vol = if (isMuted) 0f else 1f
                                            setVolume(vol, vol)
                                            setOnPreparedListener {
                                                val vVol = if (isMuted) 0f else 1f
                                                it.setVolume(vVol, vVol)
                                                if (isPlaying) {
                                                    it.start()
                                                }
                                            }
                                            prepareAsync()
                                        }
                                        audioPlayer = ap
                                    } catch (_: Exception) {}
                                }
                            } catch (e: Exception) {
                                hasError = true
                                isBuffering = false
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
                            if (videoWidth > 0 && videoHeight > 0) {
                                applyVideoAspectRatio(tv, videoWidth, videoHeight)
                            }
                        }

                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            try {
                                mediaPlayer?.setSurface(null)
                            } catch (_: Exception) {}
                            return true
                        }

                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                    tv
                },
                update = { tv ->
                    if (videoWidth > 0 && videoHeight > 0) {
                        tv.post {
                            applyVideoAspectRatio(tv, videoWidth, videoHeight)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isPlaying) {
                        detectTapGestures {
                            try {
                                if (isPlaying) {
                                    mediaPlayer?.pause()
                                    audioPlayer?.pause()
                                    isPlaying = false
                                } else {
                                    mediaPlayer?.start()
                                    audioPlayer?.start()
                                    isPlaying = true
                                }
                            } catch (_: Exception) {}
                        }
                    }
            )

            // Indicador de Buffering
            if (isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    indicatorColor = RedditOrange,
                    strokeWidth = 2.5.dp
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
                            try {
                                mediaPlayer?.start()
                                audioPlayer?.start()
                                isPlaying = true
                            } catch (_: Exception) {}
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

            // Botón de Mute / Sonido
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000))
                    .clickable {
                        isMuted = !isMuted
                        val vol = if (isMuted) 0f else 1f
                        mediaPlayer?.let { mp ->
                            try {
                                mp.setVolume(vol, vol)
                            } catch (_: Exception) {}
                        }
                        audioPlayer?.let { ap ->
                            try {
                                ap.setVolume(vol, vol)
                                if (!isMuted && isPlaying && !ap.isPlaying) {
                                    ap.seekTo(currentPositionMs)
                                    ap.start()
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    .padding(5.dp)
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (isMuted) "Silenciado" else "Sonido activado",
                    tint = Color.White,
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
                        .background(Color(0xAA000000))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .pointerInput(durationMs) {
                                detectTapGestures { offset ->
                                    val newFraction = (offset.x / size.width).coerceIn(0f, 1f)
                                    val targetMs = (newFraction * durationMs).toInt()
                                    try {
                                        mediaPlayer?.seekTo(targetMs)
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
                                .background(Color(0x55FFFFFF))
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
            // Vista de Error amigable con botón de reintentar
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .clickable {
                        hasError = false
                        isBuffering = true
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
                Text("Reintentar video", fontSize = 10.sp, color = Color.White)
            }
        }
    }
}

/**
 * Escala TextureView manteniendo la relación de aspecto original del vídeo (Fit Center).
 * Evita cualquier estiramiento o deformación vertical/horizontal.
 */
private fun applyVideoAspectRatio(textureView: TextureView, videoWidth: Int, videoHeight: Int) {
    if (videoWidth <= 0 || videoHeight <= 0) return
    val viewWidth = textureView.width.toFloat()
    val viewHeight = textureView.height.toFloat()
    if (viewWidth <= 0f || viewHeight <= 0f) return

    val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()
    val viewRatio = viewWidth / viewHeight

    var scaleX = 1.0f
    var scaleY = 1.0f

    if (videoRatio > viewRatio) {
        // Vídeo panorámico/horizontal (más ancho que la vista) -> ajustar al ancho, letterbox arriba/abajo
        scaleY = viewRatio / videoRatio
    } else {
        // Vídeo vertical/alto (más alto que la vista) -> ajustar a la altura, pillarbox izquierda/derecha
        scaleX = videoRatio / viewRatio
    }

    val matrix = Matrix()
    matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
    textureView.setTransform(matrix)
}

private fun formatTime(ms: Int): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return String.format("%d:%02d", minutes, seconds)
}
