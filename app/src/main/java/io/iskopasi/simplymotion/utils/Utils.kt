package io.iskopasi.simplymotion.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.graphics.YuvImage
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.WindowManager
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import io.iskopasi.simplymotion.utils.RealPathUtil.getRealPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.InvalidMarkException
import kotlin.math.abs


val Context.cameraManager: CameraManager?
    get() = ContextCompat.getSystemService(this, CameraManager::class.java)

fun getCameraId(
    context: Context,
    lensFacing: Int
): String =
    context.cameraManager!!
        .let { cameraManager ->
            cameraManager.cameraIdList.first {
                cameraManager
                    .getCameraCharacteristics(it)
                    .get(CameraCharacteristics.LENS_FACING) == lensFacing
            }
        }

fun getCameraCharacteristic(
    context: Context,
    lensFacing: Int
): CameraCharacteristics =
    context.cameraManager!!.getCameraCharacteristics(getCameraId(context, lensFacing))

fun getMaxSizeFront(context: Context): Size =
    getCameraCharacteristic(context, CameraMetadata.LENS_FACING_FRONT)
        .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        .getOutputSizes(SurfaceTexture::class.java)!!
        .first()

fun getMinSizeFront(context: Context): Size =
    getCameraCharacteristic(context, CameraMetadata.LENS_FACING_FRONT)
        .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        .getOutputSizes(SurfaceTexture::class.java)!!
        .last()

fun getMaxSizeBack(context: Context): Size =
    getCameraCharacteristic(context, CameraMetadata.LENS_FACING_BACK)
        .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        .getOutputSizes(SurfaceTexture::class.java)!!
        .first()

fun bg(block: suspend (CoroutineScope) -> Unit): Job = CoroutineScope(Dispatchers.IO).launch {
    block(this)
}

fun ui(block: suspend CoroutineScope.() -> Unit): Job = CoroutineScope(Dispatchers.Main).launch {
    block(this)
}

val Context.windowManager: WindowManager?
    get() = ContextCompat.getSystemService(this, WindowManager::class.java)

val Context.rotation: Int
    get() {
        return windowManager!!.defaultDisplay.rotation
//        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//            display!!.rotation
//        } else {
//            windowManager!!.defaultDisplay.rotation
//        }
    }


val String.e: String
    get() {
        Log.e("-->", this)
        return this
    }

fun ByteBuffer.toByteArray(): ByteArray {
    rewind()    // Rewind the buffer to zero
    val data = ByteArray(remaining())
    get(data)   // Copy the buffer into a byte array
    return data // Return the byte array
}

fun Bitmap.saveBitmapToFile(context: Context, filename: String): File? {
    val imageStorageAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    val imageDetails = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.MediaColumns.ORIENTATION, "90")
        put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis())
    }

    // Save the image.
    context.contentResolver.apply {
        insert(imageStorageAddress, imageDetails)?.let { uri ->
            openOutputStream(uri)?.use { outStream ->
                val isBitmapCompressed = compress(
                    Bitmap.CompressFormat.JPEG, 100, outStream
                )
                recycle()

                val file = getRealPath(context, uri)?.let { File(it) }
                "--> File: ${file?.absoluteFile}".e
                return file?.absoluteFile
            } ?: throw IOException("Failed to get output stream.")
        } ?: throw IOException("Failed to create new MediaStore record.")
    }

    return null
}

fun ImageProxy.diffGrayscaleBitmap(minusBuffer: ByteBuffer? = null): Bitmap {
    val yuvImage = toYUVGrayscale(minusBuffer)

    val imageBytes = ByteArrayOutputStream().let {
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, it)
        it.toByteArray()
    }

    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

fun ImageProxy.toYUVGrayscale(minusBuffer: ByteBuffer? = null): YuvImage {
    // Order of U/V channel guaranteed, read more:
    // https://developer.android.com/reference/android/graphics/ImageFormat#YUV_420_888
    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]

    val yBuffer = yPlane.buffer
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer

    // Full size Y channel and quarter size U+V channels.
    val numPixels = (width * height * 1.5f).toInt()
    val nv21 = ByteArray(numPixels)
    var index = 0

    // Copy Y channel.
    val yRowStride = yPlane.rowStride
    val yPixelStride = yPlane.pixelStride
    for (y in 0 until height) {
        for (x in 0 until width) {
            val yIndex = y * yRowStride + x * yPixelStride
            if (minusBuffer != null) {
                // Subtracting minusBuffer from this Image
                nv21[index++] = abs(yBuffer[yIndex] - minusBuffer[yIndex]).toByte()
            } else {
                nv21[index++] = yBuffer[yIndex]
            }
        }
    }

    // Copy VU data
    // NV21 format is expected to have YYYYVU packaging.
    // The U/V planes are guaranteed to have the same row stride and pixel stride.
    val uvRowStride = uPlane.rowStride
    val uvPixelStride = uPlane.pixelStride
    val uvWidth = width / 2
    val uvHeight = height / 2

    for (y in 0 until uvHeight) {
        for (x in 0 until uvWidth) {
//            val pixelDataIndex = y * uvRowStride + x * uvPixelStride

            // V channel
//            nv21[index++] = vBuffer[pixelDataIndex]
            // U channel
//            nv21[index++] = uBuffer[pixelDataIndex]

            // Gray-scaling
            // V channel
            nv21[index++] = 128.toByte()
            // U channel
            nv21[index++] = 128.toByte()
        }
    }

    return YuvImage(nv21, ImageFormat.NV21, width, height,  /* strides= */null)
}

fun ByteBuffer.copy(): ByteBuffer {
    //Get position, limit, and mark
    val pos = position()
    val limit = limit()
    var mark = -1
    try {
        reset()
        mark = position()
    } catch (e: InvalidMarkException) {
        //This happens when the original's mark is -1, so leave mark at default value of -1
    }

    //Create clone with matching capacity and byte order
    val clone =
        if (isDirect) ByteBuffer.allocateDirect(capacity()) else ByteBuffer.allocate(
            capacity()
        )
    clone.order(order())

    //Copy FULL buffer contents, including the "out-of-bounds" part
    limit(capacity())
    position(0)
    clone.put(this)

    //Set mark of both buffers to what it was originally
    if (mark != -1) {
        position(mark)
        mark()

        clone.position(mark)
        clone.mark()
    }

    //Set position and limit of both buffers to what they were originally
    position(pos)
    limit(limit)
    clone.position(pos)
    clone.limit(limit)

    return clone
}


private operator fun Bitmap.minus(bitmap2: Bitmap): Bitmap {
    val result = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888)

    for (x in 0 until getWidth()) {
        for (y in 0 until getHeight()) {
            val argb1: Int = getPixel(x, y)
            val argb2: Int = bitmap2.getPixel(x, y)

            //int a1 = (argb1 >> 24) & 0xFF;
            val r1 = (argb1 shr 16) and 0xFF
            val g1 = (argb1 shr 8) and 0xFF
            val b1 = argb1 and 0xFF

            //int a2 = (argb2 >> 24) & 0xFF;
            val r2 = (argb2 shr 16) and 0xFF
            val g2 = (argb2 shr 8) and 0xFF
            val b2 = argb2 and 0xFF

            //int aDiff = Math.abs(a2 - a1);
            val rDiff = abs((r2 - r1).toDouble()).toInt()
            val gDiff = abs((g2 - g1).toDouble()).toInt()
            val bDiff = abs((b2 - b1).toDouble()).toInt()

            val diff =
                (255 shl 24) or (rDiff shl 16) or (gDiff shl 8) or bDiff

            result.setPixel(x, y, diff)
        }
    }

    return result
}