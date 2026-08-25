package com.reddit.wearos.util

import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent

object WearInputHelper {
    const val EXTRA_KEY = "input_text"

    fun createInputIntent(prompt: String): Intent {
        val remoteInput = RemoteInput.Builder(EXTRA_KEY)
            .setLabel(prompt)
            .build()

        return Intent("android.wearable.action.REMOTE_INPUT").apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            putParcelableArrayListExtra("android.wearable.extra.REMOTE_INPUTS", arrayListOf(remoteInput))
        }
    }

    fun createSpeechFallbackIntent(prompt: String): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
    }

    fun extractText(data: Intent?): String? {
        if (data == null) return null
        try {
            val bundle: Bundle? = RemoteInput.getResultsFromIntent(data)
            val fromRemoteInput = bundle?.getCharSequence(EXTRA_KEY)?.toString()?.trim()
            if (!fromRemoteInput.isNullOrBlank()) return fromRemoteInput
        } catch (_: Exception) {}

        try {
            val fromSpeech = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
            if (!fromSpeech.isNullOrBlank()) return fromSpeech
        } catch (_: Exception) {}

        return null
    }
}
