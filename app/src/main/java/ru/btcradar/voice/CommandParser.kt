package ru.btcradar.voice

import ru.btcradar.model.Alert
import ru.btcradar.model.AlertKind
import ru.btcradar.model.Direction

/**
 * Распознанные намерения. Функционал ассистента намеренно ограничен
 * самим приложением — он не лезет в систему, только управляет радаром.
 */
sealed interface VoiceIntent {
    data class CreateAlert(val alert: Alert) : VoiceIntent
    data object Refresh : VoiceIntent
    data object TorOn : VoiceIntent
    data object TorOff : VoiceIntent
    data object OpenSources : VoiceIntent
    data object OpenAlerts : VoiceIntent
    data object OpenBrowser : VoiceIntent
    data class Say(val text: String) : VoiceIntent      // переспрос/ответ
    data class Unknown(val heard: String) : VoiceIntent
}

/**
 * Простой парсер русских команд по ключевым словам. Без нейросети и без
 * сети — работает на тексте, который вернул системный распознаватель.
 */
object CommandParser {

    fun parse(raw: String): VoiceIntent {
        val t = raw.lowercase().trim()
        if (t.isBlank()) return VoiceIntent.Unknown(raw)

        // Tor
        if (contains(t, "tor", "тор")) {
            if (contains(t, "выключ", "отключ", "офф", "стоп")) return VoiceIntent.TorOff
            if (contains(t, "включ", "запус", "подключ", "вкл")) return VoiceIntent.TorOn
        }

        // навигация
        if (contains(t, "обнови", "обновить", "сканируй", "проверь курс")) return VoiceIntent.Refresh
        if (contains(t, "источник")) return VoiceIntent.OpenSources
        if (contains(t, "браузер", "браузере", "открой сайт", "открой страниц")) return VoiceIntent.OpenBrowser
        if (contains(t, "алерт", "уведомлен") && !contains(t, "постав", "создай", "добавь")) {
            return VoiceIntent.OpenAlerts
        }

        // создание алерта: "поставь алерт на продажу дороже 9700000"
        if (contains(t, "постав", "создай", "добавь", "напомни", "следи")) {
            parseAlert(t)?.let { return VoiceIntent.CreateAlert(it) }
            return VoiceIntent.Say("Не разобрал порог. Скажи, например: поставь алерт на продажу дороже девять миллионов семьсот тысяч.")
        }

        return VoiceIntent.Unknown(raw)
    }

    private fun parseAlert(t: String): Alert? {
        val threshold = extractNumber(t) ?: return null
        if (threshold < 1000) return null

        val direction = when {
            contains(t, "продаж", "продать", "продай", "сбыт") -> Direction.SELL_BTC
            contains(t, "покупк", "купить", "купи", "покуп") -> Direction.BUY_BTC
            else -> Direction.SELL_BTC
        }
        val kind = when {
            contains(t, "дороже", "выше", "больше", "вырос") -> AlertKind.RATE_ABOVE
            contains(t, "дешевле", "ниже", "меньше", "упал", "упадёт", "упадет") -> AlertKind.RATE_BELOW
            else -> if (direction == Direction.SELL_BTC) AlertKind.RATE_ABOVE else AlertKind.RATE_BELOW
        }

        return Alert(
            id = "a_" + System.currentTimeMillis().toString().takeLast(7),
            direction = direction,
            kind = kind,
            thresholdRubPerBtc = threshold,
            label = "голосом"
        )
    }

    private fun contains(t: String, vararg keys: String) = keys.any { t.contains(it) }

    /**
     * Достаём число как из цифр ("9700000"), так и из слов
     * ("девять миллионов семьсот тысяч"). Распознаватель часто отдаёт
     * и то, и другое вперемешку.
     */
    private fun extractNumber(t: String): Double? {
        // сперва пробуем слитные цифры
        Regex("""\d[\d\s]{3,}""").find(t)?.let { m ->
            m.value.replace(" ", "").toDoubleOrNull()?.let { return it }
        }
        return wordsToNumber(t)
    }

    private val units = mapOf(
        "ноль" to 0L, "один" to 1L, "одна" to 1L, "два" to 2L, "две" to 2L, "три" to 3L,
        "четыре" to 4L, "пять" to 5L, "шесть" to 6L, "семь" to 7L, "восемь" to 8L,
        "девять" to 9L, "десять" to 10L, "одиннадцать" to 11L, "двенадцать" to 12L,
        "тринадцать" to 13L, "четырнадцать" to 14L, "пятнадцать" to 15L,
        "шестнадцать" to 16L, "семнадцать" to 17L, "восемнадцать" to 18L, "девятнадцать" to 19L,
        "двадцать" to 20L, "тридцать" to 30L, "сорок" to 40L, "пятьдесят" to 50L,
        "шестьдесят" to 60L, "семьдесят" to 70L, "восемьдесят" to 80L, "девяносто" to 90L,
        "сто" to 100L, "двести" to 200L, "триста" to 300L, "четыреста" to 400L,
        "пятьсот" to 500L, "шестьсот" to 600L, "семьсот" to 700L, "восемьсот" to 800L, "девятьсот" to 900L
    )
    private val scales = mapOf(
        "тысяч" to 1_000L, "тысяча" to 1_000L, "тысячи" to 1_000L,
        "миллион" to 1_000_000L, "миллиона" to 1_000_000L, "миллионов" to 1_000_000L,
        "лям" to 1_000_000L, "ляма" to 1_000_000L, "лямов" to 1_000_000L
    )

    private fun wordsToNumber(t: String): Double? {
        val tokens = t.split(Regex("[^а-яё]+")).filter { it.isNotBlank() }
        var total = 0L; var current = 0L; var seen = false

        for (tok in tokens) {
            // Самый ДЛИННЫЙ подходящий ключ, иначе "семьсот" матчится на "семь",
            // а "пятьсот" на "пять" — и число разъезжается.
            val u = units.entries.filter { tok.startsWith(it.key) }.maxByOrNull { it.key.length }?.value
            val s = scales.entries.filter { tok.startsWith(it.key) }.maxByOrNull { it.key.length }?.value
            when {
                u != null -> { current += u; seen = true }
                s != null -> {
                    if (current == 0L) current = 1L
                    total += current * s; current = 0L; seen = true
                }
            }
        }
        total += current
        return if (seen && total > 0) total.toDouble() else null
    }
}
