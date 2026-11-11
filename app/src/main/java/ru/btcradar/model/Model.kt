package ru.btcradar.model

import kotlinx.serialization.Serializable

/**
 * Направление обмена — с точки зрения ПОЛЬЗОВАТЕЛЯ, не обменника.
 *
 *  BUY_BTC  — я отдаю рубли, получаю BTC.  Чем НИЖЕ цена RUB/BTC, тем лучше.
 *  SELL_BTC — я отдаю BTC, получаю рубли. Чем ВЫШЕ цена RUB/BTC, тем лучше.
 *
 * Это главный источник ошибок в таких агрегаторах: нельзя просто
 * отсортировать один столбец цен и считать, что всё готово.
 */
enum class Direction { BUY_BTC, SELL_BTC }

/** Как доставали данные — видно в карточке, чтобы понимать степень доверия. */
enum class Transport { CLEARNET, TOR }

/**
 * Одно предложение одного обменника в одном направлении.
 * rubPerBtc всегда нормализован: сколько рублей за 1 BTC.
 */
@Serializable
data class Offer(
    val sourceId: String,
    val exchangerName: String,
    val direction: Direction,
    val rubPerBtc: Double,
    /** Комиссия в процентах, уже НЕ включённая в rubPerBtc. 0.0 если неизвестна/включена. */
    val feePercent: Double = 0.0,
    /** Лимиты в той валюте, которую пользователь ОТДАЁТ. */
    val minIn: Double? = null,
    val maxIn: Double? = null,
    /** Резерв обменника в той валюте, которую пользователь ПОЛУЧАЕТ. */
    val reserveOut: Double? = null,
    val url: String? = null,
    val transport: Transport = Transport.CLEARNET,
    val fetchedAtMs: Long = System.currentTimeMillis(),
    /** Сырой текст/заметка от парсера — помогает при отладке источника. */
    val note: String? = null
) {
    /**
     * Сколько пользователь реально получит на руки, отдав [amountIn].
     * Для BUY_BTC amountIn в рублях, результат в BTC.
     * Для SELL_BTC amountIn в BTC, результат в рублях.
     *
     * Комиссия срезается с выхода — так сравнение честное даже когда
     * у обменников разная модель комиссии.
     */
    fun output(amountIn: Double): Double {
        if (rubPerBtc <= 0.0 || amountIn <= 0.0) return 0.0
        val afterFee = amountIn * (1.0 - feePercent / 100.0)
        return when (direction) {
            Direction.BUY_BTC -> afterFee / rubPerBtc
            Direction.SELL_BTC -> afterFee * rubPerBtc
        }
    }

    /**
     * Эффективный курс с учётом комиссии — то, что надо показывать
     * вместо «красивой» витринной цены.
     */
    fun effectiveRubPerBtc(): Double {
        val f = 1.0 - feePercent / 100.0
        if (f <= 0.0) return rubPerBtc
        return when (direction) {
            // Отдал рублей больше за тот же BTC => эффективная цена выше
            Direction.BUY_BTC -> rubPerBtc / f
            // Получил рублей меньше за тот же BTC => эффективная цена ниже
            Direction.SELL_BTC -> rubPerBtc * f
        }
    }

    fun fitsLimits(amountIn: Double): Boolean {
        if (minIn != null && amountIn < minIn) return false
        if (maxIn != null && amountIn > maxIn) return false
        return true
    }

    /** Хватает ли у обменника резерва, чтобы выдать нам сумму. */
    fun hasReserve(amountIn: Double): Boolean {
        val r = reserveOut ?: return true
        return r >= output(amountIn)
    }

    fun isUsable(amountIn: Double): Boolean = fitsLimits(amountIn) && hasReserve(amountIn)
}

/** Результат одного опроса источника — чтобы показать ошибки, а не молча прятать. */
data class SourceResult(
    val sourceId: String,
    val title: String,
    val offers: List<Offer>,
    val error: String? = null,
    val durationMs: Long = 0,
    val transport: Transport = Transport.CLEARNET
)

/**
 * Сортировка. Ключевая идея: сравниваем не курс, а ВЫХОД на заданную сумму.
 * Тогда обе колонки сортируются одинаково — по убыванию выхода —
 * и комиссии с лимитами учитываются автоматически.
 */
fun List<Offer>.rankFor(amountIn: Double, hideUnusable: Boolean): List<Offer> {
    val usable = { o: Offer -> o.isUsable(amountIn) }
    val base = if (hideUnusable) filter(usable) else this
    return base.sortedWith(
        // Непроходные по лимитам/резерву — всегда вниз, даже с шикарным курсом
        compareByDescending<Offer> { usable(it) }
            .thenByDescending { it.output(amountIn) }
    )
}

/** Справочная «честная» цена, чтобы видеть спред обменника. */
data class SpotPrice(
    val rubPerBtc: Double,
    val source: String,
    val fetchedAtMs: Long = System.currentTimeMillis()
)

/** Отклонение предложения от спота, в процентах. Отрицательное = хуже рынка. */
fun Offer.spreadVsSpot(spot: SpotPrice?): Double? {
    if (spot == null || spot.rubPerBtc <= 0.0) return null
    val eff = effectiveRubPerBtc()
    return when (direction) {
        // Покупаю дороже спота => минус
        Direction.BUY_BTC -> (spot.rubPerBtc - eff) / spot.rubPerBtc * 100.0
        // Продаю дешевле спота => минус
        Direction.SELL_BTC -> (eff - spot.rubPerBtc) / spot.rubPerBtc * 100.0
    }
}
