// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

package com.lintab.client.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class DaemonHost(val ip: String, val port: Int)

/**
 * Discovers a running LinTab daemon on the local network using mDNS/NSD.
 * The daemon advertises itself as `_lintab._tcp`; this class browses and resolves it.
 */
class NsdDiscovery(private val context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    /**
     * Blocks (suspends) until a LinTab daemon is found on the network,
     * or until [timeoutMs] elapses (returns null on timeout).
     */
    suspend fun findDaemon(timeoutMs: Long = 8_000): DaemonHost? =
        withTimeoutOrNull(timeoutMs) { browseOnce() }

    private suspend fun browseOnce(): DaemonHost =
        suspendCancellableCoroutine { cont ->
            var discoveryListener: NsdManager.DiscoveryListener? = null

            val resolveListener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo?, code: Int) {
                    // Ignore — we'll get the next ServiceFound event
                }
                override fun onServiceResolved(info: NsdServiceInfo) {
                    val ip = info.host?.hostAddress ?: return
                    discoveryListener?.let {
                        try { nsdManager.stopServiceDiscovery(it) } catch (_: Exception) {}
                    }
                    if (cont.isActive) cont.resume(DaemonHost(ip, info.port))
                }
            }

            discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(type: String?, code: Int) {}
                override fun onStopDiscoveryFailed(type: String?, code: Int) {}
                override fun onDiscoveryStarted(type: String?) {}
                override fun onDiscoveryStopped(type: String?) {}

                override fun onServiceFound(info: NsdServiceInfo) {
                    if (info.serviceType.contains("_lintab._tcp")) {
                        nsdManager.resolveService(info, resolveListener)
                    }
                }

                override fun onServiceLost(info: NsdServiceInfo) {}
            }

            nsdManager.discoverServices(
                "_lintab._tcp",
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener
            )

            cont.invokeOnCancellation {
                try { nsdManager.stopServiceDiscovery(discoveryListener) } catch (_: Exception) {}
            }
        }
}
