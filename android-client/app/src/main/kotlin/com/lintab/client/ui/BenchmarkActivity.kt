// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

package com.lintab.client.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lintab.client.capture.BenchmarkSession
import com.lintab.client.capture.HardwareProfiler
import com.lintab.client.capture.PipelineConfig
import com.lintab.client.capture.Prefs
import com.lintab.client.databinding.ActivityBenchmarkBinding

class BenchmarkActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBenchmarkBinding
    private val session = BenchmarkSession()
    private val handler = Handler(Looper.getMainLooper())
    private var started = false

    private val DURATION_MS = 5_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityBenchmarkBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.benchmarkCanvas.setOnTouchListener { _, event ->
            onBenchmarkTouch(event)
            true
        }
    }

    private fun onBenchmarkTouch(event: MotionEvent) {
        // Detect hardware from first event before benchmark starts
        if (!HardwareProfiler.isProfileBuilt) {
            HardwareProfiler.profileFromEvent(event)
        }

        // Add trail visual feedback
        if (event.actionMasked != MotionEvent.ACTION_UP &&
            event.actionMasked != MotionEvent.ACTION_CANCEL) {
            binding.trailView.addPoint(event.x, event.y)
        }

        if (!started && event.actionMasked == MotionEvent.ACTION_DOWN) {
            startBenchmark()
        }

        session.onMotionEvent(event)
    }

    private fun startBenchmark() {
        started = true
        session.start()
        binding.tvStatus.text = "MIDIENDO..."

        val tickMs = 50L
        var elapsed = 0L

        val tick = object : Runnable {
            override fun run() {
                elapsed += tickMs
                val pct = ((elapsed * 100) / DURATION_MS).toInt().coerceIn(0, 100)
                binding.progressBar.progress = pct

                if (elapsed >= DURATION_MS) {
                    finishBenchmark()
                } else {
                    handler.postDelayed(this, tickMs)
                }
            }
        }
        handler.postDelayed(tick, tickMs)
    }

    private fun finishBenchmark() {
        binding.tvStatus.text = "ANALIZANDO..."
        val metrics = session.stop()

        // Refine profile using benchmark metrics
        val baseProfile = HardwareProfiler.cached
            ?: HardwareProfiler.profileFromEvent(
                // Fallback: no event available, use defaults embedded in profile
                android.view.MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
            )

        val refinedProfile = session.refineProfile(baseProfile, metrics)
        val config = PipelineConfig.forProfile(refinedProfile, refinedProfile.qualityTier.let {
            // Default to absolute (drawing) for auto-detected hardware
            com.lintab.client.capture.OperatingMode.ABSOLUTE
        })

        Prefs.saveConfig(this, config)
        Prefs.saveHwTier(this, refinedProfile.qualityTier)
        Prefs.setBenchmarkDone(this)

        binding.tvStatus.text = "LISTO — TIER: ${refinedProfile.qualityTier.name}"

        handler.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 1_200)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        HardwareProfiler.onHoverEvent(event)
        return super.onGenericMotionEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
