package ru.btcradar.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val RU = Locale("ru", "RU")

/**
 * Рубли: разряды через узкий неразрывный пробел, без копеек на больших суммах.
 *
 * Форматируем в US-локали намеренно: там разделитель разрядов — запятая,
 * а десятичный — точка, и их можно однозначно различить. В ru-локали
 * разделитель разрядов сам по себе пробел, а десятичный — запятая,
 * и попытка «почистить» вывод затирает копейки.
 */
fun fmtRub(v: Double): String {
    if (!v.isFinite()) return "—"
    val decimals = if (abs(v) >= 1000) 0 else 2
    return String.format(Locale.US, "%,.${decimals}f", v)
        .replace(",", " ")   // разряды -> узкий неразрывный пробел
        .replace('.', ',')        // десятичная точка -> запятая, как принято в РФ
}

/** BTC: 8 знаков, но без хвоста нулей — читать проще. */
fun fmtBtc(v: Double): String {
    if (!v.isFinite()) return "—"
    return String.format(Locale.US, "%.8f", v).trimEnd('0').trimEnd('.')
}

fun fmtPercent(v: Double?): String {
    if (v == null || !v.isFinite()) return "—"
    val sign = if (v > 0) "+" else ""
    return "$sign${String.format(Locale.US, "%.2f", v)}%"
}

fun fmtTime(ms: Long): String {
    if (ms <= 0) return "—"
    return SimpleDateFormat("HH:mm:ss", RU).format(Date(ms))
}

fun fmtAgo(ms: Long): String {
    if (ms <= 0) return "ещё не обновлялось"
    val sec = (System.currentTimeMillis() - ms) / 1000
    return when {
        sec < 10 -> "только что"
        sec < 60 -> "$sec с назад"
        sec < 3600 -> "${sec / 60} мин назад"
        else -> "${sec / 3600} ч назад"
    }
}

/** Компактно: 1 234 567 -> "1.23 млн" */
fun fmtCompactRub(v: Double?): String {
    if (v == null || !v.isFinite()) return "—"
    return when {
        abs(v) >= 1_000_000_000 -> String.format(Locale.US, "%.2f млрд", v / 1_000_000_000)
        abs(v) >= 1_000_000 -> String.format(Locale.US, "%.2f млн", v / 1_000_000)
        abs(v) >= 1_000 -> String.format(Locale.US, "%.0f тыс", v / 1_000)
        else -> String.format(Locale.US, "%.0f", v)
    }
}
