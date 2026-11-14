package com.example.ssh

import android.util.Base64
import com.example.data.local.KeyAlgorithm
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Security

data class GeneratedSshKey(
    val algorithm: KeyAlgorithm,
    val publicKeyOpenSsh: String,
    val fingerprintSha256: String,
    val privateKeyBytes: ByteArray
)

data class ImportedSshKey(
    val detectedAlgorithm: String,
    val publicKeyOpenSsh: String,
    val fingerprintSha256: String,
    val privateKeyBytes: ByteArray,
    val wasEncrypted: Boolean
)

sealed class KeyImportResult {
    data class Success(val key: ImportedSshKey) : KeyImportResult()
    data object PassphraseRequired : KeyImportResult()
    data class InvalidKey(val reason: String) : KeyImportResult()
}

object SshKeyTools {

    @Synchronized
    fun ensureCryptoProviders() {
        val existing = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
        if (existing == null || existing !is BouncyCastleProvider) {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        }
        JSch.setConfig("FingerprintHash", "sha256")
    }

    fun generateKeyPair(
        algorithm: KeyAlgorithm,
        comment: String,
        passphrase: String?
    ): GeneratedSshKey {
        ensureCryptoProviders()
        val cleanComment = comment.trim().ifBlank { "easy-terminal" }

        if (algorithm == KeyAlgorithm.ED25519) {
            val generator = Ed25519KeyPairGenerator()
            generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
            val pair = generator.generateKeyPair()

            val openSshPrivateBlob = OpenSSHPrivateKeyUtil.encodePrivateKey(pair.private)
            val pemWriterOut = StringWriter()
            PemWriter(pemWriterOut).use { writer ->
                writer.writeObject(PemObject("OPENSSH PRIVATE KEY", openSshPrivateBlob))
            }
            val privateBytes = pemWriterOut.toString().toByteArray(StandardCharsets.UTF_8)

            val publicBlob = OpenSSHPublicKeyUtil.encodePublicKey(pair.public)
            val publicKeyBase64 = Base64.encodeToString(publicBlob, Base64.NO_WRAP)
            val publicOpenSsh = "ssh-ed25519 $publicKeyBase64 $cleanComment"
            val fingerprint = computeSha256Fingerprint(publicBlob)

            return GeneratedSshKey(
                algorithm = KeyAlgorithm.ED25519,
                publicKeyOpenSsh = publicOpenSsh,
                fingerprintSha256 = fingerprint,
                privateKeyBytes = privateBytes
            )
        }

        val jsch = JSch()
        val keyPair = KeyPair.genKeyPair(jsch, KeyPair.RSA, 4096)
        try {
            val privateOut = ByteArrayOutputStream()
            val passBytes = passphrase?.takeIf { it.isNotEmpty() }?.toByteArray(StandardCharsets.UTF_8)
            if (passBytes != null) {
                keyPair.writePrivateKey(privateOut, passBytes)
            } else {
                keyPair.writePrivateKey(privateOut)
            }

            val publicOut = ByteArrayOutputStream()
            keyPair.writePublicKey(publicOut, cleanComment)

            val fingerprint = computeSha256Fingerprint(keyPair.publicKeyBlob)
            return GeneratedSshKey(
                algorithm = KeyAlgorithm.RSA_4096,
                publicKeyOpenSsh = publicOut.toString(StandardCharsets.UTF_8.name()).trim(),
                fingerprintSha256 = fingerprint,
                privateKeyBytes = privateOut.toByteArray()
            )
        } finally {
            keyPair.dispose()
        }
    }

    fun importPrivateKey(
        rawBytes: ByteArray,
        passphrase: String?,
        comment: String = "imported-key"
    ): KeyImportResult {
        ensureCryptoProviders()
        if (rawBytes.isEmpty()) {
            return KeyImportResult.InvalidKey("Private key content is empty.")
        }
        val jsch = JSch()
        return try {
            val keyPair = KeyPair.load(jsch, rawBytes, null)
            try {
                val encrypted = keyPair.isEncrypted
                if (encrypted) {
                    if (passphrase.isNullOrEmpty()) {
                        return KeyImportResult.PassphraseRequired
                    }
                    val decrypted = keyPair.decrypt(passphrase.toByteArray(StandardCharsets.UTF_8))
                    if (!decrypted) {
                        return KeyImportResult.InvalidKey("Incorrect passphrase for encrypted private key.")
                    }
                }
                val publicOut = ByteArrayOutputStream()
                keyPair.writePublicKey(publicOut, comment.trim().ifBlank { "easy-terminal" })
                val publicText = publicOut.toString(StandardCharsets.UTF_8.name()).trim()
                val detected = when (keyPair.keyType) {
                    KeyPair.ED25519 -> KeyAlgorithm.ED25519.name
                    KeyPair.RSA -> KeyAlgorithm.RSA_4096.name
                    else -> KeyAlgorithm.IMPORTED.name
                }
                val fingerprint = computeSha256Fingerprint(keyPair.publicKeyBlob)
                KeyImportResult.Success(
                    ImportedSshKey(
                        detectedAlgorithm = detected,
                        publicKeyOpenSsh = publicText,
                        fingerprintSha256 = fingerprint,
                        privateKeyBytes = rawBytes,
                        wasEncrypted = encrypted
                    )
                )
            } finally {
                keyPair.dispose()
            }
        } catch (e: Exception) {
            KeyImportResult.InvalidKey(e.message ?: "Unsupported or malformed private key.")
        }
    }

    fun computeSha256Fingerprint(blob: ByteArray?): String {
        if (blob == null || blob.isEmpty()) return "SHA256:unknown"
        val digest = MessageDigest.getInstance("SHA-256").digest(blob)
        val b64 = Base64.encodeToString(digest, Base64.NO_WRAP or Base64.NO_PADDING)
        return "SHA256:$b64"
    }
}
