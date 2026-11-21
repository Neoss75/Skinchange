package com.neos.skinchange.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.SystemClock

/**
 * Fully rounded button background: a fill, a progress pill growing from the left while a skin
 * is being switched, and a glint of light sweeping across now and then.
 */
class PillDrawable(private val density: Float) : Drawable() {

    var fillColor = 0x26FFFFFF
        set(value) { field = value; invalidateSelf() }
    var strokeColor = 0x40FFFFFF
        set(value) { field = value; invalidateSelf() }
    var progressColor = 0x66FFFFFF
        set(value) { field = value; invalidateSelf() }

    /** 0..1; 0 hides the progress pill. */
    var progress = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidateSelf() }

    /** Colour of the sweeping glint; 0 turns it off. It redraws itself while on. */
    var shimmerColor = 0
        set(value) {
            if (field == value) return
            field = value
            buildShimmer()
            invalidateSelf()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val matrix = Matrix()
    private var shimmer: Shader? = null
    private val band get() = 90 * density

    override fun onBoundsChange(bounds: Rect) {
        rect.set(bounds)
        buildShimmer()
    }

    private fun buildShimmer() {
        shimmer = if (shimmerColor == 0) null else LinearGradient(
            0f, 0f, band, 0f,
            intArrayOf(shimmerColor and 0x00FFFFFF, shimmerColor, shimmerColor and 0x00FFFFFF), null,
            Shader.TileMode.CLAMP,
        )
    }

    override fun draw(canvas: Canvas) {
        val r = rect.height() / 2
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = fillColor
        canvas.drawRoundRect(rect, r, r, paint)

        if (progress > 0f) {
            paint.color = progressColor
            val right = rect.left + maxOf(rect.height(), rect.width() * progress)
            canvas.drawRoundRect(rect.left, rect.top, right, rect.bottom, r, r, paint)
        }

        shimmer?.let { s ->
            val phase = ((SystemClock.uptimeMillis() - Motion.epoch) % SHIMMER_PERIOD_MS) / SHIMMER_SWEEP_MS.toFloat()
            if (phase < 1f) {
                matrix.setTranslate(rect.left - band + (rect.width() + 2 * band) * phase, 0f)
                matrix.postSkew(-0.35f, 0f, 0f, rect.centerY())
                s.setLocalMatrix(matrix)
                paint.shader = s
                canvas.drawRoundRect(rect, r, r, paint)
                paint.shader = null
            }
            invalidateSelf()
        }

        if ((strokeColor ushr 24) != 0) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = density
            paint.color = strokeColor
            val h = density / 2
            canvas.drawRoundRect(rect.left + h, rect.top + h, rect.right - h, rect.bottom - h, r - h, r - h, paint)
        }
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val SHIMMER_PERIOD_MS = 2600L
        const val SHIMMER_SWEEP_MS = 1000L
    }
}
