// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

package com.lintab.client.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val OWNER = "dev-fcastro"
    private const val REPO = "LinTab"
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    data class Result(
        val updateAvailable: Boolean,
        val current: String,
        val latest: String,
        val htmlUrl: String,
        val apkDownloadUrl: String?,
    )

    /**
     * Consulta el último release publicado en GitHub y lo compara contra la
     * versión instalada. Nunca lanza: sin conexión o cualquier error de red
     * simplemente devuelve null.
     */
    suspend fun check(currentVersion: String): Result? = withContext(Dispatchers.IO) {
        runCatching {
            val body = getJson(LATEST_RELEASE_URL, currentVersion) ?: return@runCatching null

            val tagName = extract(body, "tag_name") ?: return@runCatching null
            val htmlUrl = extract(body, "html_url")
                ?: "https://github.com/$OWNER/$REPO/releases/latest"
            val latest = tagName.trimStart('v')

            Result(
                updateAvailable = isNewer(latest, currentVersion),
                current = currentVersion,
                latest = latest,
                htmlUrl = htmlUrl,
                apkDownloadUrl = extractApkUrl(body),
            )
        }.getOrNull()
    }

    /**
     * Notas del release [version] (sin el prefijo "v"), para el diálogo de
     * "novedades" que se muestra una vez tras actualizar. Null si falla o si
     * el release no tiene body.
     */
    suspend fun fetchReleaseNotes(version: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.github.com/repos/$OWNER/$REPO/releases/tags/v$version"
            val body = getJson(url, version) ?: return@runCatching null
            extract(body, "body")?.unescapeJson()?.trim()?.ifEmpty { null }
        }.getOrNull()
    }

    /**
     * Descarga el APK a [destination], reportando progreso 0..1 por
     * [onProgress]. Lanza si la descarga falla; el caller decide cómo
     * mostrarlo.
     */
    suspend fun downloadApk(
        url: String,
        destination: File,
        onProgress: ((Float) -> Unit)? = null,
    ): File = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 15_000
        }
        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
            conn.disconnect()
            throw IllegalStateException("Descarga falló (HTTP ${conn.responseCode})")
        }
        val total = conn.contentLength
        var received = 0
        conn.inputStream.use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(8 * 1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    received += read
                    if (total > 0) onProgress?.invoke(received.toFloat() / total)
                }
            }
        }
        conn.disconnect()
        destination
    }

    private fun getJson(url: String, currentVersion: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "lintab-android/$currentVersion")
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 5_000
            readTimeout = 5_000
        }
        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
            conn.disconnect()
            return null
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        return body
    }

    private fun extract(json: String, key: String): String? =
        """"$key"\s*:\s*"((?:[^"\\]|\\.)*)"""".toRegex().find(json)?.groupValues?.get(1)

    /**
     * Busca el primer asset cuyo "name" termine en ".apk" y devuelve su
     * "browser_download_url". No hay parser JSON real en el proyecto, así
     * que se asume el orden de campos que devuelve hoy la API de GitHub
     * (name antes que browser_download_url dentro del mismo asset).
     */
    private fun extractApkUrl(json: String): String? =
        """"name"\s*:\s*"([^"]+\.apk)"[\s\S]*?"browser_download_url"\s*:\s*"([^"]+)"""".toRegex()
            .find(json)?.groupValues?.get(2)

    private fun String.unescapeJson(): String =
        replace("\\r\\n", "\n").replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")

    private fun isNewer(candidate: String, base: String): Boolean {
        fun parse(v: String) = v.split('.').mapNotNull { it.toIntOrNull() }
        val a = parse(candidate)
        val b = parse(base)
        for (i in 0 until maxOf(a.size, b.size)) {
            val ai = a.getOrElse(i) { 0 }
            val bi = b.getOrElse(i) { 0 }
            if (ai != bi) return ai > bi
        }
        return false
    }
}
