package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.HostEntity
import com.example.data.local.SnippetEntity
import com.example.data.repository.HostRepository
import com.example.data.repository.SnippetRepository
import com.example.data.repository.TerminalPreferences
import com.example.data.repository.TerminalPreferencesRepository
import com.example.ssh.ConnectTarget
import com.example.ssh.SshSessionManager
import com.example.ssh.TerminalTabState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TerminalUiState(
    val tabs: List<TerminalTabState> = emptyList(),
    val activeTabId: String? = null,
    val savedHosts: List<HostEntity> = emptyList(),
    val snippets: List<SnippetEntity> = emptyList(),
    val preferences: TerminalPreferences = TerminalPreferences(),
    val ctrlActive: Boolean = false,
    val ctrlLocked: Boolean = false,
    val altActive: Boolean = false,
    val altLocked: Boolean = false
) {
    val activeTab: TerminalTabState?
        get() = tabs.firstOrNull { it.tabId == activeTabId } ?: tabs.firstOrNull()
}

private data class ModifierState(
    val ctrlActive: Boolean = false,
    val ctrlLocked: Boolean = false,
    val altActive: Boolean = false,
    val altLocked: Boolean = false
)

class TerminalViewModel(
    private val sshSessionManager: SshSessionManager,
    private val hostRepository: HostRepository,
    private val snippetRepository: SnippetRepository,
    private val preferencesRepository: TerminalPreferencesRepository
) : ViewModel() {

    private val modifierState = MutableStateFlow(ModifierState())

    val uiState: StateFlow<TerminalUiState> = combine(
        sshSessionManager.tabs,
        sshSessionManager.activeTabId,
        hostRepository.hosts,
        snippetRepository.snippets,
        preferencesRepository.preferences,
        modifierState
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val tabs = values[0] as List<TerminalTabState>
        val activeId = values[1] as String?
        @Suppress("UNCHECKED_CAST")
        val hosts = values[2] as List<HostEntity>
        @Suppress("UNCHECKED_CAST")
        val snippets = values[3] as List<SnippetEntity>
        val prefs = values[4] as TerminalPreferences
        val mods = values[5] as ModifierState

        TerminalUiState(
            tabs = tabs,
            activeTabId = activeId,
            savedHosts = hosts,
            snippets = snippets,
            preferences = prefs,
            ctrlActive = mods.ctrlActive,
            ctrlLocked = mods.ctrlLocked,
            altActive = mods.altActive,
            altLocked = mods.altLocked
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TerminalUiState()
    )

    fun selectTab(tabId: String) = sshSessionManager.selectTab(tabId)

    fun closeTab(tabId: String) = sshSessionManager.closeTab(tabId)

    fun reconnectTab(tabId: String) = sshSessionManager.reconnectTab(tabId)

    fun openHostInNewTab(host: HostEntity) {
        val password = hostRepository.decryptPassword(host)
        sshSessionManager.openSession(ConnectTarget.fromHost(host, password))
    }

    fun resizeTab(tabId: String, cols: Int, rows: Int, widthPx: Int, heightPx: Int) {
        sshSessionManager.resizeTab(tabId, cols, rows, widthPx, heightPx)
    }

    fun toggleCtrl() {
        val current = modifierState.value
        modifierState.value = when {
            !current.ctrlActive -> current.copy(ctrlActive = true, ctrlLocked = false)
            !current.ctrlLocked -> current.copy(ctrlActive = true, ctrlLocked = true)
            else -> current.copy(ctrlActive = false, ctrlLocked = false)
        }
    }

    fun toggleAlt() {
        val current = modifierState.value
        modifierState.value = when {
            !current.altActive -> current.copy(altActive = true, altLocked = false)
            !current.altLocked -> current.copy(altActive = true, altLocked = true)
            else -> current.copy(altActive = false, altLocked = false)
        }
    }

    fun sendTypedText(tabId: String, rawText: String) {
        if (rawText.isEmpty()) return
        val mods = modifierState.value
        if (!mods.ctrlActive && !mods.altActive) {
            val normalized = rawText.replace("\n", "\r")
            sshSessionManager.sendText(tabId, normalized)
            return
        }

        val firstChar = rawText[0]
        val bytes = mutableListOf<Byte>()
        if (mods.altActive) {
            bytes.add(0x1B)
        }
        if (mods.ctrlActive) {
            val ctrlByte = mapCharToControlByte(firstChar)
            if (ctrlByte != null) {
                bytes.add(ctrlByte)
            } else {
                bytes.addAll(firstChar.toString().toByteArray(Charsets.UTF_8).toList())
            }
        } else {
            bytes.addAll(firstChar.toString().toByteArray(Charsets.UTF_8).toList())
        }
        if (rawText.length > 1) {
            val rest = rawText.substring(1).replace("\n", "\r")
            bytes.addAll(rest.toByteArray(Charsets.UTF_8).toList())
        }
        sshSessionManager.sendBytes(tabId, bytes.toByteArray())
        consumeNonLockedModifiers()
    }

    fun sendRawBytes(tabId: String, bytes: ByteArray) {
        val mods = modifierState.value
        if (mods.altActive && bytes.isNotEmpty() && bytes[0] != 0x1B.toByte()) {
            sshSessionManager.sendBytes(tabId, byteArrayOf(0x1B) + bytes)
        } else {
            sshSessionManager.sendBytes(tabId, bytes)
        }
        consumeNonLockedModifiers()
    }

    fun sendRawText(tabId: String, text: String) {
        sshSessionManager.sendText(tabId, text)
    }

    fun sendArrowKey(tabId: String, direction: Char, applicationMode: Boolean) {
        val prefix = if (applicationMode) "\u001BO" else "\u001B["
        sendRawBytes(tabId, "$prefix$direction".toByteArray(Charsets.US_ASCII))
    }

    fun executeSnippet(tabId: String, snippet: SnippetEntity) {
        val payload = if (snippet.executeImmediately) {
            snippet.command.trimEnd('\r', '\n') + "\r"
        } else {
            snippet.command
        }
        sshSessionManager.sendText(tabId, payload)
    }

    fun saveSnippet(
        id: Long = 0,
        title: String,
        command: String,
        category: String,
        executeImmediately: Boolean
    ) {
        viewModelScope.launch {
            snippetRepository.saveSnippet(id, title, command, category, executeImmediately)
        }
    }

    fun deleteSnippet(id: Long) {
        viewModelScope.launch {
            snippetRepository.deleteSnippet(id)
        }
    }

    fun resolveHostTrust(tabId: String, trusted: Boolean) {
        sshSessionManager.resolveHostTrust(tabId, trusted)
    }

    fun resolveAuthPrompt(tabId: String, response: String?) {
        sshSessionManager.resolveAuthPrompt(tabId, response)
    }

    fun adjustFontSize(delta: Int) {
        viewModelScope.launch {
            val next = (uiState.value.preferences.fontSizeSp + delta).coerceIn(10, 20)
            preferencesRepository.setFontSizeSp(next)
        }
    }

    fun clearActiveTerminalScreen(tabId: String) {
        uiState.value.tabs.firstOrNull { it.tabId == tabId }?.emulator?.clearScrollbackAndScreen()
    }

    private fun consumeNonLockedModifiers() {
        val current = modifierState.value
        val nextCtrl = if (current.ctrlLocked) current.ctrlActive else false
        val nextAlt = if (current.altLocked) current.altActive else false
        if (nextCtrl != current.ctrlActive || nextAlt != current.altActive) {
            modifierState.value = current.copy(ctrlActive = nextCtrl, altActive = nextAlt)
        }
    }

    private fun mapCharToControlByte(ch: Char): Byte? {
        return when (ch) {
            in 'a'..'z' -> (ch.code - 'a'.code + 1).toByte()
            in 'A'..'Z' -> (ch.code - 'A'.code + 1).toByte()
            '@' -> 0x00
            '[' -> 0x1B
            '\\' -> 0x1C
            ']' -> 0x1D
            '^' -> 0x1E
            '_' -> 0x1F
            '?' -> 0x7F
            else -> null
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TerminalViewModel(
                        sshSessionManager = container.sshSessionManager,
                        hostRepository = container.hostRepository,
                        snippetRepository = container.snippetRepository,
                        preferencesRepository = container.preferencesRepository
                    ) as T
                }
            }
    }
}
