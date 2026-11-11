package ru.btcradar.net

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Режим подключения к Tor.
 *
 * ВАЖНО, и это главная грабля всей затеи:
 *
 * OkHttp с Proxy.Type.SOCKS отдаёт прокси уже РАЗРЕШЁННЫЙ IP-адрес,
 * потому что DNS-резолв происходит до создания сокета. Для .onion это
 * смертельно: такого адреса нет в обычном DNS, резолв падает, и запрос
 * не уходит вообще. Плюс в clearnet это утечка DNS мимо Tor.
 *
 * Proxy.Type.HTTP ведёт себя иначе: OkHttp шлёт прокси
 * "CONNECT hostname:port", то есть ИМЯ, а не адрес. Резолвом занимается
 * сам Tor. Поэтому .onion работает именно через HTTP-прокси Orbot (8118),
 * а не через SOCKS (9050).
 *
 * Отсюда дефолт — HTTP_PROXY. SOCKS оставлен для clearnet-трафика
 * через Tor, где remote DNS не принципиален.
 */
enum class TorMode(val label: String) {
    OFF("Выключен"),
    HTTP_PROXY("Orbot HTTP (.onion работает)"),
    SOCKS("Orbot SOCKS (только clearnet)")
}

data class TorSettings(
    val mode: TorMode = TorMode.OFF,
    val host: String = "127.0.0.1",
    val httpPort: Int = 8118,
    val socksPort: Int = 9050
) {
    val port: Int get() = if (mode == TorMode.SOCKS) socksPort else httpPort
}

object TorHttp {

    private const val ORBOT_PACKAGE = "org.torproject.android"
    private const val ORBOT_START_ACTION = "org.torproject.android.intent.action.START"

    /** Обычный клиент — напрямую. */
    fun directClient(timeoutSec: Long = 30): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(timeoutSec, TimeUnit.SECONDS)
            .readTimeout(timeoutSec, TimeUnit.SECONDS)
            .callTimeout(timeoutSec * 2, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    /** Клиент через Tor. Таймауты щедрее — onion-ресурсы медленные. */
    fun torClient(settings: TorSettings, timeoutSec: Long = 60): OkHttpClient {
        val type = when (settings.mode) {
            TorMode.SOCKS -> Proxy.Type.SOCKS
            else -> Proxy.Type.HTTP
        }
        val proxy = Proxy(type, InetSocketAddress(settings.host, settings.port))
        return OkHttpClient.Builder()
            .proxy(proxy)
            .connectTimeout(timeoutSec, TimeUnit.SECONDS)
            .readTimeout(timeoutSec, TimeUnit.SECONDS)
            .callTimeout(timeoutSec * 2, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun clientFor(useTor: Boolean, settings: TorSettings, timeoutSec: Long): OkHttpClient =
        if (useTor && settings.mode != TorMode.OFF) torClient(settings, timeoutSec)
        else directClient(timeoutSec)

    /** Слушает ли вообще кто-то на порту прокси. Быстрая проверка, без Tor-рукопожатия. */
    suspend fun isProxyListening(settings: TorSettings): Boolean = withContext(Dispatchers.IO) {
        if (settings.mode == TorMode.OFF) return@withContext false
        runCatching {
            Socket().use { s ->
                s.connect(InetSocketAddress(settings.host, settings.port), 2500)
                true
            }
        }.getOrDefault(false)
    }

    /**
     * Полноценная проверка: реально ли трафик идёт через Tor.
     * Порт может слушать что угодно — убеждаемся, что это именно Tor.
     */
    suspend fun verifyTor(settings: TorSettings): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val client = torClient(settings, timeoutSec = 45)
            val req = Request.Builder()
                .url("https://check.torproject.org/api/ip")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val isTor = body.contains("\"IsTor\":true", ignoreCase = true) ||
                        body.contains("\"IsTor\": true", ignoreCase = true)
                if (!isTor) error("Прокси отвечает, но трафик идёт НЕ через Tor: $body")
                body
            }
        }
    }

    fun isOrbotInstalled(ctx: Context): Boolean =
        runCatching {
            ctx.packageManager.getPackageInfo(ORBOT_PACKAGE, 0)
            true
        }.getOrDefault(false)

    /** Просим Orbot подняться. Он сам покажет пользователю диалог согласия. */
    fun requestOrbotStart(ctx: Context) {
        runCatching {
            val i = Intent(ORBOT_START_ACTION).apply {
                setPackage(ORBOT_PACKAGE)
                putExtra("org.torproject.android.intent.extra.PACKAGE_NAME", ctx.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(i)
        }.onFailure {
            runCatching {
                val launch = ctx.packageManager.getLaunchIntentForPackage(ORBOT_PACKAGE)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (launch != null) ctx.startActivity(launch)
            }
        }
    }

    fun orbotPlayStoreIntent(): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse("https://orbot.app/")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    @Suppress("unused")
    fun pmFlagsCompat(): Int = PackageManager.GET_META_DATA
}
