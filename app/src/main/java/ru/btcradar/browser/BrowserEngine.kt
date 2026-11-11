package ru.btcradar.browser

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import ru.btcradar.net.TorSettings

/**
 * Настройка WebView под приватный сёрфинг через Tor.
 *
 * Трафик WebView заворачиваем в HTTP-прокси Orbot (по умолчанию 8118) через
 * androidx.webkit ProxyController. Именно HTTP-прокси, а не SOCKS — ровно по
 * той же причине, что и в остальном приложении: при HTTP-прокси имя хоста
 * резолвит сам Tor, поэтому .onion открывается, а DNS не утекает мимо Tor.
 *
 * Это НЕ настоящий Tor Browser (тот — отдельное Firefox-приложение). Это
 * закалённый WebView, который ходит через Tor. Фингерпринт у него другой,
 * чем у Tor Browser, так что для максимальной анонимности серьёзных задач
 * всё равно используй настоящий Tor Browser (кнопка на него есть в разделе Tor).
 */
object BrowserEngine {

    /** Юзер-агент как у десктопного Tor Browser — чтобы меньше выделяться. */
    const val TOR_BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"

    data class Hardening(
        val javascriptEnabled: Boolean = true,   // как в Tor Browser по умолчанию
        val blockThirdPartyCookies: Boolean = true,
        val incognitoKeyboard: Boolean = true,
        val spoofUserAgent: Boolean = true
    )

    fun proxySupported(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)

    /**
     * Направить ВЕСЬ трафик WebView в прокси Orbot. Возвращает true, если
     * прокси применён. Если фича не поддерживается — честно false, и браузер
     * пойдёт напрямую (об этом UI предупредит).
     */
    fun applyProxy(settings: TorSettings, onDone: (Boolean) -> Unit) {
        if (!proxySupported()) { onDone(false); return }
        val host = settings.host
        val port = settings.httpPort   // всегда HTTP-порт — ради remote DNS и .onion
        val config = ProxyConfig.Builder()
            .addProxyRule("$host:$port")
            .addDirect()                // если прокси недоступен — не валимся (но это утечка!)
            .build()
        runCatching {
            ProxyController.getInstance().setProxyOverride(
                config,
                { r -> r.run() },
                { onDone(true) }
            )
        }.onFailure { onDone(false) }
    }

    fun clearProxy(onDone: () -> Unit = {}) {
        if (!proxySupported()) { onDone(); return }
        runCatching {
            ProxyController.getInstance().clearProxyOverride({ r -> r.run() }, { onDone() })
        }.onFailure { onDone() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(web: TorWebView, h: Hardening) {
        web.settings.apply {
            javaScriptEnabled = h.javascriptEnabled
            domStorageEnabled = h.javascriptEnabled
            if (h.spoofUserAgent) userAgentString = TOR_BROWSER_UA

            // Меньше поверхности для фингерпринтинга
            setGeolocationEnabled(false)
            databaseEnabled = false
            saveFormData = false
            savePassword = false
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_NO_CACHE
            builtInZoomControls = true
            displayZoomControls = false
        }
        web.incognitoKeyboard = h.incognitoKeyboard

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, !h.blockThirdPartyCookies)
        }
    }

    /** Полная зачистка следов сессии. */
    fun wipeSession(web: WebView) {
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            web.clearCache(true)
            web.clearHistory()
            web.clearFormData()
            android.webkit.WebStorage.getInstance().deleteAllData()
        }
    }

    /** Простой клиент: держим навигацию внутри WebView и отдаём колбэки. */
    fun client(
        onPageStarted: (String) -> Unit,
        onPageFinished: (String) -> Unit
    ): WebViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
            view.loadUrl(req.url.toString())
            return true
        }
        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            onPageStarted(url)
        }
        override fun onPageFinished(view: WebView, url: String) {
            onPageFinished(url)
        }
    }

    /** Нормализуем ввод из адресной строки: запрос -> поиск, домен -> https. */
    fun normalizeUrl(input: String): String {
        val t = input.trim()
        if (t.isBlank()) return "about:blank"
        if (t.startsWith("http://") || t.startsWith("https://")) return t
        // похоже на домен (есть точка и нет пробелов) или .onion
        val looksLikeHost = !t.contains(' ') && (t.contains('.') || t.endsWith(".onion"))
        return if (looksLikeHost) "https://$t"
        else "https://duckduckgogg42xjoc72x3sjasowoarfbgcmvfimaftt6twagswzczad.onion/?q=" +
                java.net.URLEncoder.encode(t, "UTF-8")
    }
}
