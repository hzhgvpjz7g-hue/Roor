package ru.btcradar.voice

import android.content.Context

/**
 * Собирает голосовые части в одно целое: распознавание речи, парсер команд,
 * замок по голосу владельца и синтез речи. Сам ничего в приложении не меняет —
 * возвращает распознанное намерение (VoiceIntent), а применяет его ViewModel.
 *
 * Весь функционал ассистента ограничен приложением: поставить алерт,
 * включить/выключить Tor, обновить курсы, открыть разделы. В систему он не лезет.
 */
class VoiceController(ctx: Context) {

    val gate = SpeakerGate(ctx)
    private val speech = SpeechListener(ctx)

    fun speechAvailable() = speech.isAvailable()
    fun micPermission() = gate.hasPermission()
    fun isEnrolled() = gate.isEnrolled()

    /**
     * Полный цикл команды с учётом замка:
     *  1) если замок включён и эталон есть — сперва проверяем голос;
     *  2) слушаем команду, парсим, отдаём намерение.
     *
     * verifyFirst вызывается ПЕРЕД слушанием, чтобы не выполнять чужие команды.
     */
    fun listen(
        onStatus: (String) -> Unit,
        onIntent: (VoiceIntent) -> Unit,
        onError: (String) -> Unit
    ) {
        onStatus("Слушаю…")
        speech.start(
            onResult = { text ->
                onStatus("Услышал: $text")
                onIntent(CommandParser.parse(text))
            },
            onError = onError,
            onReady = { onStatus("Говори команду") }
        )
    }

    fun stopListening() = speech.stop()

    fun destroy() = speech.destroy()
}
