package com.example.data.repository

import com.example.data.local.AuthType
import com.example.data.local.HostDao
import com.example.data.local.HostEntity
import com.example.data.local.KnownHostDao
import com.example.data.local.KnownHostEntity
import com.example.data.local.SessionHistoryDao
import com.example.data.local.SessionHistoryEntity
import com.example.data.local.SnippetDao
import com.example.data.local.SnippetEntity
import com.example.data.local.SshKeyDao
import com.example.data.local.SshKeyEntity
import com.example.security.VaultCipher
import kotlinx.coroutines.flow.Flow

data class DecryptedKeyMaterial(
    val key: SshKeyEntity,
    val privateKeyBytes: ByteArray,
    val passphrase: String?
)

class HostRepository(
    private val hostDao: HostDao,
    private val vaultCipher: VaultCipher
) {
    val hosts: Flow<List<HostEntity>> = hostDao.observeAllHosts()

    suspend fun getHostById(id: Long): HostEntity? = hostDao.getById(id)

    suspend fun saveHost(
        id: Long = 0,
        alias: String,
        hostname: String,
        port: Int,
        username: String,
        authType: AuthType,
        sshKeyId: Long?,
        plainPassword: String?,
        keepExistingPasswordIfBlank: Boolean,
        startupCommand: String,
        colorTagHex: Long
    ): Long {
        val existing = if (id != 0L) hostDao.getById(id) else null
        val (encPw, pwIv) = when {
            authType != AuthType.PASSWORD -> null to null
            !plainPassword.isNullOrEmpty() -> {
                val encrypted = vaultCipher.encryptString(plainPassword)
                encrypted.ciphertext to encrypted.iv
            }
            keepExistingPasswordIfBlank && existing != null -> {
                existing.encryptedPassword to existing.passwordIv
            }
            else -> null to null
        }

        val entity = HostEntity(
            id = id,
            alias = alias.trim().ifBlank { hostname.trim() },
            hostname = hostname.trim(),
            port = port,
            username = username.trim(),
            authType = authType.name,
            sshKeyId = if (authType == AuthType.SSH_KEY) sshKeyId else null,
            encryptedPassword = encPw,
            passwordIv = pwIv,
            startupCommand = startupCommand.trim(),
            colorTagHex = colorTagHex,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            lastConnectedAt = existing?.lastConnectedAt
        )
        return if (id == 0L) {
            hostDao.insertHost(entity)
        } else {
            hostDao.updateHost(entity)
            id
        }
    }

    fun decryptPassword(host: HostEntity): String? {
        val cipher = host.encryptedPassword ?: return null
        val iv = host.passwordIv ?: return null
        return runCatching { vaultCipher.decryptString(cipher, iv) }.getOrNull()
    }

    suspend fun markConnected(hostId: Long) {
        if (hostId > 0L) {
            hostDao.updateLastConnected(hostId, System.currentTimeMillis())
        }
    }

    suspend fun deleteHost(hostId: Long) = hostDao.deleteHostById(hostId)
}

class KeyVaultRepository(
    private val sshKeyDao: SshKeyDao,
    private val vaultCipher: VaultCipher
) {
    val keys: Flow<List<SshKeyEntity>> = sshKeyDao.observeAllKeys()

    suspend fun getKeyById(id: Long): SshKeyEntity? = sshKeyDao.getById(id)

    suspend fun saveEncryptedKey(
        name: String,
        algorithm: String,
        publicKeyOpenSsh: String,
        fingerprintSha256: String,
        privateKeyBytes: ByteArray,
        passphrase: String?
    ): Long {
        val encryptedKey = vaultCipher.encrypt(privateKeyBytes)
        val encryptedPass = if (!passphrase.isNullOrEmpty()) {
            vaultCipher.encryptString(passphrase)
        } else {
            null
        }
        val entity = SshKeyEntity(
            name = name.trim(),
            algorithm = algorithm,
            publicKeyOpenSsh = publicKeyOpenSsh.trim(),
            fingerprintSha256 = fingerprintSha256,
            encryptedPrivateKey = encryptedKey.ciphertext,
            privateKeyIv = encryptedKey.iv,
            hasPassphrase = !passphrase.isNullOrEmpty(),
            encryptedPassphrase = encryptedPass?.ciphertext,
            passphraseIv = encryptedPass?.iv
        )
        return sshKeyDao.insertKey(entity)
    }

    suspend fun loadDecryptedKey(keyId: Long): DecryptedKeyMaterial? {
        val entity = sshKeyDao.getById(keyId) ?: return null
        val privateBytes = vaultCipher.decrypt(entity.encryptedPrivateKey, entity.privateKeyIv)
        val passphrase = if (
            entity.hasPassphrase &&
            entity.encryptedPassphrase != null &&
            entity.passphraseIv != null
        ) {
            vaultCipher.decryptString(entity.encryptedPassphrase, entity.passphraseIv)
        } else {
            null
        }
        return DecryptedKeyMaterial(
            key = entity,
            privateKeyBytes = privateBytes,
            passphrase = passphrase
        )
    }

    suspend fun deleteKey(id: Long) = sshKeyDao.deleteKeyById(id)
}

class KnownHostRepository(private val knownHostDao: KnownHostDao) {
    val knownHosts: Flow<List<KnownHostEntity>> = knownHostDao.observeAllKnownHosts()

    suspend fun getKnownHost(hostname: String, port: Int): KnownHostEntity? =
        knownHostDao.getByHostKeyId("$hostname:$port")

    suspend fun trustHost(
        hostname: String,
        port: Int,
        algorithm: String,
        fingerprintSha256: String,
        publicKeyBase64: String
    ) {
        knownHostDao.upsertKnownHost(
            KnownHostEntity(
                hostKeyId = "$hostname:$port",
                hostname = hostname,
                port = port,
                algorithm = algorithm,
                fingerprintSha256 = fingerprintSha256,
                publicKeyBase64 = publicKeyBase64
            )
        )
    }

    suspend fun removeHost(hostKeyId: String) = knownHostDao.deleteKnownHost(hostKeyId)

    suspend fun clearAll() = knownHostDao.clearAll()
}

class SnippetRepository(private val snippetDao: SnippetDao) {
    val snippets: Flow<List<SnippetEntity>> = snippetDao.observeAllSnippets()

    suspend fun seedDefaultsIfEmpty() {
        if (snippetDao.countSnippets() == 0) {
            snippetDao.insertSnippets(defaultSnippets())
        }
    }

    suspend fun saveSnippet(
        id: Long = 0,
        title: String,
        command: String,
        category: String,
        executeImmediately: Boolean
    ) {
        val entity = SnippetEntity(
            id = id,
            title = title.trim(),
            command = command.trim(),
            category = category.trim().ifBlank { "Custom" },
            executeImmediately = executeImmediately,
            sortOrder = if (id == 0L) 100 else 0,
            isBuiltIn = false
        )
        if (id == 0L) {
            snippetDao.insertSnippet(entity)
        } else {
            snippetDao.updateSnippet(entity)
        }
    }

    suspend fun deleteSnippet(id: Long) = snippetDao.deleteSnippetById(id)

    private fun defaultSnippets(): List<SnippetEntity> = listOf(
        SnippetEntity(
            title = "System Monitor",
            command = "htop || top",
            category = "System",
            executeImmediately = true,
            sortOrder = 1,
            isBuiltIn = true
        ),
        SnippetEntity(
            title = "Disk Usage",
            command = "df -h",
            category = "System",
            executeImmediately = true,
            sortOrder = 2,
            isBuiltIn = true
        ),
        SnippetEntity(
            title = "Memory & Swap",
            command = "free -h",
            category = "System",
            executeImmediately = true,
            sortOrder = 3,
            isBuiltIn = true
        ),
        SnippetEntity(
            title = "Docker Containers",
            command = "docker ps --format 'table {{.Names}}\\t{{.Status}}\\t{{.Ports}}'",
            category = "Docker",
            executeImmediately = true,
            sortOrder = 4,
            isBuiltIn = true
        ),
        SnippetEntity(
            title = "Recent System Logs",
            command = "sudo journalctl -n 50 --no-pager",
            category = "Logs",
            executeImmediately = true,
            sortOrder = 5,
            isBuiltIn = true
        ),
        SnippetEntity(
            title = "Listening Ports",
            command = "ss -tulpn",
            category = "Network",
            executeImmediately = true,
            sortOrder = 6,
            isBuiltIn = true
        ),
        SnippetEntity(
            title = "Service Status...",
            command = "sudo systemctl status ",
            category = "System",
            executeImmediately = false,
            sortOrder = 7,
            isBuiltIn = true
        ),
        SnippetEntity(
            title = "Attach tmux",
            command = "tmux attach || tmux new -s main",
            category = "Shell",
            executeImmediately = true,
            sortOrder = 8,
            isBuiltIn = true
        )
    )
}

class SessionHistoryRepository(private val sessionHistoryDao: SessionHistoryDao) {
    val history: Flow<List<SessionHistoryEntity>> = sessionHistoryDao.observeAllHistory()

    suspend fun startSession(
        hostId: Long?,
        hostAlias: String,
        hostname: String,
        port: Int,
        username: String,
        authUsed: String,
        startedAt: Long
    ): Long {
        return sessionHistoryDao.insertHistory(
            SessionHistoryEntity(
                hostId = hostId,
                hostAlias = hostAlias,
                hostname = hostname,
                port = port,
                username = username,
                authUsed = authUsed,
                startedAt = startedAt,
                status = "CONNECTED"
            )
        )
    }

    suspend fun finishSession(
        historyId: Long,
        status: String,
        errorMessage: String?,
        transcriptPreview: String
    ) {
        if (historyId <= 0L) return
        sessionHistoryDao.finalizeHistory(
            id = historyId,
            endedAt = System.currentTimeMillis(),
            status = status,
            errorMessage = errorMessage,
            transcriptPreview = transcriptPreview
        )
    }

    suspend fun deleteEntry(id: Long) = sessionHistoryDao.deleteById(id)

    suspend fun clearAll() = sessionHistoryDao.clearAll()
}
