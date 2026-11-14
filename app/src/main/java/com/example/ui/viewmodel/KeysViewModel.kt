package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.KeyAlgorithm
import com.example.data.local.SshKeyEntity
import com.example.data.repository.KeyVaultRepository
import com.example.ssh.KeyImportResult
import com.example.ssh.SshKeyTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class KeysUiState(
    val keys: List<SshKeyEntity> = emptyList(),
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null
)

class KeysViewModel(
    private val keyVaultRepository: KeyVaultRepository
) : ViewModel() {

    private val isBusy = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<KeysUiState> = combine(
        keyVaultRepository.keys,
        isBusy,
        errorMessage,
        statusMessage
    ) { keys, busy, err, status ->
        KeysUiState(
            keys = keys,
            isBusy = busy,
            errorMessage = err,
            statusMessage = status
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = KeysUiState()
    )

    fun clearMessages() {
        errorMessage.value = null
        statusMessage.value = null
    }

    fun generateKey(
        name: String,
        algorithm: KeyAlgorithm,
        passphrase: String?,
        onSuccess: () -> Unit
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            errorMessage.value = "Please enter a key name."
            return
        }
        viewModelScope.launch {
            isBusy.value = true
            errorMessage.value = null
            try {
                val generated = withContext(Dispatchers.IO) {
                    SshKeyTools.generateKeyPair(
                        algorithm = algorithm,
                        comment = cleanName.lowercase().replace(' ', '-'),
                        passphrase = passphrase?.takeIf { it.isNotEmpty() }
                    )
                }
                keyVaultRepository.saveEncryptedKey(
                    name = cleanName,
                    algorithm = generated.algorithm.name,
                    publicKeyOpenSsh = generated.publicKeyOpenSsh,
                    fingerprintSha256 = generated.fingerprintSha256,
                    privateKeyBytes = generated.privateKeyBytes,
                    passphrase = passphrase?.takeIf { it.isNotEmpty() }
                )
                statusMessage.value = "Generated ${algorithm.displayLabel} key \"$cleanName\"."
                onSuccess()
            } catch (e: Exception) {
                errorMessage.value = e.message ?: "Failed to generate SSH key."
            } finally {
                isBusy.value = false
            }
        }
    }

    fun importKey(
        name: String,
        privateKeyBytes: ByteArray,
        passphrase: String?,
        onPassphraseNeeded: () -> Unit,
        onSuccess: () -> Unit
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            errorMessage.value = "Please enter a label for the imported key."
            return
        }
        viewModelScope.launch {
            isBusy.value = true
            errorMessage.value = null
            try {
                val result = withContext(Dispatchers.IO) {
                    SshKeyTools.importPrivateKey(
                        rawBytes = privateKeyBytes,
                        passphrase = passphrase?.takeIf { it.isNotEmpty() },
                        comment = cleanName.lowercase().replace(' ', '-')
                    )
                }
                when (result) {
                    is KeyImportResult.Success -> {
                        keyVaultRepository.saveEncryptedKey(
                            name = cleanName,
                            algorithm = result.key.detectedAlgorithm,
                            publicKeyOpenSsh = result.key.publicKeyOpenSsh,
                            fingerprintSha256 = result.key.fingerprintSha256,
                            privateKeyBytes = result.key.privateKeyBytes,
                            passphrase = passphrase?.takeIf { it.isNotEmpty() }
                        )
                        statusMessage.value = "Imported key \"$cleanName\" into encrypted vault."
                        onSuccess()
                    }
                    KeyImportResult.PassphraseRequired -> {
                        errorMessage.value = "This key is passphrase-protected. Enter its passphrase to import."
                        onPassphraseNeeded()
                    }
                    is KeyImportResult.InvalidKey -> {
                        errorMessage.value = result.reason
                    }
                }
            } catch (e: Exception) {
                errorMessage.value = e.message ?: "Failed to import private key."
            } finally {
                isBusy.value = false
            }
        }
    }

    fun deleteKey(id: Long) {
        viewModelScope.launch {
            keyVaultRepository.deleteKey(id)
            statusMessage.value = "SSH key removed from local vault."
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return KeysViewModel(container.keyVaultRepository) as T
                }
            }
    }
}
