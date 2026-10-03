package online.draran.billing.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Data saved by v1.0.0 (schema 1) must survive the upgrade to schema 2. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MigrationTest {

    // Under Robolectric the merged test assets (exported schemas) live in the app context
    private val instrumentation = object : android.app.Instrumentation() {
        override fun getContext(): android.content.Context = androidx.test.core.app.ApplicationProvider.getApplicationContext()
        override fun getTargetContext(): android.content.Context = getContext()
    }

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, BillingDatabase::class.java)

    @Test fun migrate1To2KeepsData() {
        helper.createDatabase("migration-test", 1).apply {
            execSQL(
                "INSERT INTO business (id, name, ownerName, phone, email, address, stateCode, gstEnabled, gstin, upiId, bankDetails, terms, roundOff, showUpiQr, pricesIncludeTax, thermalWidthMm, printerAddress, printerName, prefixes, onboarded) " +
                    "VALUES (1, 'Sharma Store', '', '9840012345', '', 'Chennai', '33', 0, '', '', '', 'Thanks', 1, 1, 0, 58, '', '', 'SALE=INV-', 1)",
            )
            execSQL(
                "INSERT INTO invoice (id, type, number, seq, date, dueDate, partyId, partyName, partyPhone, partyGstin, partyAddress, placeOfSupply, interState, gstEnabled, roundOffEnabled, subtotal, discount, taxable, cgst, sgst, igst, roundOff, total, notes, convertedFromId, createdAt) " +
                    "VALUES (1, 'SALE', 'INV-0001', 1, 20000, NULL, NULL, 'Cash Customer', '', '', '', '33', 0, 0, 1, 10000, 0, 10000, 0, 0, 0, 0, 10000, '', NULL, 0)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate("migration-test", 2, true)
        db.query("SELECT name, businessType, logoFile, printLogoOnReceipt FROM business").use { c ->
            c.moveToFirst()
            assertEquals("Sharma Store", c.getString(0))
            assertEquals("RETAIL", c.getString(1))
            assertEquals("", c.getString(2))
            assertEquals(1, c.getInt(3))
        }
        db.query("SELECT number, total, customFields FROM invoice").use { c ->
            c.moveToFirst()
            assertEquals("INV-0001", c.getString(0))
            assertEquals(10000L, c.getLong(1))
            assertEquals("", c.getString(2))
        }
    }

    /** A 1.0.0 database opened by 1.2: runs 1 -> 2 -> 3 and keeps the data. */
    @Test fun migrate1To3KeepsData() {
        helper.createDatabase("migration-test-3", 1).apply {
            execSQL(
                "INSERT INTO business (id, name, ownerName, phone, email, address, stateCode, gstEnabled, gstin, upiId, bankDetails, terms, roundOff, showUpiQr, pricesIncludeTax, thermalWidthMm, printerAddress, printerName, prefixes, onboarded) " +
                    "VALUES (1, 'Sharma Store', '', '9840012345', '', 'Chennai', '33', 0, '', '', '', 'Thanks', 1, 1, 0, 58, '', '', 'SALE=INV-', 1)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate("migration-test-3", 3, true)
        db.query("SELECT name, businessType, udyamNumber, msmeCategory, printMsmeNote FROM business").use { c ->
            c.moveToFirst()
            assertEquals("Sharma Store", c.getString(0))
            assertEquals("RETAIL", c.getString(1))
            assertEquals("", c.getString(2))
            assertEquals("", c.getString(3))
            assertEquals(1, c.getInt(4))
        }
    }
}
