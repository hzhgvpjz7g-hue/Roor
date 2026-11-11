package ru.btcradar.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import ru.btcradar.model.Direction
import ru.btcradar.model.Offer
import ru.btcradar.model.SourceConfig
import ru.btcradar.model.SourceKind
import ru.btcradar.model.SourceResult
import ru.btcradar.model.SpotPrice
import ru.btcradar.model.Transport
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

private val lenientJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

private const val UA =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"

/** Результат загрузки источника: либо предложения, либо спот, либо ошибка. */
class FetchOutcome(
    val result: SourceResult,
    val spot: SpotPrice? = null
)

object Fetchers {

    suspend fun fetch(
        cfg: SourceConfig,
        client: OkHttpClient,
        viaTor: Boolean
    ): FetchOutcome = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val transport = if (viaTor) Transport.TOR else Transport.CLEARNET
        val url = cfg.effectiveUrl(viaTor)

        if (url.isNullOrBlank()) {
            return@withContext FetchOutcome(
                SourceResult(
                    cfg.id, cfg.title, emptyList(),
                    error = if (cfg.requireTor) "Источник требует Tor, но Tor выключен или .onion не задан"
                    else "Не задан URL",
                    durationMs = 0, transport = transport
                )
            )
        }

        try {
            val bytes = download(url, cfg, client)
            when (cfg.kind) {
                SourceKind.SPOT_JSON -> {
                    val spot = parseSpot(bytes, cfg)
                    FetchOutcome(
                        SourceResult(
                            cfg.id, cfg.title, emptyList(),
                            error = if (spot == null) "Не удалось вытащить спот-курс" else null,
                            durationMs = System.currentTimeMillis() - started,
                            transport = transport
                        ),
                        spot = spot
                    )
                }
                SourceKind.JSON -> {
                    val offers = parseJson(bytes, cfg, transport, url)
                    FetchOutcome(
                        SourceResult(
                            cfg.id, cfg.title, offers,
                            error = if (offers.isEmpty()) "0 предложений — проверь пути в конфиге" else null,
                            durationMs = System.currentTimeMillis() - started,
                            transport = transport
                        )
                    )
                }
                SourceKind.HTML -> {
                    val offers = parseHtml(bytes, cfg, transport, url)
                    FetchOutcome(
                        SourceResult(
                            cfg.id, cfg.title, offers,
                            error = if (offers.isEmpty()) "0 предложений — проверь CSS-селекторы" else null,
                            durationMs = System.currentTimeMillis() - started,
                            transport = transport
                        )
                    )
                }
                SourceKind.BESTCHANGE -> {
                    val offers = BestChange.parse(bytes, cfg, transport)
                    FetchOutcome(
                        SourceResult(
                            cfg.id, cfg.title, offers,
                            error = if (offers.isEmpty())
                                "0 предложений — формат выгрузки BestChange мог поменяться"
                            else null,
                            durationMs = System.currentTimeMillis() - started,
                            transport = transport
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            FetchOutcome(
                SourceResult(
                    cfg.id, cfg.title, emptyList(),
                    error = t.message ?: t.javaClass.simpleName,
                    durationMs = System.currentTimeMillis() - started,
                    transport = transport
                )
            )
        }
    }

    private fun download(url: String, cfg: SourceConfig, client: OkHttpClient): ByteArray {
        val b = Request.Builder().url(url).header("User-Agent", UA)
        cfg.headers.forEach { (k, v) -> b.header(k, v) }
        client.newCall(b.build()).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            return resp.body?.bytes() ?: error("Пустой ответ")
        }
    }

    // ---------- JSON ----------

    private fun parseSpot(bytes: ByteArray, cfg: SourceConfig): SpotPrice? {
        val m = cfg.json ?: return null
        val root = lenientJson.parseToJsonElement(bytes.decodeToString())
        val raw = JsonPath.double(root, m.buyPath) ?: JsonPath.double(root, m.sellPath)
        val rate = normalizeRate(raw, m.inverted, m.multiplier) ?: return null
        return SpotPrice(rate, cfg.title)
    }

    private fun parseJson(
        bytes: ByteArray,
        cfg: SourceConfig,
        transport: Transport,
        baseUrl: String
    ): List<Offer> {
        val m = cfg.json ?: error("Для kind=JSON нужен блок json в конфиге")
        val root: JsonElement = lenientJson.parseToJsonElement(bytes.decodeToString())
        val rows = JsonPath.rows(root, m.rowsPath)
        val out = ArrayList<Offer>(rows.size * 2)

        for ((i, row) in rows.withIndex()) {
            val name = JsonPath.string(row, m.namePath) ?: "${cfg.title} #${i + 1}"
            val fee = JsonPath.double(row, m.feePath) ?: cfg.feePercent
            val minIn = JsonPath.double(row, m.minInPath)
            val maxIn = JsonPath.double(row, m.maxInPath)
            val reserve = JsonPath.double(row, m.reservePath)
            val link = JsonPath.string(row, m.urlPath) ?: baseUrl

            normalizeRate(JsonPath.double(row, m.buyPath), m.inverted, m.multiplier)?.let {
                out += Offer(
                    sourceId = cfg.id, exchangerName = name,
                    direction = Direction.BUY_BTC, rubPerBtc = it,
                    feePercent = fee, minIn = minIn, maxIn = maxIn,
                    reserveOut = reserve, url = link, transport = transport
                )
            }
            normalizeRate(JsonPath.double(row, m.sellPath), m.inverted, m.multiplier)?.let {
                out += Offer(
                    sourceId = cfg.id, exchangerName = name,
                    direction = Direction.SELL_BTC, rubPerBtc = it,
                    feePercent = fee, minIn = minIn, maxIn = maxIn,
                    reserveOut = reserve, url = link, transport = transport
                )
            }
        }
        return out
    }

    // ---------- HTML ----------

    private fun parseHtml(
        bytes: ByteArray,
        cfg: SourceConfig,
        transport: Transport,
        baseUrl: String
    ): List<Offer> {
        val m = cfg.html ?: error("Для kind=HTML нужен блок html в конфиге")
        val doc = Jsoup.parse(bytes.decodeToString(), baseUrl)
        val rows = doc.select(m.rowSelector)
        val out = ArrayList<Offer>(rows.size * 2)

        fun txt(row: Element, sel: String?): String? =
            sel?.let { row.selectFirst(it)?.text()?.trim() }?.takeIf { it.isNotEmpty() }

        for ((i, row) in rows.withIndex()) {
            val name = txt(row, m.nameSelector) ?: "${cfg.title} #${i + 1}"
            val minIn = Num.parseFirst(txt(row, m.minInSelector))
            val maxIn = Num.parseFirst(txt(row, m.maxInSelector))
            val reserve = Num.parseFirst(txt(row, m.reserveSelector))
            val link = m.urlSelector
                ?.let { row.selectFirst(it)?.absUrl(m.urlAttr) }
                ?.takeIf { it.isNotBlank() } ?: baseUrl

            normalizeRate(Num.parseFirst(txt(row, m.buySelector)), m.inverted, m.multiplier)?.let {
                out += Offer(
                    sourceId = cfg.id, exchangerName = name,
                    direction = Direction.BUY_BTC, rubPerBtc = it,
                    feePercent = cfg.feePercent, minIn = minIn, maxIn = maxIn,
                    reserveOut = reserve, url = link, transport = transport
                )
            }
            normalizeRate(Num.parseFirst(txt(row, m.sellSelector)), m.inverted, m.multiplier)?.let {
                out += Offer(
                    sourceId = cfg.id, exchangerName = name,
                    direction = Direction.SELL_BTC, rubPerBtc = it,
                    feePercent = cfg.feePercent, minIn = minIn, maxIn = maxIn,
                    reserveOut = reserve, url = link, transport = transport
                )
            }
        }
        return out
    }
}

/**
 * Адаптер под публичную выгрузку BestChange (info.zip).
 *
 * ВНИМАНИЕ: формат выгрузки у них менялся не раз, а описание неофициальное.
 * Поэтому источник выключен по умолчанию — включай и проверяй кнопкой «Тест».
 * Если формат разъехался, поправить надо будет только этот объект.
 *
 * Ожидаемая структура: ZIP с .dat-файлами, записи разделены ";",
 * кодировка windows-1251.
 *   bm_cy.dat    : id;код;название;...
 *   bm_exch.dat  : id;название;...
 *   bm_rates.dat : валюта_из;валюта_в;id_обменника;отдаёте;получаете;резерв;...
 */
object BestChange {

    private val CP1251 = charset("windows-1251")

    fun parse(zipBytes: ByteArray, cfg: SourceConfig, transport: Transport): List<Offer> {
        val files = unzip(zipBytes)
        val currencies = parseCurrencies(files["bm_cy.dat"])
        val exchangers = parseExchangers(files["bm_exch.dat"])
        val ratesRaw = files["bm_rates.dat"] ?: return emptyList()

        val btcIds = currencies.filterValues { it.contains("bitcoin", true) || it.trim().equals("btc", true) }.keys
        val rubIds = currencies.filterValues { it.contains("руб", true) || it.contains("rub", true) }.keys
        if (btcIds.isEmpty() || rubIds.isEmpty()) return emptyList()

        val out = ArrayList<Offer>()
        for (line in ratesRaw.toString(CP1251).lineSequence()) {
            if (line.isBlank()) continue
            val p = line.split(';')
            if (p.size < 6) continue

            val from = p[0].trim()
            val to = p[1].trim()
            val exchId = p[2].trim()
            val give = Num.parse(p[3]) ?: continue
            val get = Num.parse(p[4]) ?: continue
            val reserve = Num.parse(p[5])
            if (give <= 0.0 || get <= 0.0) continue

            val name = exchangers[exchId] ?: "Обменник #$exchId"

            when {
                // BTC -> RUB : пользователь продаёт биткоин
                from in btcIds && to in rubIds -> {
                    val rate = normalizeRate(get / give, false, 1.0) ?: continue
                    out += Offer(
                        sourceId = cfg.id, exchangerName = name,
                        direction = Direction.SELL_BTC, rubPerBtc = rate,
                        reserveOut = reserve, url = cfg.url, transport = transport,
                        note = "BestChange"
                    )
                }
                // RUB -> BTC : пользователь покупает биткоин
                from in rubIds && to in btcIds -> {
                    val rate = normalizeRate(give / get, false, 1.0) ?: continue
                    out += Offer(
                        sourceId = cfg.id, exchangerName = name,
                        direction = Direction.BUY_BTC, rubPerBtc = rate,
                        reserveOut = reserve, url = cfg.url, transport = transport,
                        note = "BestChange"
                    )
                }
            }
        }
        return out
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                if (!e.isDirectory) {
                    out[e.name.substringAfterLast('/').lowercase()] = zis.readBytes()
                }
                zis.closeEntry()
                e = zis.nextEntry
            }
        }
        return out
    }

    private fun parseCurrencies(data: ByteArray?): Map<String, String> {
        if (data == null) return emptyMap()
        return data.toString(CP1251).lineSequence()
            .mapNotNull { line ->
                val p = line.split(';')
                if (p.size < 3) null else p[0].trim() to p.drop(1).joinToString(" ")
            }.toMap()
    }

    private fun parseExchangers(data: ByteArray?): Map<String, String> {
        if (data == null) return emptyMap()
        return data.toString(CP1251).lineSequence()
            .mapNotNull { line ->
                val p = line.split(';')
                if (p.size < 2) null else p[0].trim() to p[1].trim()
            }.toMap()
    }
}
