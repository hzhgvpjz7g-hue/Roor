package ru.btcradar.net

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import ru.btcradar.model.Alert
import ru.btcradar.model.DefaultSources
import ru.btcradar.model.Offer
import ru.btcradar.model.SourceConfig
import ru.btcradar.model.SourceResult
import ru.btcradar.model.SpotPrice
import ru.btcradar.sources.Fetchers
import ru.btcradar.voice.AssistantSettings

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    prettyPrint = true
    encodeDefaults = true
}

data class ScanResult(
    val offers: List<Offer> = emptyList(),
    val results: List<SourceResult> = emptyList(),
    val spot: SpotPrice? = null,
    val scannedAtMs: Long = 0
)

class Repository(private val ctx: Context) {

    private val prefs = ctx.getSharedPreferences("btcradar", Context.MODE_PRIVATE)

    // ---------- источники ----------

    fun loadSources(): List<SourceConfig> {
        val raw = prefs.getString(KEY_SOURCES, null) ?: return DefaultSources.all()
        return runCatching {
            json.decodeFromString(ListSerializer(SourceConfig.serializer()), raw)
        }.getOrElse { DefaultSources.all() }
    }

    fun saveSources(list: List<SourceConfig>) {
        prefs.edit()
            .putString(KEY_SOURCES, json.encodeToString(ListSerializer(SourceConfig.serializer()), list))
            .apply()
    }

    fun exportSources(): String =
        json.encodeToString(ListSerializer(SourceConfig.serializer()), loadSources())

    /** Импорт конфига. Падать не должен — возвращаем понятную ошибку. */
    fun importSources(raw: String): Result<List<SourceConfig>> = runCatching {
        val parsed = json.decodeFromString(ListSerializer(SourceConfig.serializer()), raw)
        require(parsed.isNotEmpty()) { "В конфиге нет ни одного источника" }
        val ids = parsed.map { it.id }
        require(ids.size == ids.distinct().size) { "Дублирующиеся id источников" }
        saveSources(parsed)
        parsed
    }

    fun resetSources(): List<SourceConfig> {
        val d = DefaultSources.all()
        saveSources(d)
        return d
    }

    // ---------- настройки ----------

    fun loadTor(): TorSettings = TorSettings(
        mode = runCatching { TorMode.valueOf(prefs.getString(KEY_TOR_MODE, TorMode.OFF.name)!!) }
            .getOrDefault(TorMode.OFF),
        host = prefs.getString(KEY_TOR_HOST, "127.0.0.1") ?: "127.0.0.1",
        httpPort = prefs.getInt(KEY_TOR_HTTP, 8118),
        socksPort = prefs.getInt(KEY_TOR_SOCKS, 9050)
    )

    fun saveTor(s: TorSettings) {
        prefs.edit()
            .putString(KEY_TOR_MODE, s.mode.name)
            .putString(KEY_TOR_HOST, s.host)
            .putInt(KEY_TOR_HTTP, s.httpPort)
            .putInt(KEY_TOR_SOCKS, s.socksPort)
            .apply()
    }

    fun loadAmountRub(): Double = prefs.getFloat(KEY_AMOUNT_RUB, 100_000f).toDouble()
    fun saveAmountRub(v: Double) = prefs.edit().putFloat(KEY_AMOUNT_RUB, v.toFloat()).apply()

    fun loadAmountBtc(): Double = prefs.getFloat(KEY_AMOUNT_BTC, 0.01f).toDouble()
    fun saveAmountBtc(v: Double) = prefs.edit().putFloat(KEY_AMOUNT_BTC, v.toFloat()).apply()

    fun loadHideUnusable(): Boolean = prefs.getBoolean(KEY_HIDE_UNUSABLE, false)
    fun saveHideUnusable(v: Boolean) = prefs.edit().putBoolean(KEY_HIDE_UNUSABLE, v).apply()

    // ---------- опрос ----------

    /**
     * Опрашиваем все включённые источники параллельно.
     * Один упавший источник не должен ломать остальные — каждый
     * возвращает свой SourceResult с ошибкой внутри.
     */
    suspend fun scan(
        sources: List<SourceConfig>,
        tor: TorSettings,
        torLive: Boolean
    ): ScanResult = coroutineScope {
        val enabled = sources.filter { it.enabled }

        val jobs = enabled.map { cfg ->
            async {
                val useTor = cfg.usesTor(torLive)
                val client = TorHttp.clientFor(useTor, tor, cfg.timeoutSec)
                Fetchers.fetch(cfg, client, useTor)
            }
        }

        val outcomes = jobs.awaitAll()
        ScanResult(
            offers = outcomes.flatMap { it.result.offers },
            results = outcomes.map { it.result },
            spot = outcomes.firstNotNullOfOrNull { it.spot },
            scannedAtMs = System.currentTimeMillis()
        )
    }

    /** Проверка одного источника — кнопка «Тест» в редакторе. */
    suspend fun testSource(cfg: SourceConfig, tor: TorSettings, torLive: Boolean): SourceResult {
        val useTor = cfg.usesTor(torLive)
        val client = TorHttp.clientFor(useTor, tor, cfg.timeoutSec)
        return Fetchers.fetch(cfg, client, useTor).result
    }

    // ---------- алерты ----------

    fun loadAlerts(): List<Alert> {
        val raw = prefs.getString(KEY_ALERTS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(Alert.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    fun saveAlerts(list: List<Alert>) {
        prefs.edit()
            .putString(KEY_ALERTS, json.encodeToString(ListSerializer(Alert.serializer()), list))
            .apply()
    }

    fun loadWatchEnabled(): Boolean = prefs.getBoolean(KEY_WATCH, false)
    fun saveWatchEnabled(v: Boolean) = prefs.edit().putBoolean(KEY_WATCH, v).apply()

    fun loadWatchPeriodMin(): Long = prefs.getLong(KEY_WATCH_PERIOD, 15L)
    fun saveWatchPeriodMin(v: Long) = prefs.edit().putLong(KEY_WATCH_PERIOD, v).apply()

    // ---------- ассистент ----------

    fun loadAssistant(): AssistantSettings {
        val raw = prefs.getString(KEY_ASSISTANT, null) ?: return AssistantSettings()
        return runCatching {
            json.decodeFromString(AssistantSettings.serializer(), raw)
        }.getOrDefault(AssistantSettings())
    }

    fun saveAssistant(s: AssistantSettings) {
        prefs.edit()
            .putString(KEY_ASSISTANT, json.encodeToString(AssistantSettings.serializer(), s))
            .apply()
    }

    private companion object {
        const val KEY_SOURCES = "sources_json"
        const val KEY_TOR_MODE = "tor_mode"
        const val KEY_TOR_HOST = "tor_host"
        const val KEY_TOR_HTTP = "tor_http_port"
        const val KEY_TOR_SOCKS = "tor_socks_port"
        const val KEY_AMOUNT_RUB = "amount_rub"
        const val KEY_AMOUNT_BTC = "amount_btc"
        const val KEY_HIDE_UNUSABLE = "hide_unusable"
        const val KEY_ALERTS = "alerts_json"
        const val KEY_WATCH = "watch_enabled"
        const val KEY_WATCH_PERIOD = "watch_period_min"
        const val KEY_ASSISTANT = "assistant_json"
    }
}
