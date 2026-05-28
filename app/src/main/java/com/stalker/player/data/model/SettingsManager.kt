package com.stalker.player.data.model

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    companion object {
        val KEY_HOSTNAME = stringPreferencesKey("hostname")
        val KEY_MAC = stringPreferencesKey("mac_address")
        val KEY_PORTAL_TYPE = stringPreferencesKey("portal_type")
        val KEY_NUM_THREADS = intPreferencesKey("num_threads")
        val KEY_PROFILES = stringPreferencesKey("profiles")
        val KEY_LANGUAGE = stringPreferencesKey("language")
    }

    val hostname: Flow<String> = context.dataStore.data.map { it[KEY_HOSTNAME] ?: "" }
    val mac: Flow<String> = context.dataStore.data.map { it[KEY_MAC] ?: "" }
    val portalType: Flow<String> = context.dataStore.data.map { it[KEY_PORTAL_TYPE] ?: "" }
    val numThreads: Flow<Int> = context.dataStore.data.map { it[KEY_NUM_THREADS] ?: 5 }
    val language: Flow<String> = context.dataStore.data.map { it[KEY_LANGUAGE] ?: "it" }
    val profilesJson: Flow<String> = context.dataStore.data.map { it[KEY_PROFILES] ?: "[]" }

    fun getSavedLanguage(): String = prefs.getString("language", "it") ?: "it"

    suspend fun saveConnection(hostname: String, mac: String, portalType: String) {
        context.dataStore.edit {
            it[KEY_HOSTNAME] = hostname
            it[KEY_MAC] = mac
            it[KEY_PORTAL_TYPE] = portalType
        }
    }

    suspend fun saveLanguage(lang: String) {
        prefs.edit().putString("language", lang).apply()
        context.dataStore.edit { it[KEY_LANGUAGE] = lang }
    }

    suspend fun saveProfilesJson(json: String) {
        context.dataStore.edit { it[KEY_PROFILES] = json }
    }

    fun profilesJson(): Flow<String> = context.dataStore.data.map { it[KEY_PROFILES] ?: "[]" }
}