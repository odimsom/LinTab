// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

package com.lintab.client.capture

import android.view.MotionEvent
import com.lintab.protocol.EventsProto.ActionType
import com.lintab.protocol.EventsProto.TabletEvent
import com.lintab.protocol.EventsProto.ToolType

object StylusCapture {

    private const val MAX_PRESSURE = 8191

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

        val action = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> ActionType.ACTION_DOWN
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL       -> ActionType.ACTION_UP
            else                            -> ActionType.ACTION_MOVE
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
            .setPressure(if (action == ActionType.ACTION_UP) 0 else pressure)
            .setTiltX(tiltDeg)
            .setTiltY(orientDeg)
            .setTool(toolType)
            .setAction(action)
            .setTimestampUs(event.eventTime * 1_000L)
            .setScreenWidth(screenWidth)
            .setScreenHeight(screenHeight)
            .build()
    }
}
