// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

package com.lintab.client.capture

import android.view.MotionEvent
import kotlin.math.sqrt

/**
 * Adaptive input compensation pipeline.
 *
 * Applies up to five techniques in order:
 *  1. Touch-slop detection — distinguishes tap from draw intent.
 *  2. Exponential smoothing — reduces coordinate jitter.
 *  3. Linear velocity prediction — extrapolates forward to reduce perceived latency.
 *  4. Delayed commit — holds events in a ring buffer to allow trajectory stabilisation.
 *  5. Fake-hover interpolation — maintains a visible cursor between strokes.
 *
 * The active set of techniques and their parameters are controlled by [PipelineConfig],
 * which is produced by [PipelineConfig.forProfile] based on detected hardware quality.
 */
class InputPipeline(private var config: PipelineConfig) {

    data class CommitPoint(
        val x: Float,
        val y: Float,
        val pressure: Int,
        val tiltX: Int,
        val tiltY: Int,
        val timestampMs: Long,
        val isUp: Boolean = false,
    )

    // Smoothing state
    private var smoothedX = 0f
    private var smoothedY = 0f
    private var isFirst = true

    // Velocity estimation for prediction
    private var lastX = 0f
    private var lastY = 0f
    private var velX = 0f
    private var velY = 0f
    private var lastTs = 0L

    // Touch-slop state
    private var downX = 0f
    private var downY = 0f
    private var slopResolved = false

    // Delayed-commit buffer
    private val pending = ArrayDeque<CommitPoint>()

    fun updateConfig(c: PipelineConfig) {
        config = c
        reset()
    }

    fun reset() {
        isFirst = true
        slopResolved = false
        pending.clear()
        velX = 0f
        velY = 0f
    }

    fun process(event: MotionEvent): List<CommitPoint> {
        val rawX = event.x
        val rawY = event.y
        val pr = (event.pressure * 8191f).toInt().coerceIn(0, 8191)
        val tx = Math.toDegrees(event.getAxisValue(MotionEvent.AXIS_TILT).toDouble())
            .toInt().coerceIn(-90, 90)
        val ty = Math.toDegrees(event.getAxisValue(MotionEvent.AXIS_ORIENTATION).toDouble())
            .toInt().coerceIn(-90, 90)
        val ts = event.eventTime

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> onDown(rawX, rawY, pr, tx, ty, ts)
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL       -> onUp(rawX, rawY, tx, ty, ts)
            else                             -> onMove(rawX, rawY, pr, tx, ty, ts)
        }
    }

    private fun onDown(x: Float, y: Float, pr: Int, tx: Int, ty: Int, ts: Long): List<CommitPoint> {
        downX = x; downY = y
        smoothedX = x; smoothedY = y
        lastX = x; lastY = y
        velX = 0f; velY = 0f
        lastTs = ts
        isFirst = true
        pending.clear()
        slopResolved = config.touchSlopPx <= 0f

        return if (slopResolved) listOf(CommitPoint(x, y, pr, tx, ty, ts)) else emptyList()
    }

    private fun onMove(x: Float, y: Float, pr: Int, tx: Int, ty: Int, ts: Long): List<CommitPoint> {
        // ── 1. Touch slop ─────────────────────────────────────────────────────
        if (!slopResolved) {
            val dx = x - downX; val dy = y - downY
            if (sqrt(dx * dx + dy * dy) < config.touchSlopPx) return emptyList()
            slopResolved = true
            // Flush the held-back ACTION_DOWN now that we know this is a draw gesture
            val holdTs = (ts - config.delayCommitMs).coerceAtLeast(0L)
            pending.addLast(CommitPoint(downX, downY, 0, tx, ty, holdTs))
        }

        // ── 2. Exponential smoothing ──────────────────────────────────────────
        val sx: Float; val sy: Float
        if (config.smoothingAlpha > 0f && !isFirst) {
            val inv = 1f - config.smoothingAlpha
            smoothedX += (x - smoothedX) * inv
            smoothedY += (y - smoothedY) * inv
            sx = smoothedX; sy = smoothedY
        } else {
            smoothedX = x; smoothedY = y
            sx = x; sy = y
        }

        // ── 3. Velocity for prediction ────────────────────────────────────────
        val dt = (ts - lastTs).coerceAtLeast(1L)
        velX = (sx - lastX) / dt
        velY = (sy - lastY) / dt
        lastX = sx; lastY = sy; lastTs = ts
        isFirst = false

        // ── 4. Prediction ─────────────────────────────────────────────────────
        val finalX = if (config.predictionMs > 0L) sx + velX * config.predictionMs else sx
        val finalY = if (config.predictionMs > 0L) sy + velY * config.predictionMs else sy

        val pt = CommitPoint(finalX, finalY, pr, tx, ty, ts)

        // ── 5. Delayed commit ─────────────────────────────────────────────────
        return if (config.delayCommitMs > 0L) {
            pending.addLast(pt)
            drainExpired(ts)
        } else {
            listOf(pt)
        }
    }

    private fun onUp(x: Float, y: Float, tx: Int, ty: Int, ts: Long): List<CommitPoint> {
        val result = mutableListOf<CommitPoint>()
        result.addAll(drainAll())
        result.add(CommitPoint(x, y, 0, tx, ty, ts, isUp = true))
        reset()
        return result
    }

    private fun drainExpired(nowMs: Long): List<CommitPoint> {
        val cutoff = nowMs - config.delayCommitMs
        val out = mutableListOf<CommitPoint>()
        while (pending.isNotEmpty() && pending.first().timestampMs <= cutoff) {
            out.add(pending.removeFirst())
        }
        return out
    }

    private fun drainAll(): List<CommitPoint> = pending.toList().also { pending.clear() }
}
