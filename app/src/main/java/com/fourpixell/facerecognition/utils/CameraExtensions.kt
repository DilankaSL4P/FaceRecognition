package com.fourpixell.facerecognition.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ✅ ROBUST: Converts an ImageProxy (in YUV_420_888 format) to a Bitmap.
 * This method is less prone to memory errors than manual YUV plane manipulation.
 */
@SuppressLint("UnsafeOptInUsageError")
fun ImageProxy.toBitmap(): Bitmap? {
    val image = this.image ?: return null

    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
    val imageBytes = out.toByteArray()

    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

// Replace rotateAndMirror() with this simpler function
fun Bitmap.mirror(): Bitmap {
    val matrix = Matrix().apply { postScale(-1f, 1f) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

suspend fun ImageCapture.awaitTakePicture(context: Context): ImageProxy {
    return suspendCancellableCoroutine { continuation ->
        val executor = ContextCompat.getMainExecutor(context)

        takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            @SuppressLint("UnsafeOptInUsageError")
            override fun onCaptureSuccess(image: ImageProxy) {
                // ✅ RESUMES THE COROUTINE: This was the missing line.
                continuation.resume(image)
            }

            override fun onError(exception: ImageCaptureException) {
                continuation.resumeWithException(exception)
            }
        })

        continuation.invokeOnCancellation {
            // Cleanup logic, if needed
        }
    }
}

fun Bitmap.correctRotation(rotationDegrees: Int, isFrontCamera: Boolean): Bitmap {
    val matrix = Matrix()

    // Apply the initial rotation
    matrix.postRotate(rotationDegrees.toFloat())

    // For front camera, we need to flip it horizontally.
    // This is what corrects the "mirror image" effect.
    if (isFrontCamera) {
        matrix.postScale(-1f, 1f)
    }

    // Create the new bitmap with the correct transformation
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

/**
 * A utility function to convert a Google Play Services Task into a suspend function.
 * This function is already correct and requires no changes.
 */
suspend fun <T> Task<T>.await(): T {
    return suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            continuation.resume(result)
        }.addOnFailureListener { exception ->
            continuation.resumeWithException(exception)
        }
    }
}