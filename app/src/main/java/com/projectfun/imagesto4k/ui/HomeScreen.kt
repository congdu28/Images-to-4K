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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectfun.imagesto4k.data.BackgroundStyle
import com.projectfun.imagesto4k.data.EnhancementMode
import com.projectfun.imagesto4k.data.ExifUtil
import com.projectfun.imagesto4k.data.ExportFormat
import com.projectfun.imagesto4k.data.ImageEnhancer
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
    RESIZE("Kích Thước", "📐"),
    BACKGROUND("Tách Nền", "✂️"),
    EXPORT("Lưu & Xuất", "💾")
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

    // Enhance Settings
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
            // Cancel running task if any
            processingJob?.cancel()
            processingJob = null
            isProcessing = false

            // Recycle previous bitmaps to free memory
            processedBitmap?.recycle()
            processedBitmap = null
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
                        Text(
                            text = "Images to 4K",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    // GPU / CPU Chip Toggle
                    Surface(
                        shape = CircleShape,
                        color = if (useGpu) NeonCyan.copy(alpha = 0.2f) else Color(0xFF333333),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clickable { useGpu = !useGpu }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (useGpu) "⚡ GPU/NPU" else "💻 CPU",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (useGpu) NeonCyan else Color.LightGray
                            )
                        }
                    }

                    if (originalBitmap != null) {
                        // Reset to original button
                        if (processedBitmap != null) {
                            IconButton(
                                onClick = {
                                    processingJob?.cancel()
                                    processingJob = null
                                    isProcessing = false
                                    processedBitmap?.recycle()
                                    processedBitmap = null
                                    System.gc()
                                    Toast.makeText(context, "Đã khôi phục về ảnh gốc!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Hoàn tác",
                                    tint = TextSecondary
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
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Đổi ảnh",
                                tint = TextSecondary
                            )
                        }
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
                                text = "Làm nét siêu tốc • AI Upscale 4K • Tách nền Offline\nResize kích thước tự do • Xuất JPG & PNG",
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
                                // 1. Mode Selection
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    EnhancementMode.values().forEach { mode ->
                                        val isSelected = selectedMode == mode
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedMode = mode },
                                            label = {
                                                Text(
                                                    text = mode.title,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = NeonCyan,
                                                selectedLabelColor = Color.Black,
                                                containerColor = DarkSurfaceElevated,
                                                labelColor = TextPrimary
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }
                                }

                                // Mode Description
                                Text(
                                    text = selectedMode.description,
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )

                                // Intensity Slider
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
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
                                            inactiveTrackColor = DarkSurfaceElevated
                                        )
                                    )
                                }

                                // Enhance Action Button
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
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonCyan,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isProcessing) "Đang xử lý (Có thể bấm Hủy ở trên)..." else "✨ Làm Nét Ngay (${selectedMode.title})",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            ToolTab.RESIZE -> {
                                val srcW = (processedBitmap ?: originalBitmap!!).width
                                val srcH = (processedBitmap ?: originalBitmap!!).height
                                val aspectRatio = srcH.toFloat() / srcW.toFloat()

                                Text(
                                    text = "Chọn tỉ lệ hoặc độ phân giải tiêu chuẩn:",
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                )

                                // Preset Resolutions Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        "4K UHD" to 3840,
                                        "2K QHD" to 2560,
                                        "Full HD" to 1920,
                                        "HD 720p" to 1280
                                    ).forEach { (label, targetW) ->
                                        val targetH = (targetW * aspectRatio).toInt()
                                        FilterChip(
                                            selected = customWidth == targetW,
                                            onClick = {
                                                customWidth = targetW
                                                customHeight = targetH
                                                resizeScalePercent = (targetW.toFloat() / srcW * 100f)
                                            },
                                            label = {
                                                Text(text = "$label (${targetW}p)", fontSize = 11.sp)
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = NeonCyan,
                                                selectedLabelColor = Color.Black,
                                                containerColor = DarkSurfaceElevated,
                                                labelColor = TextPrimary
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }
                                }

                                // Quick Percentages Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(50, 75, 150, 200).forEach { pct ->
                                        val isSel = (resizeScalePercent.roundToInt() == pct)
                                        FilterChip(
                                            selected = isSel,
                                            onClick = {
                                                resizeScalePercent = pct.toFloat()
                                                customWidth = (srcW * pct / 100f).toInt()
                                                customHeight = (srcH * pct / 100f).toInt()
                                            },
                                            label = {
                                                Text(text = "$pct%", fontSize = 11.sp)
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = NeonCyan,
                                                selectedLabelColor = Color.Black,
                                                containerColor = DarkSurfaceElevated,
                                                labelColor = TextPrimary
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        )
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
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonCyan,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "📐 Áp Dụng Resize (${customWidth}x${customHeight})",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            ToolTab.BACKGROUND -> {
                                Text(
                                    text = "AI nhận diện chủ thể & người hoàn toàn Offline (MediaPipe ML):",
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    BackgroundStyle.values().forEach { style ->
                                        val isSelected = selectedBgStyle == style
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedBgStyle = style },
                                            label = {
                                                Text(
                                                    text = style.title,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = NeonCyan,
                                                selectedLabelColor = Color.Black,
                                                containerColor = DarkSurfaceElevated,
                                                labelColor = TextPrimary
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                Text(
                                    text = when (selectedBgStyle) {
                                        BackgroundStyle.TRANSPARENT -> "💡 Nền sẽ trong suốt (được tự động xuất định dạng PNG để giữ nền rỗng)."
                                        BackgroundStyle.WHITE -> "💡 Thay nền cũ bằng nền trắng tinh khiết, thích hợp làm ảnh thẻ/chân dung."
                                        BackgroundStyle.BLACK -> "💡 Thay nền cũ bằng nền đen studio chuyên nghiệp."
                                    },
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )

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
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonCyan,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isProcessing) "Đang tách (Có thể bấm Hủy ở trên)..." else "✂️ Tách Nền Ngay",
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
                                    // JPG Option Card
                                    val isJpg = selectedExportFormat == ExportFormat.JPG
                                    Surface(
                                        color = if (isJpg) NeonCyan.copy(alpha = 0.15f) else DarkSurfaceElevated,
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isJpg) NeonCyan else Color.Transparent
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { selectedExportFormat = ExportFormat.JPG }
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "JPG (Khuyên dùng)",
                                                    fontSize = 13.sp,
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
                                                text = "Giữ nguyên thông số máy ảnh EXIF (ISO, khẩu độ). Dung lượng nhẹ tối ưu.",
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }

                                    // PNG Option Card
                                    val isPng = selectedExportFormat == ExportFormat.PNG
                                    Surface(
                                        color = if (isPng) NeonCyan.copy(alpha = 0.15f) else DarkSurfaceElevated,
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isPng) NeonCyan else Color.Transparent
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { selectedExportFormat = ExportFormat.PNG }
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "PNG (Không nén)",
                                                    fontSize = 13.sp,
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
                                                text = "Bảo toàn 100% pixel, hỗ trợ nền trong suốt khi tách nền.",
                                                fontSize = 11.sp,
                                                color = TextSecondary
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
                                            text = "• Độ phân giải: ${finalBitmap.width} x ${finalBitmap.height} px\n" +
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
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentGreen,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "💾 Lưu Vào Bộ Sưu Tập (.${selectedExportFormat.extension.uppercase()})",
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
}

/**
 * Memory-safe bitmap loader preventing OOM from ultra-high megapixel camera photos.
 * Limits loaded dimension safely within standard 4K boundaries.
 */
private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        // Step 1: Query image dimensions without allocating byte arrays
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }

        val origW = boundsOptions.outWidth
        val origH = boundsOptions.outHeight
        if (origW <= 0 || origH <= 0) return null

        // Step 2: Compute inSampleSize so loaded bitmap doesn't exceed 4096px
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

        // If still > 3840px, scale down smoothly to standard 4K bounds
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
