package ru.btcradar.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.btcradar.net.TorMode
import ru.btcradar.net.TorSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorScreen(
    tor: TorState,
    onBack: () -> Unit,
    onSettings: (TorSettings) -> Unit,
    onRefreshStatus: () -> Unit,
    onVerify: () -> Unit,
    onStartOrbot: () -> Unit
) {
    val s = tor.settings

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tor", fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            StatusCard(tor)

            Text("Режим", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            TorMode.entries.forEach { mode ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = s.mode == mode,
                        onClick = { onSettings(s.copy(mode = mode)) }
                    )
                    Column {
                        Text(mode.label, fontSize = 13.sp)
                        if (mode == TorMode.SOCKS) {
                            Text(
                                "SOCKS не умеет удалённый DNS в OkHttp — .onion через него не откроется",
                                style = MonoSmall,
                                color = Warn
                            )
                        }
                        if (mode == TorMode.HTTP_PROXY) {
                            Text(
                                "Рекомендуется: имя хоста резолвит сам Tor",
                                style = MonoSmall,
                                color = Good
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            Text("Адрес прокси", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            PortField("Хост", s.host) { onSettings(s.copy(host = it)) }
            NumPort("HTTP-порт (Orbot: 8118)", s.httpPort) { onSettings(s.copy(httpPort = it)) }
            NumPort("SOCKS-порт (Orbot: 9050)", s.socksPort) { onSettings(s.copy(socksPort = it)) }

            HorizontalDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRefreshStatus, modifier = Modifier.weight(1f)) {
                    Text("Проверить порт", fontSize = 12.sp)
                }
                Button(
                    onClick = onVerify,
                    enabled = !tor.checking && s.mode != TorMode.OFF,
                    modifier = Modifier.weight(1f)
                ) {
                    if (tor.checking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Проверить Tor", fontSize = 12.sp)
                }
            }

            if (tor.orbotInstalled) {
                Button(onClick = onStartOrbot, modifier = Modifier.fillMaxWidth()) {
                    Text("Запустить Orbot")
                }
            } else {
                Card(colors = CardDefaults.cardColors(containerColor = Warn.copy(alpha = 0.12f))) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Orbot не установлен", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            "Приложение не тащит с собой Tor-бинарник — оно ходит через прокси Orbot. " +
                                    "Поставь Orbot (orbot.app, F-Droid или Google Play), включи его, " +
                                    "и в его настройках разреши доступ этому приложению.",
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Трезво о приватности", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        "Tor прячет твой IP от сайта — и всё. Он не делает анонимной саму сделку. " +
                                "Если ты отправляешь рубли со своей карты или проходишь верификацию, " +
                                "вся цепочка всё равно привязана к тебе, хоть через десять узлов заходи.",
                        fontSize = 12.sp
                    )
                    Text(
                        "И отдельно: .onion-обменники — зона с концентрацией скама сильно выше средней. " +
                                "Хороший курс там часто и есть приманка. Проверяй на мелких суммах.",
                        fontSize = 12.sp,
                        color = Warn
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(tor: TorState) {
    val (text, color) = when {
        tor.settings.mode == TorMode.OFF -> "Tor выключен — всё идёт напрямую" to MaterialTheme.colorScheme.onSurfaceVariant
        tor.verified -> "Tor подтверждён" to TorPurple
        tor.proxyListening -> "Прокси отвечает, но цепочка не проверена" to Warn
        else -> "Прокси недоступен" to Bad
    }
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))) {
        Column(Modifier.padding(12.dp)) {
            Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = color)
            tor.message?.let {
                Text(it, style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PortField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        textStyle = MonoSmall,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun NumPort(label: String, value: Int, onValue: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.toIntOrNull()?.takeIf { p -> p in 1..65535 }?.let(onValue)
        },
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        textStyle = MonoSmall,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}
