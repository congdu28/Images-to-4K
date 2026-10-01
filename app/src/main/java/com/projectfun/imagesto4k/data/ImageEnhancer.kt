package com.projectfun.imagesto4k.data

import android.content.ContentValues
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

enum class EnhancementMode(val title: String, val scale: Int) {
    PRO_SHARP("Làm nét tức thì (Pro Sharp)", 1),
    AI_EDSR_2X("AI Nhanh 2x (EDSR)", 2),
    AI_ESRGAN_4X("AI Siêu Phân Giải 4K (ESRGAN)", 4)
}

class ImageEnhancer(private val context: Context) {

    private var interpreter4x: Interpreter? = null
    private var interpreter2x: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var isGpuActive: Boolean = false

    init {
        // Initialize delegates and interpreters lazily or on demand
    }

    private fun loadModelFile(modelPath: String): ByteBuffer {
        val fileDescriptor: AssetFileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel: FileChannel = inputStream.channel
        val startOffset: Long = fileDescriptor.startOffset
        val declaredLength: Long = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun getInterpreter(mode: EnhancementMode, useGpu: Boolean): Interpreter {
        val modelPath = when (mode) {
            EnhancementMode.AI_ESRGAN_4X -> "models/esrgan_quant.tflite"
            EnhancementMode.AI_EDSR_2X -> "models/edsr_quant.tflite"
            EnhancementMode.PRO_SHARP -> throw IllegalArgumentException("Pro sharp does not use TFLite")
        }

        val options = Interpreter.Options()
        val numCores = Runtime.getRuntime().availableProcessors()
        options.setNumThreads(max(2, min(numCores, 4)))

        if (useGpu) {
            val compatList = CompatibilityList()
            if (compatList.isDelegateSupportedOnThisDevice) {
                try {
                    val delegateOptions = compatList.bestOptionsForThisDevice
                    val delegate = GpuDelegate(delegateOptions)
                    options.addDelegate(delegate)
                    gpuDelegate = delegate
                    isGpuActive = true
                } catch (e: Exception) {
                    isGpuActive = false
                }
            } else {
                isGpuActive = false
            }
        } else {
            isGpuActive = false
        }

        val modelBuffer = loadModelFile(modelPath)
        return Interpreter(modelBuffer, options)
    }

    /**
     * Main Enhancement Pipeline
     */
    suspend fun enhance(
        inputBitmap: Bitmap,
        mode: EnhancementMode,
        useGpu: Boolean,
        intensity: Float = 1.0f,
        onProgress: (Float, String) -> Unit
    ): Bitmap = withContext(Dispatchers.Default) {
        when (mode) {
            EnhancementMode.PRO_SHARP -> {
                onProgress(0.2f, "Đang phân tích độ tương phản viền...")
                val sharpened = applyUnsharpMask(inputBitmap, amount = 1.4f * intensity, radius = 2)
                onProgress(1.0f, "Hoàn tất làm nét")
                sharpened
            }
            EnhancementMode.AI_EDSR_2X, EnhancementMode.AI_ESRGAN_4X -> {
                val scale = mode.scale
                onProgress(0.05f, "Đang chuẩn bị mô hình mạng nơ-ron (${if (useGpu) "GPU" else "CPU"})...")
                val interpreter = getInterpreter(mode, useGpu)

                try {
                    processWithTiling(
                        input = inputBitmap,
                        interpreter = interpreter,
                        scale = scale,
                        onProgress = onProgress
                    )
                } finally {
                    try {
                        interpreter.close()
                        gpuDelegate?.close()
                        gpuDelegate = null
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    /**
     * Tiling engine to process large photos without running out of RAM (OOM)
     */
    private fun processWithTiling(
        input: Bitmap,
        interpreter: Interpreter,
        scale: Int,
        onProgress: (Float, String) -> Unit
    ): Bitmap {
        val inputW = input.width
        val inputH = input.height

        // Limit maximum dimension for mobile memory safety if image is extreme (>4000px)
        val workingBitmap: Bitmap
        val shouldDownscale = max(inputW, inputH) > 2000
        if (shouldDownscale) {
            val ratio = 1920f / max(inputW, inputH)
            val newW = (inputW * ratio).toInt()
            val newH = (inputH * ratio).toInt()
            workingBitmap = Bitmap.createScaledBitmap(input, newW, newH, true)
        } else {
            workingBitmap = input
        }

        val srcW = workingBitmap.width
        val srcH = workingBitmap.height

        val tileSize = 128
        val overlap = 16
        val step = tileSize - (overlap * 2)

        val outW = srcW * scale
        val outH = srcH * scale
        val outputBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val xSteps = Math.ceil(srcW.toDouble() / step).toInt()
        val ySteps = Math.ceil(srcH.toDouble() / step).toInt()
        val totalTiles = xSteps * ySteps
        var processedTiles = 0

        // Allocate reusable buffers for inference
        // Input buffer: [1, tileSize, tileSize, 3]
        val inputTensorIndex = 0
        interpreter.resizeInput(inputTensorIndex, intArrayOf(1, tileSize, tileSize, 3))
        interpreter.allocateTensors()

        val inputDetails = interpreter.getInputTensor(0)
        val outputDetails = interpreter.getOutputTensor(0)

        val isInputFloat = inputDetails.dataType() == org.tensorflow.lite.DataType.FLOAT32
        val isOutputFloat = outputDetails.dataType() == org.tensorflow.lite.DataType.FLOAT32

        val outTileW = tileSize * scale
        val outTileH = tileSize * scale

        val inPixels = IntArray(tileSize * tileSize)
        val inputByteBuffer = ByteBuffer.allocateDirect(
            1 * tileSize * tileSize * 3 * (if (isInputFloat) 4 else 1)
        ).apply {
            order(ByteOrder.nativeOrder())
        }

        val outputByteBuffer = ByteBuffer.allocateDirect(
            1 * outTileH * outTileW * 3 * (if (isOutputFloat) 4 else 1)
        ).apply {
            order(ByteOrder.nativeOrder())
        }

        val outPixels = IntArray(outTileW * outTileH)

        for (y in 0 until srcH step step) {
            for (x in 0 until srcW step step) {
                val cropX = min(x, max(0, srcW - tileSize))
                val cropY = min(y, max(0, srcH - tileSize))
                val actualTileW = min(tileSize, srcW - cropX)
                val actualTileH = min(tileSize, srcH - cropY)

                val tileBitmap = Bitmap.createBitmap(workingBitmap, cropX, cropY, actualTileW, actualTileH)
                val uniformTile = if (actualTileW != tileSize || actualTileH != tileSize) {
                    Bitmap.createScaledBitmap(tileBitmap, tileSize, tileSize, true)
                } else {
                    tileBitmap
                }

                // Extract pixels and load into ByteBuffer
                uniformTile.getPixels(inPixels, 0, tileSize, 0, 0, tileSize, tileSize)
                inputByteBuffer.rewind()

                if (isInputFloat) {
                    for (pixel in inPixels) {
                        val r = ((pixel shr 16) and 0xFF) / 255.0f
                        val g = ((pixel shr 8) and 0xFF) / 255.0f
                        val b = (pixel and 0xFF) / 255.0f
                        inputByteBuffer.putFloat(r)
                        inputByteBuffer.putFloat(g)
                        inputByteBuffer.putFloat(b)
                    }
                } else {
                    for (pixel in inPixels) {
                        val r = ((pixel shr 16) and 0xFF).toByte()
                        val g = ((pixel shr 8) and 0xFF).toByte()
                        val b = (pixel and 0xFF).toByte()
                        inputByteBuffer.put(r)
                        inputByteBuffer.put(g)
                        inputByteBuffer.put(b)
                    }
                }

                // Run inference
                outputByteBuffer.rewind()
                interpreter.run(inputByteBuffer, outputByteBuffer)

                // Read output tensor into outPixels
                outputByteBuffer.rewind()
                if (isOutputFloat) {
                    for (i in 0 until outTileW * outTileH) {
                        val r = (outputByteBuffer.float.coerceIn(0f, 1f) * 255f).toInt()
                        val g = (outputByteBuffer.float.coerceIn(0f, 1f) * 255f).toInt()
                        val b = (outputByteBuffer.float.coerceIn(0f, 1f) * 255f).toInt()
                        outPixels[i] = Color.rgb(r, g, b)
                    }
                } else {
                    for (i in 0 until outTileW * outTileH) {
                        val r = (outputByteBuffer.get().toInt() and 0xFF)
                        val g = (outputByteBuffer.get().toInt() and 0xFF)
                        val b = (outputByteBuffer.get().toInt() and 0xFF)
                        outPixels[i] = Color.rgb(r, g, b)
                    }
                }

                val outTileBitmap = Bitmap.createBitmap(outPixels, outTileW, outTileH, Bitmap.Config.ARGB_8888)
                val targetX = (cropX * scale).toFloat()
                val targetY = (cropY * scale).toFloat()
                canvas.drawBitmap(outTileBitmap, targetX, targetY, paint)

                if (uniformTile != tileBitmap) uniformTile.recycle()
                tileBitmap.recycle()
                outTileBitmap.recycle()

                processedTiles++
                val progress = processedTiles.toFloat() / totalTiles
                onProgress(
                    progress,
                    "Đang tái tạo chi tiết ô $processedTiles/$totalTiles (${(progress * 100).toInt()}%)"
                )
            }
        }

        if (workingBitmap != input) {
            workingBitmap.recycle()
        }

        return outputBitmap
    }

    /**
     * Fast High-Pass Unsharp Masking Algorithm for instant crisp edges
     */
    private fun applyUnsharpMask(src: Bitmap, amount: Float, radius: Int): Bitmap {
        val width = src.width
        val height = src.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val srcPixels = IntArray(width * height)
        val blurredPixels = IntArray(width * height)
        val outPixels = IntArray(width * height)

        src.getPixels(srcPixels, 0, width, 0, 0, width, height)

        // Fast Box Blur approximation for unsharp mask
        fastBoxBlur(srcPixels, blurredPixels, width, height, radius)

        for (i in 0 until width * height) {
            val orig = srcPixels[i]
            val blur = blurredPixels[i]

            val a = (orig shr 24) and 0xFF

            val rOrig = (orig shr 16) and 0xFF
            val gOrig = (orig shr 8) and 0xFF
            val bOrig = orig and 0xFF

            val rBlur = (blur shr 16) and 0xFF
            val gBlur = (blur shr 8) and 0xFF
            val bBlur = blur and 0xFF

            // Unsharp formula: Enhanced = Original + amount * (Original - Blurred)
            val rNew = (rOrig + amount * (rOrig - rBlur)).toInt().coerceIn(0, 255)
            val gNew = (gOrig + amount * (gOrig - gBlur)).toInt().coerceIn(0, 255)
            val bNew = (bOrig + amount * (bOrig - bBlur)).toInt().coerceIn(0, 255)

            outPixels[i] = (a shl 24) or (rNew shl 16) or (gNew shl 8) or bNew
        }

        output.setPixels(outPixels, 0, width, 0, 0, width, height)
        return output
    }

    private fun fastBoxBlur(src: IntArray, dest: IntArray, w: Int, h: Int, radius: Int) {
        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(max(w, h))

        yw = 0
        yi = 0

        for (yIdx in 0 until h) {
            rsum = 0
            gsum = 0
            bsum = 0
            for (iIdx in -radius..radius) {
                p = src[yi + min(wm, max(iIdx, 0))]
                rsum += (p and 0xFF0000) shr 16
                gsum += (p and 0x00FF00) shr 8
                bsum += (p and 0x0000FF)
            }
            for (xIdx in 0 until w) {
                r[yi] = rsum / div
                g[yi] = gsum / div
                b[yi] = bsum / div

                if (yIdx == 0) {
                    vmin[xIdx] = min(xIdx + radius + 1, wm)
                }
                val p1 = src[yw + vmin[xIdx]]
                val p2 = src[yw + max(xIdx - radius, 0)]

                rsum += ((p1 and 0xFF0000) - (p2 and 0xFF0000)) shr 16
                gsum += ((p1 and 0x00FF00) - (p2 and 0x00FF00)) shr 8
                bsum += (p1 and 0x0000FF) - (p2 and 0x0000FF)
                yi++
            }
            yw += w
        }

        for (xIdx in 0 until w) {
            rsum = 0
            gsum = 0
            bsum = 0
            yp = -radius * w
            for (iIdx in -radius..radius) {
                yi = max(0, yp) + xIdx
                rsum += r[yi]
                gsum += g[yi]
                bsum += b[yi]
                yp += w
            }
            yi = xIdx
            for (yIdx in 0 until h) {
                dest[yi] = (-0x1000000 and dest[yi]) or
                        ((rsum / div) shl 16) or
                        ((gsum / div) shl 8) or
                        (bsum / div)

                if (xIdx == 0) {
                    vmin[yIdx] = min(yIdx + radius + 1, hm) * w
                }
                val p1 = xIdx + vmin[yIdx]
                val p2 = xIdx + max(yIdx - radius, 0) * w

                rsum += r[p1] - r[p2]
                gsum += g[p1] - g[p2]
                bsum += b[p1] - b[p2]

                yi += w
            }
        }
    }

    /**
     * Save enhanced 4K image to MediaStore and preserve all camera EXIF data
     */
    suspend fun saveImageToGallery(
        bitmap: Bitmap,
        originalUri: Uri?
    ): Uri? = withContext(Dispatchers.IO) {
        val filename = "IMG_4K_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ImagesTo4K")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        if (imageUri != null) {
            val tempFile = File(context.cacheDir, "temp_export.jpg")
            val tempOut = FileOutputStream(tempFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 98, tempOut)
            tempOut.flush()
            tempOut.close()

            // Copy EXIF metadata from original photo if available
            if (originalUri != null) {
                ExifUtil.copyExif(context, originalUri, tempFile)
            }

            // Write to gallery
            val outputStream: OutputStream? = resolver.openOutputStream(imageUri)
            if (outputStream != null) {
                FileInputStream(tempFile).use { it.copyTo(outputStream) }
                outputStream.flush()
                outputStream.close()
            }
            tempFile.delete()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }
        }

        imageUri
    }
}
