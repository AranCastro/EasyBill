package online.draran.billing.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable
import online.draran.billing.R
import online.draran.billing.core.designsystem.icon.AppIcons
import kotlin.reflect.KClass

// Type-safe navigation routes ("Nav" prefix avoids clashing with feature composables).
@Serializable data object NavOnboarding
@Serializable data object NavHome
@Serializable data object NavSales
@Serializable data object NavItems
@Serializable data object NavParties
@Serializable data object NavMore

@Serializable data class NavItemEditor(val id: Long = 0, val barcode: String = "", val name: String = "")
@Serializable data class NavPartyEditor(val id: Long = 0, val type: String = "CUSTOMER", val name: String = "")
@Serializable data class NavPartyDetail(val id: Long)
@Serializable data class NavInvoiceEditor(val type: String, val id: Long = 0, val sourceId: Long = 0, val partyId: Long = 0)
@Serializable data class NavInvoiceDetail(val id: Long, val saved: Boolean = false)
@Serializable data object NavCounter
@Serializable data class NavDocuments(val list: String, val filter: String = "")
@Serializable data object NavPayments
@Serializable data class NavPaymentEditor(val direction: String, val id: Long = 0, val partyId: Long = 0)
@Serializable data object NavExpenses
@Serializable data class NavExpenseEditor(val id: Long = 0)
@Serializable data object NavReports
@Serializable data class NavReport(val kind: String)
@Serializable data object NavSettings
@Serializable data object NavBusinessProfile
@Serializable data object NavInvoiceSettings
@Serializable data object NavPrinter
@Serializable data object NavBackup
@Serializable data object NavAbout

/** Bottom navigation tabs: outline icon when unselected, filled when selected. */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val label: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(NavHome, NavHome::class, R.string.nav_home, AppIcons.HomeFilled, AppIcons.Home),
    SALES(NavSales, NavSales::class, R.string.nav_sales, AppIcons.SalesFilled, AppIcons.Sales),
    ITEMS(NavItems, NavItems::class, R.string.nav_items, AppIcons.ItemsFilled, AppIcons.Items),
    PARTIES(NavParties, NavParties::class, R.string.nav_parties, AppIcons.PartiesFilled, AppIcons.Parties),
    MORE(NavMore, NavMore::class, R.string.nav_more, AppIcons.MoreFilled, AppIcons.More),
}
