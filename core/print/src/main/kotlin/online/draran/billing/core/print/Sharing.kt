package online.draran.billing.core.print

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/** Sharing, WhatsApp, calling and system printing helpers. */
object Sharing {
    /** Folder for files being shared. Anything older than a day is deleted, so old bills do not pile up on the phone. */
    fun sharedDir(context: Context) = File(context.cacheDir, "shared").apply {
        mkdirs()
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        listFiles()?.filter { it.isFile && it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    fun safeName(name: String) = name.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifEmpty { "document" }

    private fun uri(context: Context, file: File): Uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)

    /** Opens the share sheet for a file. [whatsAppPhone] targets WhatsApp directly when set. */
    fun shareFile(context: Context, file: File, mime: String, text: String = "", whatsApp: Boolean = false) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri(context, file))
            if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (whatsApp) {
            for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
                try {
                    context.startActivity(Intent(intent).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return
                } catch (_: ActivityNotFoundException) {
                }
            }
        }
        context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Opens a WhatsApp chat with a pre-filled message (no paid SMS gateway needed). */
    fun whatsAppMessage(context: Context, phone: String, message: String) {
        val url = "https://wa.me/${whatsAppNumber(phone)}?text=" + Uri.encode(message)
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            shareText(context, message)
        }
    }

    /** wa.me needs the country code: "98765 43210", "098765 43210" and "+91 98765 43210" all become 919876543210. */
    fun whatsAppNumber(phone: String): String =
        phone.filter { it.isDigit() }.trimStart('0').let { if (it.length == 10) "91$it" else it }

    fun dial(context: Context, phone: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
        }
    }

    /** Sends a PDF to the Android print system (Wi-Fi printers, Save as PDF, printer apps). */
    fun printPdf(context: Context, file: File, jobName: String) {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print(jobName, object : PrintDocumentAdapter() {
            override fun onLayout(old: PrintAttributes?, new: PrintAttributes, signal: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?) {
                if (signal?.isCanceled == true) return callback.onLayoutCancelled()
                callback.onLayoutFinished(PrintDocumentInfo.Builder(file.name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true)
            }

            override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, signal: CancellationSignal?, callback: WriteResultCallback) {
                try {
                    file.inputStream().use { input -> FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) } }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                }
            }
        }, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
    }
}
