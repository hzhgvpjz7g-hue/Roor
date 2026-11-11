package ru.btcradar.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import kotlinx.serialization.Serializable
import java.util.Locale

enum class VoiceGender { ANY, MALE, FEMALE }

@Serializable
data class AssistantSettings(
    val enabled: Boolean = true,
    val name: String = "Радар",
    val gender: String = VoiceGender.ANY.name,
    /** Проговаривать срабатывания вслух. */
    val speakAlerts: Boolean = true,
    /** 0.5..2.0 */
    val rate: Float = 1.0f,
    val pitch: Float = 1.0f,
    /** Имя системного голоса, если пользователь выбрал конкретный. */
    val voiceName: String = "",
    /** Требовать совпадение голоса владельца перед выполнением команд. */
    val voiceLock: Boolean = false
) {
    fun genderEnum(): VoiceGender =
        runCatching { VoiceGender.valueOf(gender) }.getOrDefault(VoiceGender.ANY)
}

/**
 * Обёртка над системным TTS. Это «говорящая» половина ассистента —
 * она готова и работает уже сейчас. Распознавание команд и замок по голосу
 * подключаются отдельным модулем (следующий шаг).
 *
 * Работает полностью на устройстве, если установлен офлайн-движок TTS
 * (у большинства прошивок он есть из коробки для русского).
 */
class Tts(private val appContext: Context) {

    private var engine: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null
    private var settings: AssistantSettings = AssistantSettings()

    fun init(onReady: (Boolean) -> Unit = {}) {
        if (engine != null) { onReady(ready); return }
        engine = TextToSpeech(appContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                engine?.language = Locale("ru", "RU")
                applyVoice()
                pending?.let { speak(it); pending = null }
            }
            onReady(ready)
        }
    }

    fun apply(s: AssistantSettings) {
        settings = s
        if (ready) applyVoice()
    }

    /** Доступные русские голоса — чтобы показать пользователю список для выбора. */
    fun russianVoices(): List<Voice> {
        val e = engine ?: return emptyList()
        return runCatching {
            e.voices.orEmpty().filter {
                it.locale.language == "ru" && !it.isNetworkConnectionRequired
            }.sortedBy { it.name }
        }.getOrDefault(emptyList())
    }

    private fun applyVoice() {
        val e = engine ?: return
        e.setSpeechRate(settings.rate.coerceIn(0.5f, 2.0f))
        e.setPitch(settings.pitch.coerceIn(0.5f, 2.0f))

        // 1) Явно выбранный голос имеет приоритет.
        val explicit = settings.voiceName.takeIf { it.isNotBlank() }
            ?.let { name -> e.voices?.firstOrNull { it.name == name } }
        if (explicit != null) { e.voice = explicit; return }

        // 2) Иначе эвристика по полу. У Android нет флага пола, поэтому
        //    угадываем по имени голоса — грубо, но ничего лучше платформа не даёт.
        //    Мужской обычно чуть ниже, женский чуть выше — подкрутим pitch как намёк.
        val voices = russianVoices()
        when (settings.genderEnum()) {
            VoiceGender.MALE -> {
                voices.firstOrNull { it.name.contains("male", true) && !it.name.contains("female", true) }
                    ?.let { e.voice = it }
                e.setPitch((settings.pitch * 0.85f).coerceIn(0.5f, 2.0f))
            }
            VoiceGender.FEMALE -> {
                voices.firstOrNull { it.name.contains("female", true) }
                    ?.let { e.voice = it }
                e.setPitch((settings.pitch * 1.1f).coerceIn(0.5f, 2.0f))
            }
            VoiceGender.ANY -> voices.firstOrNull()?.let { e.voice = it }
        }
    }

    fun speak(text: String) {
        if (!settings.enabled) return
        val e = engine
        if (e == null || !ready) { pending = text; init(); return }
        e.speak(text, TextToSpeech.QUEUE_ADD, null, "utt_${System.nanoTime()}")
    }

    fun stop() { engine?.stop() }

    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        ready = false
    }
}
