// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

package com.lintab.client.capture

enum class StylusType { ACTIVE, CAPACITIVE, FINGER_ONLY }
enum class PressureType { REAL, SIMULATED, NONE }
enum class QualityTier { PREMIUM, INTERMEDIATE, BASIC }
enum class OperatingMode { ABSOLUTE, RELATIVE, HYBRID }

data class HardwareProfile(
    val stylusType: StylusType = StylusType.FINGER_ONLY,
    val hasHover: Boolean = false,
    val pressureType: PressureType = PressureType.NONE,
    val samplingHz: Float = 60f,
    val jitterPx: Float = 3f,
    val latencyMs: Float = 30f,
    val hasPalmRejection: Boolean = false,
    val qualityTier: QualityTier = QualityTier.BASIC,
)

data class PipelineConfig(
    val mode: OperatingMode = OperatingMode.RELATIVE,
    val delayCommitMs: Long = 0L,
    val smoothingAlpha: Float = 0f,
    val predictionMs: Long = 0L,
    val touchSlopPx: Float = 8f,
    val fakeHoverEnabled: Boolean = false,
) {
    companion object {
        fun forProfile(profile: HardwareProfile, mode: OperatingMode): PipelineConfig =
            when (profile.qualityTier) {
                QualityTier.PREMIUM -> PipelineConfig(
                    mode = mode,
                    delayCommitMs = 0L,
                    smoothingAlpha = 0.08f,
                    predictionMs = 6L,
                    touchSlopPx = 3f,
                    fakeHoverEnabled = false,
                )
                QualityTier.INTERMEDIATE -> PipelineConfig(
                    mode = mode,
                    delayCommitMs = 6L,
                    smoothingAlpha = 0.28f,
                    predictionMs = 10L,
                    touchSlopPx = 8f,
                    fakeHoverEnabled = !profile.hasHover,
                )
                QualityTier.BASIC -> PipelineConfig(
                    mode = mode,
                    delayCommitMs = 12L,
                    smoothingAlpha = 0.48f,
                    predictionMs = 14L,
                    touchSlopPx = 14f,
                    fakeHoverEnabled = true,
                )
            }

        fun defaults(mode: OperatingMode = OperatingMode.RELATIVE): PipelineConfig =
            PipelineConfig(mode = mode)
    }
}
