package com.example

import com.example.data.local.KeyAlgorithm
import com.example.ssh.KeyImportResult
import com.example.ssh.SshKeyTools
import com.example.terminal.Vt100TerminalEmulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TerminalAndKeyUnitTest {

    @Test
    fun `vt100 parses sgr colors, cursor movement, osc title, and alt screen`() {
        val emulator = Vt100TerminalEmulator(initialCols = 40, initialRows = 6)
        emulator.feed("\u001B]0;prod-vps\u0007")
        emulator.feed("\u001B[32mOK\u001B[0m ready\r\nline2")

        val snap1 = emulator.snapshot.value
        assertEquals("prod-vps", snap1.title)
        assertEquals("OK ready", snap1.screen[0].toPlainText())
        assertEquals("line2", snap1.screen[1].toPlainText())
        assertEquals(0xFF22C55E.toInt(), snap1.screen[0].cells[0].fgColor)

        // Enter alternate screen (e.g., vim/htop) and leave it
        emulator.feed("\u001B[?1049hALT SCREEN")
        assertTrue(emulator.snapshot.value.isAlternateScreen)
        assertEquals("ALT SCREEN", emulator.snapshot.value.screen[0].toPlainText())

        emulator.feed("\u001B[?1049l")
        assertFalse(emulator.snapshot.value.isAlternateScreen)
        assertEquals("OK ready", emulator.snapshot.value.screen[0].toPlainText())
    }

    @Test
    fun `vt100 records scrollback and handles resize`() {
        val emulator = Vt100TerminalEmulator(initialCols = 30, initialRows = 6)
        emulator.feed("line1\r\nline2\r\nline3\r\nline4\r\nline5\r\nline6\r\nline7\r\nline8\r\n")
        val snap = emulator.snapshot.value
        assertTrue(snap.scrollback.isNotEmpty())
        assertTrue(emulator.getTranscriptPreview().contains("line1"))
        assertTrue(emulator.getTranscriptPreview().contains("line8"))

        emulator.resize(50, 10)
        assertEquals(50, emulator.snapshot.value.cols)
        assertEquals(10, emulator.snapshot.value.rows)
    }

    @Test
    fun `ed25519 key generation and jsch OpenSSH import succeed`() {
        val generated = SshKeyTools.generateKeyPair(
            algorithm = KeyAlgorithm.ED25519,
            comment = "test-vps",
            passphrase = null
        )
        assertTrue(generated.publicKeyOpenSsh.startsWith("ssh-ed25519 "))
        assertTrue(generated.fingerprintSha256.startsWith("SHA256:"))

        val importResult = SshKeyTools.importPrivateKey(
            rawBytes = generated.privateKeyBytes,
            passphrase = null
        )
        assertTrue(importResult is KeyImportResult.Success)
        val imported = (importResult as KeyImportResult.Success).key
        assertEquals(generated.fingerprintSha256, imported.fingerprintSha256)
        assertEquals(KeyAlgorithm.ED25519.name, imported.detectedAlgorithm)
    }

    @Test
    fun `rsa 4096 key generation and passphrase-protected import succeed`() {
        val generated = SshKeyTools.generateKeyPair(
            algorithm = KeyAlgorithm.RSA_4096,
            comment = "rsa-vps",
            passphrase = "secret-passphrase"
        )
        assertTrue(generated.publicKeyOpenSsh.startsWith("ssh-rsa "))
        assertTrue(generated.fingerprintSha256.startsWith("SHA256:"))

        val withoutPass = SshKeyTools.importPrivateKey(
            rawBytes = generated.privateKeyBytes,
            passphrase = null
        )
        assertTrue(withoutPass is KeyImportResult.PassphraseRequired)

        val withPass = SshKeyTools.importPrivateKey(
            rawBytes = generated.privateKeyBytes,
            passphrase = "secret-passphrase"
        )
        assertTrue(withPass is KeyImportResult.Success)
        val imported = (withPass as KeyImportResult.Success).key
        assertEquals(generated.fingerprintSha256, imported.fingerprintSha256)
        assertEquals(KeyAlgorithm.RSA_4096.name, imported.detectedAlgorithm)
    }
}
