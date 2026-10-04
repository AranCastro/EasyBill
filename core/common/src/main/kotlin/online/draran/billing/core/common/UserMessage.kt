package online.draran.billing.core.common

/**
 * A message that is fine to show on screen. The app's own plain messages (thrown with
 * require() or error()) pass through; technical ones from the database, files or the
 * system are replaced by [fallback], so nobody sees text such as "SQLiteConstraintException".
 */
fun Throwable.userMessage(fallback: String): String {
    val text = message?.trim().orEmpty()
    val own = (this is IllegalArgumentException || this is IllegalStateException) && this !is NumberFormatException
    val technical = text.contains("SQL", ignoreCase = true) || text.contains("Exception") || text.contains("java.") || text.contains("android.")
    return if (own && text.isNotEmpty() && !technical) text else fallback
}
