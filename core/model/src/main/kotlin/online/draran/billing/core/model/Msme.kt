package online.draran.billing.core.model

/** Enterprise class on the Udyam certificate. */
enum class MsmeCategory(val label: String) {
    MICRO("Micro"),
    SMALL("Small"),
    MEDIUM("Medium"),
    ;

    /** Section 15 of the MSMED Act, 2006 (time limit for buyers' payments) covers micro and small suppliers only. */
    val hasPaymentProtection: Boolean get() = this != MEDIUM

    companion object {
        fun of(name: String?): MsmeCategory? = entries.firstOrNull { it.name == name }
    }
}

/** Udyam (MSME) registration number, e.g. UDYAM-TN-02-0012345. */
object Udyam {
    private val pattern = Regex("^UDYAM-[A-Z]{2}-\\d{2}-\\d{7}$")

    /** Uppercase, spaces removed, so "udyam tn 02 0012345" style typing still matches after hyphens are added. */
    fun normalise(text: String): String = text.trim().uppercase().replace(" ", "")

    fun isValid(text: String): Boolean = pattern.matches(normalise(text))
}
