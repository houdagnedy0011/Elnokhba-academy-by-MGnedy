package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "elite_academy_prefs")

class PreferencesManager(private val context: Context) {
    private val darkModeKey = booleanPreferencesKey("dark_mode_override")
    private val hasDarkOverrideKey = booleanPreferencesKey("has_dark_override")

    val darkModeFlow: Flow<Boolean?> = context.dataStore.data.map { prefs ->
        if (prefs[hasDarkOverrideKey] == true) prefs[darkModeKey] else null
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[hasDarkOverrideKey] = true
            prefs[darkModeKey] = enabled
        }
    }
}
