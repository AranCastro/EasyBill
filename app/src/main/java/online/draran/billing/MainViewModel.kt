package online.draran.billing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

    init {
        // Daily safety copy in app storage; cheap when not due
        viewModelScope.launch {
            if (businessRepository.get().onboarded) runCatching { backupManager.autoBackupIfDue() }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }
}
