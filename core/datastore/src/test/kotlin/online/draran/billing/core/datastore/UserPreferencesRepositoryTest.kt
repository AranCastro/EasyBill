package online.draran.billing.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import online.draran.billing.core.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UserPreferencesRepositoryTest {

    @get:Rule val tmp: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    private fun TestScope.repository() = UserPreferencesRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "prefs.preferences_pb") },
    )

    @Test fun defaultsToLightThemeWithBrandColours() = runTest(UnconfinedTestDispatcher()) {
        val prefs = repository().preferences.first()
        assertEquals(ThemeMode.LIGHT, prefs.themeMode)
        assertFalse(prefs.dynamicColor)
    }

    @Test fun savesThemeAndDynamicColour() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository()
        repo.setThemeMode(ThemeMode.DARK)
        repo.setDynamicColor(true)
        val prefs = repo.preferences.first()
        assertEquals(ThemeMode.DARK, prefs.themeMode)
        assertTrue(prefs.dynamicColor)
    }
}
