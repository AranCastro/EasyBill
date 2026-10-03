package online.draran.billing.ui

import androidx.compose.animation.AnimatedVisibility
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
) {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentTab = TopLevelDestination.entries.firstOrNull { tab ->
        backStackEntry?.destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
    }
    var partiesTabType by remember { mutableStateOf(PartyType.CUSTOMER) }

    fun newBill(type: DocType, partyId: Long = 0) = nav.navigate(NavInvoiceEditor(type.name, partyId = partyId))
    fun openInvoice(id: Long) = nav.navigate(NavInvoiceDetail(id))

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
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            val fab: Pair<String, () -> Unit>? = when (currentTab) {
                TopLevelDestination.HOME, TopLevelDestination.SALES -> stringResource(R.string.new_sale) to { newBill(DocType.SALE) }
                TopLevelDestination.ITEMS -> "Add item" to { nav.navigate(NavItemEditor()) }
                TopLevelDestination.PARTIES -> "Add party" to { nav.navigate(NavPartyEditor(type = partiesTabType.name)) }
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
        ) {
            composable<NavOnboarding> {
                OnboardingRoute(onDone = { nav.navigate(NavHome) { popUpTo<NavOnboarding> { inclusive = true } } })
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
                            DashboardDestination.COUNTER -> nav.navigate(NavCounter)
                            DashboardDestination.PAYMENT_IN -> nav.navigate(NavPaymentEditor(PaymentDirection.IN.name))
                            DashboardDestination.ADD_ITEM -> nav.navigate(NavItemEditor())
                            DashboardDestination.PURCHASE -> newBill(DocType.PURCHASE)
                            DashboardDestination.EXPENSE -> nav.navigate(NavExpenseEditor())
                            DashboardDestination.ESTIMATE -> newBill(DocType.ESTIMATE)
                            DashboardDestination.REPORTS -> nav.navigate(NavReports)
                            DashboardDestination.TO_COLLECT, DashboardDestination.TO_PAY -> nav.navigate(NavReport(ReportKind.PARTY_BALANCES.name))
                            DashboardDestination.LOW_STOCK -> nav.navigate(NavReport(ReportKind.STOCK.name))
                            DashboardDestination.ALL_TRANSACTIONS -> nav.navigate(NavReport(ReportKind.DAY_BOOK.name))
                            DashboardDestination.BACKUP -> nav.navigate(NavBackup)
                            DashboardDestination.SETTINGS -> nav.navigate(NavSettings)
                        }
                    },
                    onOpenRecent = { t ->
                        when (t.type) {
                            TransactionType.PAYMENT_IN, TransactionType.PAYMENT_OUT -> nav.navigate(NavPaymentEditor(PaymentDirection.IN.name, id = t.id))
                            TransactionType.EXPENSE -> nav.navigate(NavExpenseEditor(t.id))
                            else -> openInvoice(t.id)
                        }
                    },
                )
            }
            composable<NavSales> { SalesTabRoute(contentPadding = innerPadding, onOpen = ::openInvoice) }
            composable<NavItems> {
                ItemsRoute(contentPadding = innerPadding, onOpenItem = { nav.navigate(NavItemEditor(it)) }, onAddItem = { nav.navigate(NavItemEditor(barcode = it)) })
            }
            composable<NavParties> {
                PartiesRoute(
                    contentPadding = innerPadding,
                    onOpenParty = { nav.navigate(NavPartyDetail(it)) },
                    onAddParty = { type -> partiesTabType = type; nav.navigate(NavPartyEditor(type = type.name)) },
                    onTypeChanged = { partiesTabType = it },
                )
            }
            composable<NavMore> {
                MoreScreen(
                    contentPadding = innerPadding,
                    onOpen = { d ->
                        when (d) {
                            MoreDestination.COUNTER -> nav.navigate(NavCounter)
                            MoreDestination.PURCHASES -> nav.navigate(NavDocuments(DocList.PURCHASES.name))
                            MoreDestination.ESTIMATES -> nav.navigate(NavDocuments(DocList.ESTIMATES.name))
                            MoreDestination.PAYMENTS -> nav.navigate(NavPayments)
                            MoreDestination.EXPENSES -> nav.navigate(NavExpenses)
                            MoreDestination.REPORTS -> nav.navigate(NavReports)
                            MoreDestination.BACKUP -> nav.navigate(NavBackup)
                            MoreDestination.SETTINGS -> nav.navigate(NavSettings)
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
                    onBack = { nav.popBackStack() },
                    onSaved = { id ->
                        nav.previousBackStackEntry?.savedStateHandle?.set(NEW_ITEM_KEY, id)
                        nav.popBackStack()
                    },
                )
            }
            composable<NavPartyEditor> { entry ->
                val r = entry.toRoute<NavPartyEditor>()
                PartyEditorRoute(
                    partyId = r.id,
                    type = PartyType.valueOf(r.type),
                    prefillName = r.name,
                    onBack = { nav.popBackStack() },
                    onSaved = { id -> if (r.id == 0L) nav.navigate(NavPartyDetail(id)) { popUpTo<NavPartyEditor> { inclusive = true } } else nav.popBackStack() },
                )
            }
            composable<NavPartyDetail> { entry ->
                val id = entry.toRoute<NavPartyDetail>().id
                PartyDetailRoute(
                    partyId = id,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(NavPartyEditor(id)) },
                    onNewBill = { type -> newBill(if (type == PartyType.CUSTOMER) DocType.SALE else DocType.PURCHASE, id) },
                    onPayment = { type -> nav.navigate(NavPaymentEditor((if (type == PartyType.CUSTOMER) PaymentDirection.IN else PaymentDirection.OUT).name, partyId = id)) },
                    onOpenInvoice = ::openInvoice,
                    onOpenPayment = { nav.navigate(NavPaymentEditor(PaymentDirection.IN.name, id = it)) },
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
                    onBack = { nav.popBackStack() },
                    onSaved = { id -> nav.navigate(NavInvoiceDetail(id)) { popUpTo<NavInvoiceEditor> { inclusive = true } } },
                    onCreateItem = { code -> nav.navigate(createItemRoute(code)) },
                )
            }
            composable<NavCounter> { entry ->
                val newItem by entry.newItemFlow()
                CounterRoute(
                    onBack = { nav.popBackStack() },
                    onOpenInvoice = ::openInvoice,
                    onCreateItem = { code -> nav.navigate(createItemRoute(code)) },
                    newItemId = newItem,
                )
            }
            composable<NavInvoiceDetail> { entry ->
                InvoiceDetailRoute(
                    invoiceId = entry.toRoute<NavInvoiceDetail>().id,
                    onBack = { nav.popBackStack() },
                    onEdit = { type, id -> nav.navigate(NavInvoiceEditor(type.name, id = id)) },
                    onCreateFrom = { type, source -> nav.navigate(NavInvoiceEditor(type.name, sourceId = source)) },
                    onOpenInvoice = { id -> nav.navigate(NavInvoiceDetail(id)) { popUpTo<NavInvoiceDetail> { inclusive = true } } },
                    onRecordPayment = { type, partyId -> nav.navigate(NavPaymentEditor(type.paymentDirection.name, partyId = partyId ?: 0)) },
                    onPrinterSettings = { nav.navigate(NavPrinter) },
                )
            }
            composable<NavDocuments> { entry ->
                DocumentsRoute(
                    list = DocList.valueOf(entry.toRoute<NavDocuments>().list),
                    onBack = { nav.popBackStack() },
                    onOpen = ::openInvoice,
                    onNew = { newBill(it) },
                )
            }

            // Money
            composable<NavPayments> {
                PaymentsRoute(
                    onBack = { nav.popBackStack() },
                    onOpen = { nav.navigate(NavPaymentEditor(PaymentDirection.IN.name, id = it)) },
                    onNew = { nav.navigate(NavPaymentEditor(it.name)) },
                )
            }
            composable<NavPaymentEditor> { entry ->
                val r = entry.toRoute<NavPaymentEditor>()
                PaymentEditorRoute(PaymentDirection.valueOf(r.direction), r.id, r.partyId, onBack = { nav.popBackStack() })
            }
            composable<NavExpenses> {
                ExpensesRoute(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(NavExpenseEditor(it)) }, onNew = { nav.navigate(NavExpenseEditor()) })
            }
            composable<NavExpenseEditor> { entry -> ExpenseEditorRoute(entry.toRoute<NavExpenseEditor>().id, onBack = { nav.popBackStack() }) }

            // Reports
            composable<NavReports> { ReportsHubRoute(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(NavReport(it.name)) }) }
            composable<NavReport> { entry -> ReportRoute(ReportKind.valueOf(entry.toRoute<NavReport>().kind), onBack = { nav.popBackStack() }) }

            // Settings
            composable<NavSettings> {
                SettingsRoute(
                    versionName = versionName,
                    onBack = { nav.popBackStack() },
                    onBusinessProfile = { nav.navigate(NavBusinessProfile) },
                    onInvoiceSettings = { nav.navigate(NavInvoiceSettings) },
                    onPrinter = { nav.navigate(NavPrinter) },
                    onBackup = { nav.navigate(NavBackup) },
                )
            }
            composable<NavBusinessProfile> { BusinessProfileRoute(onBack = { nav.popBackStack() }) }
            composable<NavInvoiceSettings> { InvoiceSettingsRoute(onBack = { nav.popBackStack() }) }
            composable<NavPrinter> { PrinterSettingsRoute(onBack = { nav.popBackStack() }) }
            composable<NavBackup> { BackupRoute(onBack = { nav.popBackStack() }) }
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
