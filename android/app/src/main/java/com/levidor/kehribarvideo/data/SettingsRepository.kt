package com.levidor.kehribarvideo.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "kehribar_settings")

/**
 * Sunucu (GPU backend) adresini kalıcı olarak saklar. Colab/başka bir GPU'ya
 * geçince APK'yı yeniden derlemeye gerek kalmadan buradan güncellenir.
 */
class SettingsRepository(private val context: Context) {

    companion object {
        private val SERVER_URL_KEY = stringPreferencesKey("server_url")
        const val DEFAULT_URL = "http://10.0.2.2:8000/"
    }

    val serverUrlFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SERVER_URL_KEY] ?: DEFAULT_URL
    }

    suspend fun setServerUrl(url: String) {
        val normalized = if (url.endsWith("/")) url else "$url/"
        context.dataStore.edit { prefs ->
            prefs[SERVER_URL_KEY] = normalized
        }
    }
}
