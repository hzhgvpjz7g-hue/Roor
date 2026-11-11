package ru.btcradar.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Что именно сравниваем с порогом.
 *
 *  RATE_BELOW  — эффективный курс УПАЛ ниже порога (хорошо для покупки).
 *  RATE_ABOVE  — эффективный курс ВЫРОС выше порога (хорошо для продажи).
 *
 * Берём именно эффективный курс (с учётом комиссии), а не витринный —
 * иначе алерт врёт ровно так же, как врёт витрина.
 */
@Serializable
enum class AlertKind {
    @SerialName("below") RATE_BELOW,
    @SerialName("above") RATE_ABOVE
}

/**
 * Правило уведомления.
 *
 * Фильтры необязательные: пустой exchangerContains = "любой обменник".
 * Это покрывает оба твоих случая — «любой обменник с курсом X»
 * и «вот этот конкретный обменник».
 */
@Serializable
data class Alert(
    val id: String,
    val direction: Direction,
    val kind: AlertKind,
    @SerialName("threshold_rub_per_btc")
    val thresholdRubPerBtc: Double,

    /** Фильтр по названию обменника (подстрока, без регистра). Пусто = все. */
    @SerialName("exchanger_contains")
    val exchangerContains: String = "",
    /** Фильтр по id источника. Пусто = все. */
    @SerialName("source_id")
    val sourceId: String = "",

    /** Учитывать только проходные по лимитам/резерву предложения. */
    @SerialName("usable_only")
    val usableOnly: Boolean = true,
    /** Сумма, относительно которой проверяется проходимость. */
    @SerialName("check_amount")
    val checkAmount: Double = 100_000.0,

    val enabled: Boolean = true,
    /** Сработать один раз и выключиться. Иначе — повторяется, но не чаще кулдауна. */
    @SerialName("one_shot")
    val oneShot: Boolean = false,

    @SerialName("last_fired_ms")
    val lastFiredMs: Long = 0,
    @SerialName("cooldown_min")
    val cooldownMin: Int = 30,

    /** Произносимая вслух метка, чтобы ассистент назвал алерт по-человечески. */
    val label: String = ""
) {
    private fun matchesFilters(o: Offer): Boolean {
        if (o.direction != direction) return false
        if (sourceId.isNotBlank() && o.sourceId != sourceId) return false
        if (exchangerContains.isNotBlank() &&
            !o.exchangerName.contains(exchangerContains, ignoreCase = true)
        ) return false
        if (usableOnly && !o.isUsable(checkAmount)) return false
        return true
    }

    private fun crosses(rate: Double): Boolean = when (kind) {
        AlertKind.RATE_BELOW -> rate <= thresholdRubPerBtc
        AlertKind.RATE_ABOVE -> rate >= thresholdRubPerBtc
    }

    /**
     * Ищем ЛУЧШЕЕ предложение, пробивающее порог.
     * Для BELOW лучшее — минимальный курс, для ABOVE — максимальный.
     */
    fun bestTrigger(offers: List<Offer>): Offer? {
        val matching = offers.filter { matchesFilters(it) && crosses(it.effectiveRubPerBtc()) }
        if (matching.isEmpty()) return null
        return when (kind) {
            AlertKind.RATE_BELOW -> matching.minByOrNull { it.effectiveRubPerBtc() }
            AlertKind.RATE_ABOVE -> matching.maxByOrNull { it.effectiveRubPerBtc() }
        }
    }

    fun inCooldown(now: Long): Boolean =
        lastFiredMs > 0 && now - lastFiredMs < cooldownMin * 60_000L

    /** Можно ли этому алерту сработать прямо сейчас. */
    fun canFire(now: Long): Boolean = enabled && !inCooldown(now)

    fun humanText(): String {
        val who = when {
            exchangerContains.isNotBlank() -> "у «$exchangerContains»"
            else -> "где угодно"
        }
        val dir = if (direction == Direction.BUY_BTC) "покупка BTC" else "продажа BTC"
        val cmp = if (kind == AlertKind.RATE_BELOW) "дешевле" else "дороже"
        return "$dir $who $cmp ${thresholdRubPerBtc.toLong()} ₽"
    }
}

/** Что произошло при проверке — отдаём наружу, чтобы показать и озвучить. */
data class AlertHit(
    val alert: Alert,
    val offer: Offer
) {
    fun spokenText(assistantName: String): String {
        val dir = if (alert.direction == Direction.BUY_BTC) "купить" else "продать"
        val rate = offer.effectiveRubPerBtc().toLong()
        val name = offer.exchangerName
        return buildString {
            if (assistantName.isNotBlank()) append("$assistantName на связи. ")
            append("Сработал курс: $dir биткоин $name по $rate рублей.")
        }
    }

    fun shortText(): String {
        val dir = if (alert.direction == Direction.BUY_BTC) "Купить" else "Продать"
        return "$dir BTC · ${offer.exchangerName} · ${offer.effectiveRubPerBtc().toLong()} ₽"
    }
}
