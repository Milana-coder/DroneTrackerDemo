
package com.example.dronetrackerdemo

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceAlertManager(context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS

            if (ready) {
                val result = tts?.setLanguage(
                    Locale("ru", "RU")
                )

                ready = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }

    fun speak(message: String) {
        if (ready) {
            tts?.speak(
                message,
                TextToSpeech.QUEUE_ADD,
                null,
                "threat_warning"
            )
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
