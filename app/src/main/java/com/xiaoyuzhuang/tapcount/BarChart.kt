package com.xiaoyuzhuang.tapcount

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import java.time.LocalDate

class BarChart(ctx: Context, private val scores: List<DayScore>, private val dark: Boolean) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(200f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = dp(9f)
        val right = width - dp(9f)
        val top = dp(27f)
        val bottom = height - dp(37f)
        val maximum = (scores.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
        p.strokeWidth = dp(1f)
        p.color = if (dark) Color.rgb(82, 94, 112) else Color.rgb(217, 223, 233)
        canvas.drawLine(left, bottom, right, bottom, p)
        if (scores.isEmpty()) return
        val slot = (right - left) / scores.size
        val barWidth = slot * if (scores.size <= 7) 0.57f else 0.74f
        scores.forEachIndexed { index, record ->
            val x = left + slot * index + (slot - barWidth) / 2f
            val heightFraction = record.count.toFloat() / maximum
            val barTop = bottom - (bottom - top) * heightFraction
            p.color = if (dark) Color.rgb(135, 169, 250) else Color.rgb(64, 104, 197)
            if (record.count > 0) {
                canvas.drawRoundRect(x, barTop, x + barWidth, bottom, dp(3f), dp(3f), p)
            }
            if (scores.size <= 7 || index == 0 || index == scores.lastIndex || index % 5 == 0) {
                p.color = if (dark) Color.LTGRAY else Color.DKGRAY
                p.textSize = dp(10f)
                p.textAlign = Paint.Align.CENTER
                val date = LocalDate.parse(record.day)
                canvas.drawText(date.dayOfMonth.toString(), x + barWidth / 2, bottom + dp(17f), p)
                if (scores.size <= 7 && record.count > 0) {
                    canvas.drawText(record.count.toString(), x + barWidth / 2, barTop - dp(7f), p)
                }
            }
        }
    }
}
