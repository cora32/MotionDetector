package io.iskopasi.simplymotion

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.graphics.rotationMatrix
import java.nio.ByteBuffer
import java.util.concurrent.Callable
import java.util.concurrent.Executors


data class DetectionData(
    val detected: Boolean,
    val detectRect: Rect,
    val deltaX: Int,
    val deltaY: Int
)

class MotionAnalyzer(
    val width: Int,
    val height: Int,
    val listener: (Bitmap, Rect) -> Unit,
) : ImageAnalysis.Analyzer {
    private val mtx = rotationMatrix(-90f).apply {
        preScale(1f, -1f)
    }

    private var lastEvalTime: Long = 0L
    private var prev: ByteBuffer? = null
    private val detectColor = Color.RED
    private val offset = intArrayOf(-1, 0, 1)
    private val gaussKernel: Array<Array<Float>> = Array(3) {
        arrayOf(1 / 16f, 1 / 8f, 1 / 16f)
        arrayOf(1 / 8f, 1 / 4f, 1 / 8f)
        arrayOf(1 / 16f, 1 / 8f, 1 / 16f)
    }
    private val gaussKernelInt: Array<Array<Int>> = Array(3) {
        arrayOf(1, 2, 1)
        arrayOf(2, 4, 2)
        arrayOf(1, 2, 1)
    }
    private val workers = 4
    private val pool = Executors.newFixedThreadPool(workers)

    override fun analyze(image: ImageProxy) {
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
                adaptiveThresholdTask(dst, dst, chunk, 255 * 255 * 3)
            }
            val results = pool.invokeAll(tasks)
            val finalBitmap = results[0].get()

            val xCoefficient = width / finalBitmap.width
            val yCoefficient = height / finalBitmap.height
            val detectRect = finalBitmap.detect(xCoefficient, yCoefficient, 5)

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
        for (x in chunk.left until chunk.right) {
            for (y in chunk.top until chunk.bottom) {
                val pixel = src.medianPixel(x, y)

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
                val pixel = if (newSource.getPixel(x, y) > threshold) Color.BLACK else detectColor

                dst.setPixel(x, y, pixel)
            }
        }

        newSource.recycle()
        return dst
    }

    private fun Bitmap.adaptiveThreshold(): Bitmap {
        val result = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888)

        for (x in 1 until getWidth() - 1) {
            for (y in 1 until getHeight() - 1) {
                val threshold = weightedSum(x, y) - 255 * 255 * 2
                val pixel = if (getPixel(x, y) > threshold) Color.BLACK else detectColor

                result.setPixel(x, y, pixel)
            }
        }

        return result
    }

    private fun Bitmap.detect(xCoefficient: Int, yCoefficient: Int, threshold: Int): Rect {
        val verticalSums = mutableListOf<Int>()
        for (x in (5..<width - 5)) {
            var accumulator = 0

            for (y in (5..<height - 5)) {
                if (getPixel(x, y) == detectColor) {
                    accumulator++
                }
            }

            verticalSums.add(accumulator)
        }

        val horizontalSums = mutableListOf<Int>()
        for (y in (5..<height - 5)) {
            var accumulator = 0

            for (x in (5..<width - 5)) {
                if (getPixel(x, y) == detectColor) {
                    accumulator++
                }
            }

            horizontalSums.add(accumulator)
        }

        return Rect(
            verticalSums.indexOfFirst { it > threshold } * xCoefficient,
            horizontalSums.indexOfFirst { it > threshold } * yCoefficient,
            verticalSums.indexOfLast { it > threshold } * xCoefficient,
            horizontalSums.indexOfLast { it > threshold } * yCoefficient
        )
    }

    private fun Bitmap.blurMedian(): Bitmap {
        val result = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888)

        for (x in 1 until getWidth() - 1) {
            for (y in 1 until getHeight() - 1) {
                val pixel = medianPixel(x, y)

                result.setPixel(x, y, pixel)
            }
        }

        return result
    }

    private fun Bitmap.medianPixel(x: Int, y: Int): Int {
        val result = IntArray(size = 9)
        var index = 0

        for (i in offset) {
            for (j in offset) {
                result[index++] = getPixel(x + i, y + j)
            }
        }
        result.sort()

        return result[4]
    }

    private fun Bitmap.weightedSum(x: Int, y: Int): Int {
        var result = 0

        for (i in offset) {
            for (j in offset) {
//            result += getPixel(x + i, y + j) * gaussKernelInt[i + 1][j + 1]
                result += getPixel(x + i, y + j)
            }
        }

        return result / 9
    }
}

private fun Rect.rotateCCW() {
    val saved = bottom

    bottom = left
    left = top
    top = right
    right = saved
}
