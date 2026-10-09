package com.xiaoyuzhuang.tapcount

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class HourlyChart(ctx: Context, private val scores: List<HourScore>, private val dark: Boolean) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun dp(x: Float) = x * resources.displayMetrics.density
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),dp(126f).toInt())
    }
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (scores.isEmpty()) return
        val left = dp(8f); val right = width-dp(8f)
        val top = dp(20f); val bottom = height-dp(30f)
        val m = scores.maxOf { it.taps }.coerceAtLeast(1)
        val slot = (right-left)/scores.size
        p.color = if (dark) Color.GRAY else Color.LTGRAY
        p.strokeWidth = dp(1f); c.drawLine(left,bottom,right,bottom,p)
        for ((i,h) in scores.withIndex()) {
            val x = left + i*slot + slot*0.18f
            val bw = slot*0.64f
            val ht = (bottom-top) * h.taps.toFloat()/m
            p.color = if (dark) Color.rgb(142,179,254) else Color.rgb(59,101,190)
            if (h.taps>0) c.drawRoundRect(x,bottom-ht,x+bw,bottom,dp(2f),dp(2f),p)
            p.color = if (dark) Color.LTGRAY else Color.DKGRAY
            p.textAlign=Paint.Align.CENTER; p.textSize=dp(9f)
            c.drawText(h.hour.toString(),x+bw/2,bottom+dp(14f),p)
        }
    }
}
