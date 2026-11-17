package cl.syncpass.app.ui.scanner

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.Closeable

private class QrCodeAnalyzer(
    private val onQrCode: (String) -> Unit
) : ImageAnalysis.Analyzer, Closeable {
    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )

    @Volatile
    private var busy = false

    @Volatile
    private var closed = false

    override fun analyze(image: ImageProxy) {
        if (busy || closed) {
            image.close()
            return
        }
        val mediaImage = image.image
        if (mediaImage == null) {
            image.close()
            return
        }
        busy = true
        val input = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue?.let(onQrCode)
            }
            .addOnCompleteListener {
                busy = false
                image.close()
            }
    }

    override fun close() {
        closed = true
        scanner.close()
    }
}

@Composable
fun CameraPreview(
    torchOn: Boolean,
    onQrCode: (String) -> Unit,
    onCameraError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnQr by rememberUpdatedState(onQrCode)
    val currentOnError by rememberUpdatedState(onCameraError)
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val analyzer = remember {
        QrCodeAnalyzer { value -> currentOnQr(value) }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val executor = remember { ContextCompat.getMainExecutor(context) }

    DisposableEffect(lifecycleOwner) {
        var provider: ProcessCameraProvider? = null
        var disposed = false
        val future = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            if (disposed) return@Runnable
            try {
                provider = future.get()
                val preview = Preview.Builder()
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor, analyzer) }
                provider?.unbindAll()
                camera = provider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } catch (error: Exception) {
                currentOnError(error.message ?: "No se pudo abrir la cámara.")
            }
        }
        future.addListener(listener, executor)
        onDispose {
            disposed = true
            runCatching { provider?.unbindAll() }
            analyzer.close()
        }
    }

    LaunchedEffect(torchOn, camera) {
        runCatching { camera?.cameraControl?.enableTorch(torchOn) }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}
