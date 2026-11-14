package com.example.terminal

import com.example.ui.theme.AnsiPalette16
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min

data class TerminalCell(
    val char: Char = ' ',
    val fgColor: Int = DEFAULT_FG,
    val bgColor: Int = DEFAULT_BG,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val inverse: Boolean = false
) {
    companion object {
        const val DEFAULT_FG = 0xFFE6EDF3.toInt()
        const val DEFAULT_BG = 0x00000000
    }
}

data class TerminalLine(
    val cells: List<TerminalCell>
) {
    fun toPlainText(): String {
        val sb = StringBuilder(cells.size)
        for (cell in cells) {
            sb.append(cell.char)
        }
        return sb.toString().trimEnd()
    }
}

data class TerminalSnapshot(
    val cols: Int,
    val rows: Int,
    val scrollback: List<TerminalLine>,
    val screen: List<TerminalLine>,
    val cursorRow: Int,
    val cursorCol: Int,
    val cursorVisible: Boolean,
    val title: String,
    val isAlternateScreen: Boolean,
    val applicationCursorKeys: Boolean,
    val version: Long
)

class Vt100TerminalEmulator(
    initialCols: Int = 80,
    initialRows: Int = 24,
    private val maxScrollback: Int = 1500,
    private val onBell: () -> Unit = {},
    private val onDeviceResponse: (ByteArray) -> Unit = {}
) {
    private val lock = Any()

    private var cols = initialCols.coerceAtLeast(20)
    private var rows = initialRows.coerceAtLeast(6)

    private var primaryGrid = createGrid(rows, cols)
    private var alternateGrid = createGrid(rows, cols)
    private var usingAlternate = false

    private val scrollbackLines = ArrayDeque<TerminalLine>()

    private var cursorRow = 0
    private var cursorCol = 0
    private var savedRow = 0
    private var savedCol = 0
    private var cursorVisible = true
    private var wrapPending = false

    private var scrollTop = 0
    private var scrollBottom = rows - 1

    private var curFg = TerminalCell.DEFAULT_FG
    private var curBg = TerminalCell.DEFAULT_BG
    private var curBold = false
    private var curItalic = false
    private var curUnderline = false
    private var curInverse = false

    private var windowTitle = ""
    private var applicationCursorKeys = false
    private var version = 0L

    private enum class ParserState {
        NORMAL,
        ESC,
        ESC_CHARSET,
        CSI,
        OSC,
        OSC_ESC
    }

    private var parserState = ParserState.NORMAL
    private val csiBuffer = StringBuilder()
    private val oscBuffer = StringBuilder()

    private val _snapshot = MutableStateFlow(buildSnapshotLocked())
    val snapshot: StateFlow<TerminalSnapshot> = _snapshot.asStateFlow()

    fun feed(text: String) {
        if (text.isEmpty()) return
        synchronized(lock) {
            for (ch in text) {
                processCharLocked(ch)
            }
            version++
            _snapshot.value = buildSnapshotLocked()
        }
    }

    fun appendLocalLine(line: String, fgColor: Int = TerminalCell.DEFAULT_FG) {
        synchronized(lock) {
            val prevFg = curFg
            curFg = fgColor
            for (ch in line) {
                processCharLocked(ch)
            }
            processCharLocked('\r')
            processCharLocked('\n')
            curFg = prevFg
            version++
            _snapshot.value = buildSnapshotLocked()
        }
    }

    fun resize(newCols: Int, newRows: Int) {
        val targetCols = newCols.coerceIn(20, 240)
        val targetRows = newRows.coerceIn(6, 120)
        synchronized(lock) {
            if (targetCols == cols && targetRows == rows) return
            primaryGrid = resizeGrid(primaryGrid, targetRows, targetCols)
            alternateGrid = resizeGrid(alternateGrid, targetRows, targetCols)
            cols = targetCols
            rows = targetRows
            scrollTop = 0
            scrollBottom = rows - 1
            cursorRow = cursorRow.coerceIn(0, rows - 1)
            cursorCol = cursorCol.coerceIn(0, cols - 1)
            savedRow = savedRow.coerceIn(0, rows - 1)
            savedCol = savedCol.coerceIn(0, cols - 1)
            wrapPending = false
            version++
            _snapshot.value = buildSnapshotLocked()
        }
    }

    fun clearScrollbackAndScreen() {
        synchronized(lock) {
            scrollbackLines.clear()
            primaryGrid = createGrid(rows, cols)
            alternateGrid = createGrid(rows, cols)
            cursorRow = 0
            cursorCol = 0
            wrapPending = false
            version++
            _snapshot.value = buildSnapshotLocked()
        }
    }

    fun getTranscriptPreview(maxLines: Int = 100): String {
        synchronized(lock) {
            val combined = mutableListOf<String>()
            for (line in scrollbackLines) {
                combined.add(line.toPlainText())
            }
            for (row in primaryGrid) {
                val text = TerminalLine(row.toList()).toPlainText()
                if (text.isNotEmpty() || combined.isNotEmpty()) {
                    combined.add(text)
                }
            }
            while (combined.isNotEmpty() && combined.last().isEmpty()) {
                combined.removeAt(combined.lastIndex)
            }
            return combined.takeLast(maxLines).joinToString("\n")
        }
    }

    private fun activeGrid(): Array<Array<TerminalCell>> =
        if (usingAlternate) alternateGrid else primaryGrid

    private fun processCharLocked(ch: Char) {
        when (parserState) {
            ParserState.NORMAL -> processNormalCharLocked(ch)
            ParserState.ESC -> processEscCharLocked(ch)
            ParserState.ESC_CHARSET -> {
                parserState = ParserState.NORMAL
            }
            ParserState.CSI -> processCsiCharLocked(ch)
            ParserState.OSC -> processOscCharLocked(ch)
            ParserState.OSC_ESC -> {
                if (ch == '\\') {
                    applyOscLocked()
                    parserState = ParserState.NORMAL
                } else {
                    parserState = ParserState.OSC
                    oscBuffer.append('\u001B').append(ch)
                }
            }
        }
    }

    private fun processNormalCharLocked(ch: Char) {
        when (ch) {
            '\u001B' -> {
                parserState = ParserState.ESC
            }
            '\r' -> {
                cursorCol = 0
                wrapPending = false
            }
            '\n', '\u000B', '\u000C' -> {
                lineFeedLocked()
                wrapPending = false
            }
            '\b' -> {
                wrapPending = false
                if (cursorCol > 0) cursorCol--
            }
            '\t' -> {
                wrapPending = false
                val nextStop = ((cursorCol / 8) + 1) * 8
                cursorCol = min(cols - 1, nextStop)
            }
            '\u0007' -> {
                onBell()
            }
            '\u0000', '\u000E', '\u000F' -> Unit
            else -> {
                if (ch.code < 0x20) return
                if (wrapPending) {
                    cursorCol = 0
                    lineFeedLocked()
                    wrapPending = false
                }
                val grid = activeGrid()
                grid[cursorRow][cursorCol] = TerminalCell(
                    char = ch,
                    fgColor = curFg,
                    bgColor = curBg,
                    bold = curBold,
                    italic = curItalic,
                    underline = curUnderline,
                    inverse = curInverse
                )
                if (cursorCol >= cols - 1) {
                    wrapPending = true
                } else {
                    cursorCol++
                }
            }
        }
    }

    private fun processEscCharLocked(ch: Char) {
        when (ch) {
            '[' -> {
                csiBuffer.clear()
                parserState = ParserState.CSI
            }
            ']' -> {
                oscBuffer.clear()
                parserState = ParserState.OSC
            }
            '(', ')', '*', '+' -> {
                parserState = ParserState.ESC_CHARSET
            }
            '7' -> {
                savedRow = cursorRow
                savedCol = cursorCol
                parserState = ParserState.NORMAL
            }
            '8' -> {
                cursorRow = savedRow.coerceIn(0, rows - 1)
                cursorCol = savedCol.coerceIn(0, cols - 1)
                wrapPending = false
                parserState = ParserState.NORMAL
            }
            'D' -> {
                lineFeedLocked()
                parserState = ParserState.NORMAL
            }
            'E' -> {
                cursorCol = 0
                lineFeedLocked()
                wrapPending = false
                parserState = ParserState.NORMAL
            }
            'M' -> {
                reverseIndexLocked()
                parserState = ParserState.NORMAL
            }
            'c' -> {
                resetTerminalLocked()
                parserState = ParserState.NORMAL
            }
            else -> {
                parserState = ParserState.NORMAL
            }
        }
    }

    private fun processCsiCharLocked(ch: Char) {
        if (ch.code in 0x40..0x7E) {
            executeCsiLocked(csiBuffer.toString(), ch)
            parserState = ParserState.NORMAL
        } else {
            if (csiBuffer.length < 128) {
                csiBuffer.append(ch)
            }
        }
    }

    private fun processOscCharLocked(ch: Char) {
        when (ch) {
            '\u0007' -> {
                applyOscLocked()
                parserState = ParserState.NORMAL
            }
            '\u001B' -> {
                parserState = ParserState.OSC_ESC
            }
            else -> {
                if (oscBuffer.length < 512) {
                    oscBuffer.append(ch)
                }
            }
        }
    }

    private fun applyOscLocked() {
        val raw = oscBuffer.toString()
        val sep = raw.indexOf(';')
        if (sep > 0) {
            val code = raw.substring(0, sep)
            val payload = raw.substring(sep + 1)
            if (code == "0" || code == "2") {
                windowTitle = payload
            }
        }
    }

    private fun executeCsiLocked(rawParams: String, cmd: Char) {
        val isPrivate = rawParams.startsWith("?")
        val clean = if (isPrivate) rawParams.substring(1) else rawParams
        val params = if (clean.isEmpty()) {
            emptyList()
        } else {
            clean.split(';').map { it.trim().toIntOrNull() ?: 0 }
        }

        fun p(index: Int, default: Int): Int =
            params.getOrNull(index)?.takeIf { it > 0 } ?: default

        fun pRaw(index: Int, default: Int = 0): Int =
            params.getOrNull(index) ?: default

        if (isPrivate) {
            when (cmd) {
                'h', 'l' -> {
                    val enable = (cmd == 'h')
                    for (mode in params) {
                        when (mode) {
                            1 -> applicationCursorKeys = enable
                            25 -> cursorVisible = enable
                            47, 1047, 1049 -> {
                                if (enable && !usingAlternate) {
                                    savedRow = cursorRow
                                    savedCol = cursorCol
                                    usingAlternate = true
                                    alternateGrid = createGrid(rows, cols)
                                    cursorRow = 0
                                    cursorCol = 0
                                    wrapPending = false
                                } else if (!enable && usingAlternate) {
                                    usingAlternate = false
                                    cursorRow = savedRow.coerceIn(0, rows - 1)
                                    cursorCol = savedCol.coerceIn(0, cols - 1)
                                    wrapPending = false
                                }
                            }
                        }
                    }
                }
            }
            return
        }

        val grid = activeGrid()
        when (cmd) {
            'A' -> {
                cursorRow = max(scrollTop, cursorRow - p(0, 1))
                wrapPending = false
            }
            'B' -> {
                cursorRow = min(scrollBottom, cursorRow + p(0, 1))
                wrapPending = false
            }
            'C' -> {
                cursorCol = min(cols - 1, cursorCol + p(0, 1))
                wrapPending = false
            }
            'D' -> {
                cursorCol = max(0, cursorCol - p(0, 1))
                wrapPending = false
            }
            'E' -> {
                cursorRow = min(scrollBottom, cursorRow + p(0, 1))
                cursorCol = 0
                wrapPending = false
            }
            'F' -> {
                cursorRow = max(scrollTop, cursorRow - p(0, 1))
                cursorCol = 0
                wrapPending = false
            }
            'G', '`' -> {
                cursorCol = (p(0, 1) - 1).coerceIn(0, cols - 1)
                wrapPending = false
            }
            'H', 'f' -> {
                val targetRow = p(0, 1) - 1
                val targetCol = p(1, 1) - 1
                cursorRow = targetRow.coerceIn(0, rows - 1)
                cursorCol = targetCol.coerceIn(0, cols - 1)
                wrapPending = false
            }
            'd' -> {
                cursorRow = (p(0, 1) - 1).coerceIn(0, rows - 1)
                wrapPending = false
            }
            'J' -> {
                when (pRaw(0, 0)) {
                    0 -> {
                        clearRange(grid, cursorRow, cursorCol, cols - 1)
                        for (r in (cursorRow + 1) until rows) {
                            clearRow(grid[r])
                        }
                    }
                    1 -> {
                        for (r in 0 until cursorRow) {
                            clearRow(grid[r])
                        }
                        clearRange(grid, cursorRow, 0, cursorCol)
                    }
                    2, 3 -> {
                        if (pRaw(0, 0) == 3 && !usingAlternate) {
                            scrollbackLines.clear()
                        }
                        for (r in 0 until rows) {
                            clearRow(grid[r])
                        }
                    }
                }
            }
            'K' -> {
                when (pRaw(0, 0)) {
                    0 -> clearRange(grid, cursorRow, cursorCol, cols - 1)
                    1 -> clearRange(grid, cursorRow, 0, cursorCol)
                    2 -> clearRow(grid[cursorRow])
                }
            }
            'L' -> {
                val count = p(0, 1).coerceAtMost(scrollBottom - cursorRow + 1)
                for (i in 0 until count) {
                    scrollRegionDownLocked(cursorRow, scrollBottom)
                }
            }
            'M' -> {
                val count = p(0, 1).coerceAtMost(scrollBottom - cursorRow + 1)
                for (i in 0 until count) {
                    scrollRegionUpLocked(cursorRow, scrollBottom, recordScrollback = false)
                }
            }
            'P' -> {
                val count = p(0, 1)
                val rowArr = grid[cursorRow]
                for (c in cursorCol until cols) {
                    rowArr[c] = if (c + count < cols) rowArr[c + count] else blankCell()
                }
            }
            '@' -> {
                val count = p(0, 1)
                val rowArr = grid[cursorRow]
                for (c in (cols - 1) downTo cursorCol) {
                    rowArr[c] = if (c - count >= cursorCol) rowArr[c - count] else blankCell()
                }
            }
            'X' -> {
                val count = p(0, 1)
                val endCol = min(cols - 1, cursorCol + count - 1)
                clearRange(grid, cursorRow, cursorCol, endCol)
            }
            'S' -> {
                val count = p(0, 1)
                repeat(count) {
                    scrollRegionUpLocked(scrollTop, scrollBottom, recordScrollback = !usingAlternate && scrollTop == 0)
                }
            }
            'T' -> {
                val count = p(0, 1)
                repeat(count) {
                    scrollRegionDownLocked(scrollTop, scrollBottom)
                }
            }
            'r' -> {
                val top = (p(0, 1) - 1).coerceIn(0, rows - 1)
                val bottom = (p(1, rows) - 1).coerceIn(0, rows - 1)
                if (top < bottom) {
                    scrollTop = top
                    scrollBottom = bottom
                } else {
                    scrollTop = 0
                    scrollBottom = rows - 1
                }
                cursorRow = 0
                cursorCol = 0
                wrapPending = false
            }
            's' -> {
                savedRow = cursorRow
                savedCol = cursorCol
            }
            'u' -> {
                cursorRow = savedRow.coerceIn(0, rows - 1)
                cursorCol = savedCol.coerceIn(0, cols - 1)
                wrapPending = false
            }
            'n' -> {
                when (pRaw(0, 0)) {
                    5 -> onDeviceResponse("\u001B[0n".toByteArray(Charsets.US_ASCII))
                    6 -> onDeviceResponse("\u001B[${cursorRow + 1};${cursorCol + 1}R".toByteArray(Charsets.US_ASCII))
                }
            }
            'c' -> {
                if (pRaw(0, 0) == 0) {
                    onDeviceResponse("\u001B[?1;2c".toByteArray(Charsets.US_ASCII))
                }
            }
            'm' -> {
                applySgrLocked(if (params.isEmpty()) listOf(0) else params)
            }
        }
    }

    private fun applySgrLocked(params: List<Int>) {
        var i = 0
        while (i < params.size) {
            when (val code = params[i]) {
                0 -> {
                    curFg = TerminalCell.DEFAULT_FG
                    curBg = TerminalCell.DEFAULT_BG
                    curBold = false
                    curItalic = false
                    curUnderline = false
                    curInverse = false
                }
                1 -> curBold = true
                3 -> curItalic = true
                4 -> curUnderline = true
                7 -> curInverse = true
                22 -> curBold = false
                23 -> curItalic = false
                24 -> curUnderline = false
                27 -> curInverse = false
                in 30..37 -> curFg = AnsiPalette16[code - 30]
                39 -> curFg = TerminalCell.DEFAULT_FG
                in 40..47 -> curBg = AnsiPalette16[code - 40]
                49 -> curBg = TerminalCell.DEFAULT_BG
                in 90..97 -> curFg = AnsiPalette16[code - 90 + 8]
                in 100..107 -> curBg = AnsiPalette16[code - 100 + 8]
                38, 48 -> {
                    val isFg = (code == 38)
                    val mode = params.getOrNull(i + 1)
                    if (mode == 5 && i + 2 < params.size) {
                        val color = xterm256ToArgb(params[i + 2])
                        if (isFg) curFg = color else curBg = color
                        i += 2
                    } else if (mode == 2 && i + 4 < params.size) {
                        val r = params[i + 2].coerceIn(0, 255)
                        val g = params[i + 3].coerceIn(0, 255)
                        val b = params[i + 4].coerceIn(0, 255)
                        val color = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                        if (isFg) curFg = color else curBg = color
                        i += 4
                    }
                }
            }
            i++
        }
    }

    private fun lineFeedLocked() {
        if (cursorRow == scrollBottom) {
            scrollRegionUpLocked(
                top = scrollTop,
                bottom = scrollBottom,
                recordScrollback = !usingAlternate && scrollTop == 0
            )
        } else if (cursorRow < rows - 1) {
            cursorRow++
        }
    }

    private fun reverseIndexLocked() {
        if (cursorRow == scrollTop) {
            scrollRegionDownLocked(scrollTop, scrollBottom)
        } else if (cursorRow > 0) {
            cursorRow--
        }
    }

    private fun scrollRegionUpLocked(top: Int, bottom: Int, recordScrollback: Boolean) {
        val grid = activeGrid()
        if (recordScrollback) {
            if (scrollbackLines.size >= maxScrollback) {
                scrollbackLines.removeFirst()
            }
            scrollbackLines.addLast(TerminalLine(grid[top].toList()))
        }
        for (r in top until bottom) {
            grid[r] = grid[r + 1]
        }
        grid[bottom] = Array(cols) { blankCell() }
    }

    private fun scrollRegionDownLocked(top: Int, bottom: Int) {
        val grid = activeGrid()
        for (r in bottom downTo (top + 1)) {
            grid[r] = grid[r - 1]
        }
        grid[top] = Array(cols) { blankCell() }
    }

    private fun resetTerminalLocked() {
        usingAlternate = false
        primaryGrid = createGrid(rows, cols)
        alternateGrid = createGrid(rows, cols)
        cursorRow = 0
        cursorCol = 0
        savedRow = 0
        savedCol = 0
        scrollTop = 0
        scrollBottom = rows - 1
        curFg = TerminalCell.DEFAULT_FG
        curBg = TerminalCell.DEFAULT_BG
        curBold = false
        curItalic = false
        curUnderline = false
        curInverse = false
        cursorVisible = true
        applicationCursorKeys = false
        wrapPending = false
    }

    private fun blankCell(): TerminalCell = TerminalCell(
        char = ' ',
        fgColor = TerminalCell.DEFAULT_FG,
        bgColor = curBg
    )

    private fun clearRow(rowArr: Array<TerminalCell>) {
        val blank = blankCell()
        for (c in rowArr.indices) {
            rowArr[c] = blank
        }
    }

    private fun clearRange(grid: Array<Array<TerminalCell>>, row: Int, startCol: Int, endCol: Int) {
        if (row !in 0 until rows) return
        val from = max(0, startCol)
        val to = min(cols - 1, endCol)
        val blank = blankCell()
        for (c in from..to) {
            grid[row][c] = blank
        }
    }

    private fun createGrid(r: Int, c: Int): Array<Array<TerminalCell>> =
        Array(r) { Array(c) { TerminalCell() } }

    private fun resizeGrid(
        oldGrid: Array<Array<TerminalCell>>,
        newRows: Int,
        newCols: Int
    ): Array<Array<TerminalCell>> {
        val next = createGrid(newRows, newCols)
        val copyRows = min(oldGrid.size, newRows)
        val startOffset = (oldGrid.size - copyRows).coerceAtLeast(0)
        for (r in 0 until copyRows) {
            val srcRow = oldGrid[startOffset + r]
            val copyCols = min(srcRow.size, newCols)
            for (c in 0 until copyCols) {
                next[r][c] = srcRow[c]
            }
        }
        return next
    }

    private fun buildSnapshotLocked(): TerminalSnapshot {
        val grid = activeGrid()
        val screenLines = grid.map { row -> TerminalLine(row.toList()) }
        val scrollbackCopy = if (usingAlternate) emptyList() else scrollbackLines.toList()
        return TerminalSnapshot(
            cols = cols,
            rows = rows,
            scrollback = scrollbackCopy,
            screen = screenLines,
            cursorRow = cursorRow,
            cursorCol = cursorCol,
            cursorVisible = cursorVisible,
            title = windowTitle,
            isAlternateScreen = usingAlternate,
            applicationCursorKeys = applicationCursorKeys,
            version = version
        )
    }

    private fun xterm256ToArgb(index: Int): Int {
        val idx = index.coerceIn(0, 255)
        if (idx < 16) return AnsiPalette16[idx]
        if (idx < 232) {
            val v = idx - 16
            val r = v / 36
            val g = (v % 36) / 6
            val b = v % 6
            val rr = if (r == 0) 0 else 55 + r * 40
            val gg = if (g == 0) 0 else 55 + g * 40
            val bb = if (b == 0) 0 else 55 + b * 40
            return (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
        }
        val gray = 8 + (idx - 232) * 10
        return (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
    }
}
