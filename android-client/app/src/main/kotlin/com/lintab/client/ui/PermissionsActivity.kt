// Copyright © 2026 Francisco Daniel Castro Borrome. All rights reserved.
// SPDX-License-Identifier: GPL-3.0-or-later

package com.lintab.client.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lintab.client.R
import com.lintab.client.capture.Prefs
import com.lintab.client.databinding.ActivityPermissionsBinding

class PermissionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionsBinding

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        updateNetworkStatus()
        launchNext()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (allPermissionsGranted()) {
            launchNext()
            return
        }

        binding = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateNetworkStatus()

        binding.btnContinue.setOnClickListener { permissionLauncher.launch(requiredPermissions) }
        binding.btnSkip.setOnClickListener     { launchNext() }
    }

    private fun allPermissionsGranted(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateNetworkStatus() {
        val granted = allPermissionsGranted()
        binding.tvNetworkStatus.text = if (granted) "CONCEDIDO" else "PENDIENTE"
        binding.tvNetworkStatus.setTextColor(
            if (granted) 0xFF2D7A2D.toInt() else 0xFFFF4F00.toInt()
        )
        binding.dotNetwork.visibility = if (granted) View.INVISIBLE else View.VISIBLE
    }

    /** Route: first run → ModeSelectionActivity; returning user → MainActivity. */
    private fun launchNext() {
        val target = if (Prefs.hasMode(this)) {
            MainActivity::class.java
        } else {
            ModeSelectionActivity::class.java
        }
        startActivity(Intent(this, target))
        finish()
    }
}
