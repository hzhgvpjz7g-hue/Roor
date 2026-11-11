package ru.btcradar.vault

import kotlinx.serialization.Serializable

/** Какой из двух сейфов открыт. */
enum class VaultSlot { REAL, DECOY }

/** Запись о файле внутри сейфа. Сами байты лежат зашифрованными отдельно. */
@Serializable
data class VaultFile(
    val id: String,
    val name: String,
    val size: Long,
    val mime: String = "application/octet-stream",
    val addedMs: Long = System.currentTimeMillis()
)

/** Сохранённые логин/пароль для сайта. */
@Serializable
data class Credential(
    val id: String,
    val host: String,
    val login: String,
    val password: String,
    val note: String = ""
)

/** Содержимое одного сейфа (шифруется целиком). */
@Serializable
data class VaultData(
    val files: List<VaultFile> = emptyList(),
    val credentials: List<Credential> = emptyList()
)
