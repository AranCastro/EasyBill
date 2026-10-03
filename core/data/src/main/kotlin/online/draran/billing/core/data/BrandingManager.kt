package online.draran.billing.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Stores the business logo and the authorised signature as PNG files in
 * files/branding. File names are saved in the business profile.
 */
@Singleton
class BrandingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val businessRepository: BusinessRepository,
) {
    val dir: File get() = File(context.filesDir, DIR).apply { mkdirs() }

    private val _version = MutableStateFlow(0L)

    /** Changes whenever the logo or signature changes, so screens reload images. */
    val version: StateFlow<Long> = _version.asStateFlow()

    fun logo(fileName: String): Bitmap? = load(fileName)
    fun signature(fileName: String): Bitmap? = load(fileName)

    /** Main colours of the logo for bill colour suggestions, most used first. */
    fun logoColours(fileName: String): List<Int> = logo(fileName)?.let { coloursOf(it) }.orEmpty()

    private fun load(fileName: String): Bitmap? =
        fileName.takeIf { it.isNotBlank() }?.let { File(dir, it) }?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    /** Copies a picked image as the logo (max 600 px, transparency kept). */
    suspend fun setLogo(uri: Uri) = withContext(Dispatchers.IO) {
        val bitmap = decode(uri, MAX_LOGO) ?: error("Could not read this image")
        save(LOGO, bitmap)
        val current = businessRepository.get()
        // First logo sets the bill colour, unless one was already chosen
        val colour = if (current.billColor == 0) coloursOf(bitmap).firstOrNull() ?: 0 else current.billColor
        businessRepository.save(current.copy(logoFile = LOGO, billColor = colour))
        _version.value++
    }

    /** Uses a photo of a signature; the paper background is made transparent. */
    suspend fun setSignaturePhoto(uri: Uri) = withContext(Dispatchers.IO) {
        val bitmap = decode(uri, MAX_SIGNATURE) ?: error("Could not read this image")
        setSignature(cleanSignature(bitmap))
    }

    /** Saves a signature drawn on screen (already transparent). */
    suspend fun setSignature(bitmap: Bitmap) = withContext(Dispatchers.IO) {
        save(SIGNATURE, trim(bitmap))
        businessRepository.save(businessRepository.get().copy(signatureFile = SIGNATURE))
        _version.value++
    }

    suspend fun removeLogo() = withContext(Dispatchers.IO) {
        File(dir, LOGO).delete()
        businessRepository.save(businessRepository.get().copy(logoFile = ""))
        _version.value++
    }

    suspend fun removeSignature() = withContext(Dispatchers.IO) {
        File(dir, SIGNATURE).delete()
        businessRepository.save(businessRepository.get().copy(signatureFile = ""))
        _version.value++
    }

    /** Called after a restore replaced the files. */
    fun notifyChanged() {
        _version.value++
    }

    private fun save(name: String, bitmap: Bitmap) {
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun decode(uri: Uri, maxSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSize) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        // Phone camera photos are often stored sideways with a rotation tag
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        return scaleDown(upright(decoded, orientation), maxSize)
    }

    companion object {
        const val DIR = "branding"
        const val LOGO = "logo.png"
        const val SIGNATURE = "signature.png"
        private const val MAX_LOGO = 600
        private const val MAX_SIGNATURE = 900

        /** Turns a decoded photo upright according to its EXIF orientation tag. */
        fun upright(bitmap: Bitmap, orientation: Int): Bitmap {
            val m = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
                else -> return bitmap
            }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        }

        /** Samples a 64 px thumbnail; enough for the main colours and fast on any phone. */
        fun coloursOf(bitmap: Bitmap): List<Int> {
            val small = scaleDown(bitmap, 64)
            val pixels = IntArray(small.width * small.height)
            small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
            return online.draran.billing.core.model.BillColors.fromLogo(pixels)
        }

        fun scaleDown(bitmap: Bitmap, maxSize: Int): Bitmap {
            val largest = max(bitmap.width, bitmap.height)
            if (largest <= maxSize) return bitmap
            val f = maxSize.toFloat() / largest
            return Bitmap.createScaledBitmap(bitmap, (bitmap.width * f).toInt().coerceAtLeast(1), (bitmap.height * f).toInt().coerceAtLeast(1), true)
        }

        /**
         * Turns light paper pixels transparent and darkens the ink, so a phone
         * photo of a signature sits cleanly on the bill.
         */
        fun cleanSignature(source: Bitmap): Bitmap {
            val w = source.width
            val h = source.height
            val pixels = IntArray(w * h)
            source.copy(Bitmap.Config.ARGB_8888, false).getPixels(pixels, 0, w, 0, 0, w, h)
            // Estimate paper brightness from the image average, so grey photos still work
            var sum = 0L
            for (p in pixels) sum += luminance(p)
            val paper = (sum / pixels.size).toInt()
            val threshold = (paper * 0.78).toInt().coerceIn(60, 200)
            for (i in pixels.indices) {
                val l = luminance(pixels[i])
                pixels[i] = if (l >= threshold) {
                    Color.TRANSPARENT
                } else {
                    // Darker ink is more opaque; keep it a deep blue-black
                    val alpha = (255 * (threshold - l) / threshold.toFloat() * 1.6f).toInt().coerceIn(90, 255)
                    Color.argb(alpha, 20, 24, 60)
                }
            }
            return trim(Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888))
        }

        /**
         * Draws finger strokes (x, y pairs in pixels) on a transparent bitmap of
         * the pad's size, in the same blue-black ink as cleaned photos.
         */
        fun drawStrokes(strokes: List<List<Pair<Float, Float>>>, width: Int, height: Int, strokeWidth: Float): Bitmap {
            val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(20, 24, 60)
                style = android.graphics.Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
            }
            strokes.filter { it.size > 1 }.forEach { pts ->
                val path = android.graphics.Path()
                path.moveTo(pts[0].first, pts[0].second)
                for (i in 1 until pts.size) {
                    val (px, py) = pts[i - 1]
                    val (x, y) = pts[i]
                    path.quadTo(px, py, (px + x) / 2f, (py + y) / 2f)
                }
                path.lineTo(pts.last().first, pts.last().second)
                canvas.drawPath(path, paint)
            }
            return bitmap
        }

        private fun luminance(p: Int) = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000

        /** Crops transparent margins around the drawing. */
        fun trim(bitmap: Bitmap): Bitmap {
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            var minX = w; var minY = h; var maxX = -1; var maxY = -1
            for (y in 0 until h) for (x in 0 until w) {
                if (Color.alpha(pixels[y * w + x]) > 24) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
            if (maxX < 0) return bitmap
            val pad = 6
            val left = (minX - pad).coerceAtLeast(0)
            val top = (minY - pad).coerceAtLeast(0)
            val right = (maxX + pad).coerceAtMost(w - 1)
            val bottom = (maxY + pad).coerceAtMost(h - 1)
            return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
        }
    }
}
