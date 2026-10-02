package online.draran.billing.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable
import online.draran.billing.R
import online.draran.billing.core.designsystem.icon.AppIcons
import kotlin.reflect.KClass

// Type-safe navigation routes.
@Serializable data object HomeRoute
@Serializable data object SalesRoute
@Serializable data object ItemsRoute
@Serializable data object PartiesRoute
@Serializable data object MoreRoute
@Serializable data object SettingsRoute

/** Full-screen destinations opened from tabs. Placeholders until their phase is built. */
@Serializable data class PlaceholderRoute(val title: String, val phase: String)

/** Bottom navigation tabs: outline icon when unselected, filled when selected. */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val label: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(HomeRoute, HomeRoute::class, R.string.nav_home, AppIcons.HomeFilled, AppIcons.Home),
    SALES(SalesRoute, SalesRoute::class, R.string.nav_sales, AppIcons.SalesFilled, AppIcons.Sales),
    ITEMS(ItemsRoute, ItemsRoute::class, R.string.nav_items, AppIcons.ItemsFilled, AppIcons.Items),
    PARTIES(PartiesRoute, PartiesRoute::class, R.string.nav_parties, AppIcons.PartiesFilled, AppIcons.Parties),
    MORE(MoreRoute, MoreRoute::class, R.string.nav_more, AppIcons.MoreFilled, AppIcons.More),
}
