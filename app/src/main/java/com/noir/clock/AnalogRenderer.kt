package com.noir.clock

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

/**
 * One renderer, used by both the in-app live preview (drawn every frame)
 * and the home-screen widget (drawn into a Bitmap once a minute).
 * Everything is proportional to [size], so it scales to any widget size.
 */
object AnalogRenderer {

    fun dialScale(sizePct: Int): Float = (sizePct / 110f).coerceIn(0.5f, 1f)

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
    }

    fun draw(
        canvas: Canvas,
        size: Float,
        timeMillis: Long,
        s: ClockSettings,
        secondHand: Boolean,
        dateText: String?,
        dialScale: Float = 1f,
    ) {
        val cal = Calendar.getInstance().apply { timeInMillis = timeMillis }
        val sec = cal.get(Calendar.SECOND) + cal.get(Calendar.MILLISECOND) / 1000f
        val min = cal.get(Calendar.MINUTE) + sec / 60f
        val hour = (cal.get(Calendar.HOUR) % 12) + min / 60f

        val accent = s.accentArgb
        val cx = size / 2f
        val cy = size / 2f
        val r = size / 2f * 0.96f * dialScale

        val white = 0xFFF2F2F2.toInt()

        // ---- face -------------------------------------------------------
        val face = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy - r * 0.4f, r * 1.4f,
                intArrayOf(0xFF1B1B1F.toInt(), 0xFF0B0B0C.toInt(), 0xFF040404.toInt()),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy, r, face)

        // soft accent ambience at the bottom of the dial
        canvas.save()
        canvas.clipPath(Path().apply { addCircle(cx, cy, r, Path.Direction.CW) })
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy + r * 0.95f, r * 0.95f,
                withAlpha(accent, 0x2E), withAlpha(accent, 0x00),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy + r * 0.95f, r * 0.95f, glow)
        canvas.restore()

        // rim + inner hairline
        val rimW = r * 0.012f
        val rim = stroke(0xFFFFFFFF.toInt(), rimW).apply {
            shader = LinearGradient(
                cx - r, cy - r, cx + r, cy + r,
                0x70FFFFFF, 0x14FFFFFF, Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy, r - rimW / 2f, rim)
        canvas.drawCircle(cx, cy, r * 0.90f, stroke(0x0FFFFFFF, r * 0.004f))

        // ---- markers ----------------------------------------------------
        fun tick(deg: Float, inner: Float, outer: Float, w: Float, color: Int) {
            canvas.save()
            canvas.rotate(deg, cx, cy)
            canvas.drawLine(cx, cy - r * inner, cx, cy - r * outer, stroke(color, w))
            canvas.restore()
        }

        fun dot(deg: Float, radiusFrac: Float, dotR: Float, color: Int) {
            val rad = Math.toRadians(deg.toDouble())
            val x = cx + (r * radiusFrac * sin(rad)).toFloat()
            val y = cy - (r * radiusFrac * cos(rad)).toFloat()
            canvas.drawCircle(x, y, dotR, fill(color))
        }

        when (s.layout) {
            1 -> for (i in 0 until 12) {
                val big = i % 3 == 0
                dot(
                    i * 30f, 0.80f, r * (if (big) 0.028f else 0.020f),
                    if (i == 0) accent else if (big) white else 0x99FFFFFF.toInt(),
                )
            }

            2 -> for (i in 0 until 12) {
                if (i % 3 == 0) {
                    tick(i * 30f, 0.70f, 0.87f, r * 0.022f, if (i == 0) accent else white)
                } else {
                    dot(i * 30f, 0.84f, r * 0.011f, 0x59FFFFFF)
                }
            }

            else -> for (i in 0 until 60) {
                if (i % 5 == 0) {
                    tick(i * 6f, 0.76f, 0.88f, r * 0.020f, if (i == 0) accent else 0xEBF2F2F2.toInt())
                } else {
                    tick(i * 6f, 0.845f, 0.88f, r * 0.007f, 0x47FFFFFF)
                }
            }
        }

        // ---- date pill --------------------------------------------------
        if (dateText != null) {
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xB3FFFFFF.toInt()
                textSize = r * 0.10f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                letterSpacing = 0.14f
                textAlign = Paint.Align.CENTER
            }
            val tw = tp.measureText(dateText)
            val padX = r * 0.07f
            val h = r * 0.17f
            val top = cy + r * 0.34f
            canvas.drawRoundRect(
                RectF(cx - tw / 2f - padX, top, cx + tw / 2f + padX, top + h),
                h / 2f, h / 2f, fill(0x14FFFFFF),
            )
            canvas.drawText(dateText, cx, top + h / 2f - (tp.descent() + tp.ascent()) / 2f, tp)
        }

        // ---- hands ------------------------------------------------------
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x73000000
            maskFilter = BlurMaskFilter(r * 0.025f, BlurMaskFilter.Blur.NORMAL)
        }

        fun hand(deg: Float, length: Float, tail: Float, baseW: Float, tipW: Float, color: Int) {
            val path = Path().apply {
                moveTo(cx - baseW / 2f, cy + r * tail)
                lineTo(cx - tipW / 2f, cy - r * length)
                lineTo(cx + tipW / 2f, cy - r * length)
                lineTo(cx + baseW / 2f, cy + r * tail)
                close()
                addCircle(cx, cy - r * length, tipW / 2f, Path.Direction.CW)
                addCircle(cx, cy + r * tail, baseW / 2f, Path.Direction.CW)
            }
            // shadow first, in the un-rotated light direction
            canvas.save()
            canvas.translate(r * 0.012f, r * 0.03f)
            canvas.rotate(deg, cx, cy)
            canvas.drawPath(path, shadow)
            canvas.restore()

            canvas.save()
            canvas.rotate(deg, cx, cy)
            canvas.drawPath(path, fill(color))
            canvas.restore()
        }

        hand(hour * 30f, 0.52f, 0.12f, r * 0.062f, r * 0.042f, white)
        hand(min * 6f, 0.80f, 0.14f, r * 0.046f, r * 0.026f, white)

        if (secondHand) {
            canvas.save()
            canvas.rotate(sec * 6f, cx, cy)
            val glowPaint = stroke(withAlpha(accent, 0x66), r * 0.022f).apply {
                maskFilter = BlurMaskFilter(r * 0.03f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawLine(cx, cy + r * 0.22f, cx, cy - r * 0.88f, glowPaint)
            canvas.drawLine(cx, cy + r * 0.22f, cx, cy - r * 0.88f, stroke(accent, r * 0.008f))
            canvas.drawCircle(cx, cy + r * 0.22f, r * 0.016f, fill(accent))
            canvas.restore()
        }

        // ---- centre cap -------------------------------------------------
        canvas.drawCircle(cx, cy, r * 0.05f, fill(accent))
        canvas.drawCircle(cx, cy, r * 0.022f, fill(0xFF050505.toInt()))
    }
}
