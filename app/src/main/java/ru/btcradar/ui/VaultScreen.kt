package ru.btcradar.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.btcradar.vault.Credential
import ru.btcradar.vault.VaultFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    state: UiState,
    onBack: () -> Unit,
    onUnlock: (String) -> Unit,
    onLock: () -> Unit,
    onImportFile: (String, String, ByteArray) -> Unit,
    onExportFile: (String, Uri) -> Unit,
    onDeleteFile: (String) -> Unit,
    onAddCredential: (String, String, String) -> Unit,
    onDeleteCredential: (String) -> Unit
) {
    val v = state.vault

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (v.open) "Хранилище" else "Вход", fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = { if (v.open) onLock() else onBack() }) {
                        Icon(
                            if (v.open) Icons.Default.Lock else Icons.AutoMirrored.Filled.ArrowBack,
                            if (v.open) "Закрыть сейф" else "Назад"
                        )
                    }
                },
                actions = {
                    if (v.open) {
                        IconButton(onClick = onBack) { Icon(Icons.Default.Home, "На главную") }
                    }
                }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                !v.open -> PinPad(
                    needsSetup = !v.initialized,
                    setupStage = v.setupStage,
                    error = v.pinError,
                    onSubmit = onUnlock
                )
                else -> VaultContent(
                    files = v.files,
                    creds = v.credentials,
                    onImportFile = onImportFile,
                    onExportFile = onExportFile,
                    onDeleteFile = onDeleteFile,
                    onAddCredential = onAddCredential,
                    onDeleteCredential = onDeleteCredential
                )
            }
        }
    }
}

/**
 * PIN-пэд. Показываем РОВНО 4 точки. Буфер внутри может быть до 5 цифр —
 * пятая точка намеренно не отображается (как ты просил). «OK» отправляет
 * весь буфер: 4 цифры откроют ложный сейф, 5 — настоящий. Снаружи всё
 * выглядит одинаково.
 */
@Composable
private fun PinPad(
    needsSetup: Boolean,
    setupStage: Int,
    error: String?,
    onSubmit: (String) -> Unit
) {
    var buffer by remember { mutableStateOf("") }
    val filled = minOf(buffer.length, 4)

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(
            when {
                !needsSetup -> "Введи PIN"
                setupStage == 0 -> "Задай настоящий PIN (5 цифр)"
                else -> "Задай ложный PIN (4 цифры)"
            },
            fontWeight = FontWeight.Bold, fontSize = 18.sp
        )
        if (needsSetup) {
            Text(
                if (setupStage == 0)
                    "Введи 5 цифр и нажми ✓. Пятая точка не покажется — это норма."
                else
                    "Теперь 4 цифры для ложного сейфа и ✓. Его отдашь, если заставят.",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Spacer(Modifier.height(18.dp))

        // 4 точки
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { i ->
                Box(
                    Modifier.size(16.dp).clip(CircleShape)
                        .background(
                            if (i < filled) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            }
        }

        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = Bad, fontSize = 12.sp)
        }

        Spacer(Modifier.height(24.dp))

        // Клавиши
        val rows = listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"))
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { d -> PinKey(d) { if (buffer.length < 5) buffer += d } }
            }
            Spacer(Modifier.height(14.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // backspace
            PinKeyIcon(Icons.AutoMirrored.Filled.Backspace) {
                if (buffer.isNotEmpty()) buffer = buffer.dropLast(1)
            }
            PinKey("0") { if (buffer.length < 5) buffer += "0" }
            // OK
            PinKeyIcon(Icons.Default.Check) {
                if (buffer.isNotEmpty()) { onSubmit(buffer); buffer = "" }
            }
        }
    }
}

@Composable
private fun PinKey(digit: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(64.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(digit, fontSize = 24.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun PinKeyIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(64.dp)
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null) }
    }
}

@Composable
private fun VaultContent(
    files: List<VaultFile>,
    creds: List<Credential>,
    onImportFile: (String, String, ByteArray) -> Unit,
    onExportFile: (String, Uri) -> Unit,
    onDeleteFile: (String) -> Unit,
    onAddCredential: (String, String, String) -> Unit,
    onDeleteCredential: (String) -> Unit
) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var exportingId by remember { mutableStateOf<String?>(null) }
    var showAddCred by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val name = queryName(ctx, uri)
            val mime = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching
            onImportFile(name, mime, bytes)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val id = exportingId
        exportingId = null
        if (uri != null && id != null) onExportFile(id, uri)
    }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Файлы (${files.size})", fontSize = 12.sp) })
            Tab(tab == 1, { tab = 1 }, text = { Text("Пароли (${creds.size})", fontSize = 12.sp) })
        }

        if (tab == 0) {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    Button(onClick = { importLauncher.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Добавить файл")
                    }
                }
                if (files.isEmpty()) item {
                    Text("Пусто. Файлы здесь шифруются AES-256.", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(files, key = { it.id }) { f ->
                    Card {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.InsertDriveFile, null)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text("${f.size / 1024} КБ · ${fmtAgo(f.addedMs)}", style = MonoSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { exportingId = f.id; exportLauncher.launch(f.name) }) {
                                Icon(Icons.Default.Download, "Экспорт")
                            }
                            IconButton(onClick = { onDeleteFile(f.id) }) {
                                Icon(Icons.Default.Delete, "Удалить", tint = Bad)
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    Button(onClick = { showAddCred = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Добавить логин/пароль")
                    }
                }
                items(creds, key = { it.id }) { c ->
                    CredentialRow(c, onDelete = { onDeleteCredential(c.id) })
                }
            }
        }
    }

    if (showAddCred) {
        AddCredentialDialog(
            onDismiss = { showAddCred = false },
            onSave = { h, l, p -> onAddCredential(h, l, p); showAddCred = false }
        )
    }
}

@Composable
private fun CredentialRow(c: Credential, onDelete: () -> Unit) {
    var revealed by remember { mutableStateOf(false) }
    Card {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.host, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(c.login, style = MonoSmall)
                Text(if (revealed) c.password else "••••••••", style = MonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { revealed = !revealed }) {
                Icon(if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Показать")
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Удалить", tint = Bad) }
        }
    }
}

@Composable
private fun AddCredentialDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var host by remember { mutableStateOf("") }
    var login by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { if (host.isNotBlank()) onSave(host.trim(), login.trim(), pass) },
                enabled = host.isNotBlank()
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        title = { Text("Логин для сайта", fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(host, { host = it }, label = { Text("Сайт (host)", fontSize = 11.sp) },
                    singleLine = true, textStyle = MonoSmall, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(login, { login = it }, label = { Text("Логин", fontSize = 11.sp) },
                    singleLine = true, textStyle = MonoSmall, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pass, { pass = it }, label = { Text("Пароль", fontSize = 11.sp) },
                    singleLine = true, textStyle = MonoSmall, modifier = Modifier.fillMaxWidth())
            }
        }
    )
}

private fun queryName(ctx: android.content.Context, uri: Uri): String {
    var name = "file"
    runCatching {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) name = c.getString(idx) ?: name
        }
    }
    return name
}
