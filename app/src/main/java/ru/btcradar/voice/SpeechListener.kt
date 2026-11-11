package ru.btcradar.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Обёртка над системным распознавателем речи (press-to-talk).
 *
 * Пытаемся работать офлайн (EXTRA_PREFER_OFFLINE), но это зависит от того,
 * скачан ли на устройстве офлайн-пакет русского распознавания. Если нет —
 * система может уйти в облако. Полностью офлайновый ASR без облака требует
 * отдельного движка (например, Vosk) с моделью ~50 МБ — это можно подключить
 * отдельно, если принципиально.
 */
class SpeechListener(private val ctx: Context) {

    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(ctx)

    fun start(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onReady: () -> Unit = {}
    ) {
        if (listening) return
        if (!isAvailable()) { onError("Распознавание речи недоступно на устройстве"); return }

        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        recognizer = r

        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { onReady() }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                listening = false
                onError(errorText(error))
            }

            override fun onResults(results: Bundle?) {
                listening = false
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (text.isBlank()) onError("Ничего не расслышал") else onResult(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        }
        listening = true
        runCatching { r.startListening(intent) }
            .onFailure { listening = false; onError(it.message ?: "Ошибка запуска") }
    }

    fun stop() {
        listening = false
        runCatching { recognizer?.stopListening() }
    }

    fun destroy() {
        listening = false
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun errorText(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Проблема с микрофоном"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет разрешения на микрофон"
        SpeechRecognizer.ERROR_NETWORK -> "Сеть недоступна (нет офлайн-модели?)"
        SpeechRecognizer.ERROR_NO_MATCH -> "Не разобрал команду"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Тишина — не услышал речь"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознаватель занят"
        else -> "Ошибка распознавания ($code)"
    }
}
