package ru.btcradar.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.btcradar.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(
    state: UiState,
    testResult: SourceResult?,
    onBack: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onUpsert: (SourceConfig) -> Unit,
    onDelete: (String) -> Unit,
    onTest: (SourceConfig) -> Unit,
    onClearTest: () -> Unit,
    onExport: () -> String,
    onImport: (String) -> Unit,
    onReset: () -> Unit
) {
    var editing by remember { mutableStateOf<SourceConfig?>(null) }
    var showImport by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Источники", fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
                actions = {
                    IconButton(onClick = { showExport = true }) {
                        Icon(Icons.Default.Upload, "Экспорт")
                    }
                    IconButton(onClick = { showImport = true }) {
                        Icon(Icons.Default.Download, "Импорт")
                    }
                    IconButton(onClick = onReset) {
                        Icon(Icons.Default.RestartAlt, "Сброс")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editing = SourceConfig(
                        id = "src_" + System.currentTimeMillis().toString().takeLast(6),
                        title = "Новый источник",
                        kind = SourceKind.JSON,
                        enabled = true,
                        json = JsonMapping()
                    )
                },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Добавить") }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Text(
                "Приложение опрашивает только то, что ты сюда добавишь. " +
                        "Для .onion заполни поле «Onion URL» и включи Tor.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )

            LazyColumn(
                contentPadding = PaddingValues(bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 10.dp)
            ) {
                items(state.sources, key = { it.id }) { cfg ->
                    SourceRow(
                        cfg = cfg,
                        result = state.scan.results.firstOrNull { it.sourceId == cfg.id },
                        onToggle = { onToggle(cfg.id, it) },
                        onEdit = { editing = cfg },
                        onDelete = { onDelete(cfg.id) }
                    )
                }
            }
        }
    }

    editing?.let { cfg ->
        SourceEditor(
            cfg = cfg,
            testResult = testResult,
            onDismiss = { editing = null; onClearTest() },
            onSave = { onUpsert(it); editing = null; onClearTest() },
            onTest = onTest
        )
    }

    if (showExport) {
        val text = remember { onExport() }
        TextDialog(
            title = "Экспорт конфига",
            initial = text,
            readOnly = true,
            onDismiss = { showExport = false },
            onConfirm = { showExport = false }
        )
    }

    if (showImport) {
        TextDialog(
            title = "Импорт конфига (JSON)",
            initial = "",
            readOnly = false,
            onDismiss = { showImport = false },
            onConfirm = { onImport(it); showImport = false }
        )
    }
}

@Composable
private fun SourceRow(
    cfg: SourceConfig,
    result: SourceResult?,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(checked = cfg.enabled, onCheckedChange = onToggle)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    cfg.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append(cfg.kind.name)
                        if (!cfg.onionUrl.isNullOrBlank()) append(" · onion")
                        if (cfg.requireTor) append(" · только Tor")
                    },
                    style = MonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                result?.let { r ->
                    Text(
                        if (r.error != null) "✕ ${r.error}"
                        else "✓ ${r.offers.size} предл. за ${r.durationMs} мс",
                        style = MonoSmall,
                        color = if (r.error != null) Bad else Good,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Править") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Удалить", tint = Bad) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceEditor(
    cfg: SourceConfig,
    testResult: SourceResult?,
    onDismiss: () -> Unit,
    onSave: (SourceConfig) -> Unit,
    onTest: (SourceConfig) -> Unit
) {
    var c by remember(cfg.id) { mutableStateOf(cfg) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onSave(c) }) { Text("Сохранить") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onTest(c) }) { Text("Тест") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
        title = { Text("Источник", fontSize = 16.sp) },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Field("Название", c.title) { c = c.copy(title = it) }
                Field("ID (уникальный)", c.id) { c = c.copy(id = it) }

                Text("Тип парсера", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SourceKind.entries.forEach { k ->
                        FilterChip(
                            selected = c.kind == k,
                            onClick = {
                                c = when (k) {
                                    SourceKind.JSON, SourceKind.SPOT_JSON ->
                                        c.copy(kind = k, json = c.json ?: JsonMapping())
                                    SourceKind.HTML ->
                                        c.copy(kind = k, html = c.html ?: HtmlMapping(rowSelector = ""))
                                    else -> c.copy(kind = k)
                                }
                            },
                            label = { Text(k.name, fontSize = 10.sp) }
                        )
                    }
                }

                Field("URL (https)", c.url) { c = c.copy(url = it) }
                Field("Onion URL (необязательно)", c.onionUrl ?: "") {
                    c = c.copy(onionUrl = it.ifBlank { null })
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = c.requireTor,
                        onCheckedChange = { c = c.copy(requireTor = it) }
                    )
                    Text("Только через Tor", fontSize = 12.sp)
                }

                NumField("Комиссия, %", c.feePercent) { c = c.copy(feePercent = it) }

                when (c.kind) {
                    SourceKind.JSON, SourceKind.SPOT_JSON -> {
                        val m = c.json ?: JsonMapping()
                        HorizontalDivider()
                        Text("JSON-пути (точечная нотация, можно индексы: a.b[0].c)", fontSize = 11.sp)
                        Field("rows_path (массив предложений)", m.rowsPath) {
                            c = c.copy(json = m.copy(rowsPath = it))
                        }
                        Field("name_path", m.namePath ?: "") {
                            c = c.copy(json = m.copy(namePath = it.ifBlank { null }))
                        }
                        Field("buy_path (цена покупки BTC)", m.buyPath ?: "") {
                            c = c.copy(json = m.copy(buyPath = it.ifBlank { null }))
                        }
                        Field("sell_path (цена продажи BTC)", m.sellPath ?: "") {
                            c = c.copy(json = m.copy(sellPath = it.ifBlank { null }))
                        }
                        Field("min_in_path", m.minInPath ?: "") {
                            c = c.copy(json = m.copy(minInPath = it.ifBlank { null }))
                        }
                        Field("max_in_path", m.maxInPath ?: "") {
                            c = c.copy(json = m.copy(maxInPath = it.ifBlank { null }))
                        }
                        Field("reserve_path", m.reservePath ?: "") {
                            c = c.copy(json = m.copy(reservePath = it.ifBlank { null }))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = m.inverted,
                                onCheckedChange = { c = c.copy(json = m.copy(inverted = it)) }
                            )
                            Text("Курс перевёрнут (BTC за рубль)", fontSize = 11.sp)
                        }
                        NumField("Множитель", m.multiplier) {
                            c = c.copy(json = m.copy(multiplier = it))
                        }
                    }

                    SourceKind.HTML -> {
                        val m = c.html ?: HtmlMapping(rowSelector = "")
                        HorizontalDivider()
                        Text("CSS-селекторы", fontSize = 11.sp)
                        Field("row_selector *", m.rowSelector) {
                            c = c.copy(html = m.copy(rowSelector = it))
                        }
                        Field("name_selector", m.nameSelector ?: "") {
                            c = c.copy(html = m.copy(nameSelector = it.ifBlank { null }))
                        }
                        Field("buy_selector", m.buySelector ?: "") {
                            c = c.copy(html = m.copy(buySelector = it.ifBlank { null }))
                        }
                        Field("sell_selector", m.sellSelector ?: "") {
                            c = c.copy(html = m.copy(sellSelector = it.ifBlank { null }))
                        }
                        Field("reserve_selector", m.reserveSelector ?: "") {
                            c = c.copy(html = m.copy(reserveSelector = it.ifBlank { null }))
                        }
                        Field("url_selector", m.urlSelector ?: "") {
                            c = c.copy(html = m.copy(urlSelector = it.ifBlank { null }))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = m.inverted,
                                onCheckedChange = { c = c.copy(html = m.copy(inverted = it)) }
                            )
                            Text("Курс перевёрнут", fontSize = 11.sp)
                        }
                    }

                    SourceKind.BESTCHANGE -> {
                        Text(
                            "Встроенный адаптер под выгрузку BestChange. " +
                                    "Нужен только URL до info.zip.",
                            fontSize = 11.sp
                        )
                    }
                }

                testResult?.let { r ->
                    HorizontalDivider()
                    Text(
                        if (r.error != null) "Тест: ✕ ${r.error}"
                        else "Тест: ✓ получено ${r.offers.size} предложений за ${r.durationMs} мс",
                        style = MonoSmall,
                        color = if (r.error != null) Bad else Good
                    )
                    r.offers.take(3).forEach {
                        Text(
                            "  ${it.exchangerName}: ${fmtRub(it.rubPerBtc)} (${it.direction})",
                            style = MonoSmall
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun Field(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, fontSize = 10.sp) },
        singleLine = true,
        textStyle = MonoSmall,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun NumField(label: String, value: Double, onValue: (Double) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.replace(',', '.').toDoubleOrNull()?.let(onValue)
        },
        label = { Text(label, fontSize = 10.sp) },
        singleLine = true,
        textStyle = MonoSmall,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun TextDialog(
    title: String,
    initial: String,
    readOnly: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 15.sp) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (!readOnly) text = it },
                readOnly = readOnly,
                textStyle = MonoSmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 400.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(if (readOnly) "Готово" else "Импортировать")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
