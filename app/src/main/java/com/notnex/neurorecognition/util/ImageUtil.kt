package com.notnex.neurorecognition.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import java.io.File
import kotlin.math.abs

object ImageUtil {

    fun saveBitmap(context: Context, bitmap: Bitmap, filename: String) {
        val file = File(context.filesDir, "$filename.jpeg")
        if (file.exists()) {
            file.delete()
        }
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }
    }

    fun storePixels(bitmap: Bitmap, array: IntArray) {
        bitmap.getPixels(array, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    fun getTransformMatrix(
            applyRotation: Int,
            srcWidth: Int, srcHeight: Int,
            dstWidth: Int, dstHeight: Int
    ): Matrix {
        val matrix = Matrix()

        if (applyRotation != 0) {
            matrix.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f)
            matrix.postRotate(applyRotation.toFloat())
        }

        val transpose = (abs(applyRotation) + 90) % 180 == 0

        val inWidth = if (transpose) srcHeight else srcWidth
        val inHeight = if (transpose) srcWidth else srcHeight

        if (inWidth != dstWidth || inHeight != dstHeight) {
            val scaleFactorX = dstWidth / inWidth.toFloat()
            val scaleFactorY = dstHeight / inHeight.toFloat()

            matrix.postScale(scaleFactorX, scaleFactorY)
        }

        if (applyRotation != 0) {
            matrix.postTranslate(dstWidth / 2.0f, dstHeight / 2.0f)
        }

        return matrix
    }
}