package ru.btcradar.sources

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Разбор чисел из дикой природы.
 *
 * Обменники пишут курс как угодно:
 *   "9 500 000,50"   — русский формат с неразрывными пробелами
 *   "9,500,000.50"   — английский
 *   "9500000.5 RUB"  — с валютой
 *   "от 0,001 BTC"   — с текстом вокруг
 *   "1 234,5"        — запятая как десятичная
 *   "1,234"          — а тут запятая уже разделитель тысяч
 *
 * Последние два случая различаем по длине группы после разделителя:
 * ровно 3 цифры и нет других разделителей => это тысячи.
 */
object Num {

    private val SPACES = charArrayOf(
        ' ', ' ', ' ', ' ', ' ', '\t', '\n', '\r'
    )

    fun parse(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null

        var s = raw.trim()
        // Убираем всё, кроме цифр, разделителей и знака минус
        s = buildString {
            for (ch in s) {
                when {
                    ch.isDigit() -> append(ch)
                    ch == ',' || ch == '.' -> append(ch)
                    ch == '-' && isEmpty() -> append(ch)
                    ch in SPACES -> {} // пробелы-разделители тысяч просто выкидываем
                    else -> {}
                }
            }
        }
        if (s.isEmpty() || s == "-") return null

        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')

        s = when {
            // Есть оба — десятичный тот, что правее
            lastComma >= 0 && lastDot >= 0 -> {
                if (lastComma > lastDot) s.replace(".", "").replace(',', '.')
                else s.replace(",", "")
            }
            // Только запятая — самый неоднозначный случай:
            //   "1,234"     — скорее всего тысячи
            //   "1,234,567" — точно тысячи
            //   "0,001"     — точно дробь (это BTC!), группа тысяч не бывает нулевой
            //   "123,45"    — дробь, в хвосте не 3 цифры
            //   "9500000,5" — дробь, первая группа длиннее 3 цифр
            //
            // Разбираем по группам: формат тысяч — это 1..3 цифры,
            // затем КАЖДАЯ следующая группа ровно по 3 цифры.
            lastComma >= 0 -> {
                val groups = s.split(',')
                val first = groups.first().trimStart('-')
                val rest = groups.drop(1)

                val looksLikeThousands =
                    rest.isNotEmpty() &&
                            rest.all { it.length == 3 && it.all(Char::isDigit) } &&
                            first.length in 1..3 &&
                            first.all(Char::isDigit) &&
                            first.trimStart('0').isNotEmpty()   // "0,001" -> дробь

                if (looksLikeThousands) s.replace(",", "") else s.replace(',', '.')
            }
            // Только точка
            lastDot >= 0 -> {
                val dotCount = s.count { it == '.' }
                if (dotCount > 1) s.replace(".", "") else s
            }
            else -> s
        }

        return s.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    /** Достаёт первое число из строки вида "от 1 000 до 500 000 RUB". */
    fun parseFirst(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val m = Regex("""-?\d[\d\s  .,]*""").find(raw) ?: return null
        return parse(m.value)
    }

    /** Достаёт пару (min, max) из строки с диапазоном. */
    fun parseRange(raw: String?): Pair<Double?, Double?> {
        if (raw.isNullOrBlank()) return null to null
        val all = Regex("""-?\d[\d\s  .,]*""")
            .findAll(raw).mapNotNull { parse(it.value) }.toList()
        return when (all.size) {
            0 -> null to null
            1 -> all[0] to null
            else -> all[0] to all[1]
        }
    }
}

/**
 * Навигация по JSON путём вида "data.rates[0].ask".
 * Возвращает null вместо исключения — один кривой источник
 * не должен ронять весь опрос.
 */
object JsonPath {

    fun resolve(root: JsonElement, path: String?): JsonElement? {
        if (path.isNullOrBlank()) return root
        var cur: JsonElement? = root

        for (rawSeg in path.split('.')) {
            if (cur == null) return null
            var seg = rawSeg.trim()
            if (seg.isEmpty()) continue

            // Имя до первой скобки
            val bracket = seg.indexOf('[')
            val name = if (bracket >= 0) seg.substring(0, bracket) else seg

            if (name.isNotEmpty()) {
                cur = (cur as? JsonObject)?.get(name) ?: return null
            }

            // Индексы: [0][2]
            if (bracket >= 0) {
                var rest = seg.substring(bracket)
                while (rest.startsWith("[")) {
                    val close = rest.indexOf(']')
                    if (close < 0) return null
                    val idx = rest.substring(1, close).trim().toIntOrNull() ?: return null
                    val arr = cur as? JsonArray ?: return null
                    cur = arr.getOrNull(idx) ?: return null
                    rest = rest.substring(close + 1)
                }
            }
        }
        return cur
    }

    fun double(root: JsonElement, path: String?): Double? {
        val el = resolve(root, path) ?: return null
        val prim = el as? JsonPrimitive ?: return null
        return prim.doubleOrNull ?: Num.parse(prim.content)
    }

    fun string(root: JsonElement, path: String?): String? {
        val el = resolve(root, path) ?: return null
        val prim = el as? JsonPrimitive ?: return null
        return prim.content.takeIf { it.isNotBlank() && it != "null" }
    }

    /** Разворачивает путь в список элементов: массив -> элементы, объект -> он сам. */
    fun rows(root: JsonElement, rowsPath: String?): List<JsonElement> {
        val el = resolve(root, rowsPath) ?: return emptyList()
        return when (el) {
            is JsonArray -> el.toList()
            is JsonObject -> listOf(el)
            else -> emptyList()
        }
    }
}

/** Приводим курс к «рублей за 1 BTC» с учётом флагов источника. */
fun normalizeRate(value: Double?, inverted: Boolean, multiplier: Double): Double? {
    val v = value ?: return null
    if (!v.isFinite() || v <= 0.0) return null
    val scaled = v * multiplier
    if (scaled <= 0.0) return null
    val result = if (inverted) 1.0 / scaled else scaled
    // Санити-чек: курс BTC/RUB вне этого диапазона — почти наверняка
    // перепутанное направление или не та валютная пара.
    return result.takeIf { it.isFinite() && it in 1_000.0..1_000_000_000.0 }
}
