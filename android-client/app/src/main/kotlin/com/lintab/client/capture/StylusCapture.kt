// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

package com.lintab.client.capture

import android.view.MotionEvent
import com.lintab.protocol.EventsProto.TabletEvent
import com.lintab.protocol.EventsProto.ToolType

object StylusCapture {

    private const val MAX_PRESSURE = 8191

    /**
     * Converts an Android [MotionEvent] into a protobuf [TabletEvent].
     *
     * Raw pixel coordinates are sent together with the screen dimensions so
     * the Linux daemon can apply the exact mapping:
     *   X_linux = (X_android / screen_width) * 32767
     *
     * Returns null for non-stylus/non-touch events.
     */
    fun motionEventToProto(
        event: MotionEvent,
        screenWidth: Int,
        screenHeight: Int,
    ): TabletEvent? {
        val toolType = when (event.getToolType(0)) {
            MotionEvent.TOOL_TYPE_STYLUS  -> ToolType.TOOL_PEN
            MotionEvent.TOOL_TYPE_ERASER  -> ToolType.TOOL_ERASER
            MotionEvent.TOOL_TYPE_FINGER  -> ToolType.TOOL_FINGER
            else -> return null
        }

        val pressure = (event.pressure * MAX_PRESSURE)
            .toInt()
            .coerceIn(0, MAX_PRESSURE)

        val tiltDeg = Math.toDegrees(
            event.getAxisValue(MotionEvent.AXIS_TILT).toDouble()
        ).toInt().coerceIn(-90, 90)

        val orientDeg = Math.toDegrees(
            event.getAxisValue(MotionEvent.AXIS_ORIENTATION).toDouble()
        ).toInt().coerceIn(-90, 90)

        return TabletEvent.newBuilder()
            .setX(event.x.toInt())
            .setY(event.y.toInt())
            .setPressure(pressure)
            .setTiltX(tiltDeg)
            .setTiltY(orientDeg)
            .setTool(toolType)
            .setTimestampUs(event.eventTime * 1_000L)
            .setScreenWidth(screenWidth)
            .setScreenHeight(screenHeight)
            .build()
    }
}
