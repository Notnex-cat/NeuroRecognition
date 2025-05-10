package com.notnex.neurorecognition

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.notnex.neurorecognition.camera.ObjectDetectorAnalyzer
import java.util.concurrent.Executors

class ObjectRecognitionActivity : ComponentActivity() {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ObjectRecognitionScreen()
        }
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    @Composable
    fun ObjectRecognitionScreen() {
        val context = LocalContext.current
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Определим камеры
        val allBackCameraIds = remember {
            cameraManager.cameraIdList.filter { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        }
        val frontCameraId = remember {
            cameraManager.cameraIdList.find { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            }
        }

        var useFrontCamera by remember { mutableStateOf(false) }
        var selectedBackCameraIndex by remember { mutableIntStateOf(0) }

        val selectedCameraId = if (useFrontCamera) frontCameraId else allBackCameraIds.getOrNull(selectedBackCameraIndex)

        var previewView by remember { mutableStateOf<PreviewView?>(null) }
        var resultState by remember { mutableStateOf<ObjectDetectorAnalyzer.Result?>(null) }

        LaunchedEffect(previewView, selectedCameraId) {
            if (previewView != null && selectedCameraId != null) {
                startCamera(
                    context = context,
                    lifecycleOwner = lifecycleOwner,
                    previewView = previewView!!,
                    cameraId = selectedCameraId,
                    onResult = { resultState = it }
                )
                Toast.makeText(context, selectedCameraId, Toast.LENGTH_SHORT).show()
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { previewView = it }
                },
                modifier = Modifier.fillMaxSize()
            )

            resultState?.let { result ->
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val scaleX = size.width / result.imageWidth.toFloat()
                    val scaleY = size.height / result.imageHeight.toFloat()

                    result.objects.forEach { obj ->
                        val box = obj.location
                        val left = box.left * scaleX
                        val top = box.top * scaleY
                        val right = box.right * scaleX
                        val bottom = box.bottom * scaleY

                        drawRect(
                            color = Color.Red,
                            topLeft = Offset(left, top),
                            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                            style = Stroke(width = 3f)
                        )

                        drawContext.canvas.nativeCanvas.apply {
                            val text = "${obj.text} ${(obj.confidence * 100).toInt()}%"
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.RED
                                textSize = 36f
                                isAntiAlias = true
                                typeface = android.graphics.Typeface.DEFAULT_BOLD
                            }

                            val bgPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.argb(180, 255, 255, 255)
                            }

                            val bounds = android.graphics.Rect()
                            paint.getTextBounds(text, 0, text.length, bounds)

                            val textY = top - 10f

                            drawRect(
                                left,
                                textY - bounds.height(),
                                left + bounds.width(),
                                textY + 10f,
                                bgPaint
                            )

                            drawText(text, left, textY, paint)
                        }
                    }
                }
            }

            // Кнопки управления
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!useFrontCamera && allBackCameraIds.size > 1) {
                    Button(onClick = {
                        selectedBackCameraIndex = (selectedBackCameraIndex + 1) % allBackCameraIds.size
                    }) {
                        Text("Сменить заднюю камеру")
                    }
                }
                Button(onClick = {
                    useFrontCamera = !useFrontCamera
                }) {
                    Text(if (useFrontCamera) "Переключиться на заднюю" else "Переключиться на фронтальную")
                }
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun startCamera(
        context: Context,
        lifecycleOwner: androidx.lifecycle.LifecycleOwner,
        previewView: PreviewView,
        cameraId: String,
        onResult: (ObjectDetectorAnalyzer.Result) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().apply {
                surfaceProvider = previewView.surfaceProvider
            }

            val analyzer = ObjectDetectorAnalyzer(
                context,
                ObjectDetectorAnalyzer.Config(
                    minimumConfidence = 0.5f,
                    numDetection = 10,
                    inputSize = 300,
                    isQuantized = true,
                    modelFile = "detect.tflite",
                    labelsFile = "labelmap.txt"
                ),
                onResult
            )

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .apply {
                    setAnalyzer(executor, analyzer)
                }

            val cameraSelector = CameraSelector.Builder()
                .addCameraFilter { infos ->
                    infos.filter {
                        (it as androidx.camera.core.impl.CameraInfoInternal).cameraId == cameraId
                    }
                }
                .build()


            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                analysis
            )
        }, ContextCompat.getMainExecutor(context))
    }
}
