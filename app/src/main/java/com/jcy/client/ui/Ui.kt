package com.jcy.client.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.load

/** 通用 UI 小工具（全代码构建视图，无 XML）。 */
object Ui {
    const val BG = "#0B0E14"
    const val CARD = "#151A24"
    const val TEXT = "#E8EDF5"
    const val SUB = "#8899AA"
    const val ACCENT = "#FF5C8A"

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun round(bg: String, radiusDp: Int, ctx: Context): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.parseColor(bg))
            cornerRadius = dp(ctx, radiusDp).toFloat()
        }

    fun tv(ctx: Context, text: String, size: Float, color: String = TEXT, bold: Boolean = false): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = size
            setTextColor(Color.parseColor(color))
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    fun card(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = round(CARD, 10, ctx)
    }

    /** 影视海报卡（封面 3:4 + 标题），宽度 widthDp。 */
    fun posterCard(ctx: Context, title: String, cover: String, widthDp: Int, onClick: () -> Unit): View {
        val w = dp(ctx, widthDp)
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.MarginLayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(ctx, 10)
            }
        }
        val img = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(w, w * 4 / 3)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = round(CARD, 10, ctx)
            clipToOutline = true
            if (cover.isNotEmpty()) load(cover)
        }
        val t = tv(ctx, title, 12f, TEXT).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(ctx, 2), dp(ctx, 6), dp(ctx, 2), 0)
        }
        col.addView(img)
        col.addView(t)
        col.setOnClickListener { onClick() }
        return col
    }
}
