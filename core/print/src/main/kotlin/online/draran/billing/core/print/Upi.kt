package online.draran.billing.core.print

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import online.draran.billing.core.model.Money
import java.net.URLEncoder
import java.math.BigDecimal

object Upi {
    /** Standard UPI deep link understood by every UPI app (GPay, PhonePe, Paytm, BHIM...). */
    fun link(upiId: String, payeeName: String, amount: Money?, note: String): String {
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        val am = amount?.takeIf { it.paise > 0 }?.let { "&am=" + BigDecimal.valueOf(it.paise, 2).toPlainString() }.orEmpty()
        // "@" stays as it is in the payee address, as in the UPI linking specification
        return "upi://pay?pa=${enc(upiId.trim()).replace("%40", "@")}&pn=${enc(payeeName)}$am&cu=INR&tn=${enc(note)}"
    }

    fun isValidId(upiId: String) = upiId.trim().matches(Regex("^[A-Za-z0-9.\\-_]{2,256}@[A-Za-z][A-Za-z0-9.\\-]{1,64}$"))
}

object QrCode {
    fun bitmap(text: String, sizePx: Int): Bitmap {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, mapOf(EncodeHintType.MARGIN to 4) /* the quiet zone the QR standard asks for */)
        val pixels = IntArray(sizePx * sizePx) { i -> if (matrix[i % sizePx, i / sizePx]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
    }
}
