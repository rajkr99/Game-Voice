package com.gamervoice.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gamervoice.app.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

/** 0 = Off, 1 = Standard (echo + noise + gain), 2 = Strong (Standard + typing/keyboard detection). */
object NoiseLevel { const val OFF = 0; const val STANDARD = 1; const val STRONG = 2 }

data class AppSettings(
    val lastName: String = "",
    val lastRoom: String = "",
    val pushToTalk: Boolean = false,
    val noiseLevel: Int = NoiseLevel.STANDARD,
    /** Higher Opus bitrate (32 kbps) for clearer voice. Ignored when [dataSaver] is on. */
    val clearVoice: Boolean = false,
    val dataSaver: Boolean = false,
    /** Token server base URL. Changing it lets you move to a self-hosted setup without a rebuild. */
    val serverUrl: String = BuildConfig.TOKEN_SERVER_URL,
)

/** Small DataStore wrapper. All values persist across app restarts. */
class SettingsRepository(private val context: Context) {
    private object K {
        val name = stringPreferencesKey("name")
        val room = stringPreferencesKey("room")
        val ptt = booleanPreferencesKey("ptt")
        val ns = booleanPreferencesKey("ns") // legacy on/off, only read as a fallback
        val nsLevel = intPreferencesKey("ns_level")
        val clear = booleanPreferencesKey("clear_voice")
        val saver = booleanPreferencesKey("saver")
        val server = stringPreferencesKey("server")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    private fun Preferences.toSettings() = AppSettings(
        lastName = this[K.name] ?: "",
        lastRoom = this[K.room] ?: "",
        pushToTalk = this[K.ptt] ?: false,
        noiseLevel = this[K.nsLevel]
            ?: if (this[K.ns] == false) NoiseLevel.OFF else NoiseLevel.STANDARD,
        clearVoice = this[K.clear] ?: false,
        dataSaver = this[K.saver] ?: false,
        serverUrl = this[K.server]?.takeIf { it.isNotBlank() } ?: BuildConfig.TOKEN_SERVER_URL,
    )

    suspend fun saveLastJoin(name: String, room: String) = context.dataStore.edit {
        it[K.name] = name; it[K.room] = room
    }
    suspend fun setPushToTalk(v: Boolean) = context.dataStore.edit { it[K.ptt] = v }
    suspend fun setNoiseLevel(v: Int) = context.dataStore.edit { it[K.nsLevel] = v.coerceIn(0, 2) }
    suspend fun setClearVoice(v: Boolean) = context.dataStore.edit { it[K.clear] = v }
    suspend fun setDataSaver(v: Boolean) = context.dataStore.edit { it[K.saver] = v }
    suspend fun setServerUrl(v: String) = context.dataStore.edit { it[K.server] = v.trim().trimEnd('/') }
}
