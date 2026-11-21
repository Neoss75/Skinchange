package com.neos.skinchange.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * The badge behind the palette icon: a breathing glow, a spinning ring of light and three
 * dots orbiting it. [flare] makes it flash when a skin has just been switched on.
 */
class HeroBadgeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var animated = true
        set(value) {
            field = value
            invalidate()
        }

    var accent = 0xFFFFFFFF.toInt()
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var glow: Shader? = null
    private var sweep: Shader? = null
    private var flareStart = -1L

    fun flare() {
        if (!animated) return
        flareStart = SystemClock.uptimeMillis()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val r = min(w, h) / 2f
        glow = RadialGradient(
            0f, 0f, r, intArrayOf(0x80FFFFFF.toInt(), 0x26FFFFFF, 0x00FFFFFF), floatArrayOf(0.3f, 0.7f, 1f),
            Shader.TileMode.CLAMP,
        )
        sweep = SweepGradient(0f, 0f, intArrayOf(0x00FFFFFF, 0x00FFFFFF, 0xFFFFFFFF.toInt()), floatArrayOf(0f, 0.35f, 1f))
    }

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height) / 2f
        if (size <= 0) return
        val now = SystemClock.uptimeMillis()
        val t = if (animated) (now - Motion.epoch) / 1000f else 0f
        val flare = if (flareStart < 0) 0f else {
            val f = (now - flareStart) / 1200f
            if (f >= 1f) { flareStart = -1L; 0f } else exp(-4f * f) * sin(f * Math.PI.toFloat())*2f
        }

        canvas.save()
        canvas.translate(width / 2f, height / 2f)

        // Breathing glow.
        val breath = 0.5f + 0.5f * sin(t * 2.2f)
        glowPaint.shader = glow
        glowPaint.alpha = (110 + 90 * breath + 55 * flare).toInt().coerceAtMost(255)
        canvas.drawCircle(0f, 0f, size * (0.92f + 0.08f * breath + 0.1f * flare), glowPaint)

        // Glass disc.
        discPaint.color = 0x33FFFFFF
        canvas.drawCircle(0f, 0f, size * 0.62f, discPaint)

        // Spinning ring of light.
        canvas.save()
        canvas.rotate(t * 110f)
        ringPaint.shader = sweep
        ringPaint.strokeWidth = 3.5f * density
        canvas.drawCircle(0f, 0f, size * 0.74f, ringPaint)
        canvas.restore()

        // Orbiting dots.
        for (i in 0 until 3) {
            val a = t * (0.9f + i * 0.45f) * (if (i % 2 == 0) 1 else -1) + i * 2.1f
            val r = size * (0.84f + 0.04f * i)
            dotPaint.color = if (i == 1) 0xFFFFFFFF.toInt() else accent
            dotPaint.alpha = 230
            canvas.drawCircle(cos(a) * r, sin(a) * r, (2.5f + i) * density, dotPaint)
        }
        canvas.restore()

        if ((animated || flareStart >= 0) && windowVisibility == VISIBLE) postInvalidateOnAnimation()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) invalidate()
    }
}
