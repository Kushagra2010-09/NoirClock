package com.noir.clock

import android.content.Context

const val STYLE_DIGITAL = 0
const val STYLE_ANALOG = 1

data class Accent(val name: String, val argb: Int)

val ACCENTS = listOf(
    Accent("Ice", 0xFF8FB8FF.toInt()),
    Accent("Ember", 0xFFFF7A59.toInt()),
    Accent("Mint", 0xFF6EE7B7.toInt()),
    Accent("Violet", 0xFFB794F6.toInt()),
    Accent("Amber", 0xFFFFB84D.toInt()),
    Accent("Mono", 0xFFE6E6E6.toInt()),
)

/**
 * style:  0 = digital, 1 = analog
 * layout: digital -> 0 classic, 1 stacked, 2 left
 *         analog  -> 0 ticks,   1 dots,    2 minimal
 */
data class ClockSettings(
    val style: Int = STYLE_DIGITAL,
    val is24h: Boolean = false,
    val seconds: Boolean = true,
    val showDate: Boolean = true,
    val accent: Int = 0,
    val sizePct: Int = 100,
    val layout: Int = 0,
) {
    val accentArgb: Int get() = ACCENTS[accent.coerceIn(0, ACCENTS.lastIndex)].argb
}

object ClockPrefs {
    private const val FILE = "noir_clock_prefs"

    fun load(context: Context): ClockSettings {
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val d = ClockSettings()
        return ClockSettings(
            style = p.getInt("style", d.style),
            is24h = p.getBoolean("is24h", d.is24h),
            seconds = p.getBoolean("seconds", d.seconds),
            showDate = p.getBoolean("showDate", d.showDate),
            accent = p.getInt("accent", d.accent),
            sizePct = p.getInt("sizePct", d.sizePct),
            layout = p.getInt("layout", d.layout),
        )
    }

    fun save(context: Context, s: ClockSettings) {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt("style", s.style)
            .putBoolean("is24h", s.is24h)
            .putBoolean("seconds", s.seconds)
            .putBoolean("showDate", s.showDate)
            .putInt("accent", s.accent)
            .putInt("sizePct", s.sizePct)
            .putInt("layout", s.layout)
            .apply()
    }
}
