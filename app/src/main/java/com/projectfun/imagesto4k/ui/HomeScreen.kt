package com.projectfun.imagesto4k.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectfun.imagesto4k.data.*
import com.projectfun.imagesto4k.ui.components.BeforeAfterView
import com.projectfun.imagesto4k.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

enum class ToolTab(val title: String, val emoji: String) {
    ENHANCE("Làm Nét", "✨"),
    RESIZE("Resize", "📐"),
    BACKGROUND("Tách Nền", "✂️"),
    EXPORT("Xuất File", "💾")
}

enum class ProcessingEngine(val title: String, val badge: String) {
    OFFLINE("Offline", "⚡"),
    ONLINE_GEMINI("Gemini Pro", "🌐")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val enhancer = remember { ImageEnhancer(context) }

    // Navigation and Image State
    var activeTab by remember { mutableStateOf(ToolTab.ENHANCE) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var resultLabel by remember { mutableStateOf("KẾT QUẢ") }
    var exifSummary by remember { mutableStateOf("") }

    // Engine Selection (Offline chip vs Online Gemini Pro)
    var selectedEngine by remember { mutableStateOf(ProcessingEngine.ONLINE_GEMINI) }
    var showGeminiDialog by remember { mutableStateOf(false) }
    var geminiApiKey by remember { mutableStateOf(GeminiClient.getSavedApiKey(context)) }
    var selectedGeminiModel by remember { mutableStateOf(GeminiClient.getSelectedModel(context)) }
    var geminiCustomPrompt by remember { mutableStateOf("") }
    var geminiAdviceText by remember { mutableStateOf("") }

    // Offline Enhance Settings
    var selectedMode by remember { mutableStateOf(EnhancementMode.FAST_4K) }
    var useGpu by remember { mutableStateOf(true) }
    var intensity by remember { mutableFloatStateOf(1.5f) }

    // Resize Settings
    var resizeScalePercent by remember { mutableFloatStateOf(100f) }
    var customWidth by remember { mutableIntStateOf(0) }
    var customHeight by remember { mutableIntStateOf(0) }

    // Background Removal Settings
    var selectedBgStyle by remember { mutableStateOf(BackgroundStyle.TRANSPARENT) }

    // Export Settings
    var selectedExportFormat by remember { mutableStateOf(ExportFormat.JPG) }

    // Coroutine Job for Cancellation Support
    var processingJob by remember { mutableStateOf<Job?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var progressText by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            processingJob?.cancel()
            processingJob = null
            isProcessing = false

            processedBitmap?.recycle()
            processedBitmap = null
            geminiAdviceText = ""
            originalBitmap?.recycle()
            originalBitmap = null

            selectedUri = uri
            loadBitmapFromUri(context, uri)?.let {
                originalBitmap = it
                customWidth = it.width
                customHeight = it.height
                resizeScalePercent = 100f
            }
            exifSummary = ExifUtil.getExifSummary(context, uri)
            System.gc()
        }
    }

    LaunchedEffect(originalBitmap, processedBitmap) {
        val active = processedBitmap ?: originalBitmap
        if (active != null) {
            customWidth = active.width
            customHeight = active.height
            resizeScalePercent = 100f
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(NeonCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "4K", fontSize = 11.sp, fontWeight = FontWeight.Black, color = NeonCyan)
                        }
                        Text(
                            text = "Images to 4K",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                    }
                },
                actions = {
                    if (originalBitmap != null) {
                        // Reset to original button (only when processed)
                        if (processedBitmap != null) {
                            IconButton(
                                onClick = {
                                    processingJob?.cancel()
                                    processingJob = null
                                    isProcessing = false
                                    processedBitmap?.recycle()
                                    processedBitmap = null
                                    geminiAdviceText = ""
                                    System.gc()
                                    Toast.makeText(context, "Đã khôi phục về ảnh gốc!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Hoàn tác về ảnh gốc",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Pick new photo button
                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Chọn ảnh khác",
                                tint = TextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Settings Icon for Gemini API Key & Model
                    IconButton(onClick = { showGeminiDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Cài đặt Gemini",
                            tint = if (geminiApiKey.isNotBlank()) NeonCyan else TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main Viewport (Image or Select Placeholder)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                if (originalBitmap != null) {
                    BeforeAfterView(
                        beforeBitmap = originalBitmap!!,
                        afterBitmap = processedBitmap,
                        afterLabel = resultLabel
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                            .background(DarkSurface)
                            .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
                            .clickable {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(NeonCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Text(
                                text = "Chọn bức ảnh cần chỉnh sửa",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "🌐 Gemini Pro Cloud AI • ⚡ NPU/GPU Offline 4K\n✂️ Tách nền Offline • 📐 Resize tự do • 💾 Xuất JPG/PNG",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                            Button(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonCyan,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Mở Thư Viện Ảnh",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Control Sheet
            if (originalBitmap != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = DarkSurface,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Quick Stats Line: EXIF info & Resolution
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (exifSummary.isNotBlank()) exifSummary else "Ảnh thiết bị",
                                fontSize = 11.sp,
                                color = NeonCyan,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            val currentActive = processedBitmap ?: originalBitmap!!
                            Text(
                                text = "Gốc: ${originalBitmap!!.width}x${originalBitmap!!.height} → Đích: ${currentActive.width}x${currentActive.height}",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Gemini AI Advice & Insights banner
                        if (geminiAdviceText.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(NeonCyan.copy(alpha = 0.12f))
                                    .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "💡 $geminiAdviceText",
                                    fontSize = 11.sp,
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        // Navigation Tabs (Làm Nét, Resize, Tách Nền, Xuất File)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(DarkSurfaceElevated)
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ToolTab.values().forEach { tab ->
                                val isSelected = activeTab == tab
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(if (isSelected) NeonCyan else Color.Transparent)
                                        .clickable(enabled = !isProcessing) { activeTab = tab }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = tab.emoji,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = tab.title,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.Black else TextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        // Progress Indicator with Dedicated CANCEL Button
                        AnimatedVisibility(visible = isProcessing) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkSurfaceElevated)
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = progressText,
                                        fontSize = 11.sp,
                                        color = NeonCyan,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // CANCEL BUTTON
                                    Button(
                                        onClick = {
                                            processingJob?.cancel()
                                            processingJob = null
                                            isProcessing = false
                                            progress = 0f
                                            progressText = "Đã dừng thao tác"
                                            Toast.makeText(context, "Đã hủy xử lý thành công!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFD32F2F),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Hủy",
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Hủy",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                LinearProgressIndicator(
                                    progress = progress,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = NeonCyan,
                                    trackColor = DarkBorder
                                )
                            }
                        }

                        // TAB CONTENT DISPLAY
                        when (activeTab) {
                            ToolTab.ENHANCE -> {
                                // Engine Selector: Segmented Control (Offline vs Gemini Cloud AI)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(DarkSurfaceElevated)
                                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                                        .padding(3.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(
                                        ProcessingEngine.ONLINE_GEMINI to "🌐 Gemini Cloud AI",
                                        ProcessingEngine.OFFLINE to "⚡ NPU/GPU Thiết Bị"
                                    ).forEach { (engine, label) ->
                                        val isSelected = selectedEngine == engine
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp)
                                                .clip(RoundedCornerShape(9.dp))
                                                .background(if (isSelected) NeonCyan else Color.Transparent)
                                                .clickable(enabled = !isProcessing) { selectedEngine = engine },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.Black else TextPrimary
                                            )
                                        }
                                    }
                                }

                                if (selectedEngine == ProcessingEngine.ONLINE_GEMINI) {
                                    // ONLINE GEMINI PRO PANEL
                                    Surface(
                                        color = DarkSurfaceElevated,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "Mô hình: ${selectedGeminiModel.displayName}",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = NeonCyan
                                                    )
                                                    Text(
                                                        text = selectedGeminiModel.description,
                                                        fontSize = 11.sp,
                                                        color = TextSecondary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Surface(
                                                    color = NeonCyan.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
                                                    modifier = Modifier.clickable { showGeminiDialog = true }
                                                ) {
                                                    Text(
                                                        text = "Đổi ▾",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = NeonCyan,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }

                                            // Quick style presets: 3 equal-width columns
                                            Text(
                                                text = "Chọn phong cách phục chế AI:",
                                                fontSize = 11.sp,
                                                color = TextPrimary
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                listOf(
                                                    "👤 Chân dung" to "Restore and sharpen facial details, eyes, eyelashes, and hair strands to crystal-clear 4K. Preserve exact facial identity and natural skin textures.",
                                                    "🏞️ Phong cảnh" to "Enhance natural landscapes, tree leaves, foliage, distant mountains, and sky clarity in ultra high resolution 4K.",
                                                    "🌙 Ban đêm" to "Denoise, deblur, and recover crisp edges from motion blur or low-light noise with realistic lighting."
                                                ).forEach { (label, prompt) ->
                                                    val isSelected = geminiCustomPrompt == prompt
                                                    Surface(
                                                        color = if (isSelected) NeonCyan.copy(alpha = 0.2f) else DarkBackground,
                                                        shape = RoundedCornerShape(8.dp),
                                                        border = BorderStroke(1.dp, if (isSelected) NeonCyan else DarkBorder),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(38.dp)
                                                            .clickable {
                                                                geminiCustomPrompt = if (isSelected) "" else prompt
                                                            }
                                                    ) {
                                                        Box(
                                                            contentAlignment = Alignment.Center,
                                                            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)
                                                        ) {
                                                            Text(
                                                                text = label,
                                                                fontSize = 11.sp,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                                color = if (isSelected) NeonCyan else TextPrimary,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Action Button for Gemini Pro
                                    Button(
                                        onClick = {
                                            if (geminiApiKey.isBlank()) {
                                                showGeminiDialog = true
                                                return@Button
                                            }
                                            if (originalBitmap != null && !isProcessing) {
                                                isProcessing = true
                                                progress = 0.05f
                                                progressText = "Đang kết nối Google Gemini Cloud GPU..."

                                                processingJob = coroutineScope.launch {
                                                    try {
                                                        val source = processedBitmap ?: originalBitmap!!
                                                        val enhancer = ImageEnhancer(context)
                                                        val result = GeminiClient.enhanceWithGemini(
                                                            inputBitmap = source,
                                                            apiKey = geminiApiKey,
                                                            model = selectedGeminiModel,
                                                            customPrompt = geminiCustomPrompt.ifBlank { null },
                                                            imageEnhancer = enhancer,
                                                            onProgress = { p, msg ->
                                                                progress = p
                                                                progressText = msg
                                                            }
                                                        )
                                                        processedBitmap = result.bitmap
                                                        geminiAdviceText = result.advice
                                                        resultLabel = "GEMINI 4K"
                                                        Toast.makeText(context, "Gemini phục chế ảnh 4K hoàn tất!", Toast.LENGTH_SHORT).show()
                                                    } catch (e: CancellationException) {
                                                        progressText = "Đã hủy tác vụ Gemini"
                                                    } catch (e: Throwable) {
                                                        e.printStackTrace()
                                                        Toast.makeText(context, "Lỗi Gemini: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                    } finally {
                                                        isProcessing = false
                                                        processingJob = null
                                                    }
                                                }
                                            }
                                        },
                                        enabled = !isProcessing,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NeonCyan,
                                            contentColor = Color.Black
                                        )
                                    ) {
                                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isProcessing) "Đang xử lý Cloud AI..." else "🌐 Phục Chế Bằng Gemini Pro AI",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    // OFFLINE HARDWARE ACCELERATION PANEL
                                    // 2x2 Grid of Enhancement Modes
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val modes = EnhancementMode.values()
                                        val row1 = listOf(modes[0], modes[1]) // PRO_SHARP, FAST_4K
                                        val row2 = listOf(modes[2], modes[3]) // AI_EDSR_2X, AI_ESRGAN_4X

                                        listOf(row1, row2).forEach { rowModes ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                rowModes.forEach { mode ->
                                                    val isSelected = selectedMode == mode
                                                    Surface(
                                                        color = if (isSelected) NeonCyan.copy(alpha = 0.15f) else DarkSurfaceElevated,
                                                        shape = RoundedCornerShape(12.dp),
                                                        border = BorderStroke(1.dp, if (isSelected) NeonCyan else DarkBorder),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(56.dp)
                                                            .clickable { selectedMode = mode }
                                                    ) {
                                                        Column(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                                            verticalArrangement = Arrangement.Center
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(
                                                                    text = mode.title,
                                                                    fontSize = 13.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (isSelected) NeonCyan else TextPrimary
                                                                )
                                                                Text(
                                                                    text = "${mode.scale}x",
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Black,
                                                                    color = if (isSelected) NeonCyan else TextSecondary
                                                                )
                                                            }
                                                            Text(
                                                                text = when (mode) {
                                                                    EnhancementMode.PRO_SHARP -> "Nét căng chi tiết 1x"
                                                                    EnhancementMode.FAST_4K -> "Nét viền tức thì 4x"
                                                                    EnhancementMode.AI_EDSR_2X -> "Mạng EDSR NPU 2x"
                                                                    EnhancementMode.AI_ESRGAN_4X -> "ESRGAN AI 4K"
                                                                },
                                                                fontSize = 10.sp,
                                                                color = TextSecondary,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Hardware Acceleration Switch Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(DarkSurfaceElevated)
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Tăng tốc phần cứng (GPU / NPU)",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = if (useGpu) "Đang bật tăng tốc phần cứng" else "Chạy CPU tiêu chuẩn",
                                                fontSize = 10.sp,
                                                color = TextSecondary
                                            )
                                        }
                                        Switch(
                                            checked = useGpu,
                                            onCheckedChange = { useGpu = it },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = NeonCyan,
                                                checkedTrackColor = NeonCyan.copy(alpha = 0.3f),
                                                uncheckedThumbColor = TextSecondary,
                                                uncheckedTrackColor = DarkBorder
                                            )
                                        )
                                    }

                                    // Intensity Slider Box
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(DarkSurfaceElevated)
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Mức độ làm nét (Intensity):",
                                                fontSize = 12.sp,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = "${(intensity * 100).roundToInt()}%",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NeonCyan
                                            )
                                        }
                                        Slider(
                                            value = intensity,
                                            onValueChange = { intensity = it },
                                            valueRange = 0.5f..3.0f,
                                            steps = 24,
                                            colors = SliderDefaults.colors(
                                                thumbColor = NeonCyan,
                                                activeTrackColor = NeonCyan,
                                                inactiveTrackColor = DarkBorder
                                            ),
                                            modifier = Modifier.height(28.dp)
                                        )
                                    }

                                    // Offline Enhance Action Button
                                    Button(
                                        onClick = {
                                            if (originalBitmap != null && !isProcessing) {
                                                isProcessing = true
                                                progress = 0f
                                                progressText = "Đang khởi tạo thuật toán..."

                                                processingJob = coroutineScope.launch {
                                                    try {
                                                        val source = processedBitmap ?: originalBitmap!!
                                                        val result = enhancer.enhance(
                                                            inputBitmap = source,
                                                            mode = selectedMode,
                                                            useGpu = useGpu,
                                                            intensity = intensity,
                                                            onProgress = { p, msg ->
                                                                progress = p
                                                                progressText = msg
                                                            }
                                                        )
                                                        processedBitmap = result
                                                        resultLabel = selectedMode.title
                                                        Toast.makeText(context, "Làm nét hoàn tất!", Toast.LENGTH_SHORT).show()
                                                    } catch (e: CancellationException) {
                                                        progressText = "Đã hủy thao tác"
                                                    } catch (e: Throwable) {
                                                        e.printStackTrace()
                                                        Toast.makeText(context, "Lỗi xử lý: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                    } finally {
                                                        isProcessing = false
                                                        processingJob = null
                                                    }
                                                }
                                            }
                                        },
                                        enabled = !isProcessing,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NeonCyan,
                                            contentColor = Color.Black
                                        )
                                    ) {
                                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isProcessing) "Đang xử lý (Bấm Hủy ở trên)..." else "✨ Làm Nét Ngay (${selectedMode.title})",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            ToolTab.RESIZE -> {
                                val srcW = (processedBitmap ?: originalBitmap!!).width
                                val srcH = (processedBitmap ?: originalBitmap!!).height
                                val aspectRatio = srcH.toFloat() / srcW.toFloat()

                                Text(
                                    text = "Độ phân giải chuẩn:",
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                )

                                // Preset Resolutions: 4 equal columns aligned with percentages below
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        "720p" to 1280,
                                        "1080p" to 1920,
                                        "2K" to 2560,
                                        "4K" to 3840
                                    ).forEach { (label, targetW) ->
                                        val targetH = (targetW * aspectRatio).toInt()
                                        val isSel = customWidth == targetW
                                        Surface(
                                            color = if (isSel) NeonCyan.copy(alpha = 0.2f) else DarkSurfaceElevated,
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, if (isSel) NeonCyan else DarkBorder),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(40.dp)
                                                .clickable {
                                                    customWidth = targetW
                                                    customHeight = targetH
                                                    resizeScalePercent = (targetW.toFloat() / srcW * 100f)
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                Text(
                                                    text = label,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSel) NeonCyan else TextPrimary
                                                )
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = "Tỉ lệ phần trăm (%):",
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                )

                                // Quick Percentages: 4 equal columns matching exactly above
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(50, 75, 150, 200).forEach { pct ->
                                        val isSel = (resizeScalePercent.roundToInt() == pct)
                                        Surface(
                                            color = if (isSel) NeonCyan.copy(alpha = 0.2f) else DarkSurfaceElevated,
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, if (isSel) NeonCyan else DarkBorder),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(40.dp)
                                                .clickable {
                                                    resizeScalePercent = pct.toFloat()
                                                    customWidth = (srcW * pct / 100f).toInt()
                                                    customHeight = (srcH * pct / 100f).toInt()
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                Text(
                                                    text = "$pct%",
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSel) NeonCyan else TextPrimary
                                                )
                                            }
                                        }
                                    }
                                }

                                // Target Dimension Box
                                Surface(
                                    color = DarkSurfaceElevated,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Kích thước đích:",
                                            fontSize = 12.sp,
                                            color = TextSecondary
                                        )
                                        Text(
                                            text = "${customWidth} x ${customHeight} px (${resizeScalePercent.roundToInt()}%)",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeonCyan
                                        )
                                    }
                                }

                                // Apply Resize Button
                                Button(
                                    onClick = {
                                        if (originalBitmap != null && customWidth > 0 && customHeight > 0) {
                                            processingJob = coroutineScope.launch {
                                                try {
                                                    val source = processedBitmap ?: originalBitmap!!
                                                    val resized = enhancer.resizeImage(source, customWidth, customHeight)
                                                    processedBitmap = resized
                                                    resultLabel = "RESIZE ${customWidth}x${customHeight}"
                                                    Toast.makeText(context, "Đã resize thành ${customWidth}x${customHeight} px!", Toast.LENGTH_SHORT).show()
                                                } catch (e: CancellationException) {
                                                    // User cancelled
                                                } catch (e: Throwable) {
                                                    Toast.makeText(context, "Lỗi resize: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                } finally {
                                                    processingJob = null
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isProcessing,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonCyan,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "📐 Áp Dụng Resize (${customWidth}x${customHeight})",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            ToolTab.BACKGROUND -> {
                                Text(
                                    text = "AI nhận diện chủ thể Offline (MediaPipe ML):",
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                )

                                // 3 Equal-Width Style Cards
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        Triple(BackgroundStyle.TRANSPARENT, "Trong suốt", "✂️"),
                                        Triple(BackgroundStyle.WHITE, "Nền Trắng", "⬜"),
                                        Triple(BackgroundStyle.BLACK, "Nền Đen", "⬛")
                                    ).forEach { (style, label, icon) ->
                                        val isSelected = selectedBgStyle == style
                                        Surface(
                                            color = if (isSelected) NeonCyan.copy(alpha = 0.15f) else DarkSurfaceElevated,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, if (isSelected) NeonCyan else DarkBorder),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(52.dp)
                                                .clickable { selectedBgStyle = style }
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxSize(),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(text = icon, fontSize = 14.sp)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = label,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) NeonCyan else TextPrimary
                                                )
                                            }
                                        }
                                    }
                                }

                                Surface(
                                    color = DarkSurfaceElevated,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = when (selectedBgStyle) {
                                            BackgroundStyle.TRANSPARENT -> "💡 Nền sẽ trong suốt (được tự động xuất định dạng PNG để giữ nền rỗng)."
                                            BackgroundStyle.WHITE -> "💡 Thay nền cũ bằng nền trắng tinh khiết, thích hợp làm ảnh thẻ/chân dung."
                                            BackgroundStyle.BLACK -> "💡 Thay nền cũ bằng nền đen studio chuyên nghiệp."
                                        },
                                        fontSize = 11.sp,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (originalBitmap != null && !isProcessing) {
                                            isProcessing = true
                                            progress = 0.2f
                                            progressText = "Đang phân tích chủ thể..."

                                            processingJob = coroutineScope.launch {
                                                try {
                                                    val source = originalBitmap!!
                                                    val cutout = enhancer.removeBackground(source, selectedBgStyle) { p, msg ->
                                                        progress = p
                                                        progressText = msg
                                                    }
                                                    processedBitmap = cutout
                                                    resultLabel = when (selectedBgStyle) {
                                                        BackgroundStyle.TRANSPARENT -> "TÁCH NỀN (PNG)"
                                                        BackgroundStyle.WHITE -> "NỀN TRẮNG"
                                                        BackgroundStyle.BLACK -> "NỀN ĐEN"
                                                    }
                                                    if (selectedBgStyle == BackgroundStyle.TRANSPARENT) {
                                                        selectedExportFormat = ExportFormat.PNG
                                                    }
                                                    Toast.makeText(context, "Tách nền hoàn tất!", Toast.LENGTH_SHORT).show()
                                                } catch (e: CancellationException) {
                                                    progressText = "Đã hủy tách nền"
                                                } catch (e: Throwable) {
                                                    e.printStackTrace()
                                                    Toast.makeText(context, "Lỗi tách nền: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                } finally {
                                                    isProcessing = false
                                                    processingJob = null
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isProcessing,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonCyan,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isProcessing) "Đang tách (Bấm Hủy ở trên)..." else "✂️ Tách Nền Ngay",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            ToolTab.EXPORT -> {
                                Text(
                                    text = "Chọn định dạng xuất file mong muốn:",
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val isJpg = selectedExportFormat == ExportFormat.JPG
                                    Surface(
                                        color = if (isJpg) NeonCyan.copy(alpha = 0.15f) else DarkSurfaceElevated,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isJpg) NeonCyan else DarkBorder
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(96.dp)
                                            .clickable { selectedExportFormat = ExportFormat.JPG }
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "JPG",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isJpg) NeonCyan else TextPrimary
                                                )
                                                RadioButton(
                                                    selected = isJpg,
                                                    onClick = { selectedExportFormat = ExportFormat.JPG },
                                                    colors = RadioButtonDefaults.colors(selectedColor = NeonCyan)
                                                )
                                            }
                                            Text(
                                                text = "Dung lượng nhẹ • Giữ EXIF máy ảnh",
                                                fontSize = 10.sp,
                                                color = TextSecondary,
                                                lineHeight = 13.sp
                                            )
                                        }
                                    }

                                    val isPng = selectedExportFormat == ExportFormat.PNG
                                    Surface(
                                        color = if (isPng) NeonCyan.copy(alpha = 0.15f) else DarkSurfaceElevated,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isPng) NeonCyan else DarkBorder
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(96.dp)
                                            .clickable { selectedExportFormat = ExportFormat.PNG }
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "PNG",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isPng) NeonCyan else TextPrimary
                                                )
                                                RadioButton(
                                                    selected = isPng,
                                                    onClick = { selectedExportFormat = ExportFormat.PNG },
                                                    colors = RadioButtonDefaults.colors(selectedColor = NeonCyan)
                                                )
                                            }
                                            Text(
                                                text = "Chất lượng gốc • Giữ nền trong suốt",
                                                fontSize = 10.sp,
                                                color = TextSecondary,
                                                lineHeight = 13.sp
                                            )
                                        }
                                    }
                                }

                                val finalBitmap = processedBitmap ?: originalBitmap!!
                                Surface(
                                    color = DarkSurfaceElevated,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "Thông tin file xuất:",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "• Kích thước: ${finalBitmap.width} x ${finalBitmap.height} px\n" +
                                                    "• Định dạng: .${selectedExportFormat.extension.uppercase()} (${selectedExportFormat.mimeType})\n" +
                                                    "• Thư mục lưu: Bộ sưu tập ảnh (Pictures/ImagesTo4K)",
                                            fontSize = 11.sp,
                                            color = TextSecondary,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }

                                // Save Button
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            val savedUri = enhancer.saveImageToGallery(
                                                bitmap = finalBitmap,
                                                originalUri = selectedUri,
                                                format = selectedExportFormat
                                            )
                                            if (savedUri != null) {
                                                Toast.makeText(
                                                    context,
                                                    "Đã lưu ảnh .${selectedExportFormat.extension.uppercase()} vào Thư viện thành công!",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            } else {
                                                Toast.makeText(context, "Không thể lưu ảnh", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentGreen,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "💾 Lưu Vào Bộ Sưu Tập (.${selectedExportFormat.extension.uppercase()})",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // GEMINI PRO SETTINGS DIALOG
    if (showGeminiDialog) {
        var tempApiKey by remember { mutableStateOf(geminiApiKey) }
        var isKeyVisible by remember { mutableStateOf(false) }
        var isTestingKey by remember { mutableStateOf(false) }
        var testResultMsg by remember { mutableStateOf("") }
        var tempModel by remember { mutableStateOf(selectedGeminiModel) }

        AlertDialog(
            onDismissRequest = { showGeminiDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "🌐 Cài Đặt Gemini Pro Cloud AI", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Sử dụng sức mạnh siêu máy chủ Google Cloud để phục chế và làm nét ảnh chất lượng studio mà không làm nóng điện thoại.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    OutlinedTextField(
                        value = tempApiKey,
                        onValueChange = { tempApiKey = it },
                        label = { Text("Gemini API Key") },
                        placeholder = { Text("AIzaSy...") },
                        singleLine = true,
                        visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                Icon(
                                    imageVector = if (isKeyVisible) Icons.Default.Check else Icons.Default.Info,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (testResultMsg.isNotBlank()) {
                        Text(
                            text = testResultMsg,
                            fontSize = 11.sp,
                            color = if (testResultMsg.contains("thành công")) AccentGreen else Color(0xFFFF5252),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Test Connection Button
                    Button(
                        onClick = {
                            if (tempApiKey.isNotBlank()) {
                                isTestingKey = true
                                testResultMsg = "Đang kiểm tra kết nối tới Google AI..."
                                coroutineScope.launch {
                                    val result = GeminiClient.testApiKey(tempApiKey)
                                    isTestingKey = false
                                    testResultMsg = result.getOrElse { it.localizedMessage ?: "Lỗi kết nối" }
                                }
                            }
                        },
                        enabled = !isTestingKey && tempApiKey.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated, contentColor = NeonCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = if (isTestingKey) "Đang kiểm tra..." else "Kiểm Tra API Key")
                    }

                    Text(
                        text = "Chọn mô hình Cloud AI:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )

                    GeminiModel.values().forEach { model ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { tempModel = model }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = tempModel == model,
                                onClick = { tempModel = model },
                                colors = RadioButtonDefaults.colors(selectedColor = NeonCyan)
                            )
                            Column(modifier = Modifier.padding(start = 6.dp)) {
                                Text(text = model.displayName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(text = model.description, fontSize = 10.sp, color = TextSecondary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        geminiApiKey = tempApiKey.trim()
                        selectedGeminiModel = tempModel
                        GeminiClient.saveApiKey(context, geminiApiKey)
                        GeminiClient.saveSelectedModel(context, tempModel)
                        if (geminiApiKey.isNotBlank()) {
                            selectedEngine = ProcessingEngine.ONLINE_GEMINI
                        }
                        showGeminiDialog = false
                        Toast.makeText(context, "Đã lưu cấu hình Gemini Pro!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black)
                ) {
                    Text(text = "Lưu & Kích Hoạt", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGeminiDialog = false }) {
                    Text(text = "Đóng", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/**
 * Memory-safe bitmap loader preventing OOM from ultra-high megapixel camera photos.
 * Limits loaded dimension safely within standard 4K boundaries.
 */
private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }

        val origW = boundsOptions.outWidth
        val origH = boundsOptions.outHeight
        if (origW <= 0 || origH <= 0) return null

        val maxDim = max(origW, origH)
        var sampleSize = 1
        while (maxDim / sampleSize > 4096) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val loadedBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        val curMax = max(loadedBitmap.width, loadedBitmap.height)
        if (curMax > 3840) {
            val scale = 3840f / curMax
            val scaledW = (loadedBitmap.width * scale).toInt()
            val scaledH = (loadedBitmap.height * scale).toInt()
            val scaledBitmap = Bitmap.createScaledBitmap(loadedBitmap, scaledW, scaledH, true)
            loadedBitmap.recycle()
            scaledBitmap
        } else {
            loadedBitmap
        }
    } catch (e: Exception) {
        null
    }
}
