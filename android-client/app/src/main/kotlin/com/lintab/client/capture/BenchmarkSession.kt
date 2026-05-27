// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

package com.lintab.client.capture

import android.view.MotionEvent
import kotlin.math.sqrt

class BenchmarkSession {

    data class Metrics(
        val jitterPx: Float,
        val samplingHz: Float,
        val latencyMs: Float,
        val trajectoryQuality: Float,
    )

    private data class Sample(
        val x: Float,
        val y: Float,
        val eventTimeMs: Long,
        val systemTimeMs: Long,
    )

    private val samples = mutableListOf<Sample>()
    private var startMs = 0L

    var isActive = false
        private set

    val durationMs: Long get() = if (isActive) System.currentTimeMillis() - startMs else 0L

    fun start() {
        samples.clear()
        isActive = true
        startMs = System.currentTimeMillis()
    }

    fun stop(): Metrics {
        isActive = false
        return analyze()
    }

    fun onMotionEvent(event: MotionEvent) {
        if (!isActive) return
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_DOWN) {
            samples.add(Sample(event.x, event.y, event.eventTime, System.currentTimeMillis()))
        }
    }

    private fun analyze(): Metrics {
        if (samples.size < 5) return Metrics(3f, 60f, 30f, 0.5f)

        val span = samples.last().eventTimeMs - samples.first().eventTimeMs
        val hz = if (span > 0) samples.size * 1000f / span else 60f

        var totalJitter = 0f
        for (i in 1 until samples.size - 1) {
            val mx = (samples[i - 1].x + samples[i + 1].x) / 2f
            val my = (samples[i - 1].y + samples[i + 1].y) / 2f
            val dx = samples[i].x - mx
            val dy = samples[i].y - my
            totalJitter += sqrt(dx * dx + dy * dy)
        }
        val jitter = if (samples.size > 2) totalJitter / (samples.size - 2) else 3f

        val avgLatency = samples
            .map { (it.systemTimeMs - it.eventTimeMs).toFloat() }
            .average().toFloat().coerceAtLeast(0f)

        val quality = 1f - (jitter / 10f).coerceIn(0f, 1f)

        return Metrics(
            jitterPx          = jitter.coerceIn(0f, 20f),
            samplingHz        = hz.coerceIn(10f, 360f),
            latencyMs         = avgLatency.coerceIn(0f, 200f),
            trajectoryQuality = quality,
        )
    }

    fun refineProfile(base: HardwareProfile, metrics: Metrics): HardwareProfile {
        val tier = when {
            metrics.jitterPx < 1.2f && metrics.samplingHz >= 100f -> QualityTier.PREMIUM
            metrics.jitterPx < 3.5f && metrics.samplingHz >= 50f  -> QualityTier.INTERMEDIATE
            else                                                    -> QualityTier.BASIC
        }
        return base.copy(
            jitterPx    = metrics.jitterPx,
            samplingHz  = metrics.samplingHz,
            latencyMs   = metrics.latencyMs,
            qualityTier = tier,
        )
    }
}
