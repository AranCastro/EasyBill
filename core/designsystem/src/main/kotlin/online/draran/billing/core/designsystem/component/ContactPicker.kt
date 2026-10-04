package online.draran.billing.core.designsystem.component

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/** A person chosen from the phone's contact list. */
data class PickedContact(val name: String, val phone: String)

/**
 * Opens the phone's own contact list and hands back the name and mobile number of the contact chosen.
 * The app does not ask for the contacts permission: Android gives it only the one number that was tapped.
 * Returns the function that opens the list.
 */
@Composable
fun rememberContactPicker(onPicked: (PickedContact) -> Unit): () -> Unit {
    val context = LocalContext.current
    val latest = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        readContact(context, uri)?.let { latest.value(it) }
            ?: Toast.makeText(context, "That contact could not be read.", Toast.LENGTH_SHORT).show()
    }
    return {
        try {
            launcher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No contacts app was found on this phone.", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun readContact(context: Context, uri: Uri): PickedContact? = runCatching {
    val columns = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
    context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val name = cursor.getString(0).orEmpty().trim()
        val number = cleanPhone(cursor.getString(1).orEmpty())
        if (name.isEmpty() && number.isEmpty()) null else PickedContact(name, number)
    }
}.getOrNull()

/**
 * A mobile number as the app keeps it: digits only, without the country code 91 or the leading 0 that
 * contacts often carry ("+91 98765 43210", "098765 43210" and "98765-43210" all become 9876543210).
 * Numbers from other countries keep all their digits.
 */
fun cleanPhone(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    return when {
        digits.length == 12 && digits.startsWith("91") -> digits.drop(2)
        digits.length == 11 && digits.startsWith("0") -> digits.drop(1)
        digits.length == 13 && digits.startsWith("091") -> digits.drop(3)
        else -> digits
    }
}
