package online.draran.billing.feature.items

import android.content.Context
import android.widget.Toast
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/** Google code scanner: no camera permission needed, the scan UI is provided by Play services. */
internal fun scanBarcode(context: Context, onResult: (String) -> Unit) {
    GmsBarcodeScanning.getClient(context).startScan()
        .addOnSuccessListener { barcode -> barcode.rawValue?.let(onResult) }
        .addOnFailureListener { Toast.makeText(context, "The barcode scanner is not available on this phone.", Toast.LENGTH_SHORT).show() }
}
