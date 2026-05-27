// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

package com.lintab.client.transport

import android.content.Context
import com.lintab.client.network.NsdDiscovery
import com.lintab.protocol.EventsProto.TabletEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

private const val ADB_HOST    = "127.0.0.1"  // ADB reverse tunnel endpoint
private const val TABLET_PORT = 7654

/**
 * Manages the TCP connection to the LinTab core daemon.
 *
 * Connection strategy (in priority order):
 *   1. ADB reverse tunnel: connects to localhost:7654.
 *      Works when the phone is plugged via USB and the daemon ran `adb reverse`.
 *   2. mDNS/NSD discovery: browses the local network for `_lintab._tcp`.
 *      Works over WiFi when both devices are on the same network.
 *
 * Reconnects automatically on any error.
 */
class DaemonConnection(private val context: Context) {

    private val scope   = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val queue   = Channel<TabletEvent>(capacity = Channel.UNLIMITED)
    private val nsd     = NsdDiscovery(context)

    // Exposed so MainActivity can show connection status
    var onConnected:    ((host: String) -> Unit)? = null
    var onDisconnected: (() -> Unit)?              = null

    init { scope.launch { runConnectionLoop() } }

    fun send(event: TabletEvent) { queue.trySend(event) }

    private suspend fun runConnectionLoop() {
        while (true) {
            val host = try {
                resolveHost()
            } catch (_: Exception) {
                // NSD puede lanzar SecurityException si falta NEARBY_WIFI_DEVICES
                delay(3_000)
                null
            } ?: run {
                delay(2_000)
                null
            } ?: continue

            try {
                connect(host, TABLET_PORT)
            } catch (_: Exception) {
                onDisconnected?.invoke()
                delay(1_500)
            }
        }
    }

    private suspend fun resolveHost(): String? {
        // 1. Try ADB tunnel first (instant if USB is plugged)
        if (canReach(ADB_HOST, TABLET_PORT)) {
            return ADB_HOST
        }
        // 2. Fall back to mDNS/WiFi discovery
        return nsd.findDaemon(timeoutMs = 8_000)?.ip
    }

    private fun canReach(host: String, port: Int): Boolean = runCatching {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), 800)
            true
        }
    }.getOrDefault(false)

    private suspend fun connect(host: String, port: Int) {
        Socket(host, port).use { socket ->
            socket.tcpNoDelay = true     // minimize latency
            socket.setPerformancePreferences(0, 2, 1) // latency > bandwidth
            onConnected?.invoke(host)

            val out = DataOutputStream(socket.getOutputStream().buffered(4096))
            for (event in queue) {
                val bytes = event.toByteArray()
                out.writeInt(bytes.size)
                out.write(bytes)
                out.flush()
            }
        }
    }

    fun close() {
        queue.close()
        scope.cancel()
    }
}
