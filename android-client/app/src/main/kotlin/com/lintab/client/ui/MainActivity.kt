// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

package com.lintab.client.ui

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lintab.client.capture.StylusCapture
import com.lintab.client.databinding.ActivityMainBinding
import com.lintab.client.transport.DaemonConnection
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var connection: DaemonConnection

    private var screenWidth  = 0
    private var screenHeight = 0

    // Auto-hide HUD 2 s after last touch
    private val hudHideDelay = 2_000L
    private val hideHudTask  = Runnable { fadeHud(visible = false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full-screen immersive (UI disappears while drawing)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Cache display resolution for coordinate mapping
        val dm      = resources.displayMetrics
        screenWidth = dm.widthPixels
        screenHeight= dm.heightPixels

        // Connection setup
        connection = DaemonConnection(this).apply {
            onConnected    = { host -> runOnUiThread { onDaemonConnected(host) } }
            onDisconnected = {        runOnUiThread { onDaemonDisconnected()  } }
        }

        startPulseAnimation()
        showIdle()

        binding.drawingCanvas.setOnTouchListener { _, event -> onStylusEvent(event) }

        binding.fabMenu.setOnClickListener {
            // TODO: bottom-sheet — Settings / Switch Mode / Disconnect
        }
    }

    // ── Connection state ─────────────────────────────────────────────────────

    private fun onDaemonConnected(host: String) {
        binding.tvIdleLabel.text = "LISTO PARA CREAR. DESLIZA EL STYLUS."
        binding.tvIdleLabel.postDelayed({
            binding.overlayIdle.animate().alpha(0f).setDuration(500).withEndAction {
                binding.overlayIdle.visibility = View.GONE
            }.start()
        }, 1_200)
    }

    private fun onDaemonDisconnected() {
        showIdle()
        binding.tvIdleLabel.text = "01. BUSCANDO HOST..."
    }

    private fun showIdle() {
        binding.overlayIdle.alpha = 1f
        binding.overlayIdle.visibility = View.VISIBLE
        fadeHud(visible = false)
    }

    // ── Stylus capture ────────────────────────────────────────────────────────

    private fun onStylusEvent(event: MotionEvent): Boolean {
        val proto = StylusCapture.motionEventToProto(event, screenWidth, screenHeight)
            ?: return false

        // Show HUD while drawing, schedule auto-hide
        fadeHud(visible = true)
        binding.tvHud.removeCallbacks(hideHudTask)
        binding.tvHud.postDelayed(hideHudTask, hudHideDelay)

        // Update HUD diagnostic overlay
        val pressure  = (event.pressure * 8191f).roundToInt().coerceIn(0, 8191)
        val tilt      = Math.toDegrees(event.getAxisValue(MotionEvent.AXIS_TILT).toDouble())
            .roundToInt()
        val latencyMs = ((System.nanoTime() / 1_000_000L) - event.eventTime).coerceAtLeast(0L)
        binding.tvHud.text =
            "P: ${pressure.toString().padStart(4, '0')} | T: ${
                if (tilt >= 0) "+" else ""}${tilt}° | L: ${latencyMs}.${latencyMs % 10}ms"

        connection.send(proto)
        return true
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

    override fun onDestroy() {
        super.onDestroy()
        connection.close()
    }
}
