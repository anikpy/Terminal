package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Dark Developer Console Core Tokens
val ObsidianCanvas = Color(0xFF0B0F14)
val TerminalCanvas = Color(0xFF070A0E)
val SlateSurface = Color(0xFF121821)
val ElevatedSlate = Color(0xFF18212E)
val ElevatedSlateHigh = Color(0xFF1E293B)
val HairlineBorder = Color(0xFF263244)

// Accents & Semantic Status
val CyanAccent = Color(0xFF22D3EE)
val CyanMuted = Color(0xFF0891B2)
val CyanContainer = Color(0xFF0E3442)
val EmeraldAccent = Color(0xFF34D399)
val EmeraldContainer = Color(0xFF0F352A)
val AmberWarning = Color(0xFFF59E0B)
val AmberContainer = Color(0xFF3B280A)
val CoralError = Color(0xFFFB7185)
val CoralContainer = Color(0xFF3F1622)

// Text Hierarchy
val TextPrimary = Color(0xFFE6EDF3)
val TextSecondary = Color(0xFF8B9BB4)
val TextMuted = Color(0xFF5C6B82)
val OnCyan = Color(0xFF041D24)
val OnEmerald = Color(0xFF052017)

// Host Color Tags
val HostTagPalette = listOf(
    0xFF22D3EE, // Cyan
    0xFF34D399, // Emerald
    0xFF818CF8, // Indigo
    0xFFF59E0B, // Amber
    0xFFFB7185, // Coral
    0xFFA78BFA  // Violet
)

// Calibrated ANSI 16-Color Palette for Dark Terminal Canvas
val AnsiPalette16 = intArrayOf(
    0xFF1E293B.toInt(), // 0 Black (Slate)
    0xFFEF4444.toInt(), // 1 Red
    0xFF22C55E.toInt(), // 2 Green
    0xFFEAB308.toInt(), // 3 Yellow
    0xFF3B82F6.toInt(), // 4 Blue
    0xFFC084FC.toInt(), // 5 Magenta
    0xFF22D3EE.toInt(), // 6 Cyan
    0xFFCBD5E1.toInt(), // 7 White
    0xFF64748B.toInt(), // 8 Bright Black
    0xFFF87171.toInt(), // 9 Bright Red
    0xFF4ADE80.toInt(), // 10 Bright Green
    0xFFFACC15.toInt(), // 11 Bright Yellow
    0xFF60A5FA.toInt(), // 12 Bright Blue
    0xFFE879F9.toInt(), // 13 Bright Magenta
    0xFF67E8F9.toInt(), // 14 Bright Cyan
    0xFFF8FAFC.toInt()  // 15 Bright White
)
