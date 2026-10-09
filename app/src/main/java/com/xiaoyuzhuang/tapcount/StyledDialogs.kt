package com.xiaoyuzhuang.tapcount

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Simple rounded, theme-aware dialogs without an external UI dependency. */
object StyledDialogs {
    private fun dp(ctx: Context, value: Int) = (value * ctx.resources.displayMetrics.density).toInt()
    private fun dark(ctx: Context) = UiPrefs.dark(ctx)
    private fun fg(ctx: Context) = if (dark(ctx)) Color.WHITE else Color.rgb(28, 38, 55)
    private fun muted(ctx: Context) = if (dark(ctx)) Color.rgb(181, 194, 214) else Color.rgb(95, 111, 133)
    private fun surface(ctx: Context) = if (dark(ctx)) Color.rgb(34, 41, 55) else Color.WHITE
    private fun accent(ctx: Context) = if (dark(ctx)) Color.rgb(150, 182, 251) else Color.rgb(49, 90, 184)
    private fun shape(ctx: Context, color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(ctx, radius).toFloat()
    }
    private fun text(ctx: Context, value: String, sp: Float, bold: Boolean = false): TextView =
        TextView(ctx).apply {
            text = value; textSize = sp; setTextColor(fg(ctx))
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
    private fun base(ctx: Context, title: String): Pair<Dialog, LinearLayout> {
        val dialog = Dialog(ctx)
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 20), dp(ctx, 19), dp(ctx, 20), dp(ctx, 18))
            background = shape(ctx, surface(ctx), 22)
        }
        box.addView(text(ctx, title, 19f, true),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 13) })
        dialog.setContentView(box)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        return dialog to box
    }
    private fun open(ctx: Context, dialog: Dialog) {
        dialog.show()
        val w = (ctx.resources.displayMetrics.widthPixels * 0.89f).toInt()
        dialog.window?.setLayout(w, WindowManager.LayoutParams.WRAP_CONTENT)
        dialog.window?.setDimAmount(0.48f)
    }
    private fun button(ctx: Context, value: String, main: Boolean, click: () -> Unit): TextView =
        text(ctx, value, 14f, true).apply {
            gravity = Gravity.CENTER
            setTextColor(if (main) Color.WHITE else accent(ctx))
            setPadding(dp(ctx, 13), dp(ctx, 11), dp(ctx, 13), dp(ctx, 11))
            background = shape(ctx, if (main) accent(ctx) else
                if (dark(ctx)) Color.rgb(48, 59, 80) else Color.rgb(235, 241, 253), 12)
            setOnClickListener { click() }
        }
    private fun actions(ctx: Context, dialog: Dialog, holder: LinearLayout,
                        ok: String, onYes: () -> Unit) {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button(ctx, ctx.getString(R.string.cancel), false) { dialog.dismiss() },
            LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd = dp(ctx,9) })
        row.addView(button(ctx, ok, true, onYes),
            LinearLayout.LayoutParams(0,-2,1f))
        holder.addView(row,LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(ctx,14) })
    }
    fun message(ctx: Context, title: String, description: String) {
        val (d, root) = base(ctx,title)
        root.addView(text(ctx,description,14f))
        root.addView(button(ctx,ctx.getString(R.string.confirm),true) { d.dismiss() },
            LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(ctx,15) })
        open(ctx,d)
    }
    fun confirm(ctx: Context, title: String, description: String, yes: () -> Unit) {
        val (d, root) = base(ctx,title)
        root.addView(text(ctx,description,14f))
        actions(ctx,d,root,ctx.getString(R.string.confirm)) { yes(); d.dismiss() }
        open(ctx,d)
    }
    fun options(ctx: Context, title: String, labels: Array<String>,
                onPick: (Int) -> Unit) {
        val (d, root) = base(ctx,title)
        for ((i,label) in labels.withIndex()) {
            val item = text(ctx,label,15f)
            item.setPadding(dp(ctx,12),dp(ctx,11),dp(ctx,12),dp(ctx,11))
            item.background = shape(ctx,if (dark(ctx)) Color.rgb(46,55,72)
                else Color.rgb(243,246,252),10)
            item.setOnClickListener { d.dismiss(); onPick(i) }
            root.addView(item, LinearLayout.LayoutParams(-1,-2)
                .apply { bottomMargin = dp(ctx,7) })
        }
        open(ctx,d)
    }
    fun input(ctx: Context, title: String, initial: String = "",
              numeric: Boolean = false, onSave: (String) -> Boolean) {
        val (d,root) = base(ctx,title)
        val entry = EditText(ctx).apply {
            setSingleLine(true)
            if (numeric) inputType = InputType.TYPE_CLASS_NUMBER
            setText(initial); setSelection(text.length)
            setTextColor(fg(ctx)); setHintTextColor(muted(ctx))
            setPadding(dp(ctx,12),dp(ctx,10),dp(ctx,12),dp(ctx,10))
            background = shape(ctx, if (dark(ctx)) Color.rgb(46,55,72)
                else Color.rgb(243,246,252),10)
        }
        root.addView(entry,LinearLayout.LayoutParams(-1,-2))
        actions(ctx,d,root,ctx.getString(R.string.save)) {
            if (onSave(entry.text.toString().trim())) d.dismiss()
        }
        open(ctx,d)
    }
    fun dayDetails(ctx: Context, title: String, hourly: List<HourScore>,
                   activity: String) {
        val (d,root) = base(ctx,title)
        val scroll = ScrollView(ctx)
        val content = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        content.addView(text(ctx,ctx.getString(R.string.hourly_breakdown),16f,true),
            LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(ctx,8) })
        if (hourly.isEmpty()) content.addView(text(ctx,ctx.getString(R.string.no_records),14f))
        else {
            content.addView(HourlyChart(ctx,hourly,dark(ctx)))
            for (h in hourly) {
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(dp(ctx,5),dp(ctx,6),dp(ctx,5),dp(ctx,6))
                }
                val time = String.format(java.util.Locale.getDefault(),"%02d:00–%02d:00",
                    h.hour,(h.hour+1) % 24)
                row.addView(text(ctx,time,13f),LinearLayout.LayoutParams(0,-2,1f))
                row.addView(text(ctx,ctx.getString(R.string.hourly_values,h.taps,h.points),13f,true))
                content.addView(row)
            }
        }
        content.addView(text(ctx,ctx.getString(R.string.activity_details),16f,true),
            LinearLayout.LayoutParams(-1,-2).apply {
                topMargin = dp(ctx,14); bottomMargin = dp(ctx,8)
            })
        content.addView(text(ctx,activity,12f))
        scroll.addView(content)
        root.addView(scroll,LinearLayout.LayoutParams(-1,
            dp(ctx, (220 + hourly.size * 34).coerceIn(200,380))
                .coerceAtMost((ctx.resources.displayMetrics.heightPixels*0.53).toInt())))
        root.addView(button(ctx,ctx.getString(R.string.confirm),true) { d.dismiss() },
            LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(ctx,14) })
        open(ctx,d)
    }
}
