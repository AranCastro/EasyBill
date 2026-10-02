package online.draran.billing.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import online.draran.billing.R
import online.draran.billing.core.designsystem.component.EmptyState
import online.draran.billing.feature.dashboard.DashboardDestination
import online.draran.billing.feature.dashboard.DashboardRoute
import online.draran.billing.navigation.HomeRoute
import online.draran.billing.navigation.ItemsRoute
import online.draran.billing.navigation.MoreRoute
import online.draran.billing.navigation.PartiesRoute
import online.draran.billing.navigation.PlaceholderRoute
import online.draran.billing.navigation.SalesRoute
import online.draran.billing.navigation.TopLevelDestination

/** Root composable: bottom navigation, "New Sale" button and the nav graph. */
@Composable
fun BillingApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentTab = TopLevelDestination.entries.firstOrNull { tab ->
        currentDestination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
    }
    val newSaleTitle = stringResource(R.string.new_sale)

    Scaffold(
        bottomBar = {
            if (currentTab != null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    TopLevelDestination.entries.forEach { tab ->
                        val selected = tab == currentTab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTab(tab) },
                            icon = {
                                Icon(if (selected) tab.selectedIcon else tab.unselectedIcon, contentDescription = null)
                            },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            // One tap to start a bill from Home and Sales
            AnimatedVisibility(
                visible = currentTab == TopLevelDestination.HOME || currentTab == TopLevelDestination.SALES,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                ExtendedFloatingActionButton(
                    onClick = { navController.navigate(PlaceholderRoute(newSaleTitle, "Phase 1")) },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(newSaleTitle) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable<HomeRoute> {
                DashboardRoute(
                    contentPadding = innerPadding,
                    onNavigate = { destination ->
                        navController.navigate(PlaceholderRoute(destination.title(), destination.phase()))
                    },
                )
            }
            composable<SalesRoute> {
                TabPlaceholder(innerPadding, Icons.AutoMirrored.Rounded.ReceiptLong, R.string.nav_sales, R.string.sales_empty)
            }
            composable<ItemsRoute> {
                TabPlaceholder(innerPadding, Icons.Rounded.Inventory2, R.string.nav_items, R.string.items_empty)
            }
            composable<PartiesRoute> {
                TabPlaceholder(innerPadding, Icons.Rounded.People, R.string.nav_parties, R.string.parties_empty)
            }
            composable<MoreRoute> {
                TabPlaceholder(innerPadding, Icons.Rounded.GridView, R.string.nav_more, R.string.more_empty)
            }
            composable<PlaceholderRoute> { entry ->
                val route = entry.toRoute<PlaceholderRoute>()
                PlaceholderScreen(route.title, route.phase, onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun NavHostController.navigateToTab(tab: TopLevelDestination) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun TabPlaceholder(padding: PaddingValues, icon: ImageVector, title: Int, message: Int) {
    EmptyState(
        icon = icon,
        title = stringResource(R.string.coming_soon_title, "Phase 1").let { "${stringResource(title)} · $it" },
        message = stringResource(message),
        modifier = Modifier.padding(padding).fillMaxSize(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceholderScreen(title: String, phase: String, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        EmptyState(
            icon = Icons.Rounded.Construction,
            title = stringResource(R.string.coming_soon_title, phase),
            message = stringResource(R.string.new_sale_empty).takeIf { title == stringResource(R.string.new_sale) }
                ?: "This screen is part of the build plan and is not implemented yet.",
            modifier = Modifier.padding(padding).fillMaxSize(),
        )
    }
}

private fun DashboardDestination.title(): String = when (this) {
    DashboardDestination.NEW_SALE -> "New Sale"
    DashboardDestination.COUNTER -> "Counter Billing"
    DashboardDestination.PAYMENT_IN -> "Payment In"
    DashboardDestination.ADD_ITEM -> "Add Item"
    DashboardDestination.PURCHASE -> "Purchase"
    DashboardDestination.EXPENSE -> "Expense"
    DashboardDestination.ESTIMATE -> "Estimate"
    DashboardDestination.REPORTS -> "Reports"
    DashboardDestination.TO_COLLECT -> "To Collect"
    DashboardDestination.TO_PAY -> "To Pay"
    DashboardDestination.LOW_STOCK -> "Low Stock"
    DashboardDestination.ALL_TRANSACTIONS -> "All Transactions"
    DashboardDestination.BACKUP -> "Backup & Restore"
    DashboardDestination.SETTINGS -> "Settings"
}

private fun DashboardDestination.phase(): String = when (this) {
    DashboardDestination.PURCHASE, DashboardDestination.EXPENSE, DashboardDestination.ESTIMATE -> "Phase 2"
    DashboardDestination.REPORTS -> "Phase 3"
    else -> "Phase 1"
}
