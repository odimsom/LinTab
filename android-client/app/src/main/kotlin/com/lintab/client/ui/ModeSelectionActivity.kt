// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

package com.lintab.client.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lintab.client.capture.OperatingMode
import com.lintab.client.capture.PipelineConfig
import com.lintab.client.capture.Prefs
import com.lintab.client.databinding.ActivityModeSelectionBinding

class ModeSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityModeSelectionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityModeSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cardDrawing.setOnClickListener  { selectMode(OperatingMode.ABSOLUTE,  drawing = true) }
        binding.cardNotes.setOnClickListener    { selectMode(OperatingMode.ABSOLUTE,  drawing = false) }
        binding.cardTouchpad.setOnClickListener { selectMode(OperatingMode.RELATIVE,  drawing = false) }
        binding.cardAuto.setOnClickListener     { launchBenchmark() }
    }

    private fun selectMode(mode: OperatingMode, drawing: Boolean) {
        val config = PipelineConfig(
            mode             = mode,
            delayCommitMs    = if (drawing) 6L else 4L,
            smoothingAlpha   = if (drawing) 0.2f else 0.1f,
            predictionMs     = if (mode == OperatingMode.RELATIVE) 10L else 8L,
            touchSlopPx      = if (mode == OperatingMode.RELATIVE) 10f else 5f,
            fakeHoverEnabled = false,
        )
        Prefs.saveConfig(this, config)
        launchMain()
    }

    private fun launchBenchmark() {
        startActivity(Intent(this, BenchmarkActivity::class.java))
        finish()
    }

    private fun launchMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
