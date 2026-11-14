package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.terminal.TerminalCell
import com.example.terminal.TerminalSnapshot
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.TerminalCanvas
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.roundToInt

private const val INPUT_SENTINEL = " "

@Composable
fun TerminalCanvasView(
    snapshot: TerminalSnapshot,
    fontSizeSp: Int,
    cursorBlinkEnabled: Boolean,
    focusRequester: FocusRequester,
    onResizePty: (cols: Int, rows: Int, widthPx: Int, heightPx: Int) -> Unit,
    onTypedText: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onSendRawBytes: (ByteArray) -> Unit,
    onArrowKey: (direction: Char, applicationMode: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val textMeasurer = rememberTextMeasurer()

    val baseTextStyle = remember(fontSizeSp) {
        TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp * 1.32f).sp
        )
    }

    val cellMetrics = remember(baseTextStyle, density) {
        val measured = textMeasurer.measure(text = "M", style = baseTextStyle)
        val charW = measured.size.width.toFloat().coerceAtLeast(with(density) { 6.dp.toPx() })
        val charH = measured.size.height.toFloat().coerceAtLeast(with(density) { 12.dp.toPx() })
        charW to charH
    }
    val cellWidthPx = cellMetrics.first
    val cellHeightPx = cellMetrics.second

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val horizontalPaddingPx = with(density) { 8.dp.toPx() }
    val verticalPaddingPx = with(density) { 6.dp.toPx() }

    LaunchedEffect(viewportSize, cellWidthPx, cellHeightPx) {
        if (viewportSize.width > 0 && viewportSize.height > 0) {
            val usableW = (viewportSize.width - horizontalPaddingPx * 2).coerceAtLeast(cellWidthPx)
            val usableH = (viewportSize.height - verticalPaddingPx * 2).coerceAtLeast(cellHeightPx)
            val cols = floor(usableW / cellWidthPx).toInt().coerceIn(20, 220)
            val rows = floor(usableH / cellHeightPx).toInt().coerceIn(6, 120)
            onResizePty(cols, rows, viewportSize.width, viewportSize.height)
        }
    }

    var scrollOffsetLines by remember(snapshot.isAlternateScreen) { mutableIntStateOf(0) }
    var dragRemainderPx by remember { mutableFloatStateOf(0f) }

    val maxScroll = snapshot.scrollback.size
    LaunchedEffect(maxScroll) {
        if (scrollOffsetLines > maxScroll) {
            scrollOffsetLines = maxScroll
        }
    }

    var cursorBlinkOn by remember { mutableStateOf(true) }
    LaunchedEffect(cursorBlinkEnabled, snapshot.version) {
        cursorBlinkOn = true
        if (cursorBlinkEnabled) {
            while (true) {
                delay(550)
                cursorBlinkOn = !cursorBlinkOn
            }
        }
    }

    var fieldValue by remember {
        mutableStateOf(TextFieldValue(INPUT_SENTINEL, TextRange(INPUT_SENTINEL.length)))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalCanvas)
            .testTag("terminal_canvas")
            .onSizeChanged { viewportSize = it }
            .pointerInput(maxScroll, cellHeightPx) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (snapshot.isAlternateScreen) return@detectVerticalDragGestures
                    dragRemainderPx += dragAmount
                    val deltaLines = (dragRemainderPx / cellHeightPx).roundToInt()
                    if (deltaLines != 0) {
                        dragRemainderPx -= deltaLines * cellHeightPx
                        scrollOffsetLines = (scrollOffsetLines + deltaLines).coerceIn(0, maxScroll)
                    }
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusRequester.requestFocus()
                keyboardController?.show()
            }
    ) {
        BasicTextField(
            value = fieldValue,
            onValueChange = { next ->
                val text = next.text
                when {
                    text.isEmpty() -> {
                        scrollOffsetLines = 0
                        onBackspace()
                    }
                    text != INPUT_SENTINEL -> {
                        scrollOffsetLines = 0
                        val extracted = if (text.startsWith(INPUT_SENTINEL)) {
                            text.substring(INPUT_SENTINEL.length)
                        } else if (text.endsWith(INPUT_SENTINEL)) {
                            text.substring(0, text.length - INPUT_SENTINEL.length)
                        } else {
                            text
                        }
                        if (extracted.isNotEmpty()) {
                            onTypedText(extracted)
                        }
                    }
                }
                fieldValue = TextFieldValue(INPUT_SENTINEL, TextRange(INPUT_SENTINEL.length))
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.None
            ),
            keyboardActions = KeyboardActions(
                onDone = { onEnter() },
                onSend = { onEnter() },
                onGo = { onEnter() },
                onNext = { onEnter() }
            ),
            modifier = Modifier
                .size(1.dp)
                .alpha(0f)
                .focusRequester(focusRequester)
                .testTag("terminal_hidden_input")
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (keyEvent.key) {
                        Key.Enter, Key.NumPadEnter -> {
                            scrollOffsetLines = 0
                            onEnter()
                            true
                        }
                        Key.Backspace -> {
                            scrollOffsetLines = 0
                            onBackspace()
                            true
                        }
                        Key.Tab -> {
                            scrollOffsetLines = 0
                            onSendRawBytes(byteArrayOf(0x09))
                            true
                        }
                        Key.Escape -> {
                            onSendRawBytes(byteArrayOf(0x1B))
                            true
                        }
                        Key.DirectionUp -> {
                            onArrowKey('A', snapshot.applicationCursorKeys)
                            true
                        }
                        Key.DirectionDown -> {
                            onArrowKey('B', snapshot.applicationCursorKeys)
                            true
                        }
                        Key.DirectionRight -> {
                            onArrowKey('C', snapshot.applicationCursorKeys)
                            true
                        }
                        Key.DirectionLeft -> {
                            onArrowKey('D', snapshot.applicationCursorKeys)
                            true
                        }
                        else -> {
                            val code = keyEvent.nativeKeyEvent.unicodeChar
                            if (keyEvent.isCtrlPressed && code in 'a'.code..'z'.code) {
                                onSendRawBytes(byteArrayOf((code - 'a'.code + 1).toByte()))
                                true
                            } else if (keyEvent.isCtrlPressed && code in 'A'.code..'Z'.code) {
                                onSendRawBytes(byteArrayOf((code - 'A'.code + 1).toByte()))
                                true
                            } else {
                                false
                            }
                        }
                    }
                }
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalScrollback = snapshot.scrollback.size
            val visibleRows = snapshot.rows
            val startRowGlobal = (totalScrollback - scrollOffsetLines).coerceAtLeast(0)

            for (r in 0 until visibleRows) {
                val globalIndex = startRowGlobal + r
                val line = if (globalIndex < totalScrollback) {
                    snapshot.scrollback[globalIndex]
                } else {
                    snapshot.screen.getOrNull(globalIndex - totalScrollback)
                } ?: continue

                val y = verticalPaddingPx + r * cellHeightPx
                val cells = line.cells
                val maxCols = minOf(cells.size, snapshot.cols)

                for (c in 0 until maxCols) {
                    val cell = cells[c]
                    val x = horizontalPaddingPx + c * cellWidthPx

                    val isCursorHere = scrollOffsetLines == 0 &&
                        snapshot.cursorVisible &&
                        r == snapshot.cursorRow &&
                        c == snapshot.cursorCol

                    val fgRaw = if (cell.inverse) {
                        if (cell.bgColor == TerminalCell.DEFAULT_BG) 0xFF070A0E.toInt() else cell.bgColor
                    } else {
                        cell.fgColor
                    }
                    val bgRaw = if (cell.inverse) {
                        cell.fgColor
                    } else {
                        cell.bgColor
                    }

                    if (bgRaw != TerminalCell.DEFAULT_BG) {
                        drawRect(
                            color = Color(bgRaw),
                            topLeft = Offset(x, y),
                            size = Size(cellWidthPx + 0.5f, cellHeightPx)
                        )
                    }

                    if (isCursorHere && cursorBlinkOn) {
                        drawRect(
                            color = CyanAccent.copy(alpha = 0.85f),
                            topLeft = Offset(x, y),
                            size = Size(cellWidthPx, cellHeightPx)
                        )
                    }

                    if (cell.char != ' ' || cell.underline) {
                        val glyphColor = if (isCursorHere && cursorBlinkOn) {
                            Color(0xFF041D24)
                        } else {
                            Color(fgRaw)
                        }
                        val style = baseTextStyle.copy(
                            color = glyphColor,
                            fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (cell.italic) FontStyle.Italic else FontStyle.Normal,
                            textDecoration = if (cell.underline) TextDecoration.Underline else TextDecoration.None
                        )
                        val result = textMeasurer.measure(
                            text = cell.char.toString(),
                            style = style
                        )
                        drawText(
                            textLayoutResult = result,
                            topLeft = Offset(x, y)
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = scrollOffsetLines > 0,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
        ) {
            Surface(
                onClick = { scrollOffsetLines = 0 },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 4.dp,
                modifier = Modifier.testTag("scroll_to_bottom_button")
            ) {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Jump to latest output",
                        tint = CyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "$scrollOffsetLines lines up",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}
