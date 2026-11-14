package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.AuthType
import com.example.data.local.KnownHostEntity
import com.example.data.local.SessionHistoryEntity
import com.example.data.repository.HostRepository
import com.example.data.repository.KnownHostRepository
import com.example.data.repository.SessionHistoryRepository
import com.example.data.repository.TerminalPreferences
import com.example.data.repository.TerminalPreferencesRepository
import com.example.ssh.ConnectTarget
import com.example.ssh.SshSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val entries: List<SessionHistoryEntity> = emptyList(),
    val knownHosts: List<KnownHostEntity> = emptyList(),
    val preferences: TerminalPreferences = TerminalPreferences(),
    val filterQuery: String = ""
)

class HistoryViewModel(
    private val historyRepository: SessionHistoryRepository,
    private val hostRepository: HostRepository,
    private val knownHostRepository: KnownHostRepository,
    private val preferencesRepository: TerminalPreferencesRepository,
    private val sshSessionManager: SshSessionManager
) : ViewModel() {

    private val filterQuery = MutableStateFlow("")

    val uiState: StateFlow<HistoryUiState> = combine(
        historyRepository.history,
        knownHostRepository.knownHosts,
        preferencesRepository.preferences,
        filterQuery
    ) { entries, knownHosts, prefs, query ->
        val trimmed = query.trim()
        val filtered = if (trimmed.isEmpty()) {
            entries
        } else {
            entries.filter {
                it.hostAlias.contains(trimmed, ignoreCase = true) ||
                    it.hostname.contains(trimmed, ignoreCase = true) ||
                    it.username.contains(trimmed, ignoreCase = true) ||
                    it.status.contains(trimmed, ignoreCase = true)
            }
        }
        HistoryUiState(
            entries = filtered,
            knownHosts = knownHosts,
            preferences = prefs,
            filterQuery = query
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState()
    )

    fun updateFilter(query: String) {
        filterQuery.value = query
    }

    fun reconnectFromHistory(entry: SessionHistoryEntity) {
        viewModelScope.launch {
            val savedHost = entry.hostId?.let { hostRepository.getHostById(it) }
            if (savedHost != null) {
                val pw = hostRepository.decryptPassword(savedHost)
                sshSessionManager.openSession(ConnectTarget.fromHost(savedHost, pw))
            } else {
                sshSessionManager.openSession(
                    ConnectTarget(
                        hostId = null,
                        alias = entry.hostAlias,
                        hostname = entry.hostname,
                        port = entry.port,
                        username = entry.username,
                        authType = AuthType.PASSWORD
                    )
                )
            }
        }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch {
            historyRepository.deleteEntry(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.clearAll()
        }
    }

    fun removeKnownHost(hostKeyId: String) {
        viewModelScope.launch {
            knownHostRepository.removeHost(hostKeyId)
        }
    }

    fun clearKnownHosts() {
        viewModelScope.launch {
            knownHostRepository.clearAll()
        }
    }

    fun setFontSize(sizeSp: Int) {
        viewModelScope.launch { preferencesRepository.setFontSizeSp(sizeSp) }
    }

    fun setShowExtraKeybar(show: Boolean) {
        viewModelScope.launch { preferencesRepository.setShowExtraKeybar(show) }
    }

    fun setCursorBlink(blink: Boolean) {
        viewModelScope.launch { preferencesRepository.setCursorBlink(blink) }
    }

    fun setKeepScreenOn(keepOn: Boolean) {
        viewModelScope.launch { preferencesRepository.setKeepScreenOn(keepOn) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HistoryViewModel(
                        historyRepository = container.sessionHistoryRepository,
                        hostRepository = container.hostRepository,
                        knownHostRepository = container.knownHostRepository,
                        preferencesRepository = container.preferencesRepository,
                        sshSessionManager = container.sshSessionManager
                    ) as T
                }
            }
    }
}
