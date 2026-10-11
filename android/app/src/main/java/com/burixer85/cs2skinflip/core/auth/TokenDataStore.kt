package com.burixer85.cs2skinflip.core.auth

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * The backend JWT, in a DataStore file of its own (`auth_prefs`) so the backup
 * rules can exclude it without excluding the user's other preferences: a token
 * restored onto another device is a live session nobody signed in to there.
 */
@Singleton
class TokenDataStore @Inject constructor(
    @Named("auth") private val dataStore: DataStore<Preferences>
) {
    companion object {
        val TOKEN_KEY = stringPreferencesKey("jwt_token")
    }

    val token: Flow<String?> = dataStore.data.map { it[TOKEN_KEY] }

    suspend fun saveToken(token: String) {
        dataStore.edit { it[TOKEN_KEY] = token }
    }

    /** Swaps [expected] for [replacement] atomically; a no-op if the stored token changed meanwhile. */
    suspend fun replaceToken(expected: String, replacement: String) {
        dataStore.edit { if (it[TOKEN_KEY] == expected) it[TOKEN_KEY] = replacement }
    }

    suspend fun clearToken() {
        dataStore.edit { it.remove(TOKEN_KEY) }
    }
}

/**
 * Moves the token out of `user_prefs`, where versions before the backup
 * exclusion kept it, so updating the app doesn't sign anyone out — and removes
 * it from there, since that file is still backed up.
 */
internal fun legacyTokenMigration(legacy: DataStore<Preferences>) = object : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        legacy.data.first()[TokenDataStore.TOKEN_KEY] != null

    override suspend fun migrate(currentData: Preferences): Preferences {
        val legacyToken = legacy.data.first()[TokenDataStore.TOKEN_KEY] ?: return currentData
        if (currentData[TokenDataStore.TOKEN_KEY] != null) return currentData
        return currentData.toMutablePreferences().apply { this[TokenDataStore.TOKEN_KEY] = legacyToken }.toPreferences()
    }

    override suspend fun cleanUp() {
        legacy.edit { it.remove(TokenDataStore.TOKEN_KEY) }
    }
}
