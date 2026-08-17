package dev.dsh.remote.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "dsh_settings")

class SettingsStore(private val context: Context) {
    private val serverUrlKey = stringPreferencesKey("server_url")
    private val draftsKey = stringPreferencesKey("drafts")
    private val notifyDoneKey = booleanPreferencesKey("notify_done")
    private val notifyPromptKey = booleanPreferencesKey("notify_prompt")
    private val deepseekApiKeyKey = stringPreferencesKey("deepseek_api_key")
    private val themePreferenceKey = stringPreferencesKey("theme_preference")
    private val languageKey = stringPreferencesKey("language")

    val serverUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[serverUrlKey] ?: DEFAULT_URL
    }

    /** UI appearance: "system" | "light" | "dark". Defaults to follow system. */
    val themePreference: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[themePreferenceKey] ?: "system"
    }

    /** UI language: "zh" | "en". Defaults to Chinese. */
    val language: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[languageKey] ?: "zh"
    }

    /** DeepSeek platform API key for balance/usage queries. */
    val deepseekApiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[deepseekApiKeyKey] ?: ""
    }

    /** Task-completion notification (with sound). Defaults to on. */
    val notifyDone: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[notifyDoneKey] ?: true
    }

    /** Question / approval-request notifications. Defaults to on. */
    val notifyPrompt: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[notifyPromptKey] ?: true
    }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { prefs -> prefs[serverUrlKey] = url.trimEnd('/') }
    }

    suspend fun setNotifyDone(v: Boolean) {
        context.dataStore.edit { prefs -> prefs[notifyDoneKey] = v }
    }

    suspend fun setNotifyPrompt(v: Boolean) {
        context.dataStore.edit { prefs -> prefs[notifyPromptKey] = v }
    }

    suspend fun setDeepseekApiKey(key: String) {
        context.dataStore.edit { prefs -> prefs[deepseekApiKeyKey] = key.trim() }
    }

    suspend fun setThemePreference(v: String) {
        context.dataStore.edit { prefs -> prefs[themePreferenceKey] = v }
    }

    suspend fun setLanguage(v: String) {
        context.dataStore.edit { prefs -> prefs[languageKey] = v }
    }

    /** Persist per-session composer drafts as a JSON map (WeChat-style drafts). */
    suspend fun saveDrafts(map: Map<String, String>) {
        val json = JSONObject()
        for ((k, v) in map) if (v.isNotEmpty()) json.put(k, v)
        context.dataStore.edit { prefs -> prefs[draftsKey] = json.toString() }
    }

    suspend fun loadDrafts(): Map<String, String> {
        val json = context.dataStore.data.first()[draftsKey] ?: return emptyMap()
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.optString(k, "")
                if (v.isNotEmpty()) map[k] = v
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    companion object {
        const val DEFAULT_URL = "https://desktop-e0lt97r.tailcf2bf3.ts.net:8443"
    }
}
