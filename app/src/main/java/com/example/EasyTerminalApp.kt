package com.example

import android.app.Application
import com.example.data.local.EasyTerminalDatabase
import com.example.data.repository.HostRepository
import com.example.data.repository.KeyVaultRepository
import com.example.data.repository.KnownHostRepository
import com.example.data.repository.SessionHistoryRepository
import com.example.data.repository.SnippetRepository
import com.example.data.repository.TerminalPreferencesRepository
import com.example.security.CryptoVaultManager
import com.example.ssh.SshKeyTools
import com.example.ssh.SshSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class EasyTerminalApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        SshKeyTools.ensureCryptoProviders()
        container = AppContainer(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            container.snippetRepository.seedDefaultsIfEmpty()
        }
    }
}

class AppContainer(application: Application) {
    private val database = EasyTerminalDatabase.getInstance(application)
    val cryptoVault = CryptoVaultManager()

    val hostRepository = HostRepository(database.hostDao(), cryptoVault)
    val keyVaultRepository = KeyVaultRepository(database.sshKeyDao(), cryptoVault)
    val knownHostRepository = KnownHostRepository(database.knownHostDao())
    val snippetRepository = SnippetRepository(database.snippetDao())
    val sessionHistoryRepository = SessionHistoryRepository(database.sessionHistoryDao())
    val preferencesRepository = TerminalPreferencesRepository(application)

    val sshSessionManager = SshSessionManager(
        hostRepository = hostRepository,
        keyVaultRepository = keyVaultRepository,
        knownHostRepository = knownHostRepository,
        historyRepository = sessionHistoryRepository
    )
}
