package online.draran.billing.core.print

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.UUID

data class PairedPrinter(val name: String, val address: String)

/**
 * Prints raw ESC/POS bytes to a paired Bluetooth thermal printer over the
 * Serial Port Profile. Works with most 58 mm / 80 mm portable printers.
 */
object BluetoothPrinter {
    private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    /** Runtime permissions needed for this Android version. */
    val permissions: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyArray()

    fun hasPermission(context: Context) = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun isAvailable(context: Context): Boolean =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter != null

    @SuppressLint("MissingPermission")
    fun pairedDevices(context: Context): List<PairedPrinter> {
        if (!hasPermission(context)) return emptyList()
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        return adapter.bondedDevices.orEmpty().map { PairedPrinter(it.name ?: it.address, it.address) }.sortedBy { it.name }
    }

    @SuppressLint("MissingPermission")
    suspend fun print(context: Context, address: String, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(hasPermission(context)) { "Allow Nearby devices permission to use the printer" }
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
                ?: error("This phone has no Bluetooth")
            check(adapter.isEnabled) { "Turn on Bluetooth" }
            val device = adapter.getRemoteDevice(address)
            adapter.cancelDiscovery()
            device.createRfcommSocketToServiceRecord(SPP).use { socket ->
                socket.connect()
                socket.outputStream.write(bytes)
                socket.outputStream.flush()
                delay(400) // let the printer buffer drain before closing
            }
        }
    }
}
