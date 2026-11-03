package com.noir.clock

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * The real home-screen widget.
 *
 *  - Digital: built from <TextClock> views, so the *launcher itself* ticks them
 *    (every minute, or every second if enabled). Zero alarms, zero wake-ups from us.
 *  - Analog: drawn into a Bitmap with [AnalogRenderer] and refreshed once per minute
 *    by a single non-waking exact alarm that is only scheduled while an analog
 *    widget exists.
 */
class ClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshAll(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        refreshAll(context)
    }

    override fun onDisabled(context: Context) {
        cancelTick(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TICK,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> refreshAll(context)
        }
    }

    companion object {
        const val ACTION_TICK = "com.noir.clock.ACTION_TICK"

        /** Re-render every widget instance from the saved settings. */
        fun refreshAll(context: Context) {
            val app = context.applicationContext
            val mgr = AppWidgetManager.getInstance(app)
            val ids = mgr.getAppWidgetIds(ComponentName(app, ClockWidgetProvider::class.java))
            if (ids.isEmpty()) {
                cancelTick(app)
                return
            }
            val s = ClockPrefs.load(app)
            for (id in ids) {
                mgr.updateAppWidget(id, build(app, mgr, id, s))
            }
            if (s.style == STYLE_ANALOG) scheduleTick(app) else cancelTick(app)
        }

        // ------------------------------------------------------------------

        private fun build(ctx: Context, mgr: AppWidgetManager, id: Int, s: ClockSettings): RemoteViews {
            val opts = mgr.getAppWidgetOptions(id)
            val wDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).let { if (it <= 0) 160 else it }.toFloat()
            val hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0).let { if (it <= 0) 160 else it }.toFloat()

            val views = if (s.style == STYLE_ANALOG) analog(ctx, s, wDp, hDp) else digital(ctx, s, wDp, hDp)
            views.setOnClickPendingIntent(R.id.root, openAppIntent(ctx))
            return views
        }

        private fun digital(ctx: Context, s: ClockSettings, wDp: Float, hDp: Float): RemoteViews {
            val layoutRes = when (s.layout) {
                1 -> R.layout.widget_digital_stacked
                2 -> R.layout.widget_digital_left
                else -> R.layout.widget_digital_classic
            }
            val v = RemoteViews(ctx.packageName, layoutRes)
            val accent = s.accentArgb
            val showAmPm = !s.is24h
            val sideVisible = s.seconds || showAmPm

            // formats (same pattern for both so our own 12/24h setting always wins)
            fun fmt(id: Int, f: String) {
                v.setCharSequence(id, "setFormat12Hour", f)
                v.setCharSequence(id, "setFormat24Hour", f)
            }
            fmt(R.id.tc_hour, if (s.is24h) "HH" else "h")
            fmt(R.id.tc_min, "mm")
            fmt(R.id.tc_sec, "ss")
            fmt(R.id.tc_ampm, "a")
            fmt(R.id.tv_date, "EEE, d MMM")

            // size: fit the widget, then apply the user's size setting
            val base = if (s.layout == 1) {
                min(hDp * 0.30f, wDp / 2.5f)
            } else {
                min(hDp * 0.40f, wDp / (if (sideVisible) 4.3f else 3.5f))
            }
            val limit = if (s.layout == 1) wDp / 2.2f else wDp / (if (sideVisible) 3.8f else 3.1f)
            val size = (base * s.sizePct / 100f).coerceAtMost(limit).coerceIn(20f, 120f)

            fun dp(id: Int, v2: Float) = v.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_DIP, v2)
            dp(R.id.tc_hour, size)
            dp(R.id.tc_min, size)
            dp(R.id.tv_colon, size * 0.92f)
            dp(R.id.tc_sec, max(size * 0.30f, 11f))
            dp(R.id.tc_ampm, max(size * 0.25f, 10f))
            dp(R.id.tv_date, max(size * 0.22f, 10f))

            // colours
            v.setTextColor(R.id.tc_hour, 0xFFF4F4F4.toInt())
            v.setTextColor(R.id.tc_min, if (s.layout == 1) accent else 0xFFF4F4F4.toInt())
            v.setTextColor(R.id.tv_colon, accent)
            v.setTextColor(R.id.tc_sec, accent)
            v.setInt(R.id.accent_bar, "setColorFilter", accent)

            // visibility
            v.setViewVisibility(R.id.tc_sec, if (s.seconds) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.tc_ampm, if (showAmPm) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.col_side, if (sideVisible) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.tv_date, if (s.showDate) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.accent_bar, if (s.layout == 2) View.VISIBLE else View.GONE)
            return v
        }

        private fun analog(ctx: Context, s: ClockSettings, wDp: Float, hDp: Float): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_analog)
            val density = ctx.resources.displayMetrics.density
            val px = (min(wDp, hDp) * density).toInt().coerceIn(160, 560)
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)

            val now = System.currentTimeMillis()
            val minuteStart = now - now % 60_000L
            val date = if (s.showDate) {
                SimpleDateFormat("EEE d", Locale.getDefault()).format(Date(now)).uppercase(Locale.getDefault())
            } else null

            // Android can't redraw a widget every second, so the home-screen dial has no sweeping
            // second hand (the in-app preview does). That's the battery-friendly trade-off.
            AnalogRenderer.draw(Canvas(bmp), px.toFloat(), minuteStart, s, false, date, AnalogRenderer.dialScale(s.sizePct))
            v.setImageViewBitmap(R.id.iv_dial, bmp)
            return v
        }

        private fun openAppIntent(ctx: Context): PendingIntent {
            val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(ctx, 1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        // ---- minute tick for the analog widget ---------------------------

        private fun tickIntent(ctx: Context): PendingIntent {
            val i = Intent(ctx, ClockWidgetProvider::class.java).setAction(ACTION_TICK)
            return PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun scheduleTick(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val now = System.currentTimeMillis()
            val next = now - now % 60_000L + 60_000L + 300L
            val pi = tickIntent(ctx)
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                    am.setExact(AlarmManager.RTC, next, pi) // RTC (not RTC_WAKEUP): never wakes the phone
                } else {
                    am.set(AlarmManager.RTC, next, pi)
                }
            } catch (e: SecurityException) {
                am.set(AlarmManager.RTC, next, pi)
            }
        }

        private fun cancelTick(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(tickIntent(ctx))
        }
    }
}
