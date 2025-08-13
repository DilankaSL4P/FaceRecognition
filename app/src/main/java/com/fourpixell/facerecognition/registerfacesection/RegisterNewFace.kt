package com.fourpixell.facerecognition.registerfacesection


import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.fourpixell.facerecognition.utils.FaceDataRepository
import com.fourpixell.facerecognition.utils.await
import com.fourpixell.facerecognition.utils.awaitTakePicture
import com.fourpixell.facerecognition.utils.correctRotation
import com.fourpixell.facerecognition.utils.mirror
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RegisterFaceScreen(scanType: String, navController: NavController) {

    val context = LocalContext.current // current android environment details
    val scope = rememberCoroutineScope() //running background tasks
    val cameraPermissionState =
        rememberPermissionState(android.Manifest.permission.CAMERA) //Ask and check camera permissions


    //stateful Variables
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var previewView: PreviewView? by remember { mutableStateOf(null) }
    var isCapturing by remember { mutableStateOf(false) }

    var isFaceDetected by remember { mutableStateOf<Boolean?>(null) }


    var capturedImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var detectedFace by remember { mutableStateOf<Face?>(null) } // Store the whole Face object

    //Creates face detector from Google's ML Kit - Only created once.
    val faceDetector = remember {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                // find features like whether the eyes are open or if the person is smiling
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build()
        )
    }


    //Shows a camera permission popup if not already granted
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (scanType == "register") "Register Face" else "Verify Face", fontSize = 24.sp) }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (cameraPermissionState.status.isGranted) {
                CameraView(
                    onReady = { captureUseCase, view ->
                        imageCapture = captureUseCase
                        previewView = view
                    }
                )
            } else {
                Text(
                    "Camera permission is required.",
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            //checks if a image is already taken
            if (capturedImageBitmap == null) {

                //If not taken, shows the camera icon to take a photo
                IconButton(
                    onClick = {
                        if (!isCapturing) {
                            scope.launch {
                                isCapturing = true

                                val result = takePhoto(context, imageCapture!!)

                                if (result != null) {
                                    // Use the physically rotated bitmap for the UI
                                    capturedImageBitmap = result.previewBitmap.asImageBitmap()

                                    scope.launch(Dispatchers.Default) {
                                        // Use the un-rotated bitmap and the rotation degrees for analysis
                                        val image = InputImage.fromBitmap(result.analysisBitmap, result.rotationDegrees)

                                        val faces = faceDetector.process(image).await()

                                        withContext(Dispatchers.Main) {
                                            isFaceDetected = faces.isNotEmpty()
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "Failed to capture image.", Toast.LENGTH_SHORT).show()
                                }
                                isCapturing = false
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(32.dp)
                        .size(80.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Camera,
                        contentDescription = "Take Picture",
                        modifier = Modifier.fillMaxSize(),
                        tint = if (isCapturing) androidx.compose.ui.graphics.Color.Gray else MaterialTheme.colorScheme.primary
                    )
                }

            //If a face is detected a preview of the taken image is displayed
            } else {
                ImagePreview(
                    bitmap = capturedImageBitmap!!,
                    isFaceDetected = detectedFace != null,
                    onSave = {

                        detectedFace?.let {
                            FaceDataRepository.registeredFace = it
                            Log.d("RegisterFaceScreen", "Face data saved to repository.")
                            Toast.makeText(context, "Face Data Saved!", Toast.LENGTH_SHORT).show()
                        }
                        navController.navigate("home") {
                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        }
                    },
                    onRetake = {
                        capturedImageBitmap = null
                        detectedFace = null
                    }
                )
            }
        }
    }
}

private data class CaptureResult(
    val previewBitmap: Bitmap, // For the UI
    val analysisBitmap: Bitmap, // For ML Kit
    val rotationDegrees: Int
)

private suspend fun takePhoto(
    context: Context,
    imageCapture: ImageCapture
): CaptureResult? {
    return try {
        val imageProxy = imageCapture.awaitTakePicture(context)
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        // Convert to a base bitmap first
        val bitmap = imageProxy.toBitmap()

        imageProxy.close()

        if (bitmap != null) {
            CaptureResult(
                // 1. A version that is physically rotated for the UI preview
                previewBitmap = bitmap.correctRotation(rotationDegrees, true),

                // 2. The same, corrected bitmap for analysis
                analysisBitmap = bitmap,

                // 3. The rotation value for ML Kit to use
                rotationDegrees = rotationDegrees
            )
        } else {
            null
        }
    } catch (e: Exception) {
        Log.e("TakePhoto", "Failed to take photo.", e)
        null
    }
}

// Composable for the image preview screen
@Composable
fun ImagePreview(
    bitmap: ImageBitmap,
    isFaceDetected: Boolean,
    onSave: () -> Unit,
    onRetake: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            //Shows the captured image
            bitmap = bitmap,
            contentDescription = "Captured photo",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isFaceDetected) "✅ Face Detected!" else "❌ No Face Detected",
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier
                    .padding(8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = onRetake) {
                    Text("Retake")
                }
                Button(
                    onClick = onSave,
                    // The Save button is only enabled if a face was detected
                    enabled = isFaceDetected
                ) {
                    Text("Save")
                }
            }
        }
    }
}

// CameraX view
@Composable
fun CameraView(onReady: (ImageCapture, PreviewView) -> Unit) {
    val context = LocalContext.current //the current android context
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current //getting the lco for the specific spot where the code is running
    val previewView = remember { PreviewView(context) } //cameraX preview view
    val imageCapture = remember { ImageCapture.Builder().build() } //imageCapture instance to take photos

    LaunchedEffect(previewView) {
        setupCamera(context, lifecycleOwner, previewView, imageCapture)
        onReady(imageCapture, previewView)
    }

    //Display the preview
    Box(modifier = Modifier.fillMaxSize()) {
        //Android View - allows embedding a traditional Android View inside Compose UI.
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
    }
}

// Configures the camera
private fun setupCamera(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
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
}


/*@SuppressLint("UnsafeOptInUsageError")
private fun takePhotoAndAnalyze(
    context: Context,
    imageCapture: ImageCapture,
    detector: FaceDetector,
    onResult: (Bitmap, Boolean) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(context)

    imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(imageProxy: ImageProxy) {
            val originalBitmap = imageProxy.toBitmap()
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            // Create a matrix to apply both mirroring and rotation
            val matrix = Matrix().apply {
                // Rotate the image to the correct orientation
                postRotate(rotationDegrees.toFloat())
                // Flip the image horizontally for the front camera
                postScale(-1f, 1f)
            }

            // Create the final, correctly oriented bitmap
            val finalBitmap = Bitmap.createBitmap(
                originalBitmap,
                0,
                0,
                originalBitmap.width,
                originalBitmap.height,
                matrix,
                true
            )

            // Since the bitmap is now physically rotated, we pass 0 for rotation to ML Kit
            val image = InputImage.fromBitmap(finalBitmap, 0)

            detector.process(image)
                .addOnSuccessListener { faces ->
                    // Return the FINAL bitmap, which is now upright
                    onResult(finalBitmap, faces.isNotEmpty())
                }
                .addOnFailureListener { e ->
                    Log.e("FaceAnalysis", "Face detection failed.", e)
                    // Still return the FINAL bitmap, but with a false detection result
                    onResult(finalBitmap, false)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }

        override fun onError(exception: ImageCaptureException) {
            Log.e("CameraCapture", "Photo capture failed: ${exception.message}", exception)
        }
    })
}*/

