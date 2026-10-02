package online.draran.billing.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.People
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable
import online.draran.billing.R
import kotlin.reflect.KClass

// Type-safe navigation routes.
@Serializable data object HomeRoute
@Serializable data object SalesRoute
@Serializable data object ItemsRoute
@Serializable data object PartiesRoute
@Serializable data object MoreRoute

/** Full-screen destinations opened from tabs. Placeholders until their phase is built. */
@Serializable data class PlaceholderRoute(val title: String, val phase: String)

/** Bottom navigation tabs. */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val label: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(HomeRoute, HomeRoute::class, R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home),
    SALES(SalesRoute, SalesRoute::class, R.string.nav_sales, Icons.AutoMirrored.Rounded.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    ITEMS(ItemsRoute, ItemsRoute::class, R.string.nav_items, Icons.Rounded.Inventory2, Icons.Outlined.Inventory2),
    PARTIES(PartiesRoute, PartiesRoute::class, R.string.nav_parties, Icons.Rounded.People, Icons.Outlined.People),
    MORE(MoreRoute, MoreRoute::class, R.string.nav_more, Icons.Rounded.GridView, Icons.Outlined.GridView),
}
