// GLTU 课表 App —— 课表背景图存储（用户导入图片作为课表背景）
package com.ltyksa.gltuschedule.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

object BackgroundImageStore {

    private const val FILE_NAME = "schedule_background.jpg"

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    /** 是否已设置背景图。 */
    fun exists(context: Context): Boolean = file(context).let { it.exists() && it.length() > 0 }

    /** 保存用户选中的图片（复制到应用私有目录，无需存储权限）。 */
    fun save(context: Context, uri: Uri): Boolean {
        return try {
            val target = file(context)
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return false
            // 校验能否解码，避免保存了坏图
            val ok = BitmapFactory.decodeFile(target.absolutePath) != null
            if (!ok) target.delete()
            ok
        } catch (e: Exception) {
            false
        }
    }

    /** 保存裁切后的位图（用户在裁切界面确认后调用）。 */
    fun saveBitmap(context: Context, bitmap: android.graphics.Bitmap): Boolean {
        return try {
            val target = file(context)
            target.outputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 读取待裁切的壁纸原图。
     * API 28+ 走 ImageDecoder（会自动应用 EXIF 方向，相机竖拍照片不会躺倒），
     * 低版本回退 BitmapFactory。按最大边限制采样，避免 OOM。
     */
    fun decodeForEdit(context: Context, uri: Uri, maxSide: Int = 2400): android.graphics.Bitmap? {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val src = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                    // 必须用软件位图，否则不能画到我们自己的 Canvas 上
                    decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    val longest = maxOf(info.size.width, info.size.height)
                    if (longest > maxSide) {
                        var sample = 1
                        // 一直加倍到 longest/sample <= maxSide
                        while (longest / sample > maxSide) sample *= 2
                        decoder.setTargetSampleSize(sample)
                    }
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                var sample = 1
                val longest = maxOf(bounds.outWidth, bounds.outHeight)
                while (longest / sample > maxSide) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 读取背景图采样尺寸（用于限制内存）。 */
    fun loadScaled(context: Context, maxWidth: Int = 1440): android.graphics.Bitmap? {
        val f = file(context)
        if (!f.exists()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / sample > maxWidth * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(f.absolutePath, opts)
        } catch (e: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        file(context).delete()
    }
}
