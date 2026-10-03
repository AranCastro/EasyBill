package online.draran.billing.feature.billing

import android.content.Context
import android.widget.Toast
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/** Google code scanner: no camera permission needed. */
internal fun scanBarcode(context: Context, onResult: (String) -> Unit) {
    GmsBarcodeScanning.getClient(context).startScan()
        .addOnSuccessListener { barcode -> barcode.rawValue?.let(onResult) }
        .addOnFailureListener { Toast.makeText(context, "Scanner not available on this phone", Toast.LENGTH_SHORT).show() }
}
