package com.example.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.HostEntity
import com.example.data.local.SnippetEntity
import com.example.ssh.PendingAuthPrompt
import com.example.ssh.PendingHostTrust
import com.example.ssh.TabConnectionStatus
import com.example.ssh.TerminalTabState
import com.example.ui.components.ExtraKeybar
import com.example.ui.components.TerminalCanvasView
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.ElevatedSlate
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TerminalCanvas
import com.example.ui.viewmodel.TerminalUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    uiState: TerminalUiState,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onReconnectTab: (String) -> Unit,
    onOpenHostInNewTab: (HostEntity) -> Unit,
    onGoToHosts: () -> Unit,
    onResizePty: (tabId: String, cols: Int, rows: Int, widthPx: Int, heightPx: Int) -> Unit,
    onTypedText: (tabId: String, text: String) -> Unit,
    onSendRawBytes: (tabId: String, bytes: ByteArray) -> Unit,
    onSendRawText: (tabId: String, text: String) -> Unit,
    onArrowKey: (tabId: String, direction: Char, applicationMode: Boolean) -> Unit,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onExecuteSnippet: (tabId: String, snippet: SnippetEntity) -> Unit,
    onSaveSnippet: (title: String, command: String, category: String, executeImmediately: Boolean) -> Unit,
    onDeleteSnippet: (Long) -> Unit,
    onResolveHostTrust: (tabId: String, trusted: Boolean) -> Unit,
    onResolveAuthPrompt: (tabId: String, response: String?) -> Unit,
    onAdjustFontSize: (Int) -> Unit,
    onClearScreen: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    val activeTab = uiState.activeTab

    DisposableEffect(uiState.preferences.keepScreenOn, activeTab != null) {
        val window = activity?.window
        if (uiState.preferences.keepScreenOn && activeTab != null) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var showNewTabSheet by remember { mutableStateOf(false) }
    var showSnippetsSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianCanvas)
            .windowInsetsPadding(WindowInsets.statusBars)
            .imePadding()
    ) {
        TerminalTabStrip(
            tabs = uiState.tabs,
            activeTabId = activeTab?.tabId,
            onSelectTab = onSelectTab,
            onCloseTab = onCloseTab,
            onNewTab = { showNewTabSheet = true }
        )

        if (activeTab == null) {
            EmptyTerminalState(
                savedHosts = uiState.savedHosts,
                onConnectHost = onOpenHostInNewTab,
                onGoToHosts = onGoToHosts,
                modifier = Modifier.weight(1f)
            )
        } else {
            val snapshot by activeTab.emulator.snapshot.collectAsStateWithLifecycle()

            ActiveTabToolbar(
                tab = activeTab,
                oscTitle = snapshot.title,
                fontSizeSp = uiState.preferences.fontSizeSp,
                onZoomOut = { onAdjustFontSize(-1) },
                onZoomIn = { onAdjustFontSize(1) },
                onCopyTranscript = {
                    val text = activeTab.emulator.getTranscriptPreview(150)
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Terminal Output", text))
                    Toast.makeText(context, "Copied terminal output", Toast.LENGTH_SHORT).show()
                },
                onClearScreen = { onClearScreen(activeTab.tabId) },
                onReconnect = { onReconnectTab(activeTab.tabId) },
                onOpenSnippets = { showSnippetsSheet = true },
                onToggleKeyboard = {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(TerminalCanvas)
            ) {
                TerminalCanvasView(
                    snapshot = snapshot,
                    fontSizeSp = uiState.preferences.fontSizeSp,
                    cursorBlinkEnabled = uiState.preferences.cursorBlink,
                    focusRequester = focusRequester,
                    onResizePty = { cols, rows, w, h ->
                        onResizePty(activeTab.tabId, cols, rows, w, h)
                    },
                    onTypedText = { text -> onTypedText(activeTab.tabId, text) },
                    onBackspace = { onSendRawBytes(activeTab.tabId, byteArrayOf(0x7F)) },
                    onEnter = { onSendRawBytes(activeTab.tabId, byteArrayOf(0x0D)) },
                    onSendRawBytes = { bytes -> onSendRawBytes(activeTab.tabId, bytes) },
                    onArrowKey = { dir, appMode -> onArrowKey(activeTab.tabId, dir, appMode) }
                )
            }

            if (uiState.preferences.showExtraKeybar) {
                ExtraKeybar(
                    ctrlActive = uiState.ctrlActive,
                    ctrlLocked = uiState.ctrlLocked,
                    altActive = uiState.altActive,
                    altLocked = uiState.altLocked,
                    applicationCursorKeys = snapshot.applicationCursorKeys,
                    onToggleCtrl = onToggleCtrl,
                    onToggleAlt = onToggleAlt,
                    onSendBytes = { bytes -> onSendRawBytes(activeTab.tabId, bytes) },
                    onSendText = { txt -> onSendRawText(activeTab.tabId, txt) },
                    onArrowKey = { dir, appMode -> onArrowKey(activeTab.tabId, dir, appMode) },
                    onOpenSnippets = { showSnippetsSheet = true }
                )
            }

            activeTab.pendingHostTrust?.let { trust ->
                HostTrustVerificationDialog(
                    pending = trust,
                    onTrust = { onResolveHostTrust(activeTab.tabId, true) },
                    onReject = { onResolveHostTrust(activeTab.tabId, false) }
                )
            }

            activeTab.pendingAuthPrompt?.let { prompt ->
                AuthInputDialog(
                    prompt = prompt,
                    onSubmit = { value -> onResolveAuthPrompt(activeTab.tabId, value) },
                    onCancel = { onResolveAuthPrompt(activeTab.tabId, null) }
                )
            }
        }
    }

    if (showNewTabSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNewTabSheet = false },
            containerColor = SlateSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Open New Terminal Tab",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (uiState.savedHosts.isEmpty()) {
                    Text(
                        text = "No saved VPS hosts yet. Add one from the Hosts screen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            showNewTabSheet = false
                            onGoToHosts()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Go to Hosts")
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(uiState.savedHosts, key = { it.id }) { host ->
                            Surface(
                                onClick = {
                                    showNewTabSheet = false
                                    onOpenHostInNewTab(host)
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = ElevatedSlate,
                                border = BorderStroke(1.dp, HairlineBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Terminal, contentDescription = null, tint = CyanAccent)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(host.alias, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = "${host.username}@${host.hostname}:${host.port}",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text("Connect", color = EmeraldAccent, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showSnippetsSheet && activeTab != null) {
        SnippetsBottomSheet(
            snippets = uiState.snippets,
            onDismiss = { showSnippetsSheet = false },
            onSelectSnippet = { snippet ->
                onExecuteSnippet(activeTab.tabId, snippet)
                showSnippetsSheet = false
            },
            onSaveSnippet = onSaveSnippet,
            onDeleteSnippet = onDeleteSnippet
        )
    }
}

@Composable
private fun TerminalTabStrip(
    tabs: List<TerminalTabState>,
    activeTabId: String?,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit
) {
    Surface(
        color = SlateSurface,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(tabs, key = { it.tabId }) { tab ->
                    val isActive = tab.tabId == activeTabId
                    val statusColor = when (tab.status) {
                        TabConnectionStatus.CONNECTED -> EmeraldAccent
                        TabConnectionStatus.CONNECTING,
                        TabConnectionStatus.AWAITING_HOST_TRUST,
                        TabConnectionStatus.AWAITING_AUTH_INPUT -> AmberWarning
                        TabConnectionStatus.ERROR -> CoralError
                        TabConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Surface(
                        onClick = { onSelectTab(tab.tabId) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isActive) CyanContainer else ElevatedSlate,
                        border = BorderStroke(
                            1.dp,
                            if (isActive) CyanAccent else HairlineBorder
                        ),
                        modifier = Modifier.testTag("terminal_tab_${tab.tabId}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = tab.target.alias,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isActive) CyanAccent else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(min = 56.dp, max = 148.dp)
                            )
                            IconButton(
                                onClick = { onCloseTab(tab.tabId) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("close_tab_${tab.tabId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close ${tab.target.alias} tab",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            IconButton(
                onClick = onNewTab,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("new_terminal_tab_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Open new terminal tab",
                    tint = CyanAccent
                )
            }
        }
    }
}

@Composable
private fun ActiveTabToolbar(
    tab: TerminalTabState,
    oscTitle: String,
    fontSizeSp: Int,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onCopyTranscript: () -> Unit,
    onClearScreen: () -> Unit,
    onReconnect: () -> Unit,
    onOpenSnippets: () -> Unit,
    onToggleKeyboard: () -> Unit
) {
    Surface(
        color = ObsidianCanvas,
        border = BorderStroke(1.dp, HairlineBorder.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = oscTitle.ifBlank { "${tab.target.username}@${tab.target.hostname}:${tab.target.port}" },
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = tab.statusMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (tab.status) {
                        TabConnectionStatus.CONNECTED -> EmeraldAccent
                        TabConnectionStatus.ERROR -> CoralError
                        TabConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> AmberWarning
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (tab.status == TabConnectionStatus.DISCONNECTED || tab.status == TabConnectionStatus.ERROR) {
                IconButton(
                    onClick = onReconnect,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reconnect session",
                        tint = EmeraldAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            IconButton(onClick = onZoomOut, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = Icons.Default.ZoomOut,
                    contentDescription = "Decrease terminal font size ($fontSizeSp sp)",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onZoomIn, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = "Increase terminal font size ($fontSizeSp sp)",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onCopyTranscript, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy terminal output",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onClearScreen, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = Icons.Default.ClearAll,
                    contentDescription = "Clear terminal buffer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onOpenSnippets,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("toolbar_snippets_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = "Open command snippets",
                    tint = EmeraldAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onToggleKeyboard,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("toolbar_keyboard_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = "Show keyboard",
                    tint = CyanAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyTerminalState(
    savedHosts: List<HostEntity>,
    onConnectHost: (HostEntity) -> Unit,
    onGoToHosts: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = CyanContainer,
            border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.4f)),
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = CyanAccent,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.empty_terminal_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.empty_terminal_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(22.dp))
        if (savedHosts.isNotEmpty()) {
            savedHosts.take(3).forEach { host ->
                OutlinedButton(
                    onClick = { onConnectHost(host) },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .height(50.dp)
                ) {
                    Text(
                        text = "Connect: ${host.alias} (${host.username}@${host.hostname})",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Button(
            onClick = onGoToHosts,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("empty_terminal_go_hosts_button")
        ) {
            Text("Browse Hosts")
        }
    }
}

@Composable
private fun HostTrustVerificationDialog(
    pending: PendingHostTrust,
    onTrust: () -> Unit,
    onReject: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onReject,
        icon = {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = if (pending.isKeyChanged) CoralError else AmberWarning
            )
        },
        title = {
            Text(
                text = if (pending.isKeyChanged) "Host Key Changed!" else "Verify Server Fingerprint",
                color = if (pending.isKeyChanged) CoralError else MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (pending.isKeyChanged) {
                        "WARNING: The host key for ${pending.hostname}:${pending.port} does not match the previously trusted fingerprint. This could indicate a server rebuild or a MITM attack."
                    } else {
                        "First time connecting to ${pending.hostname}:${pending.port}. Confirm this server's public key fingerprint before continuing:"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = TerminalCanvas,
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = pending.algorithm,
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanAccent
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = pending.fingerprintSha256,
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onTrust,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (pending.isKeyChanged) CoralError else EmeraldAccent,
                    contentColor = ObsidianCanvas
                ),
                modifier = Modifier.testTag("trust_host_confirm_button")
            ) {
                Text(if (pending.isKeyChanged) "Trust New Key" else "Trust & Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onReject) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AuthInputDialog(
    prompt: PendingAuthPrompt,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(prompt.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(prompt.message, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    visualTransformation = if (prompt.isSecret) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    label = { Text(if (prompt.isSecret) "Secret / Passphrase" else "Response") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ssh_auth_prompt_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(input) },
                modifier = Modifier.testTag("ssh_auth_prompt_submit")
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnippetsBottomSheet(
    snippets: List<SnippetEntity>,
    onDismiss: () -> Unit,
    onSelectSnippet: (SnippetEntity) -> Unit,
    onSaveSnippet: (title: String, command: String, category: String, executeImmediately: Boolean) -> Unit,
    onDeleteSnippet: (Long) -> Unit
) {
    val categories = remember(snippets) {
        listOf("All") + snippets.map { it.category }.distinct()
    }
    var selectedCategory by remember { mutableStateOf("All") }
    var showCreateDialog by remember { mutableStateOf(false) }

    val filtered = remember(snippets, selectedCategory) {
        if (selectedCategory == "All") snippets else snippets.filter { it.category == selectedCategory }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SlateSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Command Snippets", style = MaterialTheme.typography.titleLarge)
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.testTag("new_snippet_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Macro")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 28.dp),
                modifier = Modifier.height(320.dp)
            ) {
                items(filtered, key = { it.id }) { snippet ->
                    Surface(
                        onClick = { onSelectSnippet(snippet) },
                        shape = RoundedCornerShape(12.dp),
                        color = ElevatedSlate,
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("snippet_item_${snippet.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = snippet.title,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (snippet.executeImmediately) "RUN" else "PASTE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (snippet.executeImmediately) EmeraldAccent else CyanAccent
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = snippet.command,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CyanAccent,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (!snippet.isBuiltIn) {
                                IconButton(onClick = { onDeleteSnippet(snippet.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete snippet",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var title by remember { mutableStateOf("") }
        var command by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Custom") }
        var executeImmediately by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Command Snippet") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        placeholder = { Text("Restart Nginx") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        label = { Text("Command") },
                        placeholder = { Text("sudo systemctl restart nginx") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Execute immediately (press Enter)")
                        Switch(
                            checked = executeImmediately,
                            onCheckedChange = { executeImmediately = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank() && command.isNotBlank()) {
                            onSaveSnippet(title, command, category, executeImmediately)
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
