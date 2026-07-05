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
        
        // Outlining path offset by half of the stroke width to prevent clipping at the card edges
        val rx = dpToPx(context, 40f) - stroke / 2f
        val ry = dpToPx(context, 40f) - stroke / 2f

        val cx = w / 2f
        path.reset()
        
        // Start from top-middle point and trace clockwise
        path.moveTo(cx, stroke / 2f)
        path.lineTo(w - rx, stroke / 2f)
        path.arcTo(RectF(w - 2 * rx, stroke / 2f, w, 2 * ry), -90f, 90f, false)
        path.lineTo(w, h - ry)
        path.arcTo(RectF(w - 2 * rx, h - 2 * ry, w, h), 0f, 90f, false)
        path.lineTo(rx, h - stroke / 2f)
        path.arcTo(RectF(stroke / 2f, h - 2 * ry, 2 * rx, h), 90f, 90f, false)
        path.lineTo(stroke / 2f, ry)
        path.arcTo(RectF(stroke / 2f, stroke / 2f, 2 * rx, 2 * ry), 180f, 90f, false)
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
