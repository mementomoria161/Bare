package com.mementomoria161.bare

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.util.AttributeSet
import android.widget.ProgressBar

class CapsuleProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ProgressBar(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(context, 3f)
    }
    private val path = Path()
    private val dstPath = Path()
    private val pathMeasure = PathMeasure()

    init {
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)
        paint.color = typedValue.data
        
        // Disable default progress drawables so they don't draw under our custom path
        progressDrawable = null
        indeterminateDrawable = null
    }

    fun setStrokeColor(color: Int) {
        paint.color = color
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val progressVal = progress.toFloat() / max.toFloat()
        if (progressVal <= 0f) return

        val w = width.toFloat()
        val h = height.toFloat()
        val stroke = paint.strokeWidth
        
        val left = stroke / 2f
        val top = stroke / 2f
        val right = w - stroke / 2f
        val bottom = h - stroke / 2f
        val r = h / 2f - stroke / 2f

        val cx = w / 2f
        path.reset()
        
        // Start from top-middle point and trace clockwise
        path.moveTo(cx, top)
        path.lineTo(right - r, top)
        path.arcTo(RectF(right - 2 * r, top, right, top + 2 * r), -90f, 90f, false)
        path.lineTo(right, bottom - r)
        path.arcTo(RectF(right - 2 * r, bottom - 2 * r, right, bottom), 0f, 90f, false)
        path.lineTo(left + r, bottom)
        path.arcTo(RectF(left, bottom - 2 * r, left + 2 * r, bottom), 90f, 90f, false)
        path.lineTo(left, top + r)
        path.arcTo(RectF(left, top, left + 2 * r, top + 2 * r), 180f, 90f, false)
        path.close()

        pathMeasure.setPath(path, false)
        val length = pathMeasure.length
        dstPath.reset()
        pathMeasure.getSegment(0f, progressVal * length, dstPath, true)
        canvas.drawPath(dstPath, paint)
    }

    private fun dpToPx(context: Context, dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }
}
