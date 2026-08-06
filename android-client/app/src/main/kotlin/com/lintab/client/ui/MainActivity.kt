// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

package com.lintab.client.ui

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lintab.client.BuildConfig
import com.lintab.client.capture.HardwareProfiler
import com.lintab.client.capture.InputPipeline
import com.lintab.client.capture.OperatingMode
import com.lintab.client.capture.Prefs
import com.lintab.client.capture.StylusCapture
import com.lintab.client.databinding.ActivityMainBinding
import com.lintab.client.transport.DaemonConnection
import com.lintab.client.util.UpdateChecker
import com.lintab.protocol.EventsProto.ToolType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var connection: DaemonConnection
    private lateinit var pipeline: InputPipeline

    private var screenWidth  = 0
    private var screenHeight = 0
    private var currentTool  = ToolType.TOOL_FINGER

    private val hudHideDelay = 2_000L
    private val hideHudTask  = Runnable { fadeHud(visible = false) }

    private val hideOverlayTask = Runnable {
        binding.overlayIdle.animate().alpha(0f).setDuration(500).withEndAction {
            binding.overlayIdle.visibility = View.GONE
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val dm      = resources.displayMetrics
        screenWidth = dm.widthPixels
        screenHeight= dm.heightPixels

        val config = Prefs.loadConfig(this)
        pipeline   = InputPipeline(config)

        connection = DaemonConnection(this).apply {
            onConnected    = { host -> runOnUiThread { onDaemonConnected(host) } }
            onDisconnected = {        runOnUiThread { onDaemonDisconnected()  } }
        }

        startPulseAnimation()
        showIdle()

        binding.drawingCanvas.setOnTouchListener { _, event -> onStylusEvent(event) }

        binding.fabMenu.setOnClickListener { openModeSelection() }

        UpdateDialogs.maybeShowWhatsNew(this, BuildConfig.VERSION_NAME)
        checkForUpdate()
    }

    private fun checkForUpdate() {
        CoroutineScope(Dispatchers.Main).launch {
            val result = UpdateChecker.check(BuildConfig.VERSION_NAME) ?: return@launch
            if (result.updateAvailable) {
                UpdateDialogs.showUpdateAvailable(this@MainActivity, result)
            }
        }
    }

    // ── Connection state ─────────────────────────────────────────────────────

    private fun onDaemonConnected(host: String) {
        val mode = Prefs.loadConfig(this).mode
        binding.tvIdleLabel.text = "LISTO — MODO ${mode.name}. DESLIZA EL STYLUS."
        binding.overlayIdle.removeCallbacks(hideOverlayTask)
        binding.overlayIdle.postDelayed(hideOverlayTask, 1_200)
    }

    private fun onDaemonDisconnected() {
        binding.overlayIdle.removeCallbacks(hideOverlayTask)
        binding.overlayIdle.animate().cancel()
        binding.trailView.clear()
        showIdle()
        binding.tvIdleLabel.text = "01. BUSCANDO HOST..."
        pipeline.reset()
    }

    private fun showIdle() {
        binding.overlayIdle.alpha = 1f
        binding.overlayIdle.visibility = View.VISIBLE
        fadeHud(visible = false)
    }

    // ── Stylus capture with pipeline ─────────────────────────────────────────

    private fun onStylusEvent(event: MotionEvent): Boolean {
        // Track hover for hardware profiling before any touch
        if (!HardwareProfiler.isProfileBuilt) {
            HardwareProfiler.profileFromEvent(event)
        }

        // Determine tool type from event
        currentTool = when (event.getToolType(0)) {
            MotionEvent.TOOL_TYPE_STYLUS  -> ToolType.TOOL_PEN
            MotionEvent.TOOL_TYPE_ERASER  -> ToolType.TOOL_ERASER
            else                          -> ToolType.TOOL_FINGER
        }

        // In HYBRID mode: stylus uses pipeline in absolute, finger uses relative
        val config = Prefs.loadConfig(this)
        if (config.mode == OperatingMode.HYBRID) {
            val effectivePipeline = if (currentTool == ToolType.TOOL_FINGER) {
                InputPipeline(config.copy(mode = OperatingMode.RELATIVE))
            } else {
                pipeline
            }
            return processWithPipeline(event, effectivePipeline)
        }

        return processWithPipeline(event, pipeline)
    }

    private fun processWithPipeline(event: MotionEvent, pipe: InputPipeline): Boolean {
        val points = pipe.process(event)

        for (point in points) {
            val proto = StylusCapture.commitPointToProto(
                point, currentTool, screenWidth, screenHeight
            )
            connection.send(proto)
        }

        // Trail effect — only for active contact points
        if (event.actionMasked != MotionEvent.ACTION_UP &&
            event.actionMasked != MotionEvent.ACTION_CANCEL) {
            binding.trailView.addPoint(event.x, event.y)
        }

        // HUD
        showHud(event)

        return true
    }

    private fun showHud(event: MotionEvent) {
        fadeHud(visible = true)
        binding.tvHud.removeCallbacks(hideHudTask)
        binding.tvHud.postDelayed(hideHudTask, hudHideDelay)

        val pressure  = (event.pressure * 8191f).roundToInt().coerceIn(0, 8191)
        val tilt      = Math.toDegrees(event.getAxisValue(MotionEvent.AXIS_TILT).toDouble()).roundToInt()
        val latencyMs = (SystemClock.uptimeMillis() - event.eventTime).coerceAtLeast(0L)
        val config    = Prefs.loadConfig(this)
        binding.tvHud.text =
            "P:${pressure.toString().padStart(4, '0')} | T:${if (tilt >= 0) "+" else ""}${tilt}° | " +
            "L:${latencyMs}ms | ${config.mode.name.take(3)}"
    }

    // ── Animation helpers ─────────────────────────────────────────────────────

    private fun fadeHud(visible: Boolean) {
        binding.tvHud.animate()
            .alpha(if (visible) 1f else 0f)
            .setDuration(if (visible) 80L else 600L)
            .start()
    }

    private fun startPulseAnimation() {
        ObjectAnimator.ofFloat(binding.pulseDot, View.ALPHA, 0.15f, 1f).apply {
            duration    = 900
            repeatCount = ObjectAnimator.INFINITE
            repeatMode  = ObjectAnimator.REVERSE
            start()
        }
    }

    private fun openModeSelection() {
        startActivity(Intent(this, ModeSelectionActivity::class.java))
        // Don't finish — user can come back to drawing
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        HardwareProfiler.onHoverEvent(event)
        return super.onGenericMotionEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        connection.close()
    }
}
