package com.notnex.neurorecognition

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.notnex.neurorecognition.camera.CameraPermissionsResolver
import com.notnex.neurorecognition.camera.ObjectDetectorAnalyzer
import com.notnex.neurorecognition.util.view.RecognitionResultOverlayView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ObjectRecognitionActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var resultOverlay: RecognitionResultOverlayView
    private lateinit var rootContainer: View

    private lateinit var executor: ExecutorService
    private val cameraPermissionsResolver = CameraPermissionsResolver(this)

    private val objectDetectorConfig = ObjectDetectorAnalyzer.Config(
        minimumConfidence = 0.5f,
        numDetection = 10,
        inputSize = 300,
        isQuantized = true,
        modelFile = "detect.tflite",
        labelsFile = "labelmap.txt"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_object_recognition)

        executor = Executors.newSingleThreadExecutor()
        previewView = findViewById(R.id.preview_view)
        resultOverlay = findViewById(R.id.result_overlay)
        rootContainer = findViewById(R.id.root_container)

        cameraPermissionsResolver.checkAndRequestPermissionsIfNeeded(
            onSuccess = {
                getProcessCameraProvider(::bindCamera)
            },
            onFail = ::showSnackbar
        )
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    private fun bindCamera(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder()
            .build()

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(
            executor,
            ObjectDetectorAnalyzer(applicationContext, objectDetectorConfig, ::onDetectionResult)
        )

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        cameraProvider.unbindAll()

        cameraProvider.bindToLifecycle(
            this,
            cameraSelector,
            imageAnalysis,
            preview
        )

        // Use this line instead of the previous one
        preview.surfaceProvider = previewView.surfaceProvider // Corrected line
    }

    private fun getProcessCameraProvider(onDone: (ProcessCameraProvider) -> Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            Runnable { onDone.invoke(cameraProviderFuture.get()) },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun onDetectionResult(result: ObjectDetectorAnalyzer.Result) {
        resultOverlay.updateResults(result)
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(rootContainer, message, Snackbar.LENGTH_LONG).show()
    }
}
