package com.fourpixell.facerecognition.verifysection

/*import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.fourpixell.facerecognition.utils.FaceDataRepository
import com.fourpixell.facerecognition.utils.await
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyFaceScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var capturedImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var verificationResult by remember { mutableStateOf<Boolean?>(null) }

    val faceDetector = remember {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build()
        )
    }

    // Exit if no face is registered
    if (FaceDataRepository.registeredFace == null) {
        LaunchedEffect(Unit) {
            Toast.makeText(context, "No face registered. Please register first.", Toast.LENGTH_LONG).show()
            navController.popBackStack()
        }
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Verify Face", fontSize = 24.sp) }) }
    ) { paddingValues ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
        {
            // Show the live camera feed
            VerificationCameraView(onReady = { ic -> imageCapture = ic })

            if (capturedImageBitmap == null) {
                // UI for capturing the image
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Please smile to verify",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(16.dp)
                    )
                    IconButton(
                        onClick = {
                            scope.launch {
                                imageCapture?.let { ic ->
                                    takePhotoAndVerify(context, ic, faceDetector) { bitmap, result ->
                                        capturedImageBitmap = bitmap.asImageBitmap()
                                        verificationResult = result
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .padding(bottom = 32.dp)
                            .size(80.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.Camera, contentDescription = "Verify", modifier = Modifier.fillMaxSize())
                    }
                }
            } else {
                // UI to show the verification result
                VerificationResult(
                    bitmap = capturedImageBitmap!!,
                    isSuccess = verificationResult == true,
                    onDone = { navController.popBackStack() }
                )
            }
        }
    }
}

/*private suspend fun takePhotoAndVerify(
    context: Context,
    imageCapture: ImageCapture,
    detector: FaceDetector,
    onResult: (Bitmap, Boolean) -> Unit
) {
    val imageProxy = imageCapture.takePicture(context)

    withContext(Dispatchers.Default) {

        val finalBitmap = imageProxy.toRotatedAndMirroredBitmap(context)
        val image = InputImage.fromBitmap(finalBitmap, 0)


        val faces = detector.process(image).await()
        var isSuccess = false

        if (faces.isNotEmpty()) {
            val face = faces.first()

            val smileProb = face.smilingProbability ?: 0f
            Log.d("VerifyFace", "Smile probability: $smileProb")
            if (smileProb > 0.7f) { // You can adjust this threshold
                isSuccess = true
            }
        }

        withContext(Dispatchers.Main) {
            onResult(finalBitmap, isSuccess)
        }
        imageProxy.close()
    }
}*/

@Composable
private fun VerificationResult(bitmap: ImageBitmap, isSuccess: Boolean, onDone: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(bitmap = bitmap, contentDescription = "Captured Photo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isSuccess) "Verification Successful!" else "Verification Failed",
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.padding(8.dp)
            )

            Button(onClick = onDone) { Text("Done") }
        }
    }
}


@Composable
fun VerificationCameraView(onReady: (ImageCapture) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    LaunchedEffect(previewView) {
        setupVerificationCamera(context, lifecycleOwner, previewView, imageCapture)
        onReady(imageCapture)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
    }
}

private fun setupVerificationCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    imageCapture: ImageCapture
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
        } catch (exc: Exception) {
            Log.e("CameraSetup", "Use case binding failed", exc)
        }
    }, ContextCompat.getMainExecutor(context))
}*/