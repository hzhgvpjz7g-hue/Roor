package ru.btcradar.vault

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Шифрование сейфа на штатном javax.crypto — без внешних библиотек.
 *
 *  Ключ:  PBKDF2-HMAC-SHA256, 200 000 итераций, соль 16 байт.
 *  Данные: AES-256-GCM (аутентифицированное шифрование: поддельный или битый
 *          шифртекст не расшифруется, а бросит исключение — то, что нужно).
 *
 * Важное свойство для схемы с двумя паролями: у ложного и настоящего паролей
 * РАЗНЫЕ соли и РАЗНЫЕ зашифрованные блобы. Невозможно по одному блобу понять,
 * существует ли второй — потому что это просто два независимых файла. Нет пароля
 * — нет ключа — данные неотличимы от случайного мусора.
 */
object Crypto {

    private const val ITERATIONS = 200_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val SALT_LEN = 16
    private const val IV_LEN = 12

    private val rng = SecureRandom()

    class Key internal constructor(internal val raw: ByteArray)

    fun randomSalt(): ByteArray = ByteArray(SALT_LEN).also { rng.nextBytes(it) }

    fun deriveKey(password: CharArray, salt: ByteArray): Key {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return Key(bytes)
    }

    /** Шифртекст = [IV(12) | ciphertext+tag]. */
    fun encrypt(key: Key, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(IV_LEN).also { rng.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key.raw, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )
        val ct = cipher.doFinal(plaintext)
        return iv + ct
    }

    /** Бросает исключение, если пароль не тот или данные повреждены. */
    fun decrypt(key: Key, blob: ByteArray): ByteArray {
        require(blob.size > IV_LEN) { "Слишком короткий блоб" }
        val iv = blob.copyOfRange(0, IV_LEN)
        val ct = blob.copyOfRange(IV_LEN, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key.raw, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )
        return cipher.doFinal(ct)
    }

    /** Проверка пароля = успешная расшифровка контрольного блоба. */
    fun tryDecrypt(key: Key, blob: ByteArray): ByteArray? =
        runCatching { decrypt(key, blob) }.getOrNull()

    fun b64(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)
    fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)
}
