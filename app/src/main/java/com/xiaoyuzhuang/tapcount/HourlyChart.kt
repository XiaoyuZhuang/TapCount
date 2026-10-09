package com.xiaoyuzhuang.tapcount

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class HourlyChart(
    ctx: Context,
    private val scores: List<HourScore>,
    private val dark: Boolean,
    private val showPoints: Boolean = false
) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun dp(x: Float) = x * resources.displayMetrics.density
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),dp(146f).toInt())
    }
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (scores.isEmpty()) return
        val left = dp(10f); val right = width-dp(10f)
        val top = dp(32f); val bottom = height-dp(26f)
        val m = scores.maxOf { if (showPoints) it.points else it.taps }.coerceAtLeast(1)
        val slot = (right-left)/scores.size
        p.color = if (dark) Color.GRAY else Color.LTGRAY
        p.strokeWidth = dp(1f); c.drawLine(left,bottom,right,bottom,p)
        for ((i,h) in scores.withIndex()) {
            val x = left + i*slot + slot*0.18f
            val bw = slot*0.64f
            val value = if (showPoints) h.points else h.taps
            val ht = (bottom-top) * value.toFloat()/m
            p.color = if (dark) Color.rgb(142,179,254) else Color.rgb(59,101,190)
            if (value>0) c.drawRoundRect(x,bottom-ht,x+bw,bottom,dp(2f),dp(2f),p)
            p.color = if (dark) Color.WHITE else Color.rgb(34, 51, 74)
            p.textAlign=Paint.Align.CENTER
            p.typeface=android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            p.textSize=dp(10f)
            val countLabel = when {
                value >= 1000000 -> (value / 1000000).toString() + "m+"
                value >= 10000 -> (value / 1000).toString() + "k+"
                else -> value.toString()
            }
            // Explicit numeric value above each hourly column (including 0).
            val numberY = if (value>0) bottom-ht-dp(7f) else bottom-dp(7f)
            c.drawText(countLabel,x+bw/2,numberY.coerceAtLeast(dp(15f)),p)
            p.typeface=android.graphics.Typeface.DEFAULT
            p.color = if (dark) Color.LTGRAY else Color.DKGRAY
            p.textSize=dp(9f)
            c.drawText(h.hour.toString(),x+bw/2,bottom+dp(15f),p)
        }
    }
}
