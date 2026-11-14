package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.AuthType
import com.example.data.local.HostEntity
import com.example.data.local.SshKeyEntity
import com.example.data.repository.HostRepository
import com.example.data.repository.KeyVaultRepository
import com.example.ssh.ConnectTarget
import com.example.ssh.SshSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HostsUiState(
    val hosts: List<HostEntity> = emptyList(),
    val keys: List<SshKeyEntity> = emptyList(),
    val searchQuery: String = "",
    val activeTabCount: Int = 0
)

class HostsViewModel(
    private val hostRepository: HostRepository,
    keyVaultRepository: KeyVaultRepository,
    private val sshSessionManager: SshSessionManager
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<HostsUiState> = combine(
        hostRepository.hosts,
        keyVaultRepository.keys,
        searchQuery,
        sshSessionManager.tabs
    ) { hosts, keys, query, tabs ->
        val trimmed = query.trim()
        val filtered = if (trimmed.isEmpty()) {
            hosts
        } else {
            hosts.filter {
                it.alias.contains(trimmed, ignoreCase = true) ||
                    it.hostname.contains(trimmed, ignoreCase = true) ||
                    it.username.contains(trimmed, ignoreCase = true)
            }
        }
        HostsUiState(
            hosts = filtered,
            keys = keys,
            searchQuery = query,
            activeTabCount = tabs.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HostsUiState()
    )

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun saveHost(
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
        colorTagHex: Long,
        onSaved: () -> Unit = {}
    ) {
        viewModelScope.launch {
            hostRepository.saveHost(
                id = id,
                alias = alias,
                hostname = hostname,
                port = port,
                username = username,
                authType = authType,
                sshKeyId = sshKeyId,
                plainPassword = plainPassword,
                keepExistingPasswordIfBlank = keepExistingPasswordIfBlank,
                startupCommand = startupCommand,
                colorTagHex = colorTagHex
            )
            onSaved()
        }
    }

    fun deleteHost(hostId: Long) {
        viewModelScope.launch {
            hostRepository.deleteHost(hostId)
        }
    }

    fun duplicateHost(host: HostEntity) {
        viewModelScope.launch {
            val decrypted = hostRepository.decryptPassword(host)
            val auth = runCatching { AuthType.valueOf(host.authType) }.getOrDefault(AuthType.PASSWORD)
            hostRepository.saveHost(
                id = 0,
                alias = "${host.alias} Copy",
                hostname = host.hostname,
                port = host.port,
                username = host.username,
                authType = auth,
                sshKeyId = host.sshKeyId,
                plainPassword = decrypted,
                keepExistingPasswordIfBlank = false,
                startupCommand = host.startupCommand,
                colorTagHex = host.colorTagHex
            )
        }
    }

    fun connectToHost(host: HostEntity) {
        val decrypted = hostRepository.decryptPassword(host)
        sshSessionManager.openSession(ConnectTarget.fromHost(host, decrypted))
    }

    fun quickConnect(
        hostname: String,
        port: Int,
        username: String,
        authType: AuthType,
        sshKeyId: Long?,
        password: String?,
        saveHost: Boolean,
        alias: String
    ) {
        val cleanHost = hostname.trim()
        val cleanUser = username.trim()
        val cleanAlias = alias.trim().ifBlank { "$cleanUser@$cleanHost" }
        if (saveHost) {
            viewModelScope.launch {
                val id = hostRepository.saveHost(
                    alias = cleanAlias,
                    hostname = cleanHost,
                    port = port,
                    username = cleanUser,
                    authType = authType,
                    sshKeyId = sshKeyId,
                    plainPassword = password,
                    keepExistingPasswordIfBlank = false,
                    startupCommand = "",
                    colorTagHex = 0xFF22D3EE
                )
                sshSessionManager.openSession(
                    ConnectTarget(
                        hostId = id,
                        alias = cleanAlias,
                        hostname = cleanHost,
                        port = port,
                        username = cleanUser,
                        authType = authType,
                        sshKeyId = sshKeyId,
                        plainPassword = password
                    )
                )
            }
        } else {
            sshSessionManager.openSession(
                ConnectTarget(
                    hostId = null,
                    alias = cleanAlias,
                    hostname = cleanHost,
                    port = port,
                    username = cleanUser,
                    authType = authType,
                    sshKeyId = sshKeyId,
                    plainPassword = password
                )
            )
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HostsViewModel(
                        hostRepository = container.hostRepository,
                        keyVaultRepository = container.keyVaultRepository,
                        sshSessionManager = container.sshSessionManager
                    ) as T
                }
            }
    }
}
