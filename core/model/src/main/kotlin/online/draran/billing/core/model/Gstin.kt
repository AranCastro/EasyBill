package online.draran.billing.core.model

/**
 * GSTIN format: 2-digit state code + 10-character PAN + entity number +
 * 'Z' (default) + check character. The check character uses the
 * base-36 algorithm published for GSTIN.
 */
object Gstin {
    private const val CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val PATTERN = Regex("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z][A-Z0-9][0-9A-Z]$")

    fun normalise(input: String): String = input.trim().uppercase()

    fun checkChar(first14: String): Char {
        var sum = 0
        first14.forEachIndexed { index, c ->
            val value = CHARS.indexOf(c)
            val product = value * (if (index % 2 == 0) 1 else 2)
            sum += product / 36 + product % 36
        }
        return CHARS[(36 - sum % 36) % 36]
    }

    fun isValid(input: String): Boolean {
        val g = normalise(input)
        if (!PATTERN.matches(g)) return false
        if (IndianStates.byCode(g.substring(0, 2)) == null) return false
        return checkChar(g.substring(0, 14)) == g[14]
    }

    /** State code from a GSTIN, or null if too short. */
    fun stateCode(input: String): String? = normalise(input).takeIf { it.length >= 2 }?.substring(0, 2)
}
