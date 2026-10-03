package online.draran.billing.feature.items

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import online.draran.billing.core.data.BusinessRepository
import online.draran.billing.core.data.ItemRepository
import online.draran.billing.core.model.Business
import online.draran.billing.core.model.Item
import online.draran.billing.core.model.ItemType
import online.draran.billing.core.model.ItemWithStock
import online.draran.billing.core.model.Money
import online.draran.billing.core.model.MoneyParse
import online.draran.billing.core.model.Percent
import online.draran.billing.core.model.Qty
import online.draran.billing.core.model.StockMove
import javax.inject.Inject

enum class ItemFilter(val label: String) { ALL("All"), LOW("Low stock"), FAVOURITES("Favourites"), SERVICES("Services") }

data class ItemsUi(
    val items: List<ItemWithStock> = emptyList(),
    val totalCount: Int = 0,
    val lowCount: Int = 0,
    val stockValue: Money = Money.ZERO,
    val loading: Boolean = true,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class ItemsViewModel @Inject constructor(
    private val repository: ItemRepository,
    businessRepository: BusinessRepository,
) : ViewModel() {
    val business: StateFlow<Business> = businessRepository.business.stateIn(viewModelScope, SharingStarted.Eagerly, Business())
    val query = MutableStateFlow("")

    /** Loads the business type's sample services (skips names already saved). */
    fun addStarter() {
        val b = business.value
        viewModelScope.launch { repository.addPresets(b.type, b.gstEnabled) }
    }
    val filter = MutableStateFlow(ItemFilter.ALL)

    private val all: Flow<List<ItemWithStock>> = repository.items()
    private val searched = query.debounce(120).flatMapLatest { repository.items(it) }

    val ui: StateFlow<ItemsUi> = combine(all, searched, filter) { everything, found, f ->
        val shown = when (f) {
            ItemFilter.ALL -> found
            ItemFilter.LOW -> found.filter { it.isLow }
            ItemFilter.FAVOURITES -> found.filter { it.item.favourite }
            ItemFilter.SERVICES -> found.filter { it.item.type == ItemType.SERVICE }
        }
        ItemsUi(
            items = shown,
            totalCount = everything.size,
            lowCount = everything.count { it.isLow },
            stockValue = Money(everything.filter { it.item.tracksStock && it.stockMilli > 0 }.sumOf { it.stockMilli * it.item.purchasePrice.paise / 1000 }),
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemsUi())
}

data class ItemForm(
    val name: String = "",
    val type: ItemType = ItemType.GOODS,
    val salePrice: String = "",
    val purchasePrice: String = "",
    val taxRateBp: Int = 0,
    val taxInclusive: Boolean = false,
    val hsn: String = "",
    val unit: String = "pcs",
    val openingStock: String = "",
    val lowStock: String = "",
    val barcode: String = "",
    val code: String = "",
    val favourite: Boolean = false,
    val category: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ItemEditorViewModel @Inject constructor(
    private val repository: ItemRepository,
    private val businessRepository: BusinessRepository,
) : ViewModel() {

    var form by mutableStateOf(ItemForm())
        private set
    var showErrors by mutableStateOf(false)
        private set
    var nameTaken by mutableStateOf(false)
        private set
    var itemId by mutableStateOf(0L)
        private set
    private var original: Item? = null

    val business: StateFlow<Business> = businessRepository.business.stateIn(viewModelScope, SharingStarted.Eagerly, Business())

    private val idFlow = MutableStateFlow(0L)
    val stock: StateFlow<ItemWithStock?> = idFlow.flatMapLatest { if (it == 0L) flowOf(null) else repository.item(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val moves: StateFlow<List<StockMove>> = idFlow.flatMapLatest { if (it == 0L) emptyFlow() else repository.moves(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var started = false

    fun load(id: Long, prefillBarcode: String = "", prefillName: String = "") {
        // Once per screen: after a rotation this runs again and must not reset what was typed
        if (started && id == itemId) return
        started = true
        itemId = id
        idFlow.value = id
        viewModelScope.launch {
            val b = business.value.takeIf { it.onboarded } ?: businessRepository.get()
            if (id == 0L) {
                form = ItemForm(
                    taxInclusive = b.pricesIncludeTax, barcode = prefillBarcode, name = prefillName,
                    // Schools, salons, clinics and consultants mostly bill services
                    type = if (b.type.tracksStock) ItemType.GOODS else ItemType.SERVICE,
                )
                return@launch
            }
            val item = repository.get(id) ?: return@launch
            original = item
            form = ItemForm(
                name = item.name, type = item.type, salePrice = MoneyParse.toInput(item.salePrice).takeIf { !item.salePrice.isZero }.orEmpty(),
                purchasePrice = MoneyParse.toInput(item.purchasePrice).takeIf { !item.purchasePrice.isZero }.orEmpty(),
                taxRateBp = item.taxRateBp, taxInclusive = item.taxInclusive, hsn = item.hsn, unit = item.unit,
                openingStock = Qty.format(item.openingStockMilli).takeIf { item.openingStockMilli != 0L }.orEmpty(),
                lowStock = Qty.format(item.lowStockMilli).takeIf { item.lowStockMilli != 0L }.orEmpty(),
                barcode = item.barcode, code = item.code, favourite = item.favourite, category = item.category,
            )
        }
    }

    fun update(transform: (ItemForm) -> ItemForm) {
        form = transform(form)
        nameTaken = false
    }

    val nameError: String? get() = when {
        form.name.isBlank() -> "Enter the item name"
        nameTaken -> "An item with this name already exists"
        else -> null
    }

    fun save(onSaved: (Long) -> Unit) {
        showErrors = true
        if (form.name.isBlank()) return
        viewModelScope.launch {
            if (repository.nameTaken(form.name, itemId)) {
                nameTaken = true
                return@launch
            }
            val f = form
            val id = repository.save(
                Item(
                    id = itemId,
                    name = f.name,
                    type = f.type,
                    code = f.code,
                    barcode = f.barcode,
                    unit = f.unit.ifBlank { "pcs" },
                    hsn = f.hsn,
                    salePrice = MoneyParse.parse(f.salePrice) ?: Money.ZERO,
                    purchasePrice = MoneyParse.parse(f.purchasePrice) ?: Money.ZERO,
                    taxRateBp = f.taxRateBp,
                    taxInclusive = f.taxInclusive,
                    openingStockMilli = if (f.type == ItemType.GOODS) Qty.parse(f.openingStock) ?: 0 else 0,
                    lowStockMilli = if (f.type == ItemType.GOODS) Qty.parse(f.lowStock) ?: 0 else 0,
                    favourite = f.favourite,
                    category = f.category,
                ),
            )
            onSaved(id)
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.delete(itemId)
            onDone()
        }
    }

    fun adjust(qty: String, add: Boolean, note: String) {
        val q = Qty.parse(qty) ?: return
        viewModelScope.launch { repository.adjustStock(itemId, if (add) q else -q, note.ifBlank { if (add) "Stock added" else "Stock reduced" }) }
    }

    fun taxLabel(bp: Int) = "${Percent.format(bp)}%"
}
