package com.jcy.client.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.jcy.client.net.DanmakuItem

/**
 * 弹幕层：按播放时间轴把弹幕从右向左滚动。
 * 简化实现：固定速度 + 6 条轨道，超出 8 秒自动消失。
 */
class DanmakuView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var items: List<DanmakuItem> = emptyList()
    private var currentMs = -1L
    private var enabled = true

    private val speedDpPerSec = 110f
    private val laneCount = 6

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = Ui.dp(context, 15).toFloat()
        setShadowLayer(2f, 1f, 1f, Color.BLACK)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    fun setItems(list: List<DanmakuItem>) {
        // 时间相同的弹幕错开一点，避免完全重叠
        items = list.sortedBy { it.timeMs }
        invalidate()
    }

    fun setTime(ms: Long) {
        if (ms != currentMs) {
            currentMs = ms
            invalidate()
        }
    }

    fun setDanmakuEnabled(b: Boolean) {
        enabled = b
        invalidate()
    }

    fun isDanmakuEnabled(): Boolean = enabled

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!enabled || items.isEmpty() || currentMs < 0) return
        val w = width.toFloat()
        if (w <= 0) return
        val speed = Ui.dp(context, speedDpPerSec.toInt()).toFloat()
        val laneH = Ui.dp(context, 26).toFloat()
        val top = Ui.dp(context, 40).toFloat()  // 避开顶部标题栏

        val from = currentMs - 8000
        for (i in items.indices) {
            val d = items[i]
            if (d.timeMs < from) continue
            if (d.timeMs > currentMs) break
            val elapsed = (currentMs - d.timeMs) / 1000f
            val x = w - elapsed * speed
            if (x < -600) continue
            val lane = i % laneCount
            val y = top + lane * laneH + paint.textSize
            if (y > height) continue
            paint.color = runCatching { Color.parseColor(d.color) }.getOrDefault(Color.WHITE)
            canvas.drawText(d.text, x, y, paint)
        }
    }
}
