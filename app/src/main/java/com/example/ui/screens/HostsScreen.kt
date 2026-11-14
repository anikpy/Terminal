package com.example.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AuthType
import com.example.data.local.HostEntity
import com.example.data.local.SshKeyEntity
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.ElevatedSlate
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.HostTagPalette
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TerminalCanvas
import com.example.ui.viewmodel.HostsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostsScreen(
    uiState: HostsUiState,
    onSearchChange: (String) -> Unit,
    onConnectHost: (HostEntity) -> Unit,
    onSaveHost: (
        id: Long,
        alias: String,
        hostname: String,
        port: Int,
        username: String,
        authType: AuthType,
        sshKeyId: Long?,
        plainPassword: String?,
        keepExistingPasswordIfBlank: Boolean,
        startupCommand: String,
        colorTagHex: Long
    ) -> Unit,
    onDuplicateHost: (HostEntity) -> Unit,
    onDeleteHost: (Long) -> Unit,
    onQuickConnect: (
        hostname: String,
        port: Int,
        username: String,
        authType: AuthType,
        sshKeyId: Long?,
        password: String?,
        saveHost: Boolean,
        alias: String
    ) -> Unit,
    onOpenTerminalTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddEditSheet by remember { mutableStateOf(false) }
    var editingHost by remember { mutableStateOf<HostEntity?>(null) }
    var showQuickConnectSheet by remember { mutableStateOf(false) }
    var hostPendingDelete by remember { mutableStateOf<HostEntity?>(null) }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CyanContainer,
                            border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.45f)),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = "Easy Terminal icon",
                                    tint = CyanAccent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Easy Terminal",
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${uiState.hosts.size} VPS hosts saved",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                },
                actions = {
                    if (uiState.activeTabCount > 0) {
                        Surface(
                            onClick = onOpenTerminalTab,
                            shape = RoundedCornerShape(20.dp),
                            color = EmeraldContainer,
                            border = BorderStroke(1.dp, EmeraldAccent.copy(alpha = 0.55f)),
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .testTag("jump_active_terminal_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldAccent)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${uiState.activeTabCount} Live",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EmeraldAccent,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.hosts.isNotEmpty() || uiState.searchQuery.isNotBlank()) {
                FloatingActionButton(
                    onClick = {
                        editingHost = null
                        showAddEditSheet = true
                    },
                    containerColor = CyanAccent,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_host_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.action_add_host)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Spacious, always-reachable Quick Connect command bar
            QuickConnectCommandBar(
                onClick = { showQuickConnectSheet = true },
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            if (uiState.hosts.isNotEmpty() || uiState.searchQuery.isNotBlank()) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Search saved hosts, IP, or username...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("hosts_search_input")
                )
            }

            if (uiState.hosts.isEmpty() && uiState.searchQuery.isBlank()) {
                HostsEmptyHero(
                    onAddHost = {
                        editingHost = null
                        showAddEditSheet = true
                    },
                    onQuickConnect = { showQuickConnectSheet = true },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (uiState.hosts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No VPS hosts match \"${uiState.searchQuery}\".",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val columns = if (maxWidth >= 680.dp) 2 else 1
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(top = 2.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.hosts, key = { it.id }) { host ->
                            val keyName = uiState.keys.firstOrNull { it.id == host.sshKeyId }?.name
                            HostCard(
                                host = host,
                                keyName = keyName,
                                onConnect = { onConnectHost(host) },
                                onEdit = {
                                    editingHost = host
                                    showAddEditSheet = true
                                },
                                onDuplicate = { onDuplicateHost(host) },
                                onDelete = { hostPendingDelete = host }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddEditSheet) {
        AddEditHostBottomSheet(
            initialHost = editingHost,
            availableKeys = uiState.keys,
            onDismiss = { showAddEditSheet = false },
            onSave = { alias, hostname, port, user, authType, keyId, password, startupCmd, colorHex ->
                onSaveHost(
                    editingHost?.id ?: 0L,
                    alias,
                    hostname,
                    port,
                    user,
                    authType,
                    keyId,
                    password,
                    editingHost != null,
                    startupCmd,
                    colorHex
                )
                showAddEditSheet = false
            }
        )
    }

    if (showQuickConnectSheet) {
        QuickConnectBottomSheet(
            availableKeys = uiState.keys,
            onDismiss = { showQuickConnectSheet = false },
            onConnect = { hostname, port, user, authType, keyId, password, saveHost, alias ->
                onQuickConnect(hostname, port, user, authType, keyId, password, saveHost, alias)
                showQuickConnectSheet = false
            }
        )
    }

    hostPendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { hostPendingDelete = null },
            title = { Text("Delete Host Profile") },
            text = {
                Text("Remove \"${target.alias}\" (${target.username}@${target.hostname}:${target.port}) from this device?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteHost(target.id)
                        hostPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { hostPendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun QuickConnectCommandBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = SlateSurface,
        border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("quick_connect_button")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = CyanContainer,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Quick Connect",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = "ssh user@host -p 22",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CyanAccent
            ) {
                Text(
                    text = "Connect",
                    style = MaterialTheme.typography.labelLarge,
                    color = ObsidianCanvas,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun HostsEmptyHero(
    onAddHost: () -> Unit,
    onQuickConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = SlateSurface,
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(176.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_console_hero),
                        contentDescription = "Dark server infrastructure console artwork",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        SlateSurface.copy(alpha = 0.95f)
                                    )
                                )
                            )
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ObsidianCanvas.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "SSH-2.0 • AES-256-GCM VAULT",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanAccent,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.empty_hosts_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.empty_hosts_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = onAddHost,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("empty_add_host_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add VPS Host", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onQuickConnect,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("empty_quick_connect_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Quick Connect (user@host)", color = CyanAccent, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun HostCard(
    host: HostEntity,
    keyName: String?,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val tagColor = Color(host.colorTagHex)
    val isKeyAuth = host.authType == AuthType.SSH_KEY.name

    Surface(
        onClick = onConnect,
        shape = RoundedCornerShape(18.dp),
        color = SlateSurface,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("host_card_${host.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(tagColor)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = host.alias,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "${host.username}@${host.hostname}:${host.port}",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CyanAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Host options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDuplicate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isKeyAuth) EmeraldContainer else ElevatedSlate
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (isKeyAuth) Icons.Default.Key else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (isKeyAuth) EmeraldAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isKeyAuth) (keyName ?: "SSH Key") else "Password",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isKeyAuth) EmeraldAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    val lastSeen = host.lastConnectedAt?.let {
                        DateUtils.getRelativeTimeSpanString(
                            it,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS
                        ).toString()
                    } ?: "New"
                    Text(
                        text = lastSeen,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Surface(
                    onClick = onConnect,
                    shape = RoundedCornerShape(10.dp),
                    color = EmeraldContainer,
                    border = BorderStroke(1.dp, EmeraldAccent.copy(alpha = 0.45f)),
                    modifier = Modifier.testTag("connect_host_${host.id}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Connect to ${host.alias}",
                            tint = EmeraldAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Open",
                            style = MaterialTheme.typography.labelLarge,
                            color = EmeraldAccent,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditHostBottomSheet(
    initialHost: HostEntity?,
    availableKeys: List<SshKeyEntity>,
    onDismiss: () -> Unit,
    onSave: (
        alias: String,
        hostname: String,
        port: Int,
        username: String,
        authType: AuthType,
        sshKeyId: Long?,
        plainPassword: String?,
        startupCommand: String,
        colorTagHex: Long
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var alias by remember { mutableStateOf(initialHost?.alias ?: "") }
    var hostname by remember { mutableStateOf(initialHost?.hostname ?: "") }
    var portText by remember { mutableStateOf((initialHost?.port ?: 22).toString()) }
    var username by remember { mutableStateOf(initialHost?.username ?: "root") }
    var authType by remember {
        mutableStateOf(
            runCatching { AuthType.valueOf(initialHost?.authType ?: "") }
                .getOrDefault(if (availableKeys.isNotEmpty()) AuthType.SSH_KEY else AuthType.PASSWORD)
        )
    }
    var selectedKeyId by remember {
        mutableStateOf(initialHost?.sshKeyId ?: availableKeys.firstOrNull()?.id)
    }
    var password by remember { mutableStateOf("") }
    var startupCommand by remember { mutableStateOf(initialHost?.startupCommand ?: "") }
    var selectedColor by remember {
        mutableLongStateOf(initialHost?.colorTagHex ?: HostTagPalette.first())
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SlateSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Dns, contentDescription = null, tint = CyanAccent)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (initialHost == null) "Add VPS Host" else "Edit VPS Host",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            OutlinedTextField(
                value = alias,
                onValueChange = { alias = it },
                label = { Text("Server Label (e.g. Prod API)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("host_alias_input")
            )
            OutlinedTextField(
                value = hostname,
                onValueChange = {
                    hostname = it
                    validationError = null
                },
                label = { Text("Hostname or IP *") },
                placeholder = { Text("203.0.113.42") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("host_hostname_input")
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        validationError = null
                    },
                    label = { Text("Username *") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .testTag("host_username_input")
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(0.8f)
                        .testTag("host_port_input")
                )
            }

            Text(
                text = "Authentication Method",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = authType == AuthType.SSH_KEY,
                    onClick = { authType = AuthType.SSH_KEY },
                    label = { Text("SSH Key") },
                    leadingIcon = {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanContainer,
                        selectedLabelColor = CyanAccent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("auth_type_key_chip")
                )
                FilterChip(
                    selected = authType == AuthType.PASSWORD,
                    onClick = { authType = AuthType.PASSWORD },
                    label = { Text("Password") },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanContainer,
                        selectedLabelColor = CyanAccent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("auth_type_password_chip")
                )
            }

            if (authType == AuthType.SSH_KEY) {
                if (availableKeys.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ElevatedSlate,
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No keys in your vault yet. Generate or import a key in the Keys tab, or switch to Password authentication.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableKeys.forEach { key ->
                            val selected = selectedKeyId == key.id
                            Surface(
                                onClick = { selectedKeyId = key.id },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) CyanContainer else ElevatedSlate,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) CyanAccent else HairlineBorder
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Key,
                                        contentDescription = null,
                                        tint = if (selected) CyanAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${key.name} • ${key.algorithm}",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Text(
                                            text = key.fingerprintSha256,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = {
                        Text(
                            if (initialHost != null) "Password (blank keeps saved)"
                            else "Password (optional; prompts on connect)"
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("host_password_input")
                )
            }

            OutlinedTextField(
                value = startupCommand,
                onValueChange = { startupCommand = it },
                label = { Text("Startup Command (optional)") },
                placeholder = { Text("tmux attach || bash") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Color Tag",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HostTagPalette.forEach { hex ->
                        val active = selectedColor == hex
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .clickable { selectedColor = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (active) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(ObsidianCanvas)
                                )
                            }
                        }
                    }
                }
            }

            validationError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val cleanHost = hostname.trim()
                        val cleanUser = username.trim()
                        val port = portText.toIntOrNull()?.coerceIn(1, 65535) ?: 22
                        if (cleanHost.isEmpty() || cleanUser.isEmpty()) {
                            validationError = "Hostname and username are required."
                            return@Button
                        }
                        if (authType == AuthType.SSH_KEY && selectedKeyId == null && availableKeys.isNotEmpty()) {
                            selectedKeyId = availableKeys.first().id
                        }
                        onSave(
                            alias.ifBlank { cleanHost },
                            cleanHost,
                            port,
                            cleanUser,
                            authType,
                            selectedKeyId,
                            password.takeIf { it.isNotEmpty() },
                            startupCommand,
                            selectedColor
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(50.dp)
                        .testTag("save_host_button")
                ) {
                    Text("Save Host")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickConnectBottomSheet(
    availableKeys: List<SshKeyEntity>,
    onDismiss: () -> Unit,
    onConnect: (
        hostname: String,
        port: Int,
        username: String,
        authType: AuthType,
        sshKeyId: Long?,
        password: String?,
        saveHost: Boolean,
        alias: String
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var targetInput by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf("22") }
    var authType by remember {
        mutableStateOf(if (availableKeys.isNotEmpty()) AuthType.SSH_KEY else AuthType.PASSWORD)
    }
    var selectedKeyId by remember { mutableStateOf(availableKeys.firstOrNull()?.id) }
    var password by remember { mutableStateOf("") }
    var saveHost by remember { mutableStateOf(true) }
    var alias by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SlateSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CyanContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = CyanAccent)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Quick Connect", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Launch an instant SSH terminal session",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = targetInput,
                    onValueChange = {
                        targetInput = it
                        errorText = null
                    },
                    label = { Text("user@host *") },
                    placeholder = { Text("root@203.0.113.42") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.7f)
                        .testTag("quick_connect_target_input")
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(0.7f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = authType == AuthType.SSH_KEY,
                    onClick = { authType = AuthType.SSH_KEY },
                    label = { Text("SSH Key") },
                    leadingIcon = {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanContainer,
                        selectedLabelColor = CyanAccent
                    ),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = authType == AuthType.PASSWORD,
                    onClick = { authType = AuthType.PASSWORD },
                    label = { Text("Password") },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanContainer,
                        selectedLabelColor = CyanAccent
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            if (authType == AuthType.SSH_KEY) {
                if (availableKeys.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = TerminalCanvas,
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No SSH keys in vault yet. Switch to Password auth or generate a key in the Keys tab.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    availableKeys.forEach { key ->
                        val selected = selectedKeyId == key.id
                        Surface(
                            onClick = { selectedKeyId = key.id },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) CyanContainer else ElevatedSlate,
                            border = BorderStroke(1.dp, if (selected) CyanAccent else HairlineBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Key,
                                    contentDescription = null,
                                    tint = if (selected) CyanAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "${key.name} (${key.algorithm})",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (optional)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ElevatedSlate,
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Save to Hosts list", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = saveHost, onCheckedChange = { saveHost = it })
                    }
                    if (saveHost) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = alias,
                            onValueChange = { alias = it },
                            label = { Text("Host Alias (optional)") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            errorText?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val trimmed = targetInput.trim()
                        val (user, hostAndMaybePort) = if (trimmed.contains("@")) {
                            val parts = trimmed.split("@", limit = 2)
                            parts[0].trim() to parts[1].trim()
                        } else {
                            "root" to trimmed
                        }
                        val hostPart = hostAndMaybePort.substringBefore(":")
                        val inlinePort = hostAndMaybePort.substringAfter(":", "").toIntOrNull()
                        val finalPort = (inlinePort ?: portText.toIntOrNull() ?: 22).coerceIn(1, 65535)

                        if (hostPart.isBlank() || user.isBlank()) {
                            errorText = "Enter a valid host or user@host target."
                            return@Button
                        }
                        onConnect(
                            hostPart,
                            finalPort,
                            user,
                            authType,
                            if (authType == AuthType.SSH_KEY) selectedKeyId else null,
                            password.takeIf { it.isNotEmpty() },
                            saveHost,
                            alias
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(50.dp)
                        .testTag("quick_connect_submit_button")
                ) {
                    Text("Connect Now")
                }
            }
        }
    }
}
