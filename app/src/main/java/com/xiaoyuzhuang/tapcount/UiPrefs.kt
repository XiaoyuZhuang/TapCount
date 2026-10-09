package com.xiaoyuzhuang.tapcount

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object UiPrefs {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("tapcount", Context.MODE_PRIVATE)
    fun bool(ctx: Context, key: String, default: Boolean = false) =
        prefs(ctx).getBoolean(key, default)
    fun setBool(ctx: Context, key: String, value: Boolean) =
        prefs(ctx).edit().putBoolean(key, value).apply()
    fun text(ctx: Context, key: String, default: String = "system") =
        prefs(ctx).getString(key, default) ?: default
    fun setText(ctx: Context, key: String, value: String) =
        prefs(ctx).edit().putString(key, value).apply()

    fun dark(ctx: Context): Boolean = when (text(ctx, "theme")) {
        "dark" -> true
        "light" -> false
        else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    fun localize(base: Context): Context {
        val language = text(base, "language")
        if (language == "system") return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(if (language == "zh") Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH)
        return base.createConfigurationContext(config)
    }
}
