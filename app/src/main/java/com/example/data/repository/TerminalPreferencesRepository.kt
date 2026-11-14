package com.example.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.terminalDataStore by preferencesDataStore(name = "easy_terminal_prefs")

data class TerminalPreferences(
    val fontSizeSp: Int = 13,
    val showExtraKeybar: Boolean = true,
    val cursorBlink: Boolean = true,
    val keepScreenOn: Boolean = true
)

class TerminalPreferencesRepository(private val context: Context) {

    private object Keys {
        val FONT_SIZE_SP = intPreferencesKey("font_size_sp")
        val SHOW_EXTRA_KEYBAR = booleanPreferencesKey("show_extra_keybar")
        val CURSOR_BLINK = booleanPreferencesKey("cursor_blink")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    }

    val preferences: Flow<TerminalPreferences> = context.terminalDataStore.data.map { prefs ->
        TerminalPreferences(
            fontSizeSp = (prefs[Keys.FONT_SIZE_SP] ?: 13).coerceIn(10, 20),
            showExtraKeybar = prefs[Keys.SHOW_EXTRA_KEYBAR] ?: true,
            cursorBlink = prefs[Keys.CURSOR_BLINK] ?: true,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true
        )
    }

    suspend fun setFontSizeSp(sizeSp: Int) {
        context.terminalDataStore.edit { prefs ->
            prefs[Keys.FONT_SIZE_SP] = sizeSp.coerceIn(10, 20)
        }
    }

    suspend fun setShowExtraKeybar(show: Boolean) {
        context.terminalDataStore.edit { prefs ->
            prefs[Keys.SHOW_EXTRA_KEYBAR] = show
        }
    }

    suspend fun setCursorBlink(blink: Boolean) {
        context.terminalDataStore.edit { prefs ->
            prefs[Keys.CURSOR_BLINK] = blink
        }
    }

    suspend fun setKeepScreenOn(keepOn: Boolean) {
        context.terminalDataStore.edit { prefs ->
            prefs[Keys.KEEP_SCREEN_ON] = keepOn
        }
    }
}
