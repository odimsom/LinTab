// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

package com.lintab.client.capture

import android.content.Context
import android.content.SharedPreferences

object Prefs {

    private const val FILE = "lintab_prefs"

    private const val KEY_MODE             = "mode"
    private const val KEY_DELAY_MS         = "delay_ms"
    private const val KEY_SMOOTHING        = "smoothing"
    private const val KEY_PREDICTION_MS    = "prediction_ms"
    private const val KEY_SLOP_PX          = "slop_px"
    private const val KEY_FAKE_HOVER       = "fake_hover"
    private const val KEY_HW_TIER          = "hw_tier"
    private const val KEY_BENCHMARK_DONE   = "benchmark_done"
    private const val KEY_LAST_SEEN_VERSION = "last_seen_version"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun hasMode(ctx: Context): Boolean = sp(ctx).contains(KEY_MODE)

    fun saveConfig(ctx: Context, config: PipelineConfig) {
        sp(ctx).edit()
            .putString(KEY_MODE,          config.mode.name)
            .putLong(KEY_DELAY_MS,         config.delayCommitMs)
            .putFloat(KEY_SMOOTHING,       config.smoothingAlpha)
            .putLong(KEY_PREDICTION_MS,    config.predictionMs)
            .putFloat(KEY_SLOP_PX,         config.touchSlopPx)
            .putBoolean(KEY_FAKE_HOVER,    config.fakeHoverEnabled)
            .apply()
    }

    fun loadConfig(ctx: Context): PipelineConfig {
        val sp = sp(ctx)
        val mode = runCatching {
            OperatingMode.valueOf(sp.getString(KEY_MODE, OperatingMode.RELATIVE.name)!!)
        }.getOrDefault(OperatingMode.RELATIVE)
        return PipelineConfig(
            mode             = mode,
            delayCommitMs    = sp.getLong(KEY_DELAY_MS, 0L),
            smoothingAlpha   = sp.getFloat(KEY_SMOOTHING, 0f),
            predictionMs     = sp.getLong(KEY_PREDICTION_MS, 0L),
            touchSlopPx      = sp.getFloat(KEY_SLOP_PX, 8f),
            fakeHoverEnabled = sp.getBoolean(KEY_FAKE_HOVER, false),
        )
    }

    fun saveHwTier(ctx: Context, tier: QualityTier) {
        sp(ctx).edit().putString(KEY_HW_TIER, tier.name).apply()
    }

    fun loadHwTier(ctx: Context): QualityTier = runCatching {
        QualityTier.valueOf(sp(ctx).getString(KEY_HW_TIER, QualityTier.BASIC.name)!!)
    }.getOrDefault(QualityTier.BASIC)

    fun setBenchmarkDone(ctx: Context) {
        sp(ctx).edit().putBoolean(KEY_BENCHMARK_DONE, true).apply()
    }

    fun isBenchmarkDone(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_BENCHMARK_DONE, false)

    fun getLastSeenVersion(ctx: Context): String? =
        sp(ctx).getString(KEY_LAST_SEEN_VERSION, null)

    fun setLastSeenVersion(ctx: Context, version: String) {
        sp(ctx).edit().putString(KEY_LAST_SEEN_VERSION, version).apply()
    }
}
