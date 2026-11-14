package com.example.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.KnownHostEntity
import com.example.data.local.SessionHistoryEntity
import com.example.data.local.SessionStatus
import com.example.data.repository.TerminalPreferences
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.ElevatedSlate
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TerminalCanvas
import com.example.ui.viewmodel.HistoryUiState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    onFilterChange: (String) -> Unit,
    onReconnect: (SessionHistoryEntity) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onClearHistory: () -> Unit,
    onRemoveKnownHost: (String) -> Unit,
    onClearKnownHosts: () -> Unit,
    onSetFontSize: (Int) -> Unit,
    onSetShowExtraKeybar: (Boolean) -> Unit,
    onSetCursorBlink: (Boolean) -> Unit,
    onSetKeepScreenOn: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var inspectingEntry by remember { mutableStateOf<SessionHistoryEntity?>(null) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var confirmClearHistory by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ObsidianCanvas,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianCanvas,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = {
                    Column {
                        Text("Session History", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${uiState.entries.size} logged sessions",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (uiState.entries.isNotEmpty()) {
                        IconButton(
                            onClick = { confirmClearHistory = true },
                            modifier = Modifier.testTag("clear_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear session history",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier.testTag("open_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.action_settings),
                            tint = CyanAccent
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            if (uiState.entries.isNotEmpty() || uiState.filterQuery.isNotBlank()) {
                OutlinedTextField(
                    value = uiState.filterQuery,
                    onValueChange = onFilterChange,
                    placeholder = { Text("Filter by host, user, or status...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.filterQuery.isNotEmpty()) {
                            IconButton(onClick = { onFilterChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear filter")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }

            if (uiState.entries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = SlateSurface,
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.empty_history_title),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.empty_history_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 32.dp)
                ) {
                    items(uiState.entries, key = { it.id }) { entry ->
                        HistoryEntryCard(
                            entry = entry,
                            onInspect = { inspectingEntry = entry },
                            onReconnect = { onReconnect(entry) },
                            onDelete = { onDeleteEntry(entry.id) }
                        )
                    }
                }
            }
        }
    }

    inspectingEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { inspectingEntry = null },
            title = { Text("${entry.hostAlias} Transcript") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${entry.username}@${entry.hostname}:${entry.port} • ${entry.authUsed}",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanAccent
                    )
                    entry.errorMessage?.let { err ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CoralContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = CoralError,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = TerminalCanvas,
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = entry.transcriptPreview.ifBlank { "(No terminal output recorded)" },
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        inspectingEntry = null
                        onReconnect(entry)
                    }
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { inspectingEntry = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text("Clear Session History") },
            text = { Text("Delete all recorded SSH session logs and transcript previews?") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearHistory()
                        confirmClearHistory = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearHistory = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSettingsSheet) {
        SettingsAndKnownHostsSheet(
            preferences = uiState.preferences,
            knownHosts = uiState.knownHosts,
            onDismiss = { showSettingsSheet = false },
            onSetFontSize = onSetFontSize,
            onSetShowExtraKeybar = onSetShowExtraKeybar,
            onSetCursorBlink = onSetCursorBlink,
            onSetKeepScreenOn = onSetKeepScreenOn,
            onRemoveKnownHost = onRemoveKnownHost,
            onClearKnownHosts = onClearKnownHosts
        )
    }
}

@Composable
private fun HistoryEntryCard(
    entry: SessionHistoryEntity,
    onInspect: () -> Unit,
    onReconnect: () -> Unit,
    onDelete: () -> Unit
) {
    val statusEnum = runCatching { SessionStatus.valueOf(entry.status) }.getOrDefault(SessionStatus.COMPLETED)
    val (badgeBg, badgeFg) = when (statusEnum) {
        SessionStatus.CONNECTED -> EmeraldContainer to EmeraldAccent
        SessionStatus.COMPLETED -> CyanContainer to CyanAccent
        SessionStatus.DISCONNECTED -> AmberContainer to AmberWarning
        SessionStatus.FAILED -> CoralContainer to CoralError
    }

    val durationText = entry.endedAt?.let { end ->
        val seconds = ((end - entry.startedAt) / 1000L).coerceAtLeast(0L)
        when {
            seconds < 60L -> "${seconds}s"
            seconds < 3600L -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    } ?: "Active"

    val relativeTime = DateUtils.getRelativeTimeSpanString(
        entry.startedAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()

    Surface(
        onClick = onInspect,
        shape = RoundedCornerShape(16.dp),
        color = SlateSurface,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_entry_${entry.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = entry.hostAlias,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeBg
                        ) {
                            Text(
                                text = statusEnum.displayLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeFg,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "${entry.username}@${entry.hostname}:${entry.port}",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.bodySmall,
                        color = CyanAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onReconnect,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reconnect to ${entry.hostAlias}",
                        tint = EmeraldAccent
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete history entry",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "$relativeTime • Duration: $durationText • ${entry.authUsed}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            entry.errorMessage?.let { err ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = CoralError,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsAndKnownHostsSheet(
    preferences: TerminalPreferences,
    knownHosts: List<KnownHostEntity>,
    onDismiss: () -> Unit,
    onSetFontSize: (Int) -> Unit,
    onSetShowExtraKeybar: (Boolean) -> Unit,
    onSetCursorBlink: (Boolean) -> Unit,
    onSetKeepScreenOn: (Boolean) -> Unit,
    onRemoveKnownHost: (String) -> Unit,
    onClearKnownHosts: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SlateSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Terminal & Security Settings", style = MaterialTheme.typography.titleLarge)

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Terminal Font Size", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "${preferences.fontSizeSp} sp",
                        fontFamily = JetBrainsMonoFontFamily,
                        color = CyanAccent
                    )
                }
                Slider(
                    value = preferences.fontSizeSp.toFloat(),
                    onValueChange = { onSetFontSize(it.roundToInt()) },
                    valueRange = 10f..20f,
                    steps = 9
                )
            }

            SettingToggleRow(
                title = "Show Extra Keybar",
                subtitle = "Ctrl, Alt, Esc, Tab, and arrow keys above keyboard",
                checked = preferences.showExtraKeybar,
                onCheckedChange = onSetShowExtraKeybar
            )
            SettingToggleRow(
                title = "Blinking Cursor",
                subtitle = "Animate active terminal cursor block",
                checked = preferences.cursorBlink,
                onCheckedChange = onSetCursorBlink
            )
            SettingToggleRow(
                title = "Keep Screen On",
                subtitle = "Prevent sleep while a terminal tab is open",
                checked = preferences.keepScreenOn,
                onCheckedChange = onSetKeepScreenOn
            )

            HorizontalDivider(color = HairlineBorder)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Trusted Known Hosts (${knownHosts.size})", style = MaterialTheme.typography.titleMedium)
                if (knownHosts.isNotEmpty()) {
                    TextButton(onClick = onClearKnownHosts) {
                        Text("Clear All", color = CoralError)
                    }
                }
            }

            if (knownHosts.isEmpty()) {
                Text(
                    text = "No trusted SSH host fingerprints stored yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                knownHosts.forEach { kh ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ElevatedSlate,
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, tint = EmeraldAccent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${kh.hostKeyId} (${kh.algorithm})",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = kh.fingerprintSha256,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { onRemoveKnownHost(kh.hostKeyId) }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Forget ${kh.hostKeyId}",
                                    tint = CoralError
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
