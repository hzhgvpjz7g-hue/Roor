package ru.btcradar.ui

import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import ru.btcradar.browser.BrowserEngine
import ru.btcradar.browser.TorWebView
import ru.btcradar.net.TorMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    state: UiState,
    startUrl: String,
    onBack: () -> Unit,
    onStartOrbot: () -> Unit,
    /** host текущей страницы — для автозаполнения (модуль паролей). */
    onHostChanged: (String) -> Unit = {},
    /** Логин/пароль для подстановки, если пользователь нажал автозаполнение. */
    autofill: Pair<String, String>? = null,
    onAutofillConsumed: () -> Unit = {},
    onRequestAutofill: (String) -> Unit = {},
    hasCredentialForHost: Boolean = false
) {
    val ctx = LocalContext.current
    var address by remember { mutableStateOf(startUrl) }
    var currentUrl by remember { mutableStateOf(startUrl) }
    var loading by remember { mutableStateOf(false) }
    var proxyOn by remember { mutableStateOf(false) }
    var webRef by remember { mutableStateOf<TorWebView?>(null) }

    val torLive = state.tor.live
    val proxySupported = remember { BrowserEngine.proxySupported() }

    // Применяем прокси Orbot к WebView, когда Tor поднят
    LaunchedEffect(torLive, state.tor.settings) {
        if (torLive) {
            BrowserEngine.applyProxy(state.tor.settings) { ok -> proxyOn = ok }
        } else {
            BrowserEngine.clearProxy { proxyOn = false }
        }
    }

    // Автозаполнение: инъекция логина/пароля в поля страницы
    LaunchedEffect(autofill) {
        val w = webRef
        val creds = autofill
        if (w != null && creds != null) {
            w.evaluateJavascript(autofillJs(creds.first, creds.second), null)
            onAutofillConsumed()
        }
    }

    DisposableEffect(Unit) {
        onDispose { BrowserEngine.clearProxy() }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        val host = runCatching { android.net.Uri.parse(currentUrl).host }.getOrNull()
                        Column {
                            Text("Браузер через Tor", fontSize = 15.sp)
                            Text(
                                when {
                                    !proxySupported -> "WebView без поддержки прокси — трафик НЕ через Tor"
                                    proxyOn -> "через Tor · ${host ?: ""}"
                                    torLive -> "прокси не применился · ${host ?: ""}"
                                    else -> "БЕЗ Tor · включи Tor в разделе 🧅"
                                },
                                style = MonoSmall,
                                color = if (proxyOn) TorPurple else Warn
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                    },
                    actions = {
                        if (hasCredentialForHost) {
                            IconButton(onClick = {
                                runCatching { android.net.Uri.parse(currentUrl).host }
                                    .getOrNull()?.let(onRequestAutofill)
                            }) { Icon(Icons.Default.Password, "Автозаполнение") }
                        }
                        IconButton(onClick = { BrowserEngine.wipeSession(webRef ?: return@IconButton) }) {
                            Icon(Icons.Default.DeleteSweep, "Стереть сессию")
                        }
                    }
                )

                // Адресная строка
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        singleLine = true,
                        textStyle = MonoSmall,
                        placeholder = { Text("адрес или поиск", fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        val url = BrowserEngine.normalizeUrl(address)
                        address = url
                        webRef?.loadUrl(url)
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Перейти") }
                }

                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        bottomBar = {
            BottomAppBar(Modifier.height(48.dp)) {
                IconButton(onClick = { webRef?.takeIf { it.canGoBack() }?.goBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                }
                IconButton(onClick = { webRef?.takeIf { it.canGoForward() }?.goForward() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Вперёд")
                }
                IconButton(onClick = { webRef?.reload() }) { Icon(Icons.Default.Refresh, "Обновить") }
                Spacer(Modifier.weight(1f))
                if (!torLive) {
                    TextButton(onClick = onStartOrbot) { Text("Включить Tor", fontSize = 12.sp) }
                }
            }
        }
    ) { pad ->
        AndroidView(
            modifier = Modifier.padding(pad).fillMaxSize(),
            factory = {
                TorWebView(ctx).apply {
                    BrowserEngine.configure(this, BrowserEngine.Hardening())
                    webViewClient = BrowserEngine.client(
                        onPageStarted = { loading = true },
                        onPageFinished = { url ->
                            loading = false
                            currentUrl = url
                            address = url
                            runCatching { android.net.Uri.parse(url).host }
                                .getOrNull()?.let(onHostChanged)
                        }
                    )
                    webRef = this
                    loadUrl(BrowserEngine.normalizeUrl(startUrl))
                }
            }
        )
    }
}

/** JS для подстановки логина/пароля в типовые поля формы. */
private fun autofillJs(login: String, password: String): String {
    val l = login.replace("\\", "\\\\").replace("'", "\\'")
    val p = password.replace("\\", "\\\\").replace("'", "\\'")
    return """
        (function(){
          function set(el,val){ if(!el) return false;
            el.focus(); el.value=val;
            el.dispatchEvent(new Event('input',{bubbles:true}));
            el.dispatchEvent(new Event('change',{bubbles:true})); return true; }
          var u = document.querySelector("input[type=email],input[name*=login i],input[name*=user i],input[id*=login i],input[id*=user i],input[autocomplete=username]");
          var pw = document.querySelector("input[type=password]");
          var a = set(u,'$l'); var b = set(pw,'$p');
          return (a||b) ? 'ok' : 'nofields';
        })();
    """.trimIndent()
}
