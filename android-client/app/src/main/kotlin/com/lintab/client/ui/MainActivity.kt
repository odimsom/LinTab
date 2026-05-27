// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

package com.lintab.client.ui

import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.SystemClock
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
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val dm      = resources.displayMetrics
        screenWidth = dm.widthPixels
        screenHeight= dm.heightPixels

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
        binding.overlayIdle.removeCallbacks(hideOverlayTask)
        binding.overlayIdle.postDelayed(hideOverlayTask, 1_200)
    }

    private fun onDaemonDisconnected() {
        binding.overlayIdle.removeCallbacks(hideOverlayTask)
        binding.overlayIdle.animate().cancel()
        binding.trailView.clear()
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

        // Trail effect — add point for every move/down event
        if (event.actionMasked != MotionEvent.ACTION_UP &&
            event.actionMasked != MotionEvent.ACTION_CANCEL) {
            binding.trailView.addPoint(event.x, event.y)
        }

        // Show HUD while drawing, schedule auto-hide
        fadeHud(visible = true)
        binding.tvHud.removeCallbacks(hideHudTask)
        binding.tvHud.postDelayed(hideHudTask, hudHideDelay)

        val pressure  = (event.pressure * 8191f).roundToInt().coerceIn(0, 8191)
        val tilt      = Math.toDegrees(event.getAxisValue(MotionEvent.AXIS_TILT).toDouble())
            .roundToInt()
        // Use SystemClock.uptimeMillis() which matches event.eventTime (same clock)
        val latencyMs = (SystemClock.uptimeMillis() - event.eventTime).coerceAtLeast(0L)
        binding.tvHud.text =
            "P: ${pressure.toString().padStart(4, '0')} | T: ${
                if (tilt >= 0) "+" else ""}${tilt}° | L: ${latencyMs}ms"

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
