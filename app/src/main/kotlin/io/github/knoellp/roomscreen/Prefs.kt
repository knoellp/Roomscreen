package io.github.knoellp.roomscreen

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.knoellp.roomscreen.roon.Endpoint
import io.github.knoellp.roomscreen.roon.TokenStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("roomscreen")

/** Token, last working endpoint, manual-override flag and selected zone, all in one DataStore. */
class Prefs(context: Context) : TokenStore {
    private val store = context.applicationContext.dataStore

    private val hostKey = stringPreferencesKey("host")
    private val portKey = intPreferencesKey("port")
    private val manualKey = booleanPreferencesKey("manual")
    private val zoneKey = stringPreferencesKey("zone_id")
    private fun tokenKey(coreId: String) = stringPreferencesKey("token_$coreId")

    // TokenStore is synchronous by contract; the values are tiny.
    override fun get(coreId: String): String? = runBlocking { store.data.first()[tokenKey(coreId)] }
    override fun put(coreId: String, token: String) { runBlocking { store.edit { it[tokenKey(coreId)] = token } } }

    val zoneId: Flow<String?> = store.data.map { it[zoneKey] }

    suspend fun setZone(id: String?) { store.edit { if (id == null) it.remove(zoneKey) else it[zoneKey] = id } }

    suspend fun lastEndpoint(): Endpoint? = store.data.first().let { p ->
        val h = p[hostKey]; val port = p[portKey]
        if (h != null && port != null) Endpoint(h, port) else null
    }

    suspend fun saveEndpoint(e: Endpoint) { store.edit { it[hostKey] = e.host; it[portKey] = e.port } }

    /** True when the user entered the address by hand: skip discovery and use the saved endpoint. */
    suspend fun manual(): Boolean = store.data.first()[manualKey] ?: false
    suspend fun setManual(value: Boolean) { store.edit { it[manualKey] = value } }

    suspend fun clearAll() { store.edit { it.clear() } }
}
