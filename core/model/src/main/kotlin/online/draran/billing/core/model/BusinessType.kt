package online.draran.billing.core.model

/** A starter catalogue entry for a business type. Prices are examples and editable. */
data class ServicePreset(
    val name: String,
    val rupees: Long,
    val taxRateBp: Int,
    val sac: String,
    val unit: String = "service",
    val goods: Boolean = false,
)

/**
 * Industry mode: sets the words, extra bill fields, defaults and starter
 * services. Everything stays editable; the type only chooses sensible defaults.
 */
enum class BusinessType(
    val label: String,
    val description: String,
    val party: String,
    val parties: String,
    val item: String,
    val items: String,
    val sales: String,
    /** Sale bill title when GST is off (with GST it is always "Tax Invoice"). */
    val billTitle: String,
    val customFields: List<String>,
    val tracksStock: Boolean,
    val showsDueDate: Boolean,
    val counterLabel: String,
    val presets: List<ServicePreset>,
) {
    RETAIL(
        "Shop / Retail", "Kirana, supermarket, garments, hardware, wholesale",
        "Customer", "Parties", "Item", "Items", "Sales", "Bill of Supply",
        emptyList(), tracksStock = true, showsDueDate = false, counterLabel = "Counter billing",
        presets = emptyList(),
    ),
    EDUCATION(
        "School / Coaching / Training", "Schools, tuition and coaching centres, training institutes",
        "Student", "Students", "Fee head", "Fees", "Fee receipts", "Fee Receipt",
        listOf("Roll / Admission no.", "Class / Course", "Batch / Section", "Fee period"),
        tracksStock = false, showsDueDate = true, counterLabel = "Quick fee receipt",
        presets = listOf(
            ServicePreset("Tuition fee", 2000, 0, "9992", "month"),
            ServicePreset("Admission fee", 5000, 0, "9992"),
            ServicePreset("Exam fee", 500, 0, "9992"),
            ServicePreset("Lab fee", 800, 0, "9992"),
            ServicePreset("Library fee", 300, 0, "9992"),
            ServicePreset("Transport fee", 1200, 0, "9992", "month"),
        ),
    ),
    RESEARCH(
        "Research / Consultancy / Lab", "Research projects, consulting, testing labs, GIS and survey work",
        "Client", "Clients", "Service", "Services", "Invoices", "Invoice",
        listOf("PO / Work order no.", "Project", "Service period", "Milestone"),
        tracksStock = false, showsDueDate = true, counterLabel = "Quick invoice",
        presets = listOf(
            ServicePreset("Research project fee", 50000, 1800, "9981", "project"),
            ServicePreset("Consultancy", 8000, 1800, "9983", "day"),
            ServicePreset("Data analysis", 15000, 1800, "9983"),
            ServicePreset("Report preparation", 10000, 1800, "9983"),
            ServicePreset("GIS mapping & analysis", 20000, 1800, "9983"),
            ServicePreset("Training workshop", 2500, 1800, "9983", "participant"),
        ),
    ),
    SALON(
        "Salon / Beauty / Spa", "Hair salons, barber shops, beauty parlours, spas",
        "Customer", "Customers", "Service", "Services", "Bills", "Bill",
        listOf("Stylist", "Appointment time"),
        tracksStock = false, showsDueDate = false, counterLabel = "Quick bill",
        presets = listOf(
            ServicePreset("Haircut", 150, 1800, "9997"),
            ServicePreset("Kids haircut", 120, 1800, "9997"),
            ServicePreset("Shave", 80, 1800, "9997"),
            ServicePreset("Beard trim", 100, 1800, "9997"),
            ServicePreset("Hair colour", 600, 1800, "9997"),
            ServicePreset("Hair spa", 700, 1800, "9997"),
            ServicePreset("Facial", 800, 1800, "9997"),
            ServicePreset("Head massage", 200, 1800, "9997"),
            ServicePreset("Threading", 50, 1800, "9997"),
            ServicePreset("Waxing", 300, 1800, "9997"),
            ServicePreset("Manicure", 400, 1800, "9997"),
            ServicePreset("Pedicure", 500, 1800, "9997"),
        ),
    ),
    CLINIC(
        "Clinic / Healthcare", "Clinics, diagnostic centres, physiotherapy, dental",
        "Patient", "Patients", "Service", "Services", "Bills", "Bill / Receipt",
        listOf("Doctor", "Age / Gender", "OP no."),
        tracksStock = false, showsDueDate = false, counterLabel = "Quick bill",
        presets = listOf(
            ServicePreset("Consultation", 300, 0, "9993", "visit"),
            ServicePreset("Follow-up visit", 150, 0, "9993", "visit"),
            ServicePreset("Dressing", 200, 0, "9993"),
            ServicePreset("Injection", 100, 0, "9993"),
            ServicePreset("ECG", 400, 0, "9993"),
        ),
    ),
    REPAIR(
        "Repair / Service centre", "Mobile, electronics, vehicle and appliance service",
        "Customer", "Customers", "Item", "Services & parts", "Job invoices", "Job Invoice",
        listOf("Job card no.", "Device / Vehicle", "Serial / Reg. no.", "Technician"),
        tracksStock = true, showsDueDate = true, counterLabel = "Quick job bill",
        presets = listOf(
            ServicePreset("Inspection charge", 200, 1800, "9987"),
            ServicePreset("Labour charge", 500, 1800, "9987", "hour"),
            ServicePreset("Service charge", 300, 1800, "9987"),
        ),
    ),
    PROFESSIONAL(
        "Freelancer / Professional", "Designers, developers, architects, tutors, consultants",
        "Client", "Clients", "Service", "Services", "Invoices", "Invoice",
        listOf("Project", "Reference / PO no."),
        tracksStock = false, showsDueDate = true, counterLabel = "Quick invoice",
        presets = listOf(
            ServicePreset("Professional fees", 10000, 1800, "9983"),
            ServicePreset("Hourly consulting", 1500, 1800, "9983", "hour"),
            ServicePreset("Design work", 5000, 1800, "9983"),
        ),
    ),
    ;

    /** Short bottom-bar labels: "Fee receipts" -> "Receipts", "Services & parts" -> "Services". */
    val salesTab: String get() = sales.substringAfterLast(' ').replaceFirstChar { it.uppercase() }
    val itemsTab: String get() = items.substringBefore(" &")

    /** Main add-bill button, e.g. "New Sale", "New Fee Receipt", "New Invoice". */
    val newSaleLabel: String get() = if (this == RETAIL) "New Sale" else "New " + billTitle.substringBefore(" /")

    companion object {
        fun of(name: String?): BusinessType = entries.firstOrNull { it.name == name } ?: RETAIL
    }
}

/** Packs label/value pairs into one database column. */
object CustomFields {
    private const val PAIR = '\u001E'
    private const val SEP = '\u001F'

    fun encodeLabels(labels: List<String>) = labels.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(PAIR.toString())
    fun decodeLabels(text: String): List<String> = if (text.isBlank()) emptyList() else text.split(PAIR).filter { it.isNotBlank() }

    fun encode(values: List<Pair<String, String>>) = values.filter { it.second.isNotBlank() }
        .joinToString(PAIR.toString()) { "${it.first.trim()}$SEP${it.second.trim()}" }

    fun decode(text: String): List<Pair<String, String>> = if (text.isBlank()) emptyList() else text.split(PAIR).mapNotNull {
        val parts = it.split(SEP, limit = 2)
        if (parts.size == 2 && parts[1].isNotBlank()) parts[0] to parts[1] else null
    }
}
