package online.draran.billing.core.print

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /** Opens this app's page in the phone's settings, where a permission that was refused for good can be allowed. */
    fun openAppSettings(context: Context) {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.parse("package:${context.packageName}"),
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
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
        printLock.withLock {
            try {
                check(hasPermission(context)) { "Allow the Nearby devices permission to use the printer." }
                val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
                    ?: error("This phone has no Bluetooth")
                check(adapter.isEnabled) { "Turn on Bluetooth" }
                val device = adapter.getRemoteDevice(address)
                // No discovery is ever started here, so there is nothing to cancel
                // (cancelDiscovery needs BLUETOOTH_SCAN on Android 12 and above).
                val socket = connect(
                    listOf(
                        { device.createRfcommSocketToServiceRecord(SPP) },
                        // Some low-cost printers accept only an unencrypted link
                        { device.createInsecureRfcommSocketToServiceRecord(SPP) },
                    ),
                )
                try {
                    val out = socket.outputStream
                    // Small pieces with a short pause: the buffer of a cheap printer is only a few kilobytes
                    var offset = 0
                    while (offset < bytes.size) {
                        val n = minOf(CHUNK, bytes.size - offset)
                        out.write(bytes, offset, n)
                        offset += n
                        if (offset < bytes.size) delay(15)
                    }
                    out.flush()
                    // Let the printer's buffer drain before the socket closes; longer for pictures
                    delay((bytes.size / 8L).coerceIn(400L, 6_000L))
                } finally {
                    runCatching { socket.close() }
                }
                Result.success(Unit)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private const val CHUNK = 1024

    /** One bill at a time: a double tap must not open two sockets to the same printer. */
    private val printLock = Mutex()

    @SuppressLint("MissingPermission")
    private suspend fun connect(openers: List<() -> BluetoothSocket>): BluetoothSocket {
        var failure: Exception? = null
        for ((attempt, open) in openers.withIndex()) {
            val socket = try {
                open()
            } catch (e: Exception) {
                failure = e
                continue
            }
            try {
                socket.connect()
                return socket
            } catch (e: java.io.IOException) {
                runCatching { socket.close() }
                failure = e
                if (attempt < openers.lastIndex) delay(500)
            }
        }
        throw java.io.IOException("Could not reach the printer. Check it is switched on and in range.", failure)
    }
}
