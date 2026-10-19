package io.github.knoellp.roomscreen

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.knoellp.roomscreen.roon.Endpoint
import io.github.knoellp.roomscreen.roon.TokenStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("roomscreen")

private inline fun <reified T : Enum<T>> String?.asEnum(): T? = this?.let { v -> enumValues<T>().firstOrNull { it.name == v } }

/** Token, last working endpoint, manual-override flag and selected zone, all in one DataStore. */
class Prefs(context: Context) : TokenStore {
    private val store = context.applicationContext.dataStore

    private val hostKey = stringPreferencesKey("host")
    private val portKey = intPreferencesKey("port")
    private val manualKey = booleanPreferencesKey("manual")
    private val installKey = stringPreferencesKey("install_id")
    private val outputsKey = stringSetPreferencesKey("zone_output_ids")
    private val zoneKey = stringPreferencesKey("zone_id")
    private val bgKey = stringPreferencesKey("setting_background")
    private val sharpKey = stringPreferencesKey("setting_sharpness")
    private val colorKey = stringPreferencesKey("setting_background_color")
    private val controlsKey = stringPreferencesKey("setting_controls")
    private val screenKey = stringPreferencesKey("setting_screen_on")
    private val viaKey = booleanPreferencesKey("setting_via_roon")
    private val keySoundsKey = booleanPreferencesKey("setting_key_sounds")
    private val lrclibKey = booleanPreferencesKey("setting_lrclib")
    private val lyricsOffsetKey = intPreferencesKey("setting_lyrics_offset_ms")
    private val lyricsOnKey = booleanPreferencesKey("lyrics_on")
    private fun tokenKey(coreId: String) = stringPreferencesKey("token_$coreId")

    // TokenStore is synchronous by contract; the values are tiny.
    override fun get(coreId: String): String? = runBlocking { store.data.first()[tokenKey(coreId)] }
    override fun put(coreId: String, token: String) { runBlocking { store.edit { it[tokenKey(coreId)] = token } } }

    /** User settings; unknown or missing values fall back to the defaults. */
    val settings: Flow<AppSettings> = store.data.map { p ->
        val d = AppSettings()
        AppSettings(
            background = p[bgKey].asEnum() ?: d.background,
            sharpness = p[sharpKey].asEnum() ?: d.sharpness,
            backgroundColor = p[colorKey].asEnum() ?: d.backgroundColor,
            controls = p[controlsKey].asEnum() ?: d.controls,
            screenOn = p[screenKey].asEnum() ?: d.screenOn,
            viaRoon = p[viaKey] ?: d.viaRoon,
            keySounds = p[keySoundsKey] ?: d.keySounds,
            lrclib = p[lrclibKey] ?: d.lrclib,
            lyricsOffsetMs = (p[lyricsOffsetKey] ?: d.lyricsOffsetMs).coerceIn(LYRICS_OFFSET_MIN_MS, LYRICS_OFFSET_MAX_MS),
        )
    }

    suspend fun saveSettings(s: AppSettings) {
        store.edit {
            it[bgKey] = s.background.name; it[sharpKey] = s.sharpness.name; it[colorKey] = s.backgroundColor.name; it[controlsKey] = s.controls.name
            it[screenKey] = s.screenOn.name; it[viaKey] = s.viaRoon; it[keySoundsKey] = s.keySounds; it[lrclibKey] = s.lrclib; it[lyricsOffsetKey] = s.lyricsOffsetMs
        }
    }

    /** Last choice of the Lyrics button (default off). */
    val lyricsOn: Flow<Boolean> = store.data.map { it[lyricsOnKey] ?: false }

    suspend fun setLyricsOn(on: Boolean) { store.edit { it[lyricsOnKey] = on } }

    val zoneId: Flow<String?> = store.data.map { it[zoneKey] }

    val zoneOutputIds: Flow<Set<String>> = store.data.map { it[outputsKey] ?: emptySet() }

    /** Stores the selection: the zone id and its output ids (outputs survive grouping, zone ids do not). */
    suspend fun setZone(id: String, outputIds: Set<String>) { store.edit { it[zoneKey] = id; it[outputsKey] = outputIds } }

    /** Refreshes the output ids of the saved zone without touching the saved zone id. */
    suspend fun setOutputIds(outputIds: Set<String>) { store.edit { it[outputsKey] = outputIds } }

    suspend fun lastEndpoint(): Endpoint? = store.data.first().let { p ->
        val h = p[hostKey]; val port = p[portKey]
        if (h != null && port != null) Endpoint(h, port) else null
    }

    suspend fun saveEndpoint(e: Endpoint) { store.edit { it[hostKey] = e.host; it[portKey] = e.port } }

    /** True when the user entered the address by hand: skip discovery and use the saved endpoint. */
    suspend fun manual(): Boolean = store.data.first()[manualKey] ?: false
    suspend fun setManual(value: Boolean) { store.edit { it[manualKey] = value } }

    /** Random per-installation id; two devices must not share an extension_id (the Core evicts the older connection). */
    fun installId(): String = runBlocking {
        store.data.first()[installKey] ?: java.util.UUID.randomUUID().toString().take(8).also { id -> store.edit { it[installKey] = id } }
    }

    /** Forgets the stored Core address only (host, port, manual flag). Token and zone stay. */
    suspend fun clearEndpoint() { store.edit { it.remove(hostKey); it.remove(portKey); it.remove(manualKey) } }

    suspend fun clearAll() { store.edit { val id = it[installKey]; it.clear(); if (id != null) it[installKey] = id } }
}
