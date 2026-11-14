package com.example.ssh

import android.util.Base64
import com.example.data.local.AuthType
import com.example.data.local.HostEntity
import com.example.data.local.SessionStatus
import com.example.data.repository.HostRepository
import com.example.data.repository.KeyVaultRepository
import com.example.data.repository.KnownHostRepository
import com.example.data.repository.SessionHistoryRepository
import com.example.terminal.Vt100TerminalEmulator
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

enum class TabConnectionStatus {
    CONNECTING,
    AWAITING_HOST_TRUST,
    AWAITING_AUTH_INPUT,
    CONNECTED,
    DISCONNECTED,
    ERROR
}

data class PendingHostTrust(
    val hostname: String,
    val port: Int,
    val algorithm: String,
    val fingerprintSha256: String,
    val isKeyChanged: Boolean,
    val previousFingerprint: String?,
    val deferred: CompletableDeferred<Boolean>
)

data class PendingAuthPrompt(
    val title: String,
    val message: String,
    val isSecret: Boolean,
    val deferred: CompletableDeferred<String?>
)

data class ConnectTarget(
    val hostId: Long? = null,
    val alias: String,
    val hostname: String,
    val port: Int = 22,
    val username: String,
    val authType: AuthType,
    val sshKeyId: Long? = null,
    val plainPassword: String? = null,
    val startupCommand: String = "",
    val colorTagHex: Long = 0xFF22D3EE
) {
    companion object {
        fun fromHost(host: HostEntity, decryptedPassword: String?): ConnectTarget {
            val type = runCatching { AuthType.valueOf(host.authType) }.getOrDefault(AuthType.PASSWORD)
            return ConnectTarget(
                hostId = host.id,
                alias = host.alias,
                hostname = host.hostname,
                port = host.port,
                username = host.username,
                authType = type,
                sshKeyId = host.sshKeyId,
                plainPassword = decryptedPassword,
                startupCommand = host.startupCommand,
                colorTagHex = host.colorTagHex
            )
        }
    }
}

data class TerminalTabState(
    val tabId: String,
    val target: ConnectTarget,
    val status: TabConnectionStatus,
    val statusMessage: String,
    val emulator: Vt100TerminalEmulator,
    val startedAt: Long,
    val pendingHostTrust: PendingHostTrust? = null,
    val pendingAuthPrompt: PendingAuthPrompt? = null,
    val bellCounter: Int = 0
)

private class ActiveSessionRuntime(
    val tabId: String,
    val emulator: Vt100TerminalEmulator,
    val writeQueue: Channel<ByteArray> = Channel(Channel.UNLIMITED)
) {
    @Volatile
    var session: Session? = null

    @Volatile
    var channel: ChannelShell? = null

    @Volatile
    var historyId: Long = 0L

    @Volatile
    var closedByUser: Boolean = false

    @Volatile
    var cols: Int = 80

    @Volatile
    var rows: Int = 24

    @Volatile
    var widthPx: Int = 0

    @Volatile
    var heightPx: Int = 0
}

class SshSessionManager(
    private val hostRepository: HostRepository,
    private val keyVaultRepository: KeyVaultRepository,
    private val knownHostRepository: KnownHostRepository,
    private val historyRepository: SessionHistoryRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _tabs = MutableStateFlow<List<TerminalTabState>>(emptyList())
    val tabs: StateFlow<List<TerminalTabState>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    private val runtimes = mutableMapOf<String, ActiveSessionRuntime>()

    fun openSession(target: ConnectTarget): String {
        val tabId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        lateinit var runtime: ActiveSessionRuntime
        val emulator = Vt100TerminalEmulator(
            onBell = {
                _tabs.update { list ->
                    list.map {
                        if (it.tabId == tabId) it.copy(bellCounter = it.bellCounter + 1) else it
                    }
                }
            },
            onDeviceResponse = { responseBytes ->
                runtime.writeQueue.trySend(responseBytes)
            }
        )
        runtime = ActiveSessionRuntime(tabId = tabId, emulator = emulator)
        synchronized(runtimes) {
            runtimes[tabId] = runtime
        }

        val initialTab = TerminalTabState(
            tabId = tabId,
            target = target,
            status = TabConnectionStatus.CONNECTING,
            statusMessage = "Connecting to ${target.hostname}:${target.port}...",
            emulator = emulator,
            startedAt = now
        )
        _tabs.update { it + initialTab }
        _activeTabId.value = tabId

        scope.launch {
            runSshLifecycle(runtime, target, now)
        }
        return tabId
    }

    fun selectTab(tabId: String) {
        if (_tabs.value.any { it.tabId == tabId }) {
            _activeTabId.value = tabId
        }
    }

    fun sendBytes(tabId: String, bytes: ByteArray) {
        val runtime = synchronized(runtimes) { runtimes[tabId] } ?: return
        runtime.writeQueue.trySend(bytes)
    }

    fun sendText(tabId: String, text: String) {
        if (text.isEmpty()) return
        sendBytes(tabId, text.toByteArray(StandardCharsets.UTF_8))
    }

    fun resizeTab(tabId: String, cols: Int, rows: Int, widthPx: Int, heightPx: Int) {
        val runtime = synchronized(runtimes) { runtimes[tabId] } ?: return
        val safeCols = cols.coerceIn(20, 240)
        val safeRows = rows.coerceIn(6, 120)
        if (runtime.cols == safeCols && runtime.rows == safeRows) return
        runtime.cols = safeCols
        runtime.rows = safeRows
        runtime.widthPx = widthPx
        runtime.heightPx = heightPx
        runtime.emulator.resize(safeCols, safeRows)
        val ch = runtime.channel ?: return
        if (ch.isConnected) {
            scope.launch {
                runCatching {
                    ch.setPtySize(safeCols, safeRows, widthPx, heightPx)
                }
            }
        }
    }

    fun resolveHostTrust(tabId: String, trusted: Boolean) {
        val tab = _tabs.value.firstOrNull { it.tabId == tabId } ?: return
        val pending = tab.pendingHostTrust ?: return
        updateTab(tabId) {
            it.copy(
                pendingHostTrust = null,
                status = if (trusted) TabConnectionStatus.CONNECTING else TabConnectionStatus.ERROR,
                statusMessage = if (trusted) "Host verified. Authenticating..." else "Host verification rejected"
            )
        }
        pending.deferred.complete(trusted)
    }

    fun resolveAuthPrompt(tabId: String, response: String?) {
        val tab = _tabs.value.firstOrNull { it.tabId == tabId } ?: return
        val pending = tab.pendingAuthPrompt ?: return
        updateTab(tabId) {
            it.copy(
                pendingAuthPrompt = null,
                status = TabConnectionStatus.CONNECTING,
                statusMessage = "Authenticating..."
            )
        }
        pending.deferred.complete(response)
    }

    fun reconnectTab(tabId: String) {
        val existing = _tabs.value.firstOrNull { it.tabId == tabId } ?: return
        closeTab(tabId)
        openSession(existing.target)
    }

    fun closeTab(tabId: String) {
        val runtime = synchronized(runtimes) { runtimes.remove(tabId) }
        if (runtime != null) {
            runtime.closedByUser = true
            runtime.writeQueue.close()
            scope.launch {
                runCatching { runtime.channel?.disconnect() }
                runCatching { runtime.session?.disconnect() }
            }
        }
        _tabs.update { current ->
            val filtered = current.filterNot { it.tabId == tabId }
            if (_activeTabId.value == tabId) {
                _activeTabId.value = filtered.lastOrNull()?.tabId
            }
            filtered
        }
    }

    private suspend fun runSshLifecycle(
        runtime: ActiveSessionRuntime,
        target: ConnectTarget,
        startedAt: Long
    ) {
        SshKeyTools.ensureCryptoProviders()
        val emulator = runtime.emulator
        emulator.appendLocalLine(
            "[easy-terminal] Connecting to ${target.username}@${target.hostname}:${target.port}...",
            0xFF22D3EE.toInt()
        )

        var historyId = 0L
        var session: Session? = null
        var channel: ChannelShell? = null

        try {
            val jsch = JSch()
            var authLabel = if (target.authType == AuthType.SSH_KEY) "SSH Key" else "Password"

            if (target.authType == AuthType.SSH_KEY && target.sshKeyId != null) {
                val keyMaterial = keyVaultRepository.loadDecryptedKey(target.sshKeyId)
                    ?: throw IllegalStateException("Selected SSH key was not found in the local vault.")
                authLabel = "Key (${keyMaterial.key.algorithm})"
                val passphraseBytes = keyMaterial.passphrase?.toByteArray(StandardCharsets.UTF_8)
                jsch.addIdentity(
                    keyMaterial.key.name,
                    keyMaterial.privateKeyBytes,
                    keyMaterial.key.publicKeyOpenSsh.toByteArray(StandardCharsets.UTF_8),
                    passphraseBytes
                )
            }

            jsch.hostKeyRepository = createTofuHostKeyRepository(runtime.tabId, target)

            session = jsch.getSession(target.username, target.hostname, target.port)
            runtime.session = session

            if (target.authType == AuthType.PASSWORD && !target.plainPassword.isNullOrEmpty()) {
                session.setPassword(target.plainPassword.toByteArray(StandardCharsets.UTF_8))
            }

            session.setConfig("StrictHostKeyChecking", "ask")
            session.setConfig(
                "PreferredAuthentications",
                if (target.authType == AuthType.SSH_KEY) {
                    "publickey,keyboard-interactive,password"
                } else {
                    "password,keyboard-interactive,publickey"
                }
            )
            session.serverAliveInterval = 20_000
            session.serverAliveCountMax = 3
            session.userInfo = createUserInfo(runtime.tabId, target)

            historyId = historyRepository.startSession(
                hostId = target.hostId,
                hostAlias = target.alias,
                hostname = target.hostname,
                port = target.port,
                username = target.username,
                authUsed = authLabel,
                startedAt = startedAt
            )
            runtime.historyId = historyId

            session.connect(15_000)

            emulator.appendLocalLine(
                "[easy-terminal] SSH handshake established. Opening PTY (xterm-256color)...",
                0xFF34D399.toInt()
            )

            channel = session.openChannel("shell") as ChannelShell
            runtime.channel = channel
            channel.setPty(true)
            channel.setPtyType("xterm-256color")
            channel.setPtySize(runtime.cols, runtime.rows, runtime.widthPx, runtime.heightPx)

            val inputStream = channel.inputStream
            val outputStream: OutputStream = channel.outputStream
            channel.connect(10_000)

            target.hostId?.let { hostRepository.markConnected(it) }

            updateTab(runtime.tabId) {
                it.copy(
                    status = TabConnectionStatus.CONNECTED,
                    statusMessage = "Connected • $authLabel"
                )
            }

            val writerJob = scope.launch {
                try {
                    for (chunk in runtime.writeQueue) {
                        withContext(Dispatchers.IO) {
                            outputStream.write(chunk)
                            outputStream.flush()
                        }
                    }
                } catch (_: Exception) {
                }
            }

            if (target.startupCommand.isNotBlank()) {
                runtime.writeQueue.trySend(
                    (target.startupCommand.trim() + "\r").toByteArray(StandardCharsets.UTF_8)
                )
            }

            val reader = InputStreamReader(inputStream, StandardCharsets.UTF_8)
            val charBuf = CharArray(4096)
            while (channel.isConnected && !runtime.closedByUser) {
                val readCount = reader.read(charBuf)
                if (readCount == -1) break
                if (readCount > 0) {
                    emulator.feed(String(charBuf, 0, readCount))
                }
            }

            writerJob.cancel()

            val exitStatus = channel.exitStatus
            val finalStatus = if (runtime.closedByUser || exitStatus == 0) {
                SessionStatus.COMPLETED
            } else {
                SessionStatus.DISCONNECTED
            }
            emulator.appendLocalLine(
                "\r\n[easy-terminal] Session closed (exit $exitStatus).",
                0xFF8B9BB4.toInt()
            )
            updateTab(runtime.tabId) {
                it.copy(
                    status = TabConnectionStatus.DISCONNECTED,
                    statusMessage = "Disconnected (exit $exitStatus)"
                )
            }
            historyRepository.finishSession(
                historyId = historyId,
                status = finalStatus.name,
                errorMessage = null,
                transcriptPreview = emulator.getTranscriptPreview()
            )
        } catch (e: Exception) {
            if (!runtime.closedByUser) {
                val msg = friendlySshError(e)
                emulator.appendLocalLine(
                    "\r\n[easy-terminal] Error: $msg",
                    0xFFFB7185.toInt()
                )
                updateTab(runtime.tabId) {
                    it.copy(
                        status = TabConnectionStatus.ERROR,
                        statusMessage = msg,
                        pendingHostTrust = null,
                        pendingAuthPrompt = null
                    )
                }
                if (historyId > 0L) {
                    historyRepository.finishSession(
                        historyId = historyId,
                        status = SessionStatus.FAILED.name,
                        errorMessage = msg,
                        transcriptPreview = emulator.getTranscriptPreview()
                    )
                } else {
                    val failedId = historyRepository.startSession(
                        hostId = target.hostId,
                        hostAlias = target.alias,
                        hostname = target.hostname,
                        port = target.port,
                        username = target.username,
                        authUsed = if (target.authType == AuthType.SSH_KEY) "SSH Key" else "Password",
                        startedAt = startedAt
                    )
                    historyRepository.finishSession(
                        historyId = failedId,
                        status = SessionStatus.FAILED.name,
                        errorMessage = msg,
                        transcriptPreview = emulator.getTranscriptPreview()
                    )
                }
            } else if (historyId > 0L) {
                historyRepository.finishSession(
                    historyId = historyId,
                    status = SessionStatus.COMPLETED.name,
                    errorMessage = null,
                    transcriptPreview = emulator.getTranscriptPreview()
                )
            }
        } finally {
            runCatching { channel?.disconnect() }
            runCatching { session?.disconnect() }
        }
    }

    private fun createTofuHostKeyRepository(
        tabId: String,
        target: ConnectTarget
    ): HostKeyRepository {
        return object : HostKeyRepository {
            override fun check(host: String, key: ByteArray): Int {
                val known = runBlocking { knownHostRepository.getKnownHost(target.hostname, target.port) }
                    ?: return HostKeyRepository.NOT_INCLUDED
                val incomingFp = SshKeyTools.computeSha256Fingerprint(key)
                return if (known.fingerprintSha256 == incomingFp) {
                    HostKeyRepository.OK
                } else {
                    HostKeyRepository.CHANGED
                }
            }

            override fun add(hostkey: HostKey, ui: UserInfo?) {
                val fp = SshKeyTools.computeSha256Fingerprint(
                    Base64.decode(hostkey.key, Base64.DEFAULT)
                )
                runBlocking {
                    knownHostRepository.trustHost(
                        hostname = target.hostname,
                        port = target.port,
                        algorithm = hostkey.type,
                        fingerprintSha256 = fp,
                        publicKeyBase64 = hostkey.key
                    )
                }
            }

            override fun remove(host: String, type: String?) {
                runBlocking { knownHostRepository.removeHost("${target.hostname}:${target.port}") }
            }

            override fun remove(host: String, type: String?, key: ByteArray?) {
                runBlocking { knownHostRepository.removeHost("${target.hostname}:${target.port}") }
            }

            override fun getKnownHostsRepositoryID(): String = "easy_terminal_room_known_hosts"

            override fun getHostKey(): Array<HostKey> = emptyArray()

            override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
        }
    }

    private fun createUserInfo(tabId: String, target: ConnectTarget): UserInfo {
        return object : UserInfo {
            private var lastSecret: String? = null

            override fun getPassphrase(): String? = lastSecret
            override fun getPassword(): String? = lastSecret

            override fun promptPassword(message: String): Boolean =
                requestAuthInput("Password Required", message, isSecret = true)

            override fun promptPassphrase(message: String): Boolean =
                requestAuthInput("Key Passphrase Required", message, isSecret = true)

            override fun promptYesNo(message: String): Boolean {
                val known = runBlocking { knownHostRepository.getKnownHost(target.hostname, target.port) }
                val fpMatch = Regex("(SHA256:[A-Za-z0-9+/=]+)").find(message)?.value
                    ?: message.lines().firstOrNull { it.contains("SHA256") || it.contains(":") }
                    ?: "Fingerprint unavailable"
                val algoMatch = Regex("(ssh-ed25519|ssh-rsa|ecdsa-[a-z0-9-]+|rsa-sha2-[0-9]+)", RegexOption.IGNORE_CASE)
                    .find(message)?.value ?: "SSH Host Key"
                val isChanged = message.contains("REMOTE HOST IDENTIFICATION HAS CHANGED", ignoreCase = true) ||
                    (known != null && known.fingerprintSha256 != fpMatch)

                val deferred = CompletableDeferred<Boolean>()
                updateTab(tabId) {
                    it.copy(
                        status = TabConnectionStatus.AWAITING_HOST_TRUST,
                        statusMessage = "Verify host fingerprint...",
                        pendingHostTrust = PendingHostTrust(
                            hostname = target.hostname,
                            port = target.port,
                            algorithm = algoMatch,
                            fingerprintSha256 = fpMatch,
                            isKeyChanged = isChanged,
                            previousFingerprint = known?.fingerprintSha256,
                            deferred = deferred
                        )
                    )
                }
                return runBlocking { deferred.await() }
            }

            override fun showMessage(message: String) {
                val runtime = synchronized(runtimes) { runtimes[tabId] }
                runtime?.emulator?.appendLocalLine("[ssh] $message", 0xFF8B9BB4.toInt())
            }

            private fun requestAuthInput(title: String, message: String, isSecret: Boolean): Boolean {
                val deferred = CompletableDeferred<String?>()
                updateTab(tabId) {
                    it.copy(
                        status = TabConnectionStatus.AWAITING_AUTH_INPUT,
                        statusMessage = title,
                        pendingAuthPrompt = PendingAuthPrompt(
                            title = title,
                            message = message,
                            isSecret = isSecret,
                            deferred = deferred
                        )
                    )
                }
                val answer = runBlocking { deferred.await() }
                lastSecret = answer
                return answer != null
            }
        }
    }

    private fun updateTab(tabId: String, transform: (TerminalTabState) -> TerminalTabState) {
        _tabs.update { list ->
            list.map { if (it.tabId == tabId) transform(it) else it }
        }
    }

    private fun friendlySshError(e: Exception): String {
        val raw = e.message ?: e::class.java.simpleName
        return when {
            raw.contains("UnknownHostException", ignoreCase = true) ->
                "Unable to resolve hostname. Check the address or network connection."
            raw.contains("ECONNREFUSED", ignoreCase = true) || raw.contains("Connection refused", ignoreCase = true) ->
                "Connection refused. Verify the VPS IP, SSH port, and sshd status."
            raw.contains("Auth fail", ignoreCase = true) || raw.contains("Auth cancel", ignoreCase = true) ->
                "Authentication failed. Verify username, password, or SSH key."
            raw.contains("timed out", ignoreCase = true) ->
                "Connection timed out. Check firewall rules or port forwarding."
            raw.contains("HostKey has been changed", ignoreCase = true) || raw.contains("reject HostKey", ignoreCase = true) ->
                "Host key verification declined."
            else -> raw
        }
    }
}
