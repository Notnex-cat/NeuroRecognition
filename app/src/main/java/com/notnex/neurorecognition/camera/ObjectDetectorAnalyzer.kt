package com.notnex.neurorecognition.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.notnex.neurorecognition.detection.DetectionResult
import com.notnex.neurorecognition.detection.ObjectDetector
import com.notnex.neurorecognition.util.ImageUtil
import com.notnex.neurorecognition.util.YuvToRgbConverter
import java.util.concurrent.atomic.AtomicInteger

class ObjectDetectorAnalyzer(
    private val context: Context,
    private val config: Config,
    private val onDetectionResult: (Result) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "ObjectDetectorAnalyzer"
    }

    private val iterationCounter = AtomicInteger(0)


    private val yuvToRgbConverter = YuvToRgbConverter(context)
    private val uiHandler = Handler(Looper.getMainLooper())
    private var inputArray = IntArray(config.inputSize * config.inputSize)

    private var rgbBitmap: Bitmap? = null
    private var resizedBitmap = Bitmap.createBitmap(config.inputSize, config.inputSize, Bitmap.Config.ARGB_8888)

    // Модель загружается ОДИН раз
    private var objectDetector: ObjectDetector = ObjectDetector(
        assetManager = context.assets,
        isModelQuantized = config.isQuantized,
        inputSize = config.inputSize,
        labelFilename = config.labelsFile,
        modelFilename = config.modelFile,
        numDetections = config.numDetection,
        minimumConfidence = config.minimumConfidence,
        numThreads = 4, // или 1, если слабое устройство
        useNnapi = true // ускорение через NNAPI (если поддерживается)
    )

    override fun analyze(image: ImageProxy) {
        try {
            val rotationDegrees = image.imageInfo.rotationDegrees

            val rgbBitmap = getArgbBitmap(image.width, image.height)
            yuvToRgbConverter.yuvToRgb(image, rgbBitmap)

            val transformation = getTransformation(rotationDegrees, image.width, image.height)

            Canvas(resizedBitmap).drawBitmap(rgbBitmap, transformation, null)
            ImageUtil.storePixels(resizedBitmap, inputArray)

//            val start = System.nanoTime()
              val objects = detect(inputArray)
//            val elapsed = (System.nanoTime() - start) / 1_000_000
//            Log.d(TAG, "Detection time: $elapsed ms")
//

            val result = Result(
                objects = objects,
                imageWidth = config.inputSize,
                imageHeight = config.inputSize,
                imageRotationDegrees = rotationDegrees
            )

            uiHandler.post {
                onDetectionResult.invoke(result)
            }

        } catch (e: Exception) {
            Log.e(TAG, "analyze() error: ${e.message}", e)
        } finally {
            image.close()
        }
    }

    private fun detect(inputArray: IntArray): List<DetectionResult> {
        // здесь теперь просто используем уже готовый объект
        return objectDetector.detect(inputArray)
    }

    private fun getTransformation(rotationDegrees: Int, srcWidth: Int, srcHeight: Int): Matrix {
        return ImageUtil.getTransformMatrix(
            rotationDegrees,
            srcWidth,
            srcHeight,
            config.inputSize,
            config.inputSize
        )
    }

    private fun getArgbBitmap(width: Int, height: Int): Bitmap {
        if (rgbBitmap == null) {
            rgbBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
        return rgbBitmap!!
    }

    data class Config(
        val minimumConfidence: Float,
        val numDetection: Int,
        val inputSize: Int,
        val isQuantized: Boolean,
        val modelFile: String,
        val labelsFile: String
    )

    data class Result(
        val objects: List<DetectionResult>,
        val imageWidth: Int,
        val imageHeight: Int,
        val imageRotationDegrees: Int
    )
}