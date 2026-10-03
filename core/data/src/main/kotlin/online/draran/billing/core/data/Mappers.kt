package online.draran.billing.core.data

import online.draran.billing.core.database.BusinessEntity
import online.draran.billing.core.database.ExpenseEntity
import online.draran.billing.core.database.InvoiceEntity
import online.draran.billing.core.database.InvoiceLineEntity
import online.draran.billing.core.database.InvoiceSummaryRow
import online.draran.billing.core.database.ItemEntity
import online.draran.billing.core.database.ItemStockRow
import online.draran.billing.core.database.PartyBalanceRow
import online.draran.billing.core.database.PartyEntity
import online.draran.billing.core.database.PaymentEntity
import online.draran.billing.core.model.BillTotals
import online.draran.billing.core.model.Business
import online.draran.billing.core.model.DocType
import online.draran.billing.core.model.Expense
import online.draran.billing.core.model.Invoice
import online.draran.billing.core.model.InvoiceLine
import online.draran.billing.core.model.InvoiceSummary
import online.draran.billing.core.model.Item
import online.draran.billing.core.model.ItemWithStock
import online.draran.billing.core.model.Money
import online.draran.billing.core.model.Party
import online.draran.billing.core.model.PartyWithBalance
import online.draran.billing.core.model.Payment
import online.draran.billing.core.model.TaxEngine
import java.time.LocalDate

internal fun Long.toDate(): LocalDate = LocalDate.ofEpochDay(this)
internal fun LocalDate.toDay(): Long = toEpochDay()

internal fun BusinessEntity.toModel() = Business(
    name = name, ownerName = ownerName, phone = phone, email = email, address = address,
    stateCode = stateCode, gstEnabled = gstEnabled, gstin = gstin, upiId = upiId, bankDetails = bankDetails,
    terms = terms, roundOff = roundOff, showUpiQr = showUpiQr, pricesIncludeTax = pricesIncludeTax,
    thermalWidthMm = thermalWidthMm, printerAddress = printerAddress, printerName = printerName,
    prefixes = decodePrefixes(prefixes), onboarded = onboarded,
)

internal fun Business.toEntity() = BusinessEntity(
    name = name.trim(), ownerName = ownerName.trim(), phone = phone.trim(), email = email.trim(),
    address = address.trim(), stateCode = stateCode, gstEnabled = gstEnabled, gstin = gstin.trim().uppercase(),
    upiId = upiId.trim(), bankDetails = bankDetails.trim(), terms = terms.trim(), roundOff = roundOff,
    showUpiQr = showUpiQr, pricesIncludeTax = pricesIncludeTax, thermalWidthMm = thermalWidthMm,
    printerAddress = printerAddress, printerName = printerName, prefixes = encodePrefixes(prefixes),
    onboarded = onboarded,
)

private fun encodePrefixes(map: Map<DocType, String>) = map.entries.joinToString(";") { "${it.key.name}=${it.value}" }

private fun decodePrefixes(text: String): Map<DocType, String> {
    val parsed = text.split(";").mapNotNull { part ->
        val (k, v) = part.split("=", limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
        DocType.entries.firstOrNull { it.name == k }?.let { it to v }
    }.toMap()
    return DocType.entries.associateWith { parsed[it] ?: it.defaultPrefix }
}

internal fun PartyEntity.toModel() = Party(
    id = id, name = name, type = type, phone = phone, gstin = gstin, stateCode = stateCode,
    address = address, openingBalance = Money(openingBalance),
)

internal fun Party.toEntity(createdAt: Long) = PartyEntity(
    id = id, name = name.trim(), type = type, phone = phone.trim(), gstin = gstin.trim().uppercase(),
    stateCode = stateCode, address = address.trim(), openingBalance = openingBalance.paise, createdAt = createdAt,
)

internal fun PartyBalanceRow.toModel() = PartyWithBalance(party.toModel(), Money(balance), lastActivity?.toDate())

internal fun ItemEntity.toModel() = Item(
    id = id, name = name, type = type, code = code, barcode = barcode, unit = unit, hsn = hsn,
    salePrice = Money(salePrice), purchasePrice = Money(purchasePrice), taxRateBp = taxRateBp,
    taxInclusive = taxInclusive, openingStockMilli = openingStock, lowStockMilli = lowStock,
    favourite = favourite, category = category,
)

internal fun Item.toEntity(createdAt: Long) = ItemEntity(
    id = id, name = name.trim(), type = type, code = code.trim(), barcode = barcode.trim(), unit = unit.trim(),
    hsn = hsn.trim(), salePrice = salePrice.paise, purchasePrice = purchasePrice.paise, taxRateBp = taxRateBp,
    taxInclusive = taxInclusive, openingStock = openingStockMilli, lowStock = lowStockMilli,
    favourite = favourite, category = category.trim(), createdAt = createdAt,
)

internal fun ItemStockRow.toModel() = ItemWithStock(item.toModel(), stock)

internal fun InvoiceLineEntity.toModel() = InvoiceLine(
    id = id, itemId = itemId, name = name, hsn = hsn, unit = unit, qtyMilli = qty, rate = Money(rate),
    discountBp = discountBp, taxRateBp = taxRateBp, taxInclusive = taxInclusive, costRate = Money(costRate),
)

internal fun InvoiceEntity.toModel(lines: List<InvoiceLineEntity>, paid: Long): Invoice {
    val modelLines = lines.map { it.toModel() }
    // Recompute from lines for per-line amounts and slabs; stored totals are authoritative for the header.
    val computed: BillTotals = TaxEngine.bill(modelLines.map { it.toInput() }, interState, gstEnabled, roundOffEnabled)
    return Invoice(
        id = id, type = type, number = number, seq = seq, date = date.toDate(), dueDate = dueDate?.toDate(),
        partyId = partyId, partyName = partyName, partyPhone = partyPhone, partyGstin = partyGstin,
        partyAddress = partyAddress, placeOfSupply = placeOfSupply, interState = interState, gstEnabled = gstEnabled,
        lines = modelLines, totals = computed, notes = notes, paid = Money(paid), convertedFromId = convertedFromId,
        createdAt = createdAt,
    )
}

internal fun InvoiceSummaryRow.toModel() = InvoiceSummary(id, type, number, date.toDate(), partyName, Money(total), Money(paid))

internal fun PaymentEntity.toModel() = Payment(
    id = id, direction = direction, number = number, partyId = partyId, partyName = partyName,
    date = date.toDate(), amount = Money(amount), mode = mode, reference = reference, note = note, invoiceId = invoiceId,
)

internal fun ExpenseEntity.toModel() = Expense(id, category, date.toDate(), Money(amount), mode, note)
