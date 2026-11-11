package ru.btcradar.vault

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Два независимых сейфа под одним PIN-пэдом.
 *
 *  REAL  — настоящий, открывается 5-значным PIN.
 *  DECOY — ложный, открывается 4-значным PIN. Под принуждением даёшь его:
 *          там лежит правдоподобная ерунда, и по нему невозможно узнать,
 *          что есть второй сейф.
 *
 * Как работает разделение: у каждого сейфа свои соль и контрольный блоб.
 * При разблокировке мы берём введённый PIN, выводим ключ под каждую соль и
 * пробуем расшифровать контрольный блоб. Что расшифровалось — тот сейф и открыт.
 * Без правильного PIN ключа нет, а данные неотличимы от случайного мусора.
 *
 * Честная граница: это защита ДАННЫХ (их не прочесть без PIN) и правдоподобное
 * отрицание на бытовом уровне. Но криминалистический анализ файлов приложения
 * покажет, что зашифрованных блоба ДВА — то есть «скрытого тома» уровня
 * VeraCrypt тут нет. Для «отдал ложный пароль — отстали» этого хватает,
 * для защиты от серьёзной экспертизы — нет. Врать не буду.
 */
class VaultManager(private val ctx: Context) {

    private val prefs = ctx.getSharedPreferences("btcradar_vault", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val filesRoot = File(ctx.filesDir, "vault").apply { mkdirs() }

    // Открытая сессия (в памяти, не на диске)
    private var sessionKey: Crypto.Key? = null
    private var sessionSlot: VaultSlot? = null

    val openedSlot: VaultSlot? get() = sessionSlot
    val isOpen: Boolean get() = sessionKey != null

    fun isInitialized(): Boolean =
        prefs.contains(saltKey(VaultSlot.REAL)) && prefs.contains(saltKey(VaultSlot.DECOY))

    /**
     * Первичная настройка: задаём оба PIN. realPin — 5 цифр, decoyPin — 4.
     * Они должны различаться.
     */
    fun setup(realPin: String, decoyPin: String): Result<Unit> = runCatching {
        require(realPin.length == 5) { "Настоящий PIN — 5 цифр" }
        require(decoyPin.length == 4) { "Ложный PIN — 4 цифры" }
        require(realPin != decoyPin) { "PIN-ы не должны совпадать" }
        initSlot(VaultSlot.REAL, realPin, VaultData())
        initSlot(VaultSlot.DECOY, decoyPin, VaultData(
            // немного правдоподобного наполнения в ложном сейфе
            credentials = listOf(
                Credential("seed1", "mail.ru", "ivan.petrov", "qwerty123", "почта")
            )
        ))
    }

    private fun initSlot(slot: VaultSlot, pin: String, data: VaultData) {
        val salt = Crypto.randomSalt()
        val key = Crypto.deriveKey(pin.toCharArray(), salt)
        prefs.edit()
            .putString(saltKey(slot), Crypto.b64(salt))
            .putString(controlKey(slot), Crypto.b64(Crypto.encrypt(key, CONTROL_PLAIN)))
            .putString(metaKey(slot), Crypto.b64(Crypto.encrypt(key, encodeData(data))))
            .apply()
    }

    /**
     * Разблокировка. Пробуем оба сейфа; какой контрольный блоб расшифровался —
     * тот и открываем. Возвращает открытый слот или null.
     */
    fun unlock(pin: String): VaultSlot? {
        for (slot in VaultSlot.entries) {
            val salt = prefs.getString(saltKey(slot), null)?.let(Crypto::unb64) ?: continue
            val control = prefs.getString(controlKey(slot), null)?.let(Crypto::unb64) ?: continue
            val key = Crypto.deriveKey(pin.toCharArray(), salt)
            val plain = Crypto.tryDecrypt(key, control)
            if (plain != null && plain.contentEquals(CONTROL_PLAIN)) {
                sessionKey = key
                sessionSlot = slot
                return slot
            }
        }
        return null
    }

    fun lock() {
        sessionKey = null
        sessionSlot = null
    }

    // ---------- данные открытого сейфа ----------

    fun readData(): VaultData {
        val key = sessionKey ?: return VaultData()
        val slot = sessionSlot ?: return VaultData()
        val blob = prefs.getString(metaKey(slot), null)?.let(Crypto::unb64) ?: return VaultData()
        val plain = Crypto.tryDecrypt(key, blob) ?: return VaultData()
        return runCatching { decodeData(plain) }.getOrDefault(VaultData())
    }

    private fun writeData(data: VaultData) {
        val key = sessionKey ?: return
        val slot = sessionSlot ?: return
        prefs.edit()
            .putString(metaKey(slot), Crypto.b64(Crypto.encrypt(key, encodeData(data))))
            .apply()
    }

    // ---------- файлы ----------

    /** Зашифровать и положить файл. Возвращает запись. */
    fun addFile(name: String, mime: String, bytes: ByteArray): VaultFile? {
        val key = sessionKey ?: return null
        val slot = sessionSlot ?: return null
        val id = "f_" + System.nanoTime().toString(36)
        val enc = Crypto.encrypt(key, bytes)
        slotDir(slot).also { it.mkdirs() }
        File(slotDir(slot), "$id.bin").writeBytes(enc)

        val rec = VaultFile(id, name, bytes.size.toLong(), mime)
        val data = readData()
        writeData(data.copy(files = data.files + rec))
        return rec
    }

    /** Расшифровать файл в память (для экспорта/просмотра). */
    fun readFile(id: String): ByteArray? {
        val key = sessionKey ?: return null
        val slot = sessionSlot ?: return null
        val f = File(slotDir(slot), "$id.bin")
        if (!f.exists()) return null
        return Crypto.tryDecrypt(key, f.readBytes())
    }

    fun deleteFile(id: String) {
        val slot = sessionSlot ?: return
        runCatching { File(slotDir(slot), "$id.bin").delete() }
        val data = readData()
        writeData(data.copy(files = data.files.filterNot { it.id == id }))
    }

    // ---------- учётки ----------

    fun addCredential(c: Credential) {
        val data = readData()
        writeData(data.copy(credentials = data.credentials + c))
    }

    fun deleteCredential(id: String) {
        val data = readData()
        writeData(data.copy(credentials = data.credentials.filterNot { it.id == id }))
    }

    /** Учётка под host (по вхождению) — для автозаполнения в браузере. */
    fun credentialForHost(host: String): Credential? {
        if (sessionKey == null) return null
        val h = host.removePrefix("www.")
        return readData().credentials.firstOrNull {
            val ch = it.host.removePrefix("www.")
            h.contains(ch, true) || ch.contains(h, true)
        }
    }

    /** Полное удаление сейфа (обоих слотов и файлов). На случай «сбросить всё». */
    fun wipeEverything() {
        lock()
        prefs.edit().clear().apply()
        runCatching { filesRoot.deleteRecursively() }
        filesRoot.mkdirs()
    }

    // ---------- вспомогательное ----------

    private fun encodeData(d: VaultData): ByteArray =
        json.encodeToString(VaultData.serializer(), d).toByteArray()
    private fun decodeData(b: ByteArray): VaultData =
        json.decodeFromString(VaultData.serializer(), b.decodeToString())

    private fun slotDir(slot: VaultSlot) = File(filesRoot, slot.name.lowercase())
    private fun saltKey(slot: VaultSlot) = "salt_${slot.name}"
    private fun controlKey(slot: VaultSlot) = "ctrl_${slot.name}"
    private fun metaKey(slot: VaultSlot) = "meta_${slot.name}"

    private companion object {
        val CONTROL_PLAIN = "BTCRADAR_VAULT_OK".toByteArray()
    }
}
