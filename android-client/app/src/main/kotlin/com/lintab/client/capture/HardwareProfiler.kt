// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

package com.lintab.client.capture

import android.view.MotionEvent

object HardwareProfiler {

    private var detectedHover = false
    private var cachedProfile: HardwareProfile? = null

    fun reset() {
        detectedHover = false
        cachedProfile = null
    }

    /** Call from onGenericMotionEvent to track hover capability. */
    fun onHoverEvent(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_HOVER_MOVE ||
            event.actionMasked == MotionEvent.ACTION_HOVER_ENTER
        ) {
            detectedHover = true
        }
    }

    /**
     * Infer a HardwareProfile from the first real touch event.
     * Subsequent calls return the same cached profile.
     */
    fun profileFromEvent(event: MotionEvent): HardwareProfile {
        cachedProfile?.let { return it }

        val toolType = event.getToolType(0)
        val stylusType = when (toolType) {
            MotionEvent.TOOL_TYPE_STYLUS,
            MotionEvent.TOOL_TYPE_ERASER -> StylusType.ACTIVE
            else                          -> StylusType.FINGER_ONLY
        }

        val pressure = event.pressure
        val hasPressureVariation = pressure > 0f && pressure < 0.99f
        val hasTilt = event.getAxisValue(MotionEvent.AXIS_TILT) != 0f

        val pressureType = when {
            stylusType == StylusType.ACTIVE && hasPressureVariation -> PressureType.REAL
            hasPressureVariation                                    -> PressureType.SIMULATED
            else                                                    -> PressureType.NONE
        }

        val tier = when {
            stylusType == StylusType.ACTIVE && (detectedHover || hasTilt) -> QualityTier.PREMIUM
            stylusType == StylusType.ACTIVE || hasPressureVariation        -> QualityTier.INTERMEDIATE
            else                                                            -> QualityTier.BASIC
        }

        val profile = HardwareProfile(
            stylusType       = stylusType,
            hasHover         = detectedHover,
            pressureType     = pressureType,
            samplingHz       = 60f,
            jitterPx         = when (tier) { QualityTier.PREMIUM -> 0.8f; QualityTier.INTERMEDIATE -> 2f; else -> 4f },
            latencyMs        = when (tier) { QualityTier.PREMIUM -> 8f;   QualityTier.INTERMEDIATE -> 20f; else -> 40f },
            hasPalmRejection = stylusType == StylusType.ACTIVE,
            qualityTier      = tier,
        )

        cachedProfile = profile
        return profile
    }

    val isProfileBuilt: Boolean get() = cachedProfile != null
    val cached: HardwareProfile? get() = cachedProfile
}
