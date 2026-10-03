package online.draran.billing.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import online.draran.billing.core.model.ThemeMode
import online.draran.billing.core.model.UserPreferences
import javax.inject.Inject
import javax.inject.Singleton

/** Reads and writes [UserPreferences]. Survives app restarts and is included in backups. */
@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val preferences: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            themeMode = prefs[Keys.THEME_MODE]
                ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
                ?: UserPreferences().themeMode,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: UserPreferences().dynamicColor,
            appLock = prefs[Keys.APP_LOCK] ?: false,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setAppLock(enabled: Boolean) {
        dataStore.edit { it[Keys.APP_LOCK] = enabled }
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val APP_LOCK = booleanPreferencesKey("app_lock")
    }
}
