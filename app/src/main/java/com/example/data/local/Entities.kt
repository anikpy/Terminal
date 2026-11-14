package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AuthType {
    PASSWORD,
    SSH_KEY
}

enum class KeyAlgorithm(val displayLabel: String) {
    ED25519("Ed25519"),
    RSA_4096("RSA 4096"),
    IMPORTED("Imported")
}

enum class SessionStatus(val displayLabel: String) {
    CONNECTED("Connected"),
    COMPLETED("Closed"),
    DISCONNECTED("Disconnected"),
    FAILED("Failed")
}

@Entity(tableName = "hosts")
data class HostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alias: String,
    val hostname: String,
    val port: Int = 22,
    val username: String,
    val authType: String = AuthType.SSH_KEY.name,
    val sshKeyId: Long? = null,
    val encryptedPassword: ByteArray? = null,
    val passwordIv: ByteArray? = null,
    val startupCommand: String = "",
    val colorTagHex: Long = 0xFF22D3EE,
    val createdAt: Long = System.currentTimeMillis(),
    val lastConnectedAt: Long? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HostEntity) return false
        return id == other.id &&
            alias == other.alias &&
            hostname == other.hostname &&
            port == other.port &&
            username == other.username &&
            authType == other.authType &&
            sshKeyId == other.sshKeyId &&
            encryptedPassword.contentEqualsNullable(other.encryptedPassword) &&
            passwordIv.contentEqualsNullable(other.passwordIv) &&
            startupCommand == other.startupCommand &&
            colorTagHex == other.colorTagHex &&
            createdAt == other.createdAt &&
            lastConnectedAt == other.lastConnectedAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + alias.hashCode()
        result = 31 * result + hostname.hashCode()
        result = 31 * result + port
        result = 31 * result + username.hashCode()
        result = 31 * result + authType.hashCode()
        result = 31 * result + (sshKeyId?.hashCode() ?: 0)
        result = 31 * result + (encryptedPassword?.contentHashCode() ?: 0)
        result = 31 * result + (passwordIv?.contentHashCode() ?: 0)
        result = 31 * result + startupCommand.hashCode()
        result = 31 * result + colorTagHex.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + (lastConnectedAt?.hashCode() ?: 0)
        return result
    }
}

@Entity(tableName = "ssh_keys")
data class SshKeyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val algorithm: String,
    val publicKeyOpenSsh: String,
    val fingerprintSha256: String,
    val encryptedPrivateKey: ByteArray,
    val privateKeyIv: ByteArray,
    val hasPassphrase: Boolean = false,
    val encryptedPassphrase: ByteArray? = null,
    val passphraseIv: ByteArray? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SshKeyEntity) return false
        return id == other.id &&
            name == other.name &&
            algorithm == other.algorithm &&
            publicKeyOpenSsh == other.publicKeyOpenSsh &&
            fingerprintSha256 == other.fingerprintSha256 &&
            encryptedPrivateKey.contentEquals(other.encryptedPrivateKey) &&
            privateKeyIv.contentEquals(other.privateKeyIv) &&
            hasPassphrase == other.hasPassphrase &&
            encryptedPassphrase.contentEqualsNullable(other.encryptedPassphrase) &&
            passphraseIv.contentEqualsNullable(other.passphraseIv) &&
            createdAt == other.createdAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + algorithm.hashCode()
        result = 31 * result + publicKeyOpenSsh.hashCode()
        result = 31 * result + fingerprintSha256.hashCode()
        result = 31 * result + encryptedPrivateKey.contentHashCode()
        result = 31 * result + privateKeyIv.contentHashCode()
        result = 31 * result + hasPassphrase.hashCode()
        result = 31 * result + (encryptedPassphrase?.contentHashCode() ?: 0)
        result = 31 * result + (passphraseIv?.contentHashCode() ?: 0)
        result = 31 * result + createdAt.hashCode()
        return result
    }
}

@Entity(tableName = "known_hosts")
data class KnownHostEntity(
    @PrimaryKey val hostKeyId: String,
    val hostname: String,
    val port: Int,
    val algorithm: String,
    val fingerprintSha256: String,
    val publicKeyBase64: String,
    val firstTrustedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "snippets")
data class SnippetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val command: String,
    val category: String = "System",
    val executeImmediately: Boolean = true,
    val sortOrder: Int = 0,
    val isBuiltIn: Boolean = false
)

@Entity(tableName = "session_history")
data class SessionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hostId: Long? = null,
    val hostAlias: String,
    val hostname: String,
    val port: Int,
    val username: String,
    val authUsed: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val status: String,
    val errorMessage: String? = null,
    val transcriptPreview: String = ""
)

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean {
    if (this == null && other == null) return true
    if (this == null || other == null) return false
    return this.contentEquals(other)
}
