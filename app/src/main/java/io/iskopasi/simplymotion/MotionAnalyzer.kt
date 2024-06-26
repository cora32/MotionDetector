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
import java.nio.ByteBuffer
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.exp
import kotlin.math.pow

typealias ResultCallback = (Bitmap?, Rect) -> Unit

class MotionAnalyzer(
    private val width: Int,
    private val height: Int,
    private var threshold: Int,
    val listener: ResultCallback
) : ImageAnalysis.Analyzer {
    var isAllowed = true

    private val mtx = rotationMatrix(-90f).apply {
        preScale(1f, -1f)
    }
    private var lastEvalTime: Long = 0L
    private var prev: ByteBuffer? = null
    private val detectColor = Color.RED
    private val offset = intArrayOf(-1, 0, 1)
    private val verticalSums = mutableListOf<Int>()
    private val horizontalSums = mutableListOf<Int>()
    private val gaussKernel2: Array<Array<Float>> by lazy {
        Array(3) {
            gaussRow(it)
        }
    }
    private val workers = 4
    private val pool = Executors.newFixedThreadPool(workers)
    private val emptyRect = Rect().apply { setEmpty() }
    private val kSize = 9
    private val alpha = 1f
    private val sigma = 0.3 * ((kSize - 1) * 0.5 - 1) + 0.8

    private fun gaussRow(index: Int): Array<Float> {
        val result = Array(3) { 0f }

        for (i in 0 until 3) {
            val up = -(i + index * 3 - (kSize - 1) / 2.0).pow(2.0)
            val down = (2.0 * sigma.pow(2.0))

            result[i] = alpha * exp(up / down).toFloat()
        }

        return result
    }

    override fun analyze(image: ImageProxy) {
        if (!isAllowed) {
            listener(null, emptyRect)
            image.close()

            return
        }

        val elapsed = System.currentTimeMillis() - lastEvalTime
        if (elapsed < 100L) {
            image.close()

            return
        }

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

            val tasks = generateTasks(rotatedBitmap, workers, 1) { src, dst, chunk ->
                blurMedianTask(src, dst, chunk)
                blurMedianTask(dst, dst, chunk)
                adaptiveThresholdTask(dst, dst, chunk, 255 * 255 * 100)
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

        return result
    }

    private fun blurMedianTask(src: Bitmap, dst: Bitmap, chunk: Rect): Bitmap {
        val pixels = IntArray(size = 9)

        for (x in chunk.left until chunk.right) {
            for (y in chunk.top until chunk.bottom) {
                val pixel = src.medianPixel(x, y, pixels)

                dst.setPixel(x, y, pixel)
            }
        }

        return dst
    }

    private fun adaptiveThresholdTask(
        src: Bitmap,
        dst: Bitmap,
        chunk: Rect,
        constant: Int
    ): Bitmap {
        val newSource = src.copy(Bitmap.Config.ARGB_8888, false)

        for (x in chunk.left until chunk.right) {
            for (y in chunk.top until chunk.bottom) {
                val threshold = newSource.weightedSum(x, y) - constant
                val pixel = if (newSource.getPixel(x, y) > threshold) detectColor else Color.BLACK

                dst.setPixel(x, y, pixel)
            }
        }

        newSource.recycle()
        return dst
    }

    private fun Bitmap.detect(
        xCoefficient: Int,
        yCoefficient: Int,
        threshold: Int,
        offset: Int
    ): Rect {
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

        return Rect(
            (verticalSums.indexOfFirst { it > threshold } + offset) * xCoefficient,
            (horizontalSums.indexOfFirst { it > threshold } + offset) * yCoefficient,
            (verticalSums.indexOfLast { it > threshold } + offset * 4) * xCoefficient,
            (horizontalSums.indexOfLast { it > threshold } + offset) * yCoefficient
        )
    }

    private fun Bitmap.medianPixel(x: Int, y: Int, pixels: IntArray): Int {
        var index = 0

        for (i in offset) {
            for (j in offset) {
                pixels[index++] = getPixel(x + i, y + j)
            }
        }
        pixels.sort()

        return pixels[4]
    }

    private fun Bitmap.weightedSum(x: Int, y: Int): Int {
        var result = 0f

        for (i in offset) {
            for (j in offset) {
                result += getPixel(x + i, y + j) * gaussKernel2[i + 1][j + 1]
//                result += getPixel(x + i, y + j)
            }
        }

        return (result / 9).toInt()
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
}

private fun Rect.rotateCCW() {
    val saved = bottom

    bottom = left
    left = top
    top = right
    right = saved
}
