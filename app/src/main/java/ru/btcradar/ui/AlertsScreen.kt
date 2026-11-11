package ru.btcradar.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.btcradar.model.Alert
import ru.btcradar.model.AlertKind
import ru.btcradar.model.Direction
import ru.btcradar.voice.AssistantSettings
import ru.btcradar.voice.VoiceGender

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    state: UiState,
    onBack: () -> Unit,
    onAddAlert: (Alert) -> Unit,
    onUpdateAlert: (Alert) -> Unit,
    onDeleteAlert: (String) -> Unit,
    onWatchEnabled: (Boolean) -> Unit,
    onWatchPeriod: (Long) -> Unit,
    onAssistant: (AssistantSettings) -> Unit,
    onSpeakTest: () -> Unit,
    onEnrollVoice: () -> Unit,
    onResetVoice: () -> Unit,
    onVoiceCommand: () -> Unit,
    onMicGranted: () -> Unit
) {
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Алерты и ассистент", fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showEditor = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Новый алерт") }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 90.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { WatchCard(state, onWatchEnabled, onWatchPeriod) }
            item {
                AssistantCard(
                    a = state.assistant,
                    voice = state.voice,
                    onAssistant = onAssistant,
                    onSpeakTest = onSpeakTest,
                    onEnrollVoice = onEnrollVoice,
                    onResetVoice = onResetVoice,
                    onVoiceCommand = onVoiceCommand,
                    onMicGranted = onMicGranted
                )
            }

            item {
                Text(
                    "Мои алерты (${state.alerts.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (state.alerts.isEmpty()) {
                item {
                    Text(
                        "Пока пусто. Жми «Новый алерт» — например, «продажа BTC где угодно дороже 9 700 000 ₽».",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(state.alerts, key = { it.id }) { alert ->
                AlertRow(alert, onToggle = { onUpdateAlert(alert.copy(enabled = it)) },
                    onDelete = { onDeleteAlert(alert.id) })
            }
        }
    }

    if (showEditor) {
        AlertEditor(
            onDismiss = { showEditor = false },
            onSave = { onAddAlert(it); showEditor = false }
        )
    }
}

@Composable
private fun WatchCard(
    state: UiState,
    onWatchEnabled: (Boolean) -> Unit,
    onWatchPeriod: (Long) -> Unit
) {
    Card {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Фоновый мониторинг", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "Проверяет курсы, даже когда приложение закрыто",
                        style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = state.watchEnabled, onCheckedChange = onWatchEnabled)
            }
            if (state.watchEnabled) {
                Spacer(Modifier.height(6.dp))
                Text("Период: ${state.watchPeriodMin} мин", fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15L, 30L, 60L, 180L).forEach { p ->
                        FilterChip(
                            selected = state.watchPeriodMin == p,
                            onClick = { onWatchPeriod(p) },
                            label = { Text("$p", fontSize = 11.sp) }
                        )
                    }
                }
                Text(
                    "Android не разрешает чаще 15 мин, а в спящем режиме может реже. " +
                            "Для посекундного слежения нужен постоянный сервис с уведомлением.",
                    style = MonoSmall, color = Warn, modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun AssistantCard(
    a: AssistantSettings,
    voice: VoiceState,
    onAssistant: (AssistantSettings) -> Unit,
    onSpeakTest: () -> Unit,
    onEnrollVoice: () -> Unit,
    onResetVoice: () -> Unit,
    onVoiceCommand: () -> Unit,
    onMicGranted: () -> Unit
) {
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) onMicGranted() }

    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Голосовой ассистент", fontWeight = FontWeight.Bold,
                    fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(checked = a.enabled, onCheckedChange = { onAssistant(a.copy(enabled = it)) })
            }

            OutlinedTextField(
                value = a.name,
                onValueChange = { onAssistant(a.copy(name = it)) },
                label = { Text("Имя ассистента", fontSize = 11.sp) },
                singleLine = true,
                textStyle = MonoSmall,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Голос", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                VoiceGender.entries.forEach { g ->
                    val label = when (g) {
                        VoiceGender.ANY -> "Любой"
                        VoiceGender.MALE -> "Мужской"
                        VoiceGender.FEMALE -> "Женский"
                    }
                    FilterChip(
                        selected = a.genderEnum() == g,
                        onClick = { onAssistant(a.copy(gender = g.name)) },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = a.speakAlerts,
                    onCheckedChange = { onAssistant(a.copy(speakAlerts = it)) }
                )
                Text("Проговаривать срабатывания вслух", fontSize = 12.sp)
            }

            OutlinedButton(onClick = onSpeakTest, modifier = Modifier.fillMaxWidth()) {
                Text("Проверить голос")
            }

            HorizontalDivider()

            // ---- голосовые команды ----
            Text("Команды голосом", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

            if (!voice.micGranted) {
                Button(
                    onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Разрешить микрофон") }
            } else {
                Button(
                    onClick = onVoiceCommand,
                    enabled = !voice.busy && voice.speechAvailable,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Mic, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (voice.busy) "Слушаю…" else "Слушать команду")
                }
                if (!voice.speechAvailable) {
                    Text("Распознавание речи недоступно на этом устройстве",
                        style = MonoSmall, color = Warn)
                }
            }

            if (voice.status.isNotBlank()) {
                Text(voice.status, style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "Примеры: «поставь алерт на продажу дороже девять миллионов семьсот тысяч», " +
                        "«включи Tor», «обнови курсы», «открой браузер».",
                style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // ---- замок по голосу ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Замок по голосу", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (voice.enrolled) "Голос записан. Команды — только твоим голосом."
                        else "Сначала обучи — 3 раза произнеси имя ассистента.",
                        style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = a.voiceLock,
                    enabled = voice.enrolled,
                    onCheckedChange = { onAssistant(a.copy(voiceLock = it)) }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        if (!voice.micGranted) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        else onEnrollVoice()
                    },
                    enabled = !voice.busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (voice.enrolled) "Переобучить"
                        else "Обучить (${voice.enrollProgress}/3)",
                        fontSize = 12.sp
                    )
                }
                if (voice.enrolled) {
                    OutlinedButton(onClick = onResetVoice, modifier = Modifier.weight(1f)) {
                        Text("Сбросить", fontSize = 12.sp)
                    }
                }
            }
            voice.lastSimilarity?.let {
                Text("Последнее совпадение голоса: ${(it * 100).toInt()}%",
                    style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text(
                "Честно: замок по голосу — удобный фильтр, а не защита. Записью голоса его можно " +
                        "обмануть. Секреты в сейфе стережёт пароль, а не это.",
                style = MonoSmall, color = Warn
            )
        }
    }
}

@Composable
private fun AlertRow(alert: Alert, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    Card {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = alert.enabled, onCheckedChange = onToggle)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(alert.humanText(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    buildString {
                        append(if (alert.oneShot) "разовый" else "повтор, кулдаун ${alert.cooldownMin} мин")
                        if (alert.lastFiredMs > 0) append(" · сработал ${fmtAgo(alert.lastFiredMs)}")
                    },
                    style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Удалить", tint = Bad) }
        }
    }
}

@Composable
private fun AlertEditor(onDismiss: () -> Unit, onSave: (Alert) -> Unit) {
    var direction by remember { mutableStateOf(Direction.SELL_BTC) }
    var kind by remember { mutableStateOf(AlertKind.RATE_ABOVE) }
    var threshold by remember { mutableStateOf("9700000") }
    var exchanger by remember { mutableStateOf("") }
    var oneShot by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val thr = threshold.replace(" ", "").replace(',', '.').toDoubleOrNull()
                    if (thr != null && thr > 0) {
                        onSave(
                            Alert(
                                id = "a_" + System.currentTimeMillis().toString().takeLast(7),
                                direction = direction,
                                kind = kind,
                                thresholdRubPerBtc = thr,
                                exchangerContains = exchanger.trim(),
                                oneShot = oneShot
                            )
                        )
                    }
                }
            ) { Text("Создать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        title = { Text("Новый алерт", fontSize = 16.sp) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Направление", fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = direction == Direction.BUY_BTC,
                        onClick = { direction = Direction.BUY_BTC; kind = AlertKind.RATE_BELOW },
                        label = { Text("Купить BTC", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = direction == Direction.SELL_BTC,
                        onClick = { direction = Direction.SELL_BTC; kind = AlertKind.RATE_ABOVE },
                        label = { Text("Продать BTC", fontSize = 11.sp) }
                    )
                }

                Text("Условие", fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = kind == AlertKind.RATE_BELOW,
                        onClick = { kind = AlertKind.RATE_BELOW },
                        label = { Text("курс ниже", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = kind == AlertKind.RATE_ABOVE,
                        onClick = { kind = AlertKind.RATE_ABOVE },
                        label = { Text("курс выше", fontSize = 11.sp) }
                    )
                }

                OutlinedTextField(
                    value = threshold,
                    onValueChange = { threshold = it },
                    label = { Text("Порог, ₽ за 1 BTC", fontSize = 11.sp) },
                    singleLine = true,
                    textStyle = MonoNumber,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = exchanger,
                    onValueChange = { exchanger = it },
                    label = { Text("Обменник содержит (пусто = любой)", fontSize = 11.sp) },
                    singleLine = true,
                    textStyle = MonoSmall,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = oneShot, onCheckedChange = { oneShot = it })
                    Text("Сработать один раз", fontSize = 12.sp)
                }
            }
        }
    )
}
