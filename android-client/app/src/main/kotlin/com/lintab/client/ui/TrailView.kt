// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

package com.lintab.client.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.lintab.client.R

/**
 * Transparent overlay that draws a fading orange trail following the stylus.
 *
 * Points are added via [addPoint] on every touch event and automatically
 * expire after [TRAIL_DURATION_MS], creating a comet-tail effect.
 */
class TrailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private data class TrailPoint(val x: Float, val y: Float, val time: Long)

    private val points  = ArrayDeque<TrailPoint>(MAX_POINTS)
    private val paint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color   = context.getColor(R.color.signal_orange)
        style   = Paint.Style.FILL
    }

    fun addPoint(x: Float, y: Float) {
        val now = System.currentTimeMillis()
        points.addLast(TrailPoint(x, y, now))
        // Remove points beyond the max capacity to bound memory
        while (points.size > MAX_POINTS) points.removeFirst()
        invalidate()
    }

    fun clear() {
        points.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.currentTimeMillis()
        val iter = points.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            val age = now - p.time
            if (age > TRAIL_DURATION_MS) {
                iter.remove()
                continue
            }
            // age 0 → alpha 220, age TRAIL_DURATION_MS → alpha 0
            val alpha = ((1f - age.toFloat() / TRAIL_DURATION_MS) * 220f).toInt()
            // radius shrinks with age: newest = MAX_RADIUS, oldest = MIN_RADIUS
            val radius = MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * (1f - age.toFloat() / TRAIL_DURATION_MS)
            paint.alpha = alpha
            canvas.drawCircle(p.x, p.y, radius, paint)
        }

        // Keep animating as long as there are live points
        if (points.isNotEmpty()) {
            postInvalidateOnAnimation()
        }
    }

    companion object {
        private const val TRAIL_DURATION_MS = 500L
        private const val MAX_POINTS        = 120
        private const val MAX_RADIUS        = 10f
        private const val MIN_RADIUS        = 2f
    }
}
