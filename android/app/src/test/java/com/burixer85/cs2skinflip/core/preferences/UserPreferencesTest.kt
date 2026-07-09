package com.burixer85.cs2skinflip.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserPreferencesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun preferences(): UserPreferences {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        return UserPreferences(dataStore)
    }

    @Test
    fun `reviewRequested defaults to false`() = runTest {
        val prefs = preferences()

        assertFalse(prefs.reviewRequested.first())
    }

    @Test
    fun `setReviewRequested persists true`() = runTest {
        val prefs = preferences()

        prefs.setReviewRequested(true)

        assertEquals(true, prefs.reviewRequested.first())
    }

    @Test
    fun `sessionCount defaults to 0`() = runTest {
        val prefs = preferences()

        assertEquals(0, prefs.sessionCount.first())
    }

    @Test
    fun `incrementSessionCount increases and returns the new value`() = runTest {
        val prefs = preferences()

        val first = prefs.incrementSessionCount()
        val second = prefs.incrementSessionCount()

        assertEquals(1, first)
        assertEquals(2, second)
        assertEquals(2, prefs.sessionCount.first())
    }
}
