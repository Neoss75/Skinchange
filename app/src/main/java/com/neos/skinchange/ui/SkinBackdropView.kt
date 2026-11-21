package com.neos.skinchange.ui

import android.animation.ArgbEvaluator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Full-screen ambience behind the main screen: big glowing colour clouds drifting across,
 * bubbles rising from the bottom, and on a switch a wave of light with a burst of sparks.
 */
class SkinBackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** When false the picture stands still (the app's "Animations" setting). */
    var animated = true
        set(value) {
            field = value
            invalidate()
        }

    private class Bubble(
        val u: Float, val size: Float, val speed: Float, val offset: Float,
        val wobble: Float, val phase: Float, val ring: Boolean,
    )

    private class Spark(val dx: Float, val dy: Float, val distance: Float, val size: Float, val accent: Boolean)

    private val density = resources.displayMetrics.density
    private val random = Random(75)
    private val bubbles = List(30) {
        Bubble(
            u = random.nextFloat(),
            size = (3 + random.nextFloat() * 9) * density,
            speed = (22 + random.nextFloat() * 50) * density,
            offset = random.nextFloat(),
            wobble = 0.15f + random.nextFloat() * 0.3f,
            phase = random.nextFloat() * 6.28f,
            ring = random.nextFloat() < 0.4f,
        )
    }

    private val blobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var blobShader: Shader? = null
    private var blobRadius = 0f
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val argb = ArgbEvaluator()

    private var accentFrom = 0xFFFFFFFF.toInt()
    private var accentTo = 0xFFFFFFFF.toInt()
    private var accentSince = 0L

    private var waveX = 0f
    private var waveY = 0f
    private var waveStart = -1L
    private var sparks: List<Spark> = emptyList()

    /** The glow takes the accent colour of the skin, blending over a second. */
    fun setAccent(color: Int) {
        if (color == accentTo) return
        accentFrom = currentAccent(SystemClock.uptimeMillis())
        accentTo = color
        accentSince = SystemClock.uptimeMillis()
        invalidate()
    }

    private fun currentAccent(now: Long): Int {
        val f = ((now - accentSince) / ACCENT_MS.toFloat()).coerceIn(0f, 1f)
        return if (!animated || f >= 1f) accentTo else argb.evaluate(f, accentFrom, accentTo) as Int
    }

    /** A wave of light and a burst of sparks from ([x], [y]) in this view's coordinates. */
    fun burst(x: Float, y: Float) {
        if (!animated) return
        waveX = x
        waveY = y
        waveStart = SystemClock.uptimeMillis()
        sparks = List(34) {
            val a = random.nextFloat() * 2 * PI.toFloat()
            Spark(cos(a), sin(a), (90 + random.nextFloat() * 220) * density, (2 + random.nextFloat() * 4) * density, it % 2 == 0)
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        blobRadius = max(w, h) * 0.55f
        blobShader = RadialGradient(
            0f, 0f, blobRadius,
            intArrayOf(0xFFFFFFFF.toInt(), 0x80FFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val now = SystemClock.uptimeMillis()
        val t = if (animated) (now - Motion.epoch) / 1000f else 0f
        val accent = currentAccent(now)

        // Colour clouds: two of the accent colour and one white, on slow Lissajous paths.
        blobPaint.shader = blobShader
        drawBlob(canvas, w * (0.25f + 0.35f * sin(t * TWO_PI / 13f)), h * (0.2f + 0.25f * sin(t * TWO_PI / 17f + 1f)), accent, 0x66)
        drawBlob(canvas, w * (0.75f + 0.3f * sin(t * TWO_PI / 15f + 2f)), h * (0.8f + 0.25f * cos(t * TWO_PI / 11f)), accent, 0x55)
        drawBlob(canvas, w * (0.5f + 0.4f * cos(t * TWO_PI / 19f)), h * (0.5f + 0.3f * sin(t * TWO_PI / 23f + 3f)), 0xFFFFFFFF.toInt(), 0x30)

        // Bubbles rising from the bottom, fading out near the top.
        for (b in bubbles) {
            val span = h + 4 * b.size
            val y = h + 2 * b.size - ((t * b.speed + b.offset * span) % span)
            val x = b.u * w + sin(t * b.wobble * TWO_PI + b.phase) * 14 * density
            val fade = (y / h).coerceIn(0f, 1f)
            bubblePaint.color = if (b.ring) 0xFFFFFFFF.toInt() else accent
            bubblePaint.alpha = ((if (b.ring) 110 else 80) * fade).toInt()
            bubblePaint.style = if (b.ring) Paint.Style.STROKE else Paint.Style.FILL
            bubblePaint.strokeWidth = 1.5f * density
            canvas.drawCircle(x, y, b.size, bubblePaint)
        }

        if (waveStart >= 0) {
            val f = (now - waveStart) / WAVE_MS.toFloat()
            if (f >= 1f) {
                waveStart = -1L
            } else {
                val eased = Motion.enter.getInterpolation(f)
                val radius = eased * max(w, h) * 1.2f
                for (ring in 0 until 3) {
                    val r = radius - ring * 40 * density
                    if (r <= 0) continue
                    wavePaint.color = if (ring == 1) accent else 0xFFFFFFFF.toInt()
                    wavePaint.strokeWidth = (14 - ring * 4) * density * (1f - f) + density
                    wavePaint.alpha = ((1f - f) * (170 - ring * 45)).toInt().coerceAtLeast(0)
                    canvas.drawCircle(waveX, waveY, r, wavePaint)
                }
                val fly = Motion.enter.getInterpolation((f * 1.3f).coerceAtMost(1f))
                for (s in sparks) {
                    sparkPaint.color = if (s.accent) accent else 0xFFFFFFFF.toInt()
                    sparkPaint.alpha = ((1f - f) * 255).toInt()
                    canvas.drawCircle(
                        waveX + s.dx * s.distance * fly,
                        waveY + s.dy * s.distance * fly + 60 * density * f * f, // a little gravity
                        s.size * (1f - 0.5f * f), sparkPaint,
                    )
                }
            }
        }

        val blending = now - accentSince < ACCENT_MS
        if ((animated || waveStart >= 0 || blending) && windowVisibility == VISIBLE) postInvalidateOnAnimation()
    }

    private val whiteFilter = PorterDuffColorFilter(0xFFFFFFFF.toInt(), PorterDuff.Mode.SRC_IN)
    private var accentFilter = whiteFilter
    private var accentFilterColor = 0xFFFFFFFF.toInt()

    private fun drawBlob(canvas: Canvas, x: Float, y: Float, color: Int, alpha: Int) {
        val opaque = color or 0xFF000000.toInt()
        if (opaque != 0xFFFFFFFF.toInt() && opaque != accentFilterColor) {
            accentFilterColor = opaque
            accentFilter = PorterDuffColorFilter(opaque, PorterDuff.Mode.SRC_IN)
        }
        blobPaint.colorFilter = if (opaque == 0xFFFFFFFF.toInt()) whiteFilter else accentFilter
        blobPaint.alpha = alpha
        canvas.save()
        canvas.translate(x, y)
        canvas.drawCircle(0f, 0f, blobRadius, blobPaint)
        canvas.restore()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) invalidate()
    }

    private companion object {
        const val WAVE_MS = 1300L
        const val ACCENT_MS = 1000L
        const val TWO_PI = (2 * PI).toFloat()
    }
}
