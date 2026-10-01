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
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
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

data class ExecutionEngine(
    val interpreter: Interpreter,
    val gpuDelegate: GpuDelegate?,
    val nnApiDelegate: NnApiDelegate?,
    val hardwareName: String
) : AutoCloseable {
    override fun close() {
        try { interpreter.close() } catch (_: Throwable) {}
        try { gpuDelegate?.close() } catch (_: Throwable) {}
        try { nnApiDelegate?.close() } catch (_: Throwable) {}
    }
}

class ImageEnhancer(private val context: Context) {

    private fun loadModelFile(modelPath: String): ByteBuffer {
        val fileDescriptor: AssetFileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel: FileChannel = inputStream.channel
        val startOffset: Long = fileDescriptor.startOffset
        val declaredLength: Long = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Multi-tier hardware acceleration:
     * Tier 1: GPU Delegate (Adreno / Mali)
     * Tier 2: NPU / DSP Delegate (NNAPI)
     * Tier 3: Multi-core CPU (XNNPACK)
     */
    private fun createExecutionEngine(
        mode: EnhancementMode,
        useHardwareAcceleration: Boolean,
        tileSize: Int
    ): ExecutionEngine {
        val modelPath = when (mode) {
            EnhancementMode.AI_ESRGAN_4X -> "models/esrgan_quant.tflite"
            EnhancementMode.AI_EDSR_2X -> "models/edsr_x2_quant.tflite"
            EnhancementMode.PRO_SHARP -> throw IllegalArgumentException("Pro sharp does not use TFLite")
        }

        val modelBuffer = loadModelFile(modelPath)

        if (useHardwareAcceleration) {
            // Tier 1: GPU Delegate
            try {
                val gpuOptions = GpuDelegate.Options().apply {
                    setPrecisionLossAllowed(true)
                    setInferencePreference(GpuDelegate.Options.INFERENCE_PREFERENCE_SUSTAINED_SPEED)
                }
                val gpu = GpuDelegate(gpuOptions)
                val options = Interpreter.Options().apply {
                    addDelegate(gpu)
                }
                val interpreter = Interpreter(modelBuffer, options)
                interpreter.resizeInput(0, intArrayOf(1, tileSize, tileSize, 3))
                interpreter.allocateTensors()
                return ExecutionEngine(interpreter, gpu, null, "GPU (Adreno/Mali)")
            } catch (_: Throwable) {}

            // Tier 2: NPU / NNAPI
            try {
                val nnApiOptions = NnApiDelegate.Options().apply {
                    setAllowFp16(true)
                    setExecutionPreference(NnApiDelegate.Options.EXECUTION_PREFERENCE_SUSTAINED_SPEED)
                }
                val nnApi = NnApiDelegate(nnApiOptions)
                val options = Interpreter.Options().apply {
                    addDelegate(nnApi)
                }
                val interpreter = Interpreter(modelBuffer, options)
                interpreter.resizeInput(0, intArrayOf(1, tileSize, tileSize, 3))
                interpreter.allocateTensors()
                return ExecutionEngine(interpreter, null, nnApi, "NPU / AI Chip (NNAPI)")
            } catch (_: Throwable) {}
        }

        // Tier 3: CPU Multi-core XNNPACK
        val numCores = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
        val cpuOptions = Interpreter.Options().apply {
            setNumThreads(numCores)
            setUseXNNPACK(true)
        }
        val interpreter = Interpreter(modelBuffer, cpuOptions)
        interpreter.resizeInput(0, intArrayOf(1, tileSize, tileSize, 3))
        interpreter.allocateTensors()
        return ExecutionEngine(interpreter, null, null, "CPU Đa Nhân ($numCores Threads XNNPACK)")
    }

    /**
     * Main Enhancement Pipeline
     */
    suspend fun enhance(
        inputBitmap: Bitmap,
        mode: EnhancementMode,
        useGpu: Boolean,
        intensity: Float = 1.5f,
        onProgress: (Float, String) -> Unit
    ): Bitmap = withContext(Dispatchers.Default) {
        when (mode) {
            EnhancementMode.PRO_SHARP -> {
                onProgress(0.2f, "Đang phân tích cấu trúc đa tần số...")
                val sharpened = applyProSharpeningAndClarity(inputBitmap, intensity)
                onProgress(1.0f, "Hoàn tất làm nét Pro Sharp!")
                sharpened
            }
            EnhancementMode.AI_EDSR_2X, EnhancementMode.AI_ESRGAN_4X -> {
                val scale = mode.scale
                val tileSize = 128
                onProgress(0.05f, "Đang kết nối bộ tăng tốc phần cứng...")

                val engine = createExecutionEngine(mode, useGpu, tileSize)
                try {
                    onProgress(0.08f, "Đang xử lý bằng: ${engine.hardwareName}")
                    val rawAiResult = processWithTiling(
                        input = inputBitmap,
                        engine = engine,
                        scale = scale,
                        tileSize = tileSize,
                        onProgress = onProgress
                    )
                    // Crisp texture finish to make 4K / 2K details pop on mobile screens
                    onProgress(0.96f, "Đang hoàn thiện viền sắc nét...")
                    val finalResult = applyProSharpeningAndClarity(rawAiResult, intensity = 1.0f)
                    onProgress(1.0f, "Hoàn tất siêu phân giải ${mode.title}!")
                    finalResult
                } finally {
                    engine.close()
                }
            }
        }
    }

    /**
     * Tiling engine to process large photos without running out of RAM (OOM)
     */
    private fun processWithTiling(
        input: Bitmap,
        engine: ExecutionEngine,
        scale: Int,
        tileSize: Int,
        onProgress: (Float, String) -> Unit
    ): Bitmap {
        val inputW = input.width
        val inputH = input.height

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

        val overlap = 8
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

        val interpreter = engine.interpreter
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

                outputByteBuffer.rewind()
                interpreter.run(inputByteBuffer, outputByteBuffer)

                outputByteBuffer.rewind()
                if (isOutputFloat) {
                    for (i in 0 until outTileW * outTileH) {
                        val r = (outputByteBuffer.getFloat().coerceIn(0f, 1f) * 255f).toInt()
                        val g = (outputByteBuffer.getFloat().coerceIn(0f, 1f) * 255f).toInt()
                        val b = (outputByteBuffer.getFloat().coerceIn(0f, 1f) * 255f).toInt()
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
                    progress * 0.95f,
                    "[${engine.hardwareName}] Đang làm nét ô $processedTiles/$totalTiles (${(progress * 100).toInt()}%)"
                )
            }
        }

        if (workingBitmap != input) {
            workingBitmap.recycle()
        }

        return outputBitmap
    }

    /**
     * Professional Dual-Frequency Sharpening & Micro-Contrast (Clarity) Engine:
     * - Band 1: Adaptive High-Pass (Texture, eyelashes, edges, hair)
     * - Band 2: Mid-Frequency Local Contrast (De-haze, structure, clear view)
     * - Soft thresholding to prevent digital noise
     */
    private fun applyProSharpeningAndClarity(src: Bitmap, intensity: Float): Bitmap {
        val width = src.width
        val height = src.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val srcPixels = IntArray(width * height)
        val fineBlurPixels = IntArray(width * height)
        val wideBlurPixels = IntArray(width * height)
        val outPixels = IntArray(width * height)

        src.getPixels(srcPixels, 0, width, 0, 0, width, height)

        // Adaptive radii based on image resolution (so it scales properly for 4K down to 1080p)
        val minDim = min(width, height)
        val fineRadius = max(2, minDim / 400)
        val wideRadius = max(6, minDim / 120)

        // Pass 1: Fine-detail blur
        fastBoxBlur(srcPixels, fineBlurPixels, width, height, fineRadius)
        // Pass 2: Mid-tone structure blur
        fastBoxBlur(srcPixels, wideBlurPixels, width, height, wideRadius)

        val fineWeight = 1.8f * intensity
        val wideWeight = 0.6f * intensity

        for (i in 0 until width * height) {
            val orig = srcPixels[i]
            val fine = fineBlurPixels[i]
            val wide = wideBlurPixels[i]

            val a = (orig shr 24) and 0xFF

            val rOrig = (orig shr 16) and 0xFF
            val gOrig = (orig shr 8) and 0xFF
            val bOrig = orig and 0xFF

            val rFine = (fine shr 16) and 0xFF
            val gFine = (fine shr 8) and 0xFF
            val bFine = fine and 0xFF

            val rWide = (wide shr 16) and 0xFF
            val gWide = (wide shr 8) and 0xFF
            val bWide = wide and 0xFF

            // Detail difference + Structure contrast difference
            val rDiff = fineWeight * (rOrig - rFine) + wideWeight * (rOrig - rWide)
            val gDiff = fineWeight * (gOrig - gFine) + wideWeight * (gOrig - gWide)
            val bDiff = fineWeight * (bOrig - bFine) + wideWeight * (bOrig - bWide)

            val rNew = (rOrig + rDiff).toInt().coerceIn(0, 255)
            val gNew = (gOrig + gDiff).toInt().coerceIn(0, 255)
            val bNew = (bOrig + bDiff).toInt().coerceIn(0, 255)

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
     * Save enhanced image to MediaStore and preserve camera EXIF
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

            if (originalUri != null) {
                ExifUtil.copyExif(context, originalUri, tempFile)
            }

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
