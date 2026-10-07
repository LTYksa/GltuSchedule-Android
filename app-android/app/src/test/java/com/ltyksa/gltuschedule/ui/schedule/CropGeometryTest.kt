package com.ltyksa.gltuschedule.ui.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 壁纸裁切几何测试。
 *
 * 核心不变量：预览视口的 (0,0) 必须精确映射到输出图的 (0,0)，
 * 视口的 (vw,vh) 必须精确映射到输出的 (outW,outH)。
 * 满足这条，就同时保证了：
 *   ① 裁出来的就是预览里看到的那一块（所见即所得）
 *   ② 输出比例 == 屏幕比例，课表页 Crop 渲染时不会再被二次裁切 → 壁纸铺满背景
 */
class CropGeometryTest {

    private val srcW = 4000
    private val srcH = 3000
    private val vw = 1080f
    private val vh = 2340f

    /** 图片空间 → 输出空间（与 CropGeometry 里的矩阵一致）。 */
    private fun toOut(g: CropGeometry, px: Float, py: Float): Pair<Float, Float> =
        px * g.scale + g.tx to py * g.scale + g.ty

    /** 图片左上角在视口中的位置。 */
    private fun imgLeftInView(scale: Float, offsetX: Float) = vw / 2f + offsetX - srcW * scale / 2f
    private fun imgTopInView(scale: Float, offsetY: Float) = vh / 2f + offsetY - srcH * scale / 2f

    /** 视口坐标 → 图片坐标：v = left + p_img * scale */
    private fun viewToImg(v: Float, left: Float, scale: Float) = (v - left) / scale

    private fun checkRoundTrip(scale: Float, offsetX: Float, offsetY: Float) {
        val g = computeCropGeometry(srcW, srcH, scale, offsetX, offsetY, vw, vh)!!
        val left = imgLeftInView(scale, offsetX)
        val top = imgTopInView(scale, offsetY)

        // 视口左上角 (0,0) → 图片坐标 → 输出坐标，应为 (0,0)
        val (x0, y0) = toOut(g, viewToImg(0f, left, scale), viewToImg(0f, top, scale))
        assertEquals("视口左上角应映射到输出左上角", 0f, x0, 1f)
        assertEquals("视口左上角应映射到输出左上角", 0f, y0, 1f)

        // 视口右下角 (vw,vh) → 应为 (outW,outH)
        val (x1, y1) = toOut(g, viewToImg(vw, left, scale), viewToImg(vh, top, scale))
        assertEquals("视口右下角应映射到输出右下角", g.outW.toFloat(), x1, 1f)
        assertEquals("视口右下角应映射到输出右下角", g.outH.toFloat(), y1, 1f)
    }

    @Test
    fun `铺满时的四角映射`() {
        // 铺满：scale = max(vw/srcW, vh/srcH)，offset = 0
        val minScale = maxOf(vw / srcW, vh / srcH)
        checkRoundTrip(minScale, 0f, 0f)
    }

    @Test
    fun `放大并拖动后四角仍然对齐`() {
        val minScale = maxOf(vw / srcW, vh / srcH)
        checkRoundTrip(minScale * 2.5f, 120f, -230f)
        checkRoundTrip(minScale * 5f, -400f, 160f)
    }

    @Test
    fun `输出比例等于屏幕比例`() {
        val g = computeCropGeometry(srcW, srcH, 1f, 0f, 0f, vw, vh)!!
        val screenRatio = vh / vw
        val outRatio = g.outH.toFloat() / g.outW.toFloat()
        assertEquals(screenRatio.toDouble(), outRatio.toDouble(), 0.002)
    }

    @Test
    fun `竖屏与横屏都能算出合理输出`() {
        val portrait = computeCropGeometry(1080, 2400, 1f, 0f, 0f, 1080f, 2340f)!!
        assertTrue(portrait.outH > portrait.outW)

        val landscape = computeCropGeometry(4000, 3000, 1f, 0f, 0f, 2000f, 1200f)!!
        assertTrue(landscape.outH < landscape.outW)
        assertEquals(1440, landscape.outW)
    }

    @Test
    fun `非法输入返回 null`() {
        assertNull(computeCropGeometry(0, 100, 1f, 0f, 0f, vw, vh))
        assertNull(computeCropGeometry(100, 100, 0f, 0f, 0f, vw, vh))
        assertNull(computeCropGeometry(100, 100, 1f, 0f, 0f, 0f, vh))
        assertNull(computeCropGeometry(100, 100, 1f, 0f, 0f, vw, 0f))
    }
}
