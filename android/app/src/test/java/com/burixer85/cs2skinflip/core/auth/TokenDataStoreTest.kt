package com.burixer85.cs2skinflip.core.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TokenDataStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val otherPref = stringPreferencesKey("app_language")

    private fun legacyStore() =
        PreferenceDataStoreFactory.create(produceFile = { tempFolder.newFile("user_prefs.preferences_pb") })

    private fun authStore(legacy: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>) =
        TokenDataStore(
            PreferenceDataStoreFactory.create(
                migrations = listOf(legacyTokenMigration(legacy)),
                produceFile = { tempFolder.newFile("auth_prefs.preferences_pb") },
            )
        )

    @Test
    fun `a token from before the move survives the update`() = runTest {
        val legacy = legacyStore().apply { edit { it[TokenDataStore.TOKEN_KEY] = "jwt" } }

        assertEquals("jwt", authStore(legacy).token.first())
    }

    // user_prefs is still backed up, so the token must not linger there.
    @Test
    fun `the token is removed from the backed-up store, and nothing else is`() = runTest {
        val legacy = legacyStore().apply {
            edit {
                it[TokenDataStore.TOKEN_KEY] = "jwt"
                it[otherPref] = "es"
            }
        }

        authStore(legacy).token.first()

        val left = legacy.data.first()
        assertNull(left[TokenDataStore.TOKEN_KEY])
        assertEquals("es", left[otherPref])
    }

    @Test
    fun `replaceToken swaps only the token it expected`() = runTest {
        val store = authStore(legacyStore())

        store.saveToken("a")
        store.replaceToken(expected = "a", replacement = "b")
        store.replaceToken(expected = "a", replacement = "c")

        assertEquals("b", store.token.first())
    }
}
