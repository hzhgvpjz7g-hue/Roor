package ru.btcradar

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.btcradar.ui.*

private enum class Screen { RATES, SOURCES, TOR, ALERTS, BROWSER, VAULT }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BtcRadarTheme {
                App()
            }
        }
    }
}

@Composable
private fun App(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val testResult by vm.testResult.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    var screen by remember { mutableStateOf(Screen.RATES) }
    val snackbar = remember { SnackbarHostState() }

    // Разрешение на уведомления (Android 13+). Спрашиваем один раз при старте.
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* результат не критичен: без разрешения просто не будет уведомлений */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbar.showSnackbar(it)
            vm.clearToast()
        }
    }

    // Навигация по голосовой команде
    val voiceNav by vm.voiceNav.collectAsStateWithLifecycle()
    LaunchedEffect(voiceNav) {
        when (voiceNav) {
            "SOURCES" -> screen = Screen.SOURCES
            "ALERTS" -> screen = Screen.ALERTS
            "BROWSER" -> screen = Screen.BROWSER
            "VAULT" -> screen = Screen.VAULT
            null -> {}
        }
        if (voiceNav != null) vm.consumeVoiceNav()
    }

    // Статус Tor/голоса мог поменяться, пока пользователь уходил из приложения
    LaunchedEffect(screen) {
        if (screen == Screen.TOR) vm.refreshTorStatus()
        if (screen == Screen.ALERTS) vm.refreshVoiceState()
    }

    BackHandler(enabled = screen != Screen.RATES) { screen = Screen.RATES }

    val openUrl: (String) -> Unit = { url ->
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    when (screen) {
        Screen.RATES -> RatesScreen(
            state = state,
            onRefresh = vm::scan,
            onAmountRub = vm::setAmountRub,
            onAmountBtc = vm::setAmountBtc,
            onHideUnusable = vm::setHideUnusable,
            onOpenSources = { screen = Screen.SOURCES },
            onOpenTor = { screen = Screen.TOR },
            onOpenAlerts = { screen = Screen.ALERTS },
            onOpenBrowser = { screen = Screen.BROWSER },
            onOpenVault = { screen = Screen.VAULT },
            onOpenOffer = openUrl
        )

        Screen.BROWSER -> BrowserScreen(
            state = state,
            startUrl = "https://duckduckgo.com/",
            onBack = { screen = Screen.RATES },
            onStartOrbot = vm::startOrbot,
            onHostChanged = vm::onBrowserHost,
            autofill = state.browserAutofill,
            onAutofillConsumed = vm::consumeAutofill,
            onRequestAutofill = vm::requestAutofill,
            hasCredentialForHost = state.browserHasCredential
        )

        Screen.VAULT -> VaultScreen(
            state = state,
            onBack = { screen = Screen.RATES },
            onUnlock = vm::unlockVault,
            onLock = vm::lockVault,
            onImportFile = vm::importVaultFile,
            onExportFile = vm::exportVaultFile,
            onDeleteFile = vm::deleteVaultFile,
            onAddCredential = vm::addCredential,
            onDeleteCredential = vm::deleteCredential
        )

        Screen.ALERTS -> AlertsScreen(
            state = state,
            onBack = { screen = Screen.RATES },
            onAddAlert = vm::addAlert,
            onUpdateAlert = vm::updateAlert,
            onDeleteAlert = vm::deleteAlert,
            onWatchEnabled = vm::setWatchEnabled,
            onWatchPeriod = vm::setWatchPeriod,
            onAssistant = vm::setAssistant,
            onSpeakTest = vm::speakTest,
            onEnrollVoice = vm::enrollVoiceSample,
            onResetVoice = vm::resetVoiceEnrollment,
            onVoiceCommand = vm::voiceCommand,
            onMicGranted = vm::refreshVoiceState
        )

        Screen.SOURCES -> SourcesScreen(
            state = state,
            testResult = testResult,
            onBack = { screen = Screen.RATES; vm.scan() },
            onToggle = vm::toggleSource,
            onUpsert = vm::upsertSource,
            onDelete = vm::deleteSource,
            onTest = vm::testSource,
            onClearTest = vm::clearTestResult,
            onExport = vm::exportSources,
            onImport = vm::importSources,
            onReset = vm::resetSources
        )

        Screen.TOR -> TorScreen(
            tor = state.tor,
            onBack = { screen = Screen.RATES },
            onSettings = vm::setTorSettings,
            onRefreshStatus = vm::refreshTorStatus,
            onVerify = vm::verifyTor,
            onStartOrbot = vm::startOrbot
        )
    }

    SnackbarHost(snackbar)
}
