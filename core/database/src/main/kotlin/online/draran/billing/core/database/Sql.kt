package online.draran.billing.core.database

/** Shared SQL fragments. Must match DocType.stockSign / ledgerSign. */
internal object Sql {
    const val STOCK_SIGN =
        "CASE i.type WHEN 'SALE' THEN -1 WHEN 'PURCHASE' THEN 1 WHEN 'SALE_RETURN' THEN 1 WHEN 'PURCHASE_RETURN' THEN -1 ELSE 0 END"
    const val LEDGER_SIGN =
        "CASE i.type WHEN 'SALE' THEN 1 WHEN 'PURCHASE' THEN -1 WHEN 'SALE_RETURN' THEN -1 WHEN 'PURCHASE_RETURN' THEN 1 ELSE 0 END"
    const val STOCK_OF_ITEM =
        "(item.openingStock" +
            " + COALESCE((SELECT SUM(l.qty * " + STOCK_SIGN + ") FROM invoice_line l JOIN invoice i ON i.id = l.invoiceId WHERE l.itemId = item.id), 0)" +
            " + COALESCE((SELECT SUM(a.qty) FROM stock_adjustment a WHERE a.itemId = item.id), 0))"
    const val PAID_OF_INVOICE = "COALESCE((SELECT SUM(al.amount) FROM allocation al WHERE al.invoiceId = i.id), 0)"
    const val BALANCE_OF_PARTY =
        "(p.openingBalance" +
            " + COALESCE((SELECT SUM(i.total * " + LEDGER_SIGN + ") FROM invoice i WHERE i.partyId = p.id), 0)" +
            " + COALESCE((SELECT SUM(CASE pay.direction WHEN 'IN' THEN -pay.amount ELSE pay.amount END) FROM payment pay WHERE pay.partyId = p.id), 0))"
}
