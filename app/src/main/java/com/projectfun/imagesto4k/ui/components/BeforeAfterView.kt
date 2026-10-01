package com.projectfun.imagesto4k.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectfun.imagesto4k.ui.theme.NeonCyan
import kotlin.math.roundToInt

@Composable
fun BeforeAfterView(
    beforeBitmap: Bitmap,
    afterBitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    var sliderPosition by remember { mutableFloatStateOf(0.5f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Zoom and pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 6f)
        if (scale > 1f) {
            val maxOffsetX = (containerSize.width * (scale - 1f)) / 2f
            val maxOffsetY = (containerSize.height * (scale - 1f)) / 2f
            offset = Offset(
                x = (offset.x + panChange.x).coerceIn(-maxOffsetX, maxOffsetX),
                y = (offset.y + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
            )
        } else {
            offset = Offset.Zero
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F0F0F))
            .onSizeChanged { containerSize = it }
            .transformable(state = transformState)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            )
    ) {
        val beforeImageBitmap = remember(beforeBitmap) { beforeBitmap.asImageBitmap() }
        val afterImageBitmap = remember(afterBitmap) { afterBitmap?.asImageBitmap() }

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val w = size.width
            val h = size.height

            // Calculate destination rect to aspect-fit image into container
            val srcAspect = beforeBitmap.width.toFloat() / beforeBitmap.height.toFloat()
            val canvasAspect = w / h
            val drawW: Float
            val drawH: Float
            val drawX: Float
            val drawY: Float

            if (srcAspect > canvasAspect) {
                drawW = w
                drawH = w / srcAspect
                drawX = 0f
                drawY = (h - drawH) / 2f
            } else {
                drawH = h
                drawW = h * srcAspect
                drawX = (w - drawW) / 2f
                drawY = 0f
            }

            val imgDstRect = Rect(drawX, drawY, drawX + drawW, drawY + drawH)

            if (afterImageBitmap == null) {
                // If not enhanced yet, draw original full screen
                drawImage(
                    image = beforeImageBitmap,
                    dstOffset = IntOffset(drawX.toInt(), drawY.toInt()),
                    dstSize = IntSize(drawW.toInt(), drawH.toInt())
                )
            } else {
                val splitX = w * sliderPosition

                // 1. Draw Before (Left side)
                val leftClip = Path().apply {
                    addRect(Rect(0f, 0f, splitX, h))
                }
                clipPath(leftClip) {
                    drawImage(
                        image = beforeImageBitmap,
                        dstOffset = IntOffset(drawX.toInt(), drawY.toInt()),
                        dstSize = IntSize(drawW.toInt(), drawH.toInt())
                    )
                }

                // 2. Draw After (Right side)
                val rightClip = Path().apply {
                    addRect(Rect(splitX, 0f, w, h))
                }
                clipPath(rightClip) {
                    drawImage(
                        image = afterImageBitmap,
                        dstOffset = IntOffset(drawX.toInt(), drawY.toInt()),
                        dstSize = IntSize(drawW.toInt(), drawH.toInt())
                    )
                }

                // 3. Draw vertical divider bar
                drawLine(
                    color = NeonCyan,
                    start = Offset(splitX, 0f),
                    end = Offset(splitX, h),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }

        // Draggable handle
        if (afterBitmap != null && containerSize.width > 0) {
            val handleX = containerSize.width * sliderPosition

            Box(
                modifier = Modifier
                    .offset { IntOffset(handleX.roundToInt() - 20.dp.roundToPx(), (containerSize.height / 2) - 20.dp.roundToPx()) }
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            if (containerSize.width > 0) {
                                val newPos = sliderPosition + (dragAmount.x / containerSize.width)
                                sliderPosition = newPos.coerceIn(0.02f, 0.98f)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CompareArrows,
                    contentDescription = "Kéo so sánh",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Before / After badges
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "ẢNH GỐC",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeonCyan.copy(alpha = 0.85f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "ĐÃ LÀM NÉT 4K",
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
