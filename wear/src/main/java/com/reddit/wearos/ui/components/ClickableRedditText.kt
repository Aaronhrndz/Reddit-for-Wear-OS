package com.reddit.wearos.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert
import androidx.wear.compose.material.dialog.Dialog
import com.reddit.wearos.ui.theme.DarkSurface
import com.reddit.wearos.ui.theme.RedditOrange
import com.reddit.wearos.ui.theme.TextPrimary
import com.reddit.wearos.ui.theme.TextSecondary
import java.util.regex.Pattern

private val MARKDOWN_LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\((https?://[^\\s\\)]+)\\)")
private val RAW_URL_PATTERN = Pattern.compile("(https?://[a-zA-Z0-9./_~:=?#&%+-]+)")

@Composable
fun ClickableRedditText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 11.sp,
    color: Color = TextPrimary,
    lineHeight: TextUnit = 14.sp
) {
    val context = LocalContext.current
    var urlToOpen by remember { mutableStateOf<String?>(null) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val annotatedString = remember(text) {
        buildAnnotatedRedditText(text)
    }

    Text(
        text = annotatedString,
        fontSize = fontSize,
        color = color,
        lineHeight = lineHeight,
        onTextLayout = { textLayoutResult = it },
        modifier = modifier.pointerInput(annotatedString) {
            detectTapGestures { offsetPosition ->
                val layout = textLayoutResult ?: return@detectTapGestures
                val offset = layout.getOffsetForPosition(offsetPosition)
                val annotations = annotatedString.getStringAnnotations(
                    tag = "URL",
                    start = offset,
                    end = offset
                )
                if (annotations.isNotEmpty()) {
                    urlToOpen = annotations.first().item
                }
            }
        }
    )

    // Diálogo de Confirmación para Abrir en Samsung Internet
    val targetUrl = urlToOpen
    if (!targetUrl.isNullOrEmpty()) {
        Dialog(
            showDialog = true,
            onDismissRequest = { urlToOpen = null }
        ) {
            Alert(
                icon = {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = null,
                        tint = RedditOrange,
                        modifier = Modifier.size(24.dp)
                    )
                },
                title = {
                    Text(
                        text = "¿Abrir enlace en Samsung Internet?",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                },
                content = {
                    Text(
                        text = targetUrl,
                        fontSize = 9.sp,
                        color = TextSecondary,
                        maxLines = 2,
                        textAlign = TextAlign.Center
                    )
                },
                positiveButton = {
                    Button(
                        onClick = {
                            openInSamsungInternet(context, targetUrl)
                            urlToOpen = null
                        },
                        colors = ButtonDefaults.primaryButtonColors(backgroundColor = RedditOrange),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Abrir",
                            tint = Color.White
                        )
                    }
                },
                negativeButton = {
                    Button(
                        onClick = { urlToOpen = null },
                        colors = ButtonDefaults.secondaryButtonColors(),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancelar",
                            tint = Color.White
                        )
                    }
                }
            )
        }
    }
}

fun buildAnnotatedRedditText(rawText: String): AnnotatedString {
    val builder = AnnotatedString.Builder()
    
    // Primero procesamos markdown links [title](url)
    val mdMatcher = MARKDOWN_LINK_PATTERN.matcher(rawText)
    var lastIndex = 0

    while (mdMatcher.find()) {
        val start = mdMatcher.start()
        val end = mdMatcher.end()
        val linkTitle = mdMatcher.group(1) ?: ""
        val linkUrl = mdMatcher.group(2) ?: ""

        // Texto antes del link procesado con raw URLs
        if (start > lastIndex) {
            appendWithRawUrls(builder, rawText.substring(lastIndex, start))
        }

        // Link Markdown
        val linkStart = builder.length
        builder.append(linkTitle)
        builder.addStyle(
            SpanStyle(
                color = RedditOrange,
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.SemiBold
            ),
            start = linkStart,
            end = builder.length
        )
        builder.addStringAnnotation(
            tag = "URL",
            annotation = linkUrl,
            start = linkStart,
            end = builder.length
        )

        lastIndex = end
    }

    if (lastIndex < rawText.length) {
        appendWithRawUrls(builder, rawText.substring(lastIndex))
    }

    return builder.toAnnotatedString()
}

private fun appendWithRawUrls(builder: AnnotatedString.Builder, textChunk: String) {
    val rawMatcher = RAW_URL_PATTERN.matcher(textChunk)
    var lastIdx = 0

    while (rawMatcher.find()) {
        val start = rawMatcher.start()
        val end = rawMatcher.end()
        val url = rawMatcher.group(1) ?: ""

        if (start > lastIdx) {
            builder.append(textChunk.substring(lastIdx, start))
        }

        val linkStart = builder.length
        builder.append(url)
        builder.addStyle(
            SpanStyle(
                color = RedditOrange,
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.SemiBold
            ),
            start = linkStart,
            end = builder.length
        )
        builder.addStringAnnotation(
            tag = "URL",
            annotation = url,
            start = linkStart,
            end = builder.length
        )

        lastIdx = end
    }

    if (lastIdx < textChunk.length) {
        builder.append(textChunk.substring(lastIdx))
    }
}

fun openInSamsungInternet(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        setPackage("com.sec.android.app.sbrowser")
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // Fallback a cualquier navegador disponible
        val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(fallbackIntent)
        } catch (_: Exception) {}
    }
}
