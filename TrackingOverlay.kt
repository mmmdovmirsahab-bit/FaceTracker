package com.facetrack.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class TrackingOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB8F36B.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private var face: RectF? = null

    fun setFace(rect: Rect, sourceWidth: Int, sourceHeight: Int, rotation: Int) {
        // Preview overlay is an indicator only; it does not transform the encoded video.
        val mapped = if (rotation == 90 || rotation == 270) {
            RectF(rect.top.toFloat(), rect.left.toFloat(), rect.bottom.toFloat(), rect.right.toFloat())
        } else RectF(rect)
        val w = if (rotation == 90 || rotation == 270) sourceHeight.toFloat() else sourceWidth.toFloat()
        val h = if (rotation == 90 || rotation == 270) sourceWidth.toFloat() else sourceHeight.toFloat()
        val sx = width / w.coerceAtLeast(1f)
        val sy = height / h.coerceAtLeast(1f)
        // PreviewView fillCenter can crop edges; this scaled marker remains a helpful approximate indicator.
        face = RectF(mapped.left * sx, mapped.top * sy, mapped.right * sx, mapped.bottom * sy)
        invalidate()
    }

    fun clearFace() { face = null; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        face?.let {
            val pad = 12f
            canvas.drawRoundRect(RectF(it.left - pad, it.top - pad, it.right + pad, it.bottom + pad), 18f, 18f, paint)
            val cx = it.centerX()
            val cy = it.centerY()
            canvas.drawCircle(cx, cy, 4f, paint)
        }
    }
}
