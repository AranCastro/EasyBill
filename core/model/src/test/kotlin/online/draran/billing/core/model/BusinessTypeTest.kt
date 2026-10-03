package online.draran.billing.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BusinessTypeTest {

    @Test fun shortLabelsFitTheBottomBar() {
        assertEquals("Receipts", BusinessType.EDUCATION.salesTab)
        assertEquals("Invoices", BusinessType.REPAIR.salesTab)
        assertEquals("Services", BusinessType.REPAIR.itemsTab)
        assertEquals("Sales", BusinessType.RETAIL.salesTab)
        BusinessType.entries.forEach { assertTrue(it.name, it.salesTab.length <= 9 && it.itemsTab.length <= 9) }
    }

    @Test fun billTitlesFollowGstRules() {
        assertEquals("New Sale", BusinessType.RETAIL.newSaleLabel)
        assertEquals("New Fee Receipt", BusinessType.EDUCATION.newSaleLabel)
        assertEquals("New Bill", BusinessType.CLINIC.newSaleLabel)
        val school = Business(type = BusinessType.EDUCATION)
        assertEquals("Fee Receipt", school.saleTitle())
        assertEquals("Fee Receipt", school.docShortTitle(DocType.SALE))
        assertEquals("Estimate", school.docTitle(DocType.ESTIMATE))
        // A GST-registered business must title sale bills "Tax Invoice"
        assertEquals("Tax Invoice", school.copy(gstEnabled = true).docTitle(DocType.SALE))
        assertEquals("Sale Invoice", Business().docTitle(DocType.SALE))
    }

    @Test fun presetsAreUniqueAndUseServiceCodes() {
        BusinessType.entries.forEach { type ->
            assertEquals(type.name, type.presets.size, type.presets.map { it.name }.toSet().size)
            type.presets.forEach { assertTrue("${it.name} SAC ${it.sac}", it.sac.startsWith("99") && it.sac.length == 4) }
            assertTrue(type.customFields.size <= 4)
        }
        assertEquals(BusinessType.RETAIL, BusinessType.of("UNKNOWN"))
    }

    @Test fun udyamNumbersAndMsmeNote() {
        assertTrue(Udyam.isValid("UDYAM-TN-02-0012345"))
        assertTrue(Udyam.isValid(" udyam-tn-02-0012345 "))
        assertTrue(!Udyam.isValid("UDYAM-TN-2-0012345"))
        assertTrue(!Udyam.isValid("UDYAM-TN-02-001234"))
        assertTrue(!Udyam.isValid("UAM-TN-02-0012345"))
        val b = Business(udyamNumber = "UDYAM-TN-02-0012345", msmeCategory = MsmeCategory.SMALL)
        assertEquals("Udyam: UDYAM-TN-02-0012345 (Small)", b.udyamLine())
        assertTrue(b.msmeNote()!!.contains("Section 15 of the MSMED Act, 2006"))
        assertEquals(null, b.copy(msmeCategory = MsmeCategory.MEDIUM).msmeNote())
        assertEquals(null, b.copy(msmeCategory = null).msmeNote())
        assertEquals(null, b.copy(printMsmeNote = false).msmeNote())
        assertEquals(null, Business().udyamLine())
    }

    @Test fun customFieldsRoundTrip() {
        val values = listOf("Stylist" to "Kumar", "Appointment time" to "4:30 pm", "Empty" to "")
        assertEquals(values.take(2), CustomFields.decode(CustomFields.encode(values)))
        assertEquals(listOf("Roll no.", "Class"), CustomFields.decodeLabels(CustomFields.encodeLabels(listOf("Roll no.", " ", "Class"))))
        assertEquals(emptyList<Pair<String, String>>(), CustomFields.decode(""))
    }
}
