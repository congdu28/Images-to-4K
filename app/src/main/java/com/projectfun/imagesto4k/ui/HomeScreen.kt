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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectfun.imagesto4k.data.EnhancementMode
import com.projectfun.imagesto4k.data.ExifUtil
import com.projectfun.imagesto4k.data.ImageEnhancer
import com.projectfun.imagesto4k.ui.components.BeforeAfterView
import com.projectfun.imagesto4k.ui.theme.*
import kotlinx.coroutines.launch
import java.io.InputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val enhancer = remember { ImageEnhancer(context) }

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var enhancedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var exifSummary by remember { mutableStateOf("") }

    var selectedMode by remember { mutableStateOf(EnhancementMode.AI_ESRGAN_4X) }
    var useGpu by remember { mutableStateOf(true) }
    var intensity by remember { mutableFloatStateOf(1.0f) }

    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var progressText by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            enhancedBitmap = null
            loadBitmapFromUri(context, uri)?.let {
                originalBitmap = it
            }
            exifSummary = ExifUtil.getExifSummary(context, uri)
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
                            color = Color.White
                        )
                    }
                },
                actions = {
                    Surface(
                        shape = CircleShape,
                        color = if (useGpu) NeonCyan.copy(alpha = 0.2f) else Color(0xFF333333),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (useGpu) "⚡ GPU" else "💻 CPU",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (useGpu) NeonCyan else Color.LightGray
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
            // Main Content Area: Image Preview or Pick Placeholder
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (originalBitmap != null) {
                    BeforeAfterView(
                        beforeBitmap = originalBitmap!!,
                        afterBitmap = enhancedBitmap
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkSurface)
                            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                            .clickable {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(NeonCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Text(
                                text = "Chọn bức ảnh cần làm nét",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Hỗ trợ ảnh chụp bị out nét, rung tay, mờ chi tiết.\nTự động tái tạo sắc nét và upscale lên chuẩn 4K.",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                    tonalElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // EXIF & Dimension specs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = exifSummary,
                                    fontSize = 12.sp,
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Gốc: ${originalBitmap!!.width}x${originalBitmap!!.height} px" +
                                            if (enhancedBitmap != null) " → Đích: ${enhancedBitmap!!.width}x${enhancedBitmap!!.height} px (4K)" else "",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }

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

                        // Processing Mode Selection
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            EnhancementMode.values().forEach { mode ->
                                val isSelected = selectedMode == mode
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedMode = mode },
                                    label = {
                                        Text(
                                            text = when (mode) {
                                                EnhancementMode.AI_ESRGAN_4X -> "AI 4K (ESRGAN)"
                                                EnhancementMode.AI_EDSR_2X -> "AI 2x (EDSR)"
                                                EnhancementMode.PRO_SHARP -> "Pro Sharp"
                                            },
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

                        // Options: Hardware Acceleration Switch & Intensity Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Tăng tốc phần cứng (GPU/NPU)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (useGpu) "Tận dụng chip đồ họa của máy" else "Dùng CPU đa nhân",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = useGpu,
                                onCheckedChange = { useGpu = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = NeonCyan
                                )
                            )
                        }

                        // Progress Section
                        AnimatedVisibility(visible = isProcessing) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LinearProgressIndicator(
                                    progress = progress,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = NeonCyan,
                                    trackColor = DarkSurfaceElevated
                                )
                                Text(
                                    text = progressText,
                                    fontSize = 12.sp,
                                    color = NeonCyan
                                )
                            }
                        }

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (originalBitmap != null && !isProcessing) {
                                        isProcessing = true
                                        progress = 0f
                                        progressText = "Đang khởi tạo thuật toán..."

                                        coroutineScope.launch {
                                            try {
                                                val result = enhancer.enhance(
                                                    inputBitmap = originalBitmap!!,
                                                    mode = selectedMode,
                                                    useGpu = useGpu,
                                                    intensity = intensity,
                                                    onProgress = { p, msg ->
                                                        progress = p
                                                        progressText = msg
                                                    }
                                                )
                                                enhancedBitmap = result
                                                Toast.makeText(context, "Làm nét hoàn tất!", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                Toast.makeText(context, "Lỗi xử lý: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                            } finally {
                                                isProcessing = false
                                            }
                                        }
                                    }
                                },
                                enabled = !isProcessing,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonCyan,
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isProcessing) "Đang xử lý..." else "Làm Nét 4K",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (enhancedBitmap != null) {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            val savedUri = enhancer.saveImageToGallery(enhancedBitmap!!, selectedUri)
                                            if (savedUri != null) {
                                                Toast.makeText(context, "Đã lưu ảnh 4K kèm thông số EXIF vào Thư viện!", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "Không thể lưu ảnh", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentGreen,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Lưu Ảnh 4K",
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

private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
        bitmap
    } catch (e: Exception) {
        null
    }
}
