// GLTU 课表 App —— 自定义壁纸裁切（推倒重写版）
//
// 设计要点：
//  1. 裁切框 = 整个屏幕。你在这一屏看到的就是设置成壁纸后的效果，没有任何"框外内容"的歧义。
//  2. 输出比例 = 屏幕比例（vw : vh），所以课表页用 ContentScale.Crop 渲染时不会再被二次裁掉，
//     壁纸一定铺满背景。
//  3. 缩放下限 = max(vw/srcW, vh/srcH)，保证图片任何时候都盖满屏幕，永远不露黑边。
//  4. 裁切矩阵：p_out = p_img * (scale*k) + offset*k + outCenter - srcSize*(scale*k)/2
//     （k = 输出宽 / 视口宽；先缩放后平移，顺序不能反）
package com.gltu.schedule.ui.schedule

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gltu.schedule.data.BackgroundImageStore
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 壁纸裁切屏。
 *
 * @param uri 用户从相册选的图片
 * @param onCancel 取消
 * @param onConfirm 裁切完成，回调可直接保存的位图
 */
@Composable
fun BackgroundCropScreen(
    uri: Uri,
    onCancel: () -> Unit,
    onConfirm: (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val source = remember(uri) { BackgroundImageStore.decodeForEdit(context, uri) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(source) {
        if (source == null) message = "图片读取失败，请换一张试试"
    }

    Dialog(
        onDismissRequest = onCancel,
        // decorFitsSystemWindows = false 让 Dialog 窗口也铺满整屏（含状态栏/导航栏区域）。
        // 否则 vw/vh 会比真实屏幕小一圈，裁出来的比例 ≠ 屏幕比例，
        // 课表页用 ContentScale.Crop 渲染时还会被二次裁掉两侧。
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            val src = source
            if (src == null) {
                // 读取中 / 失败
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        message ?: "正在读取图片…",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (message != null) {
                        TextButton(onClick = onCancel) { Text("返回") }
                    }
                }
                return@Box
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val vw = with(density) { maxWidth.toPx() }
                val vh = with(density) { maxHeight.toPx() }
                val srcW = src.width.toFloat()
                val srcH = src.height.toFloat()

                // 铺满屏幕所需的最小缩放
                val minScale = remember(vw, vh, srcW, srcH) {
                    if (vw <= 0f || vh <= 0f) 1f else max(vw / srcW, vh / srcH)
                }
                var scale by remember(vw, vh, srcW, srcH) { mutableFloatStateOf(minScale) }
                var offset by remember(vw, vh, srcW, srcH) { mutableStateOf(Offset.Zero) }
                val img: ImageBitmap = remember(src) { src.asImageBitmap() }

                // 位移上限：图片放大后多出来的部分的一半
                fun clampOffset(raw: Offset, s: Float): Offset {
                    val maxX = max(0f, (srcW * s - vw) / 2f)
                    val maxY = max(0f, (srcH * s - vh) / 2f)
                    return Offset(
                        raw.x.coerceIn(-maxX, maxX),
                        raw.y.coerceIn(-maxY, maxY),
                    )
                }

                // ---- 图片层：铺满整屏，手势缩放/拖动 ----
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(vw, vh, srcW, srcH, minScale) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val next = (scale * zoom).coerceIn(minScale, minScale * 8f)
                                scale = next
                                offset = clampOffset(offset + pan, next)
                            }
                        },
                ) {
                    val left = size.width / 2f + offset.x - srcW * scale / 2f
                    val top = size.height / 2f + offset.y - srcH * scale / 2f
                    withTransform({
                        translate(left = left, top = top)
                        scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
                    }) {
                        drawImage(image = img, topLeft = Offset.Zero)
                    }
                }

                // ---- 顶部提示（浮在图片上） ----
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent),
                            ),
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    Text(
                        "裁切壁纸",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "双指缩放、单指拖动。整屏所见即最终壁纸，会自动铺满背景。",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                    )
                }

                // ---- 底部操作（浮在图片上） ----
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                            ),
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onCancel) {
                            Text("取消", color = Color.White)
                        }
                        TextButton(
                            onClick = {
                                scale = minScale
                                offset = Offset.Zero
                            },
                        ) {
                            Text("铺满屏幕", color = Color.White)
                        }
                        Button(
                            onClick = {
                                val out = cropToViewport(src, scale, offset, vw, vh)
                                if (out != null) onConfirm(out) else onCancel()
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("使用这张") }
                    }
                    Text(
                        "输出比例 = 屏幕比例，设置后不会二次裁切",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

/** 裁切几何：输出尺寸 + 画布矩阵参数（纯数据，便于单元测试）。 */
internal data class CropGeometry(
    val outW: Int,
    val outH: Int,
    /** 矩阵缩放（= 用户缩放 × k）。 */
    val scale: Float,
    val tx: Float,
    val ty: Float,
)

/**
 * 计算裁切几何。
 *
 * 坐标推导（视口坐标 → 输出坐标）：
 *   图片左上角在视口中的位置 = 视口中心 + offset - 原始尺寸*scale/2
 *   视口点 p 映射到输出：out = k * (p - 视口中心) + 输出中心
 *   合并得：out = p_img * (scale*k) + offset*k + 输出中心 - 原始尺寸*(scale*k)/2
 * 即「先按 scale*k 缩放、再平移」；顺序反了位移会被二次缩放（上一版的 bug）。
 *
 * 由此可推出关键不变量：视口 (0,0) 恰好映射到输出 (0,0)，
 * 视口 (vw,vh) 恰好映射到输出 (outW,outH) —— 所以预览里看到的就是最终壁纸，且铺满背景。
 *
 * @param vw 视口宽（px，= 屏幕宽）
 * @param vh 视口高（px，= 屏幕高）
 */
internal fun computeCropGeometry(
    srcW: Int,
    srcH: Int,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    vw: Float,
    vh: Float,
): CropGeometry? {
    if (vw <= 0f || vh <= 0f || srcW <= 0 || srcH <= 0 || scale <= 0f) return null
    // 输出分辨率跟屏幕一致（限制在 720–1440，兼顾清晰度与内存）
    val outW = vw.roundToInt().coerceIn(720, 1440)
    val outH = (outW * (vh / vw)).roundToInt().coerceAtLeast(1)
    val k = outW / vw
    val s = scale * k
    return CropGeometry(
        outW = outW,
        outH = outH,
        scale = s,
        tx = offsetX * k + outW / 2f - srcW * s / 2f,
        ty = offsetY * k + outH / 2f - srcH * s / 2f,
    )
}

/** 按当前缩放/位移，把原图裁成"预览里看到的那一块"。 */
internal fun cropToViewport(
    src: Bitmap,
    scale: Float,
    offset: Offset,
    vw: Float,
    vh: Float,
): Bitmap? {
    val g = computeCropGeometry(
        srcW = src.width,
        srcH = src.height,
        scale = scale,
        offsetX = offset.x,
        offsetY = offset.y,
        vw = vw,
        vh = vh,
    ) ?: return null
    return try {
        val out = Bitmap.createBitmap(g.outW, g.outH, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val m = Matrix()
        m.postScale(g.scale, g.scale)      // 先缩放
        m.postTranslate(g.tx, g.ty)        // 再平移（postTranslate => T * M）
        canvas.drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
        out
    } catch (e: Exception) {
        null
    }
}
