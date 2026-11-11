package ru.btcradar.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.btcradar.alerts.AlertScheduler
import ru.btcradar.alerts.Notifier
import ru.btcradar.model.Alert
import ru.btcradar.model.AlertHit
import ru.btcradar.model.SourceConfig
import ru.btcradar.model.SourceResult
import ru.btcradar.net.Repository
import ru.btcradar.net.ScanResult
import ru.btcradar.net.TorHttp
import ru.btcradar.net.TorMode
import ru.btcradar.net.TorSettings
import ru.btcradar.voice.AssistantSettings
import ru.btcradar.voice.Tts
import ru.btcradar.voice.VoiceController
import ru.btcradar.voice.VoiceIntent
import ru.btcradar.vault.Credential
import ru.btcradar.vault.VaultFile
import ru.btcradar.vault.VaultManager
import android.net.Uri

data class TorState(
    val settings: TorSettings = TorSettings(),
    val proxyListening: Boolean = false,
    val verified: Boolean = false,
    val checking: Boolean = false,
    val message: String? = null,
    val orbotInstalled: Boolean = false
) {
    /** Реально ли можно гонять трафик через Tor прямо сейчас. */
    val live: Boolean get() = settings.mode != TorMode.OFF && proxyListening
}

data class UiState(
    val sources: List<SourceConfig> = emptyList(),
    val scan: ScanResult = ScanResult(),
    val scanning: Boolean = false,
    val amountRub: Double = 100_000.0,
    val amountBtc: Double = 0.01,
    val hideUnusable: Boolean = false,
    val tor: TorState = TorState(),
    val alerts: List<Alert> = emptyList(),
    val watchEnabled: Boolean = false,
    val watchPeriodMin: Long = 15,
    val assistant: AssistantSettings = AssistantSettings(),
    val lastHits: List<AlertHit> = emptyList(),
    val voice: VoiceState = VoiceState(),
    val vault: VaultUiState = VaultUiState(),
    val browserAutofill: Pair<String, String>? = null,
    val browserHasCredential: Boolean = false,
    val toast: String? = null
)

data class VaultUiState(
    val initialized: Boolean = false,
    val open: Boolean = false,
    val files: List<VaultFile> = emptyList(),
    val credentials: List<Credential> = emptyList(),
    val pinError: String? = null,
    /** 0 — ввод настоящего 5-значного PIN при настройке, 1 — ложного 4-значного. */
    val setupStage: Int = 0
)

data class VoiceState(
    val micGranted: Boolean = false,
    val speechAvailable: Boolean = false,
    val enrolled: Boolean = false,
    /** Сколько образцов голоса уже записано при обучении (нужно 3). */
    val enrollProgress: Int = 0,
    val busy: Boolean = false,
    val status: String = "",
    val lastHeard: String = "",
    val lastSimilarity: Float? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository(app)
    private val tts = Tts(app.applicationContext)
    private val voice = VoiceController(app.applicationContext)
    private val vault = VaultManager(app.applicationContext)
    private val enrollBuffer = mutableListOf<FloatArray>()
    private var pendingRealPin: String? = null
    private var browserHost: String = ""

    /** Куда перейти по голосовой команде. MainActivity слушает и навигирует. */
    private val _voiceNav = MutableStateFlow<String?>(null)
    val voiceNav: StateFlow<String?> = _voiceNav.asStateFlow()
    fun consumeVoiceNav() { _voiceNav.value = null }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    companion object {
        const val ENROLL_SAMPLES = 3
    }

    init {
        val tor = repo.loadTor()
        val assistant = repo.loadAssistant()
        _state.update {
            it.copy(
                sources = repo.loadSources(),
                amountRub = repo.loadAmountRub(),
                amountBtc = repo.loadAmountBtc(),
                hideUnusable = repo.loadHideUnusable(),
                tor = TorState(
                    settings = tor,
                    orbotInstalled = TorHttp.isOrbotInstalled(app)
                ),
                alerts = repo.loadAlerts(),
                watchEnabled = repo.loadWatchEnabled(),
                watchPeriodMin = repo.loadWatchPeriodMin(),
                assistant = assistant
            )
        }
        Notifier.ensureChannel(app)
        if (assistant.enabled) tts.init { tts.apply(assistant) }
        _state.update { it.copy(vault = it.vault.copy(initialized = vault.isInitialized())) }
        refreshVoiceState()
        refreshTorStatus()
        scan()
    }

    // ---------- сейф ----------

    /** Единая точка входа PIN-пэда: настройка (если не инициализирован) или разблокировка. */
    fun unlockVault(pin: String) {
        if (!vault.isInitialized()) { handleSetup(pin); return }
        val slot = vault.unlock(pin)
        if (slot == null) {
            _state.update { it.copy(vault = it.vault.copy(pinError = "Неверный PIN")) }
        } else {
            val d = vault.readData()
            _state.update {
                it.copy(vault = it.vault.copy(
                    open = true, pinError = null, files = d.files, credentials = d.credentials
                ))
            }
        }
    }

    private fun handleSetup(pin: String) {
        val st = _state.value.vault
        if (st.setupStage == 0) {
            if (pin.length != 5) {
                _state.update { it.copy(vault = it.vault.copy(pinError = "Настоящий PIN — 5 цифр")) }
                return
            }
            pendingRealPin = pin
            _state.update { it.copy(vault = it.vault.copy(setupStage = 1, pinError = null)) }
        } else {
            if (pin.length != 4) {
                _state.update { it.copy(vault = it.vault.copy(pinError = "Ложный PIN — 4 цифры")) }
                return
            }
            val real = pendingRealPin
            if (real == null) {
                _state.update { it.copy(vault = it.vault.copy(setupStage = 0)) }
                return
            }
            if (real == pin) {
                _state.update { it.copy(vault = it.vault.copy(pinError = "PIN-ы не должны совпадать")) }
                return
            }
            vault.setup(real, pin)
                .onSuccess {
                    pendingRealPin = null
                    vault.unlock(real)
                    val d = vault.readData()
                    _state.update {
                        it.copy(vault = VaultUiState(
                            initialized = true, open = true, files = d.files, credentials = d.credentials
                        ))
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(vault = it.vault.copy(pinError = e.message ?: "Ошибка настройки")) }
                }
        }
    }

    fun lockVault() {
        vault.lock()
        _state.update {
            it.copy(
                vault = VaultUiState(initialized = true, open = false),
                browserAutofill = null, browserHasCredential = false
            )
        }
    }

    private fun refreshVaultData() {
        if (!vault.isOpen) return
        val d = vault.readData()
        _state.update { it.copy(vault = it.vault.copy(files = d.files, credentials = d.credentials)) }
    }

    fun importVaultFile(name: String, mime: String, bytes: ByteArray) {
        vault.addFile(name, mime, bytes)
        refreshVaultData()
    }

    fun exportVaultFile(id: String, uri: Uri) {
        val bytes = vault.readFile(id) ?: return
        runCatching {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        }
    }

    fun deleteVaultFile(id: String) {
        vault.deleteFile(id)
        refreshVaultData()
    }

    fun addCredential(host: String, login: String, password: String) {
        vault.addCredential(
            Credential("c_" + System.nanoTime().toString(36), host, login, password)
        )
        refreshVaultData()
    }

    fun deleteCredential(id: String) {
        vault.deleteCredential(id)
        refreshVaultData()
    }

    // ---------- автозаполнение в браузере ----------

    fun onBrowserHost(host: String) {
        browserHost = host
        val has = vault.isOpen && vault.credentialForHost(host) != null
        _state.update { it.copy(browserHasCredential = has) }
    }

    fun requestAutofill(host: String) {
        val c = vault.credentialForHost(host) ?: return
        _state.update { it.copy(browserAutofill = c.login to c.password) }
    }

    fun consumeAutofill() {
        _state.update { it.copy(browserAutofill = null) }
    }

    // ---------- голосовое управление ----------

    fun refreshVoiceState() {
        _state.update {
            it.copy(
                voice = it.voice.copy(
                    micGranted = voice.micPermission(),
                    speechAvailable = voice.speechAvailable(),
                    enrolled = voice.isEnrolled()
                )
            )
        }
    }

    /** Записать один образец голоса при обучении. После ENROLL_SAMPLES — сохраняем эталон. */
    fun enrollVoiceSample() {
        if (!voice.micPermission()) {
            _state.update { it.copy(voice = it.voice.copy(status = "Нет доступа к микрофону")) }
            return
        }
        _state.update { it.copy(voice = it.voice.copy(busy = true, status = "Говори своё слово…")) }
        viewModelScope.launch {
            val emb = voice.gate.captureEmbedding()
            if (emb == null) {
                _state.update { it.copy(voice = it.voice.copy(busy = false, status = "Не записалось, повтори")) }
                return@launch
            }
            enrollBuffer.add(emb)
            if (enrollBuffer.size >= ENROLL_SAMPLES) {
                voice.gate.enroll(enrollBuffer.toList())
                enrollBuffer.clear()
                refreshVoiceState()
                _state.update { it.copy(voice = it.voice.copy(busy = false, enrollProgress = 0,
                    status = "Готово, голос запомнен")) }
                speak("Запомнил твой голос.")
            } else {
                _state.update { it.copy(voice = it.voice.copy(busy = false,
                    enrollProgress = enrollBuffer.size,
                    status = "Записано ${enrollBuffer.size} из $ENROLL_SAMPLES")) }
            }
        }
    }

    fun resetVoiceEnrollment() {
        enrollBuffer.clear()
        voice.gate.clearEnrollment()
        refreshVoiceState()
        _state.update { it.copy(voice = it.voice.copy(enrollProgress = 0, status = "Эталон сброшен")) }
    }

    /** Нажал «слушать команду». С учётом замка по голосу. */
    fun voiceCommand() {
        if (_state.value.voice.busy) return
        val a = _state.value.assistant

        viewModelScope.launch {
            // Замок: если включён и есть эталон — сперва проверяем владельца
            if (a.voiceLock && voice.isEnrolled()) {
                _state.update { it.copy(voice = it.voice.copy(busy = true, status = "Проверяю голос…")) }
                val v = voice.gate.verify()
                if (!v.accepted) {
                    _state.update { it.copy(voice = it.voice.copy(busy = false,
                        lastSimilarity = v.similarity,
                        status = "Голос не совпал (%.0f%%). Команда отклонена".format(v.similarity * 100))) }
                    speak("Это не твой голос. Команду не выполняю.")
                    return@launch
                }
                _state.update { it.copy(voice = it.voice.copy(lastSimilarity = v.similarity)) }
            }

            _state.update { it.copy(voice = it.voice.copy(busy = true, status = "Слушаю…")) }
            voice.listen(
                onStatus = { s -> _state.update { it.copy(voice = it.voice.copy(status = s)) } },
                onIntent = { intent ->
                    _state.update { it.copy(voice = it.voice.copy(busy = false)) }
                    executeIntent(intent)
                },
                onError = { e ->
                    _state.update { it.copy(voice = it.voice.copy(busy = false, status = e)) }
                }
            )
        }
    }

    private fun executeIntent(intent: VoiceIntent) {
        when (intent) {
            is VoiceIntent.CreateAlert -> {
                addAlert(intent.alert)
                _state.update { it.copy(voice = it.voice.copy(status = "Алерт создан: ${intent.alert.humanText()}")) }
                speak("Поставил алерт: ${intent.alert.humanText()}.")
            }
            VoiceIntent.Refresh -> { scan(); speak("Обновляю курсы.") }
            VoiceIntent.TorOn -> {
                setTorSettings(_state.value.tor.settings.copy(mode = TorMode.HTTP_PROXY))
                startOrbot()
                speak("Включаю Tor.")
            }
            VoiceIntent.TorOff -> {
                setTorSettings(_state.value.tor.settings.copy(mode = TorMode.OFF))
                speak("Выключаю Tor.")
            }
            VoiceIntent.OpenSources -> { _voiceNav.value = "SOURCES"; speak("Открываю источники.") }
            VoiceIntent.OpenAlerts -> { _voiceNav.value = "ALERTS"; speak("Открываю алерты.") }
            VoiceIntent.OpenBrowser -> { _voiceNav.value = "BROWSER"; speak("Открываю браузер.") }
            is VoiceIntent.Say -> speak(intent.text)
            is VoiceIntent.Unknown -> {
                _state.update { it.copy(voice = it.voice.copy(lastHeard = intent.heard,
                    status = "Не понял: ${intent.heard}")) }
                speak("Не понял команду.")
            }
        }
    }

    fun setVoiceThreshold(v: Float) = voice.gate.setThreshold(v)
    fun voiceThreshold(): Float = voice.gate.threshold()

    private fun speak(text: String) {
        val a = _state.value.assistant
        if (a.enabled) tts.speak(text)
    }

    // ---------- сканирование ----------

    fun scan() {
        if (_state.value.scanning) return
        _state.update { it.copy(scanning = true) }
        viewModelScope.launch {
            val s = _state.value
            val result = repo.scan(s.sources, s.tor.settings, s.tor.live)
            _state.update { it.copy(scan = result, scanning = false) }
            evaluateAlerts(result)
        }
    }

    /**
     * Проверка алертов на переднем плане: уведомление + голос.
     * В фоне этим же занимается RateWorker, но без надёжного голоса.
     */
    private fun evaluateAlerts(scan: ScanResult) {
        val now = System.currentTimeMillis()
        val hits = mutableListOf<AlertHit>()

        val updated = _state.value.alerts.map { alert ->
            if (!alert.canFire(now)) return@map alert
            val offer = alert.bestTrigger(scan.offers) ?: return@map alert
            val hit = AlertHit(alert, offer)
            hits += hit
            Notifier.notifyHit(getApplication(), hit)
            if (alert.oneShot) alert.copy(enabled = false, lastFiredMs = now)
            else alert.copy(lastFiredMs = now)
        }

        if (hits.isEmpty()) return
        repo.saveAlerts(updated)

        val a = _state.value.assistant
        if (a.enabled && a.speakAlerts) {
            hits.forEach { tts.speak(it.spokenText(a.name)) }
        }
        _state.update { it.copy(alerts = updated, lastHits = hits) }
    }

    // ---------- алерты ----------

    fun addAlert(alert: Alert) {
        val list = _state.value.alerts + alert
        repo.saveAlerts(list)
        _state.update { it.copy(alerts = list) }
        // Сразу проверим — вдруг курс уже за порогом
        evaluateAlerts(_state.value.scan)
    }

    fun updateAlert(alert: Alert) {
        val list = _state.value.alerts.map { if (it.id == alert.id) alert else it }
        repo.saveAlerts(list)
        _state.update { it.copy(alerts = list) }
    }

    fun deleteAlert(id: String) {
        val list = _state.value.alerts.filterNot { it.id == id }
        repo.saveAlerts(list)
        _state.update { it.copy(alerts = list) }
    }

    fun setWatchEnabled(enabled: Boolean) {
        repo.saveWatchEnabled(enabled)
        _state.update { it.copy(watchEnabled = enabled) }
        if (enabled) AlertScheduler.enable(getApplication(), _state.value.watchPeriodMin)
        else AlertScheduler.disable(getApplication())
    }

    fun setWatchPeriod(min: Long) {
        repo.saveWatchPeriodMin(min)
        _state.update { it.copy(watchPeriodMin = min) }
        if (_state.value.watchEnabled) AlertScheduler.enable(getApplication(), min)
    }

    // ---------- ассистент ----------

    fun setAssistant(s: AssistantSettings) {
        repo.saveAssistant(s)
        _state.update { it.copy(assistant = s) }
        if (s.enabled) tts.init { tts.apply(s) } else tts.stop()
    }

    fun speakTest() {
        val a = _state.value.assistant
        tts.init {
            tts.apply(a)
            val name = a.name.ifBlank { "Радар" }
            tts.speak("Меня зовут $name. Я на связи и слежу за курсом.")
        }
    }

    override fun onCleared() {
        tts.shutdown()
        voice.destroy()
        super.onCleared()
    }

    fun setAmountRub(v: Double) {
        repo.saveAmountRub(v)
        _state.update { it.copy(amountRub = v) }
    }

    fun setAmountBtc(v: Double) {
        repo.saveAmountBtc(v)
        _state.update { it.copy(amountBtc = v) }
    }

    fun setHideUnusable(v: Boolean) {
        repo.saveHideUnusable(v)
        _state.update { it.copy(hideUnusable = v) }
    }

    // ---------- источники ----------

    fun upsertSource(cfg: SourceConfig) {
        val list = _state.value.sources.toMutableList()
        val idx = list.indexOfFirst { it.id == cfg.id }
        if (idx >= 0) list[idx] = cfg else list.add(cfg)
        repo.saveSources(list)
        _state.update { it.copy(sources = list) }
    }

    fun deleteSource(id: String) {
        val list = _state.value.sources.filterNot { it.id == id }
        repo.saveSources(list)
        _state.update { it.copy(sources = list) }
    }

    fun toggleSource(id: String, enabled: Boolean) {
        val list = _state.value.sources.map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        repo.saveSources(list)
        _state.update { it.copy(sources = list) }
    }

    fun exportSources(): String = repo.exportSources()

    fun importSources(raw: String) {
        repo.importSources(raw)
            .onSuccess { list ->
                _state.update { it.copy(sources = list, toast = "Загружено источников: ${list.size}") }
            }
            .onFailure { e ->
                _state.update { it.copy(toast = "Ошибка импорта: ${e.message}") }
            }
    }

    fun resetSources() {
        val d = repo.resetSources()
        _state.update { it.copy(sources = d, toast = "Источники сброшены") }
    }

    private val _testResult = MutableStateFlow<SourceResult?>(null)
    val testResult: StateFlow<SourceResult?> = _testResult.asStateFlow()

    fun testSource(cfg: SourceConfig) {
        _testResult.value = null
        viewModelScope.launch {
            val s = _state.value
            _testResult.value = repo.testSource(cfg, s.tor.settings, s.tor.live)
        }
    }

    fun clearTestResult() {
        _testResult.value = null
    }

    // ---------- Tor ----------

    fun setTorSettings(s: TorSettings) {
        repo.saveTor(s)
        _state.update { it.copy(tor = it.tor.copy(settings = s, verified = false)) }
        refreshTorStatus()
    }

    fun refreshTorStatus() {
        viewModelScope.launch {
            val s = _state.value.tor.settings
            val listening = TorHttp.isProxyListening(s)
            _state.update {
                it.copy(
                    tor = it.tor.copy(
                        proxyListening = listening,
                        orbotInstalled = TorHttp.isOrbotInstalled(getApplication()),
                        message = when {
                            s.mode == TorMode.OFF -> null
                            listening -> "Прокси ${s.host}:${s.port} отвечает"
                            else -> "Прокси ${s.host}:${s.port} молчит — запусти Orbot"
                        }
                    )
                )
            }
        }
    }

    /** Полная проверка через check.torproject.org — что трафик реально в Tor. */
    fun verifyTor() {
        _state.update { it.copy(tor = it.tor.copy(checking = true, message = "Проверяю цепочку…")) }
        viewModelScope.launch {
            val s = _state.value.tor.settings
            val res = TorHttp.verifyTor(s)
            _state.update {
                it.copy(
                    tor = it.tor.copy(
                        checking = false,
                        verified = res.isSuccess,
                        proxyListening = res.isSuccess || it.tor.proxyListening,
                        message = res.fold(
                            onSuccess = { "Tor подтверждён — трафик идёт через сеть" },
                            onFailure = { e -> "Tor не подтверждён: ${e.message}" }
                        )
                    )
                )
            }
        }
    }

    fun startOrbot() {
        TorHttp.requestOrbotStart(getApplication())
    }

    fun clearToast() {
        _state.update { it.copy(toast = null) }
    }
}
