package online.draran.billing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import online.draran.billing.core.data.BackupManager
import online.draran.billing.core.data.BusinessRepository
import online.draran.billing.core.datastore.UserPreferencesRepository
import online.draran.billing.core.model.BusinessType
import online.draran.billing.core.model.ThemeMode
import online.draran.billing.core.model.UserPreferences
import javax.inject.Inject

sealed interface MainUiState {
    data object Loading : MainUiState
    data class Ready(val preferences: UserPreferences, val onboarded: Boolean, val businessType: BusinessType = BusinessType.RETAIL) : MainUiState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    businessRepository: BusinessRepository,
    backupManager: BackupManager,
) : ViewModel() {

    val uiState: StateFlow<MainUiState> = combine(preferencesRepository.preferences, businessRepository.business) { prefs, business ->
        MainUiState.Ready(prefs, business.onboarded, business.type)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)

    /**
     * App lock state. Null until the saved setting is read (the splash stays up until then,
     * so no data shows before the lock). Kept here so turning the phone does not lock again.
     */
    var unlocked by mutableStateOf<Boolean?>(null)
        private set

    /** When the app last went to the background (elapsed realtime), or 0. */
    var backgroundAt = 0L

    fun unlock() {
        unlocked = true
    }

    /** Called when the app comes back to the screen: lock again after [LOCK_AFTER_MS] away. */
    fun onReturn(now: Long, appLock: Boolean) {
        if (appLock && backgroundAt > 0 && now - backgroundAt >= LOCK_AFTER_MS) unlocked = false
        backgroundAt = 0
    }

    init {
        viewModelScope.launch {
            unlocked = !preferencesRepository.preferences.first().appLock
        }
        // Daily safety copy in app storage. Checked every hour too, because a shop may keep
        // the app open all day and never restart it; the check is cheap when not due.
        viewModelScope.launch {
            while (true) {
                if (businessRepository.get().onboarded) runCatching { backupManager.autoBackupIfDue() }
                delay(BACKUP_CHECK_MS)
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }
}

private const val BACKUP_CHECK_MS = 60 * 60 * 1000L
const val LOCK_AFTER_MS = 60 * 1000L
