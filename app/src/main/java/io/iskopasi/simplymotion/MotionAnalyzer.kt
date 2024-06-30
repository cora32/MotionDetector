package io.iskopasi.simplymotion

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.graphics.rotationMatrix
import io.iskopasi.simplymotion.utils.copy
import io.iskopasi.simplymotion.utils.diffGrayscaleBitmap
import io.iskopasi.simplymotion.utils.e
import io.iskopasi.simplymotion.utils.toABGR
import java.nio.ByteBuffer
import java.util.concurrent.Callable
import java.util.concurrent.Executors

typealias ResultCallback = (Bitmap?, Rect?) -> Unit

class MotionAnalyzer(
    private val width: Int,
    private val height: Int,
    private var threshold: Int,
    private val isFront: Boolean,
    val listener: ResultCallback
) : ImageAnalysis.Analyzer {
    var isAllowed = true

    private val mtx = rotationMatrix(if (isFront) -90f else 90f).apply {
        preScale(1f, if (isFront) -1f else 1f)
    }
    private var lastEvalTime: Long = 0L
    private var prev: ByteBuffer? = null
    private val detectColor = Color.RED
    private val detectColorABGR = detectColor.toABGR()
    private val verticalSums = mutableListOf<Int>()
    private val horizontalSums = mutableListOf<Int>()
    private val workers = 4
    private val pool = Executors.newFixedThreadPool(workers)

    override fun analyze(image: ImageProxy) {
        // Skip analyze step if analyzer is disabled
        if (!isAllowed) {
            listener(null, null)
            image.close()

            return
        }

        // Skip analyze step if it's too fast
        val elapsed = System.currentTimeMillis() - lastEvalTime
        if (elapsed < 100L) {
            image.close()

            return
        }

        // Skip first analyze step and save image data for next step
        if (prev == null) {
            prev = image.planes[0].buffer.copy()
            image.close()

            return
        }

        prev?.let { prev ->
            val diff = image.diffGrayscaleBitmap(prev)

            val rotatedBitmap = Bitmap.createBitmap(
                diff,
                0,
                0,
                diff.getWidth(),
                diff.getHeight(),
                mtx,
                true
            )
            diff.recycle()
            val constant = 255 * 255 * 15

            val tasks = generateTasks(rotatedBitmap, workers, 1) { src, dst, chunk ->
                blurMedianTask(src, dst, chunk)
                blurMedianTask(dst, dst, chunk)
                adaptiveThresholdTask(dst, dst, chunk, constant)
            }
            val results = pool.invokeAll(tasks)
            val finalBitmap = results[0].get()

            val borderOffset = 4
            val xCoefficient = width / (finalBitmap.width - (borderOffset * 2))
            val yCoefficient = height / (finalBitmap.height - (borderOffset * 2))
            val detectRect = finalBitmap.detect(xCoefficient, yCoefficient, threshold, borderOffset)

            listener(finalBitmap, detectRect)
        }

        lastEvalTime = System.currentTimeMillis()
        prev = image.planes[0].buffer.copy()

        image.close()
    }

    private fun generateTasks(
        src: Bitmap,
        chunkNumber: Int,
        offset: Int,
        block: (Bitmap, Bitmap, Rect) -> Bitmap
    ): List<Callable<Bitmap>> {
        val dst = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888)
        val chunks = getChunks(src, chunkNumber, offset)

        return chunks.map { chunk -> Callable { block(src, dst, chunk) } }
    }

    private fun getChunks(src: Bitmap, chunks: Int, offset: Int): MutableList<Rect> {
        val result = mutableListOf<Rect>()

        if (chunks < 2) {
            result.add(Rect(offset, offset, src.width - offset, src.height - offset))
        } else {
            val blocksInRow = chunks / 2
            val blockWidth = src.width / blocksInRow
            val blockHeight = src.height / blocksInRow

            for (x in 0 until blocksInRow) {
                for (y in 0 until blocksInRow) {
                    val rect = when {
                        x == blocksInRow - 1 && y == blocksInRow - 1 -> Rect(
                            x * blockWidth,
                            y * blockHeight,
                            (x + 1) * blockWidth - offset,
                            (y + 1) * blockHeight - offset
                        )

                        x == 0 && y == 0 -> Rect(
                            offset,
                            offset,
                            blockWidth,
                            blockHeight
                        )

                        x == 0 && y == blocksInRow - 1 -> Rect(
                            offset,
                            y * blockHeight,
                            blockWidth,
                            (y + 1) * blockHeight - offset
                        )

                        x == blocksInRow - 1 && y == 0 -> Rect(
                            x * blockWidth,
                            offset,
                            (x + 1) * blockWidth - offset,
                            blockHeight
                        )

                        x == 0 -> Rect(
                            offset,
                            y * blockHeight,
                            blockWidth,
                            (y + 1) * blockHeight
                        )

                        x == blocksInRow - 1 -> Rect(
                            x * blockWidth,
                            y * blockHeight,
                            (x + 1) * (blockWidth) - offset,
                            (y + 1) * blockHeight
                        )

                        y == 0 -> Rect(
                            x * blockWidth,
                            offset,
                            (x + 1) * blockWidth,
                            src.height / blocksInRow
                        )

                        y == blocksInRow - 1 -> Rect(
                            x * blockWidth,
                            y * blockHeight,
                            (x + 1) * blockWidth,
                            (y + 1) * blockHeight - offset
                        )

                        else -> Rect(
                            x * blockWidth,
                            y * blockHeight,
                            (x + 1) * blockWidth,
                            (y + 1) * blockHeight
                        )
                    }

                    result.add(rect)
                }
            }
        }

        return result
    }

    private fun blurMedianTask(src: Bitmap, dst: Bitmap, chunk: Rect): Bitmap {
        blurMedianNative(src, dst, chunk.left, chunk.top, chunk.right, chunk.bottom)

        return dst
    }

    private fun adaptiveThresholdTask(
        src: Bitmap,
        dst: Bitmap,
        chunk: Rect,
        constant: Int
    ): Bitmap {
        adaptiveThresholdNative(
            src,
            dst,
            chunk.left,
            chunk.top,
            chunk.right,
            chunk.bottom,
            constant,
            detectColorABGR
        )

        return dst
    }

    private fun Bitmap.detect(
        xCoefficient: Int,
        yCoefficient: Int,
        threshold: Int,
        offset: Int
    ): Rect? {
        verticalSums.clear()
        horizontalSums.clear()

        for (x in (offset..<width - offset)) {
            var accumulator = 0

            for (y in (offset..<height - offset)) {
                if (getPixel(x, y) == detectColor) {
                    accumulator++
                }
            }

            verticalSums.add(accumulator)
        }

        for (y in (offset..<height - offset)) {
            var accumulator = 0

            for (x in (offset..<width - offset)) {
                if (getPixel(x, y) == detectColor) {
                    accumulator++
                }
            }

            horizontalSums.add(accumulator)
        }

        val left = verticalSums.indexOfFirst { it > threshold }
        val top = horizontalSums.indexOfFirst { it > threshold }
        val right = verticalSums.indexOfLast { it > threshold }
        val bottom = horizontalSums.indexOfLast { it > threshold }

        return if (left == -1 || top == -1 || right == -1 || bottom == -1) {
            null
        } else {
            Rect(
                (left + offset) * xCoefficient,
                (top + offset) * yCoefficient,
                (right + offset * 4) * xCoefficient,
                (bottom + offset) * yCoefficient
            )
        }
    }

    fun resume() {
        isAllowed = true
    }

    fun pause() {
        isAllowed = false
    }

    fun setSensitivity(threshold: Int) {
        this.threshold = threshold
        "threshold: $threshold".e
    }

    private external fun blurMedianNative(
        src: Bitmap,
        dst: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    )

    private external fun adaptiveThresholdNative(
        src: Bitmap,
        dst: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        constant: Int,
        detectColor: Int,
    )

    companion object {

        init {
            System.loadLibrary("simplymotion")
        }
    }
}

private fun Rect.rotateCCW() {
    val saved = bottom

    bottom = left
    left = top
    top = right
    right = saved
}
