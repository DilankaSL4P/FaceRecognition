package com.fourpixell.facerecognition.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import android.renderscript.*
import java.nio.ByteBuffer


class YuvToRgbConverter(context: Context) {
    private val rs = RenderScript.create(context)
    private val scriptYuvToRgb = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))

    // These fields are cached to avoid expensive allocations at runtime
    private var yuvBuffer: ByteBuffer? = null
    private var yuvSizes: IntArray? = null
    private var yuvType: Type.Builder? = null
    private var rgbaType: Type.Builder? = null
    private var yIn: Allocation? = null
    private var uIn: Allocation? = null
    private var vIn: Allocation? = null
    private var rgbaOut: Allocation? = null

    @Synchronized
    fun yuvToRgb(image: Image, output: (Bitmap) -> Unit) {
        val yuvPlanes = image.planes
        val yPlane = yuvPlanes[0]
        val uPlane = yuvPlanes[1]
        val vPlane = yuvPlanes[2]

        val imageWidth = image.width
        val imageHeight = image.height

        // 1. Check if the buffer needs to be reallocated
        if (yuvBuffer == null || yuvBuffer!!.capacity() < yPlane.buffer.capacity() + uPlane.buffer.capacity() + vPlane.buffer.capacity()) {
            val ySize = yPlane.buffer.capacity()
            val uSize = uPlane.buffer.capacity()
            val vSize = vPlane.buffer.capacity()
            yuvBuffer = ByteBuffer.allocateDirect(ySize + uSize + vSize)
            yuvSizes = intArrayOf(ySize, uSize, vSize)
        }

        // 2. Concatenate the Y, U, and V planes into a single buffer
        yuvBuffer!!.position(0)
        yuvBuffer!!.put(yPlane.buffer)
        yuvBuffer!!.put(uPlane.buffer)
        yuvBuffer!!.put(vPlane.buffer)
        yuvBuffer!!.flip()

        // 3. Re-create the RenderScript allocations if necessary
        if (yIn == null || yIn!!.bytesSize != yuvSizes!![0]) {
            yuvType = Type.Builder(rs, Element.U8(rs)).setX(yuvSizes!![0])
            yIn = Allocation.createTyped(rs, yuvType!!.create(), Allocation.USAGE_SCRIPT)
            rgbaType = Type.Builder(rs, Element.RGBA_8888(rs)).setX(imageWidth).setY(imageHeight)
            rgbaOut = Allocation.createTyped(rs, rgbaType!!.create(), Allocation.USAGE_SCRIPT)
        }

        // 4. Copy the YUV data to the RenderScript allocations
        yIn!!.copyFrom(yuvBuffer!!.array())

        // 5. Set the YUV-to-RGB conversion script parameters and execute
        scriptYuvToRgb.setInput(yIn)
        scriptYuvToRgb.forEach(rgbaOut)

        // 6. Create the output bitmap and copy the result from the RenderScript allocation
        val outBitmap = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888)
        rgbaOut!!.copyTo(outBitmap)

        // 7. Invoke the output lambda with the resulting bitmap
        output(outBitmap)
    }
}