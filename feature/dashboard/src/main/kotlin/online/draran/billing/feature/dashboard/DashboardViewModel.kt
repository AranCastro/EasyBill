package online.draran.billing.feature.dashboard

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/**
 * Phase 0: exposes an empty dashboard. In Phase 1 this combines flows from the
 * invoice, party, item and backup repositories into [DashboardUiState].
 */
@HiltViewModel
class DashboardViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(
        DashboardUiState.empty(
            today = LocalDate.now(),
            now = LocalTime.now(),
            businessName = "My Business",
        ),
    )
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()
}
