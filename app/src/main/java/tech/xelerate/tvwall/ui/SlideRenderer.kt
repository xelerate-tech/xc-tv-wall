package tech.xelerate.tvwall.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.IOException

/** Turns cached files into screen-sized bitmaps. Call off the main thread. */
object SlideRenderer {
    const val WIDTH = 1920
    const val HEIGHT = 1080

    fun pageCount(path: String): Int = openPdf(path) { it.pageCount }

    fun renderPdfPage(path: String, index: Int, width: Int = WIDTH, height: Int = HEIGHT): Bitmap =
        openPdf(path) { renderer ->
            renderer.openPage(index).use { page ->
                val scale = minOf(width.toFloat() / page.width, height.toFloat() / page.height)
                val w = (page.width * scale).toInt().coerceAtLeast(1)
                val h = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, Matrix().apply { setScale(scale, scale) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }

    fun decodeImage(path: String, width: Int = WIDTH, height: Int = HEIGHT): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Cannot decode image: $path")
        val sample = PlaybackLimits.sampleSize(bounds.outWidth, bounds.outHeight, width, height)
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException("Cannot decode image: $path")
    }

    private inline fun <T> openPdf(path: String, block: (PdfRenderer) -> T): T =
        ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use(block)
        }
}
