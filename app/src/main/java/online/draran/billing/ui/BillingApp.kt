package online.draran.billing.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
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
import online.draran.billing.core.designsystem.component.AppFab
import online.draran.billing.core.designsystem.icon.AppIcons
import online.draran.billing.core.model.BusinessType
import online.draran.billing.core.model.DocType
import online.draran.billing.core.model.PartyType
import online.draran.billing.core.model.PaymentDirection
import online.draran.billing.core.model.TransactionType
import online.draran.billing.feature.billing.CounterRoute
import online.draran.billing.feature.billing.DocList
import online.draran.billing.feature.billing.DocumentsRoute
import online.draran.billing.feature.billing.InvoiceDetailRoute
import online.draran.billing.feature.billing.InvoiceEditorRoute
import online.draran.billing.feature.billing.SalesTabRoute
import online.draran.billing.feature.dashboard.DashboardDestination
import online.draran.billing.feature.dashboard.DashboardRoute
import online.draran.billing.feature.items.ItemEditorRoute
import online.draran.billing.feature.items.ItemsRoute
import online.draran.billing.feature.money.ExpenseEditorRoute
import online.draran.billing.feature.money.ExpensesRoute
import online.draran.billing.feature.money.PaymentEditorRoute
import online.draran.billing.feature.money.PaymentsRoute
import online.draran.billing.feature.onboarding.BusinessProfileRoute
import online.draran.billing.feature.onboarding.OnboardingRoute
import online.draran.billing.feature.parties.PartiesRoute
import online.draran.billing.feature.parties.PartyDetailRoute
import online.draran.billing.feature.parties.PartyEditorRoute
import online.draran.billing.feature.reports.ReportKind
import online.draran.billing.feature.reports.ReportRoute
import online.draran.billing.feature.reports.ReportsHubRoute
import online.draran.billing.feature.settings.BackupRoute
import online.draran.billing.feature.settings.InvoiceSettingsRoute
import online.draran.billing.feature.settings.MoreDestination
import online.draran.billing.feature.settings.MoreScreen
import online.draran.billing.feature.settings.PrinterSettingsRoute
import online.draran.billing.feature.settings.SettingsRoute
import online.draran.billing.navigation.NavBackup
import online.draran.billing.navigation.NavBusinessProfile
import online.draran.billing.navigation.NavCounter
import online.draran.billing.navigation.NavDocuments
import online.draran.billing.feature.billing.DocFilter
import online.draran.billing.navigation.NavExpenseEditor
import online.draran.billing.navigation.NavExpenses
import online.draran.billing.navigation.NavHome
import online.draran.billing.navigation.NavInvoiceDetail
import online.draran.billing.navigation.NavInvoiceEditor
import online.draran.billing.navigation.NavInvoiceSettings
import online.draran.billing.navigation.NavItemEditor
import online.draran.billing.navigation.NavItems
import online.draran.billing.navigation.NavMore
import online.draran.billing.navigation.NavOnboarding
import online.draran.billing.navigation.NavParties
import online.draran.billing.navigation.NavPartyDetail
import online.draran.billing.navigation.NavPartyEditor
import online.draran.billing.navigation.NavPaymentEditor
import online.draran.billing.navigation.NavPayments
import online.draran.billing.navigation.NavPrinter
import online.draran.billing.navigation.NavReport
import online.draran.billing.navigation.NavReports
import online.draran.billing.navigation.NavSales
import online.draran.billing.navigation.NavSettings
import online.draran.billing.navigation.TopLevelDestination

private const val NEW_ITEM_KEY = "newItemId"

/** Root composable: bottom navigation, context-aware add button and the full nav graph. */
@Composable
fun BillingApp(
    onboarded: Boolean,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    versionName: String,
    businessType: BusinessType = BusinessType.RETAIL,
) {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentTab = TopLevelDestination.entries.firstOrNull { tab ->
        backStackEntry?.destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
    }
    var partiesTabType by remember { mutableStateOf(PartyType.CUSTOMER) }

    fun newBill(type: DocType, partyId: Long = 0) = nav.go(NavInvoiceEditor(type.name, partyId = partyId))
    fun openInvoice(id: Long) = nav.go(NavInvoiceDetail(id))

    Scaffold(
        bottomBar = {
            if (currentTab != null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    TopLevelDestination.entries.forEach { tab ->
                        val selected = tab == currentTab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.navigateToTab(tab) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.unselectedIcon, contentDescription = null) },
                            label = {
                                Text(
                                    when (tab) {
                                        TopLevelDestination.SALES -> businessType.salesTab
                                        TopLevelDestination.ITEMS -> businessType.itemsTab
                                        TopLevelDestination.PARTIES -> businessType.parties
                                        else -> stringResource(tab.label)
                                    },
                                    maxLines = 1,
                                )
                            },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            val fab: Pair<String, () -> Unit>? = when (currentTab) {
                TopLevelDestination.HOME, TopLevelDestination.SALES -> businessType.newSaleLabel to { newBill(DocType.SALE) }
                TopLevelDestination.ITEMS -> "Add ${businessType.item.lowercase()}" to { nav.go(NavItemEditor()) }
                TopLevelDestination.PARTIES -> (if (partiesTabType == PartyType.CUSTOMER) "Add ${businessType.party.lowercase()}" else "Add supplier") to { nav.go(NavPartyEditor(type = partiesTabType.name)) }
                else -> null
            }
            AnimatedVisibility(visible = fab != null, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                if (fab != null) {
                    AppFab(text = fab.first, onClick = fab.second)
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = nav,
            startDestination = if (onboarded) NavHome else NavOnboarding,
            modifier = Modifier.fillMaxSize(),
            // Tabs cross-fade; screens opened from them slide in from the right and back out again
            enterTransition = {
                if (initialState.isTab() && targetState.isTab()) fadeIn(tween(200))
                else slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(260))
            },
            exitTransition = {
                if (initialState.isTab() && targetState.isTab()) fadeOut(tween(160))
                else slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 12 } + fadeOut(tween(220))
            },
            popEnterTransition = {
                if (initialState.isTab() && targetState.isTab()) fadeIn(tween(200))
                else slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 12 } + fadeIn(tween(260))
            },
            popExitTransition = {
                if (initialState.isTab() && targetState.isTab()) fadeOut(tween(160))
                else slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 4 } + fadeOut(tween(200))
            },
        ) {
            composable<NavOnboarding> {
                OnboardingRoute(onDone = { nav.goNow(NavHome) { popUpTo<NavOnboarding> { inclusive = true } } })
            }

            // Tabs
            composable<NavHome> {
                DashboardRoute(
                    contentPadding = innerPadding,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onNavigate = { d ->
                        when (d) {
                            DashboardDestination.NEW_SALE -> newBill(DocType.SALE)
                            DashboardDestination.COUNTER -> nav.go(NavCounter)
                            DashboardDestination.PAYMENT_IN -> nav.go(NavPaymentEditor(PaymentDirection.IN.name))
                            DashboardDestination.ADD_ITEM -> nav.go(NavItemEditor())
                            DashboardDestination.PURCHASE -> newBill(DocType.PURCHASE)
                            DashboardDestination.EXPENSE -> nav.go(NavExpenseEditor())
                            DashboardDestination.ESTIMATE -> newBill(DocType.ESTIMATE)
                            DashboardDestination.REPORTS -> nav.go(NavReports)
                            DashboardDestination.TO_COLLECT, DashboardDestination.TO_PAY -> nav.go(NavReport(ReportKind.PARTY_BALANCES.name))
                            DashboardDestination.LOW_STOCK -> nav.go(NavReport(ReportKind.STOCK.name))
                            DashboardDestination.ALL_TRANSACTIONS -> nav.go(NavReport(ReportKind.DAY_BOOK.name))
                            DashboardDestination.BACKUP -> nav.go(NavBackup)
                            DashboardDestination.SETTINGS -> nav.go(NavSettings)
                            DashboardDestination.OVERDUE -> nav.go(NavDocuments(DocList.SALES.name, filter = DocFilter.OVERDUE.name))
                            DashboardDestination.ITEM_SALES -> nav.go(NavReport(ReportKind.ITEM_SALES.name))
                        }
                    },
                    onOpenRecent = { t ->
                        when (t.type) {
                            TransactionType.PAYMENT_IN, TransactionType.PAYMENT_OUT -> nav.go(NavPaymentEditor(PaymentDirection.IN.name, id = t.id))
                            TransactionType.EXPENSE -> nav.go(NavExpenseEditor(t.id))
                            else -> openInvoice(t.id)
                        }
                    },
                )
            }
            composable<NavSales> { SalesTabRoute(contentPadding = innerPadding, onOpen = ::openInvoice) }
            composable<NavItems> {
                ItemsRoute(contentPadding = innerPadding, onOpenItem = { nav.go(NavItemEditor(it)) }, onAddItem = { nav.go(NavItemEditor(barcode = it)) })
            }
            composable<NavParties> {
                PartiesRoute(
                    contentPadding = innerPadding,
                    onOpenParty = { nav.go(NavPartyDetail(it)) },
                    onAddParty = { type -> partiesTabType = type; nav.go(NavPartyEditor(type = type.name)) },
                    onTypeChanged = { partiesTabType = it },
                )
            }
            composable<NavMore> {
                MoreScreen(
                    contentPadding = innerPadding,
                    onOpen = { d ->
                        when (d) {
                            MoreDestination.COUNTER -> nav.go(NavCounter)
                            MoreDestination.PURCHASES -> nav.go(NavDocuments(DocList.PURCHASES.name))
                            MoreDestination.ESTIMATES -> nav.go(NavDocuments(DocList.ESTIMATES.name))
                            MoreDestination.PAYMENTS -> nav.go(NavPayments)
                            MoreDestination.EXPENSES -> nav.go(NavExpenses)
                            MoreDestination.REPORTS -> nav.go(NavReports)
                            MoreDestination.BACKUP -> nav.go(NavBackup)
                            MoreDestination.SETTINGS -> nav.go(NavSettings)
                        }
                    },
                )
            }

            // Items and parties
            composable<NavItemEditor> { entry ->
                val r = entry.toRoute<NavItemEditor>()
                ItemEditorRoute(
                    itemId = r.id,
                    prefillBarcode = r.barcode,
                    prefillName = r.name,
                    onBack = { nav.back() },
                    onSaved = { id ->
                        nav.previousBackStackEntry?.savedStateHandle?.set(NEW_ITEM_KEY, id)
                        nav.backNow()
                    },
                )
            }
            composable<NavPartyEditor> { entry ->
                val r = entry.toRoute<NavPartyEditor>()
                PartyEditorRoute(
                    partyId = r.id,
                    type = PartyType.valueOf(r.type),
                    prefillName = r.name,
                    onBack = { nav.back() },
                    onSaved = { id -> if (r.id == 0L) nav.goNow(NavPartyDetail(id)) { popUpTo<NavPartyEditor> { inclusive = true } } else nav.backNow() },
                )
            }
            composable<NavPartyDetail> { entry ->
                val id = entry.toRoute<NavPartyDetail>().id
                PartyDetailRoute(
                    partyId = id,
                    onBack = { nav.back() },
                    onEdit = { nav.go(NavPartyEditor(id)) },
                    onNewBill = { type -> newBill(if (type == PartyType.CUSTOMER) DocType.SALE else DocType.PURCHASE, id) },
                    onPayment = { type -> nav.go(NavPaymentEditor((if (type == PartyType.CUSTOMER) PaymentDirection.IN else PaymentDirection.OUT).name, partyId = id)) },
                    onOpenInvoice = ::openInvoice,
                    onOpenPayment = { nav.go(NavPaymentEditor(PaymentDirection.IN.name, id = it)) },
                )
            }

            // Billing
            composable<NavInvoiceEditor> { entry ->
                val r = entry.toRoute<NavInvoiceEditor>()
                val newItem by entry.newItemFlow()
                InvoiceEditorRoute(
                    type = DocType.valueOf(r.type),
                    invoiceId = r.id,
                    sourceId = r.sourceId,
                    partyId = r.partyId,
                    newItemId = newItem,
                    onBack = { nav.back() },
                    onSaved = { id ->
                        // Editing from a bill's page: go back to that page (it refreshes itself) instead of stacking a second copy
                        val cameFromDetail = nav.previousBackStackEntry?.destination?.hierarchy?.any { it.hasRoute(NavInvoiceDetail::class) } == true
                        if (r.id != 0L && cameFromDetail) nav.backNow()
                        else nav.goNow(NavInvoiceDetail(id, saved = true)) { popUpTo<NavInvoiceEditor> { inclusive = true } }
                    },
                    onCreateItem = { code -> nav.goNow(createItemRoute(code)) },
                )
            }
            composable<NavCounter> { entry ->
                val newItem by entry.newItemFlow()
                CounterRoute(
                    onBack = { nav.back() },
                    onOpenInvoice = ::openInvoice,
                    onCreateItem = { code -> nav.goNow(createItemRoute(code)) },
                    newItemId = newItem,
                )
            }
            composable<NavInvoiceDetail> { entry ->
                val r = entry.toRoute<NavInvoiceDetail>()
                InvoiceDetailRoute(
                    invoiceId = r.id,
                    justSaved = r.saved,
                    onBack = { nav.back() },
                    onEdit = { type, id -> nav.go(NavInvoiceEditor(type.name, id = id)) },
                    onCreateFrom = { type, source -> nav.go(NavInvoiceEditor(type.name, sourceId = source)) },
                    onOpenInvoice = { id -> nav.go(NavInvoiceDetail(id)) { popUpTo<NavInvoiceDetail> { inclusive = true } } },
                    onRecordPayment = { type, partyId -> nav.go(NavPaymentEditor(type.paymentDirection.name, partyId = partyId ?: 0)) },
                    onPrinterSettings = { nav.go(NavPrinter) },
                )
            }
            composable<NavDocuments> { entry ->
                val r = entry.toRoute<NavDocuments>()
                DocumentsRoute(
                    list = DocList.valueOf(r.list),
                    initialFilter = DocFilter.entries.firstOrNull { it.name == r.filter } ?: DocFilter.ALL,
                    onBack = { nav.back() },
                    onOpen = ::openInvoice,
                    onNew = { newBill(it) },
                )
            }

            // Money
            composable<NavPayments> {
                PaymentsRoute(
                    onBack = { nav.back() },
                    onOpen = { nav.go(NavPaymentEditor(PaymentDirection.IN.name, id = it)) },
                    onNew = { nav.go(NavPaymentEditor(it.name)) },
                )
            }
            composable<NavPaymentEditor> { entry ->
                val r = entry.toRoute<NavPaymentEditor>()
                PaymentEditorRoute(PaymentDirection.valueOf(r.direction), r.id, r.partyId, onBack = { nav.back() })
            }
            composable<NavExpenses> {
                ExpensesRoute(onBack = { nav.back() }, onOpen = { nav.go(NavExpenseEditor(it)) }, onNew = { nav.go(NavExpenseEditor()) })
            }
            composable<NavExpenseEditor> { entry -> ExpenseEditorRoute(entry.toRoute<NavExpenseEditor>().id, onBack = { nav.back() }) }

            // Reports
            composable<NavReports> { ReportsHubRoute(onBack = { nav.back() }, onOpen = { nav.go(NavReport(it.name)) }) }
            composable<NavReport> { entry -> ReportRoute(ReportKind.valueOf(entry.toRoute<NavReport>().kind), onBack = { nav.back() }) }

            // Settings
            composable<NavSettings> {
                SettingsRoute(
                    versionName = versionName,
                    onBack = { nav.back() },
                    onBusinessProfile = { nav.go(NavBusinessProfile) },
                    onInvoiceSettings = { nav.go(NavInvoiceSettings) },
                    onPrinter = { nav.go(NavPrinter) },
                    onBackup = { nav.go(NavBackup) },
                )
            }
            composable<NavBusinessProfile> { BusinessProfileRoute(onBack = { nav.back() }) }
            composable<NavInvoiceSettings> { InvoiceSettingsRoute(onBack = { nav.back() }) }
            composable<NavPrinter> { PrinterSettingsRoute(onBack = { nav.back() }) }
            composable<NavBackup> { BackupRoute(onBack = { nav.back() }) }
        }
    }
}

/** "name:Rice" comes from the item picker's search text; anything else is a scanned barcode. */
private fun createItemRoute(code: String) =
    if (code.startsWith("name:")) NavItemEditor(name = code.removePrefix("name:")) else NavItemEditor(barcode = code)

@Composable
private fun NavBackStackEntry.newItemFlow() = savedStateHandle.getStateFlow(NEW_ITEM_KEY, 0L).collectAsStateWithLifecycle()

private fun NavHostController.navigateToTab(tab: TopLevelDestination) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** True for the five bottom-bar tabs. */
private fun NavBackStackEntry.isTab(): Boolean =
    TopLevelDestination.entries.any { tab -> destination.hierarchy.any { it.hasRoute(tab.routeClass) } }

/**
 * Navigates only from a settled screen: a double tap, or a tap while a screen is
 * still sliding in or out, is ignored instead of opening the next screen twice.
 */
private fun NavHostController.go(route: Any, builder: androidx.navigation.NavOptionsBuilder.() -> Unit = {}) {
    if (currentBackStackEntry?.lifecycle?.currentState == androidx.lifecycle.Lifecycle.State.RESUMED) navigate(route, builder)
}

/**
 * Like [go], for a result that arrives after work has finished (a bill saved, a barcode scanned): the
 * screen may be paused for a moment then, and the move must not be dropped. The caller runs it once.
 */
private fun NavHostController.goNow(route: Any, builder: androidx.navigation.NavOptionsBuilder.() -> Unit = {}) {
    navigate(route, builder)
}

private fun NavHostController.backNow() {
    if (previousBackStackEntry != null) popBackStack()
}

/** Back, but never past the first screen: a double tap on a back arrow must not leave a blank window. */
private fun NavHostController.back() {
    if (currentBackStackEntry?.lifecycle?.currentState == androidx.lifecycle.Lifecycle.State.RESUMED && previousBackStackEntry != null) popBackStack()
}
