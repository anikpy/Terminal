package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.ElevatedSlate
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SlateSurface

@Composable
fun ExtraKeybar(
    ctrlActive: Boolean,
    ctrlLocked: Boolean,
    altActive: Boolean,
    altLocked: Boolean,
    applicationCursorKeys: Boolean,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onSendBytes: (ByteArray) -> Unit,
    onSendText: (String) -> Unit,
    onArrowKey: (direction: Char, applicationMode: Boolean) -> Unit,
    onOpenSnippets: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SlateSurface,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeybarButton(
                label = "ESC",
                testTag = "keybar_esc",
                onClick = { onSendBytes(byteArrayOf(0x1B)) }
            )
            KeybarButton(
                label = "TAB",
                testTag = "keybar_tab",
                onClick = { onSendBytes(byteArrayOf(0x09)) }
            )
            KeybarButton(
                label = if (ctrlLocked) "CTRL*" else "CTRL",
                testTag = "keybar_ctrl",
                isActive = ctrlActive,
                isLocked = ctrlLocked,
                onClick = onToggleCtrl
            )
            KeybarButton(
                label = if (altLocked) "ALT*" else "ALT",
                testTag = "keybar_alt",
                isActive = altActive,
                isLocked = altLocked,
                onClick = onToggleAlt
            )
            KeybarButton(
                label = "^C",
                testTag = "keybar_ctrl_c",
                accentColor = MaterialTheme.colorScheme.error,
                onClick = { onSendBytes(byteArrayOf(0x03)) }
            )
            KeybarButton(
                label = "←",
                testTag = "keybar_left",
                onClick = { onArrowKey('D', applicationCursorKeys) }
            )
            KeybarButton(
                label = "↓",
                testTag = "keybar_down",
                onClick = { onArrowKey('B', applicationCursorKeys) }
            )
            KeybarButton(
                label = "↑",
                testTag = "keybar_up",
                onClick = { onArrowKey('A', applicationCursorKeys) }
            )
            KeybarButton(
                label = "→",
                testTag = "keybar_right",
                onClick = { onArrowKey('C', applicationCursorKeys) }
            )
            KeybarButton(
                label = "/",
                testTag = "keybar_slash",
                onClick = { onSendText("/") }
            )
            KeybarButton(
                label = "-",
                testTag = "keybar_dash",
                onClick = { onSendText("-") }
            )
            KeybarButton(
                label = "|",
                testTag = "keybar_pipe",
                onClick = { onSendText("|") }
            )
            KeybarButton(
                label = "~",
                testTag = "keybar_tilde",
                onClick = { onSendText("~") }
            )
            KeybarButton(
                label = "HOME",
                testTag = "keybar_home",
                onClick = { onSendBytes("\u001B[H".toByteArray(Charsets.US_ASCII)) }
            )
            KeybarButton(
                label = "END",
                testTag = "keybar_end",
                onClick = { onSendBytes("\u001B[F".toByteArray(Charsets.US_ASCII)) }
            )
            KeybarButton(
                label = "PGUP",
                testTag = "keybar_pgup",
                onClick = { onSendBytes("\u001B[5~".toByteArray(Charsets.US_ASCII)) }
            )
            KeybarButton(
                label = "PGDN",
                testTag = "keybar_pgdn",
                onClick = { onSendBytes("\u001B[6~".toByteArray(Charsets.US_ASCII)) }
            )
            Spacer(modifier = Modifier.width(2.dp))
            KeybarButton(
                label = "SNIPPETS",
                testTag = "keybar_snippets",
                accentColor = EmeraldAccent,
                onClick = onOpenSnippets
            )
        }
    }
}

@Composable
private fun KeybarButton(
    label: String,
    testTag: String,
    isActive: Boolean = false,
    isLocked: Boolean = false,
    accentColor: Color? = null,
    onClick: () -> Unit
) {
    val bgColor = when {
        isActive -> CyanContainer
        else -> ElevatedSlate
    }
    val borderColor = when {
        isLocked -> EmeraldAccent
        isActive -> CyanAccent
        else -> HairlineBorder
    }
    val textColor = when {
        isLocked -> EmeraldAccent
        isActive -> CyanAccent
        accentColor != null -> accentColor
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .heightIn(min = 42.dp)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .background(Color.Transparent)
                .padding(horizontal = 11.dp, vertical = 9.dp)
        ) {
            Text(
                text = label,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp,
                color = textColor
            )
        }
    }
}
