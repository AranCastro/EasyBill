package online.draran.billing.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import online.draran.billing.core.data.BackupManager
import online.draran.billing.core.data.DashboardData
import online.draran.billing.core.data.ReportsRepository
import online.draran.billing.core.model.RecentTransaction
import online.draran.billing.core.model.TransactionType
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** Live dashboard built from bills, payments, parties, items and backup status. */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    reports: ReportsRepository,
    backup: BackupManager,
) : ViewModel() {

    val state: StateFlow<DashboardUiState> = combine(reports.dashboard(), backup.lastBackup) { data, last ->
        data.toUi(last)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DashboardUiState.empty(LocalDate.now(), LocalTime.now(), ""),
    )

    private fun DashboardData.toUi(lastBackup: Long?): DashboardUiState = DashboardUiState(
        greeting = Greeting.at(LocalTime.now()),
        businessName = businessName,
        todaySales = todaySales,
        todayBillCount = todayCount,
        changeVsYesterdayPercent = if (yesterdaySales.paise > 0) ((todaySales.paise - yesterdaySales.paise) * 100 / yesterdaySales.paise).toInt() else null,
        toCollect = toCollect,
        toCollectPartyCount = toCollectCount,
        toPay = toPay,
        toPayPartyCount = toPayCount,
        lowStockCount = lowStock,
        week = week,
        recent = recent.map {
            RecentTransaction(
                id = it.id,
                type = runCatching { TransactionType.valueOf(it.kind) }.getOrDefault(TransactionType.SALE),
                number = it.number,
                partyName = it.partyName.ifBlank { if (it.kind == "EXPENSE") it.number else "" },
                date = it.date,
                total = it.amount,
                balanceDue = it.balance,
            )
        },
        lastBackupDaysAgo = lastBackup?.let { ((System.currentTimeMillis() - it) / 86_400_000L).toInt() },
    )
}
