package ru.btcradar.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Тип парсера. Новый сайт = новая запись в конфиге, а не новый релиз APK.
 *
 *  JSON — у сайта есть API/json-ручка. Самый надёжный вариант.
 *  HTML — парсим страницу CSS-селекторами. Хрупко, ломается при редизайне.
 *  BESTCHANGE — встроенный адаптер под публичный экспорт BestChange.
 *  SPOT_JSON — справочный «рыночный» курс, не попадает в списки обменников.
 */
enum class SourceKind { JSON, HTML, BESTCHANGE, SPOT_JSON }

@Serializable
data class SourceConfig(
    val id: String,
    val title: String,
    val kind: SourceKind,
    val enabled: Boolean = true,

    /** Обычный https-адрес. */
    val url: String = "",
    /** Зеркало в Tor. Если задано и Tor включён — приложение пойдёт сюда. */
    @SerialName("onion_url")
    val onionUrl: String? = null,
    /** true = источник работает ТОЛЬКО через Tor, без него не опрашивается. */
    @SerialName("require_tor")
    val requireTor: Boolean = false,

    /** Комиссия обменника, если она не зашита в отдаваемый курс. */
    @SerialName("fee_percent")
    val feePercent: Double = 0.0,

    val headers: Map<String, String> = emptyMap(),
    @SerialName("timeout_sec")
    val timeoutSec: Long = 30,

    val json: JsonMapping? = null,
    val html: HtmlMapping? = null
) {
    /** Какой адрес реально дёргать при текущем состоянии Tor. */
    fun effectiveUrl(torAvailable: Boolean): String? = when {
        torAvailable && !onionUrl.isNullOrBlank() -> onionUrl
        requireTor -> null           // onion нет, а clearnet запрещён конфигом
        url.isNotBlank() -> url
        else -> null
    }

    fun usesTor(torAvailable: Boolean): Boolean =
        torAvailable && (!onionUrl.isNullOrBlank() || requireTor)
}

/**
 * Разбор JSON. Пути — точечная нотация с поддержкой индексов: "data.rates[0].ask".
 * rowsPath указывает на массив предложений; остальные пути — относительно элемента.
 */
@Serializable
data class JsonMapping(
    /** Путь до массива. Пусто = корень сам является массивом или единственным объектом. */
    @SerialName("rows_path")
    val rowsPath: String = "",

    @SerialName("name_path")
    val namePath: String? = null,
    /** Цена, по которой пользователь ПОКУПАЕТ BTC (обменник продаёт). */
    @SerialName("buy_path")
    val buyPath: String? = null,
    /** Цена, по которой пользователь ПРОДАЁТ BTC (обменник покупает). */
    @SerialName("sell_path")
    val sellPath: String? = null,

    @SerialName("min_in_path")
    val minInPath: String? = null,
    @SerialName("max_in_path")
    val maxInPath: String? = null,
    @SerialName("reserve_path")
    val reservePath: String? = null,
    @SerialName("fee_path")
    val feePath: String? = null,
    @SerialName("url_path")
    val urlPath: String? = null,

    /**
     * Если курс отдаётся как BTC за рубль (а не рубли за BTC) — ставь true,
     * значение будет инвертировано.
     */
    val inverted: Boolean = false,
    /** Домножить на коэффициент (например, если цена в копейках — 0.01). */
    val multiplier: Double = 1.0
)

/** Разбор HTML таблицы/списка CSS-селекторами. */
@Serializable
data class HtmlMapping(
    /** Селектор строки-предложения, например "table.rates tbody tr". */
    @SerialName("row_selector")
    val rowSelector: String,

    @SerialName("name_selector")
    val nameSelector: String? = null,
    @SerialName("buy_selector")
    val buySelector: String? = null,
    @SerialName("sell_selector")
    val sellSelector: String? = null,
    @SerialName("min_in_selector")
    val minInSelector: String? = null,
    @SerialName("max_in_selector")
    val maxInSelector: String? = null,
    @SerialName("reserve_selector")
    val reserveSelector: String? = null,
    @SerialName("url_selector")
    val urlSelector: String? = null,
    /** Атрибут для ссылки; по умолчанию href. */
    @SerialName("url_attr")
    val urlAttr: String = "href",

    val inverted: Boolean = false,
    val multiplier: Double = 1.0
)

/**
 * Стартовый набор. Намеренно пустой по части обменников:
 * сюда ты добавляешь свои сайты сам (кнопка «Источники» в приложении).
 *
 * Из коробки включён только справочный спот-курс, чтобы приложение
 * сразу показывало рыночную цену и ты видел спред любого обменника.
 */
object DefaultSources {

    /** Справочный курс BTC/RUB. Нужен, чтобы отличать нормальный обменник от грабежа. */
    private val coingeckoSpot = SourceConfig(
        id = "spot_coingecko",
        title = "Спот BTC/RUB (CoinGecko)",
        kind = SourceKind.SPOT_JSON,
        url = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=rub",
        json = JsonMapping(
            rowsPath = "",
            buyPath = "bitcoin.rub",
            sellPath = "bitcoin.rub"
        )
    )

    /**
     * BestChange — публичный российский агрегатор обменников.
     * Выключен по умолчанию: у него периодически меняется формат выгрузки,
     * включи и проверь кнопкой «Тест» в настройках источника.
     */
    private val bestchange = SourceConfig(
        id = "bestchange",
        title = "BestChange (агрегатор)",
        kind = SourceKind.BESTCHANGE,
        enabled = false,
        url = "https://www.bestchange.ru/bm/info.zip"
    )

    /**
     * Шаблон-пример. Выключен. Копируй его и правь под свой сайт —
     * быстрее, чем писать конфиг с нуля.
     */
    private val templateJson = SourceConfig(
        id = "template_json",
        title = "[Шаблон] JSON-обменник",
        kind = SourceKind.JSON,
        enabled = false,
        url = "https://example.com/api/rates",
        onionUrl = null,
        feePercent = 0.0,
        json = JsonMapping(
            rowsPath = "rates",
            namePath = "name",
            buyPath = "btc_rub_ask",
            sellPath = "btc_rub_bid",
            minInPath = "min",
            maxInPath = "max",
            reservePath = "reserve"
        )
    )

    private val templateHtml = SourceConfig(
        id = "template_html",
        title = "[Шаблон] HTML-обменник",
        kind = SourceKind.HTML,
        enabled = false,
        url = "https://example.com/exchange/btc-to-rub",
        html = HtmlMapping(
            rowSelector = "table.rates tbody tr",
            nameSelector = "td.name",
            buySelector = "td.ask",
            sellSelector = "td.bid",
            reserveSelector = "td.reserve",
            urlSelector = "td.name a"
        )
    )

    fun all(): List<SourceConfig> =
        listOf(coingeckoSpot, bestchange, templateJson, templateHtml)
}
