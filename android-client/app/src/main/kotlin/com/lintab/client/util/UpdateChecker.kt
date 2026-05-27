// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

package com.lintab.client.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val RELEASE_URL =
        "https://api.github.com/repos/odimsom/LinTab/releases/latest"

    data class Result(
        val updateAvailable: Boolean,
        val current: String,
        val latest: String,
        val downloadUrl: String,
    )

    /**
     * Queries GitHub Releases and returns update status.
     * Returns null on network error or if the response cannot be parsed.
     * Must be called from a coroutine — runs on [Dispatchers.IO].
     */
    suspend fun check(currentVersion: String): Result? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(RELEASE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "lintab-android/$currentVersion")
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 5_000
                readTimeout    = 5_000
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val tagName = """\"tag_name\"\s*:\s*\"([^\"]+)\"""".toRegex()
                .find(body)?.groupValues?.get(1) ?: return@runCatching null

            val htmlUrl = """\"html_url\"\s*:\s*\"([^\"]+)\"""".toRegex()
                .find(body)?.groupValues?.get(1) ?: ""

            val latest = tagName.trimStart('v')
            Result(
                updateAvailable = isNewer(latest, currentVersion),
                current         = currentVersion,
                latest          = latest,
                downloadUrl     = htmlUrl,
            )
        }.getOrNull()
    }

    private fun isNewer(candidate: String, base: String): Boolean {
        fun parse(v: String) = v.split('.').mapNotNull { it.toIntOrNull() }
            .let { Triple(it.getOrElse(0) { 0 }, it.getOrElse(1) { 0 }, it.getOrElse(2) { 0 }) }
        return parse(candidate) > parse(base)
    }
}
