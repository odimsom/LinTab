// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

package com.lintab.client.ui

import androidx.appcompat.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lintab.client.BuildConfig
import com.lintab.client.R
import com.lintab.client.capture.Prefs
import com.lintab.client.util.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Diálogos de actualización: "hay una versión nueva" (con descarga e
 * instalación directa del APK) y "novedades" (una sola vez, tras
 * actualizar).
 */
object UpdateDialogs {

    /** No se puede cerrar tocando afuera: el usuario elige explícitamente. */
    fun showUpdateAvailable(activity: AppCompatActivity, result: UpdateChecker.Result) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_update_available, null)
        val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        tvMessage.text = "Hay una nueva versión de LinTab (v${result.latest}) lista para instalar."

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle("Nueva versión disponible")
            .setView(view)
            .setCancelable(false)
            .setNegativeButton("Ahora no", null)
            .setPositiveButton("Descargar", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                onDownloadClicked(activity, result, dialog, tvMessage, progressBar)
            }
        }

        dialog.show()
    }

    private fun onDownloadClicked(
        activity: AppCompatActivity,
        result: UpdateChecker.Result,
        dialog: AlertDialog,
        tvMessage: TextView,
        progressBar: ProgressBar,
    ) {
        val apkUrl = result.apkDownloadUrl
        if (apkUrl == null) {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.htmlUrl)))
            dialog.dismiss()
            return
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
        progressBar.visibility = View.VISIBLE
        tvMessage.text = "Descargando..."

        CoroutineScope(Dispatchers.Main).launch {
            runCatching {
                val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
                val dest = File(dir, "lintab_update.apk")
                val file = UpdateChecker.downloadApk(apkUrl, dest) { progress ->
                    val percent = (progress * 100).toInt()
                    progressBar.progress = percent
                    tvMessage.text = "Descargando... $percent%"
                }
                installApk(activity, file)
                dialog.dismiss()
            }.onFailure {
                tvMessage.text = "No se pudo descargar la actualización. Probá de nuevo más tarde."
                progressBar.visibility = View.GONE
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                    isEnabled = true
                    text = "Reintentar"
                }
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = true
            }
        }
    }

    private fun installApk(activity: AppCompatActivity, file: File) {
        val uri = FileProvider.getUriForFile(
            activity,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }

    private fun showWhatsNew(activity: AppCompatActivity, notes: String) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Novedades de esta versión")
            .setMessage(notes)
            .setPositiveButton("Entendido", null)
            .show()
    }

    /**
     * Si la versión instalada cambió desde la última vez que se abrió la
     * app, busca las notas de ese release y muestra el diálogo de
     * novedades. No hace nada en la primera instalación (nada con qué
     * comparar) ni si ya se mostró para esta versión. Marca la versión
     * actual como vista, así solo se ofrece una vez.
     */
    fun maybeShowWhatsNew(activity: AppCompatActivity, currentVersion: String) {
        val lastSeen = Prefs.getLastSeenVersion(activity)
        Prefs.setLastSeenVersion(activity, currentVersion)
        if (lastSeen == null || lastSeen == currentVersion) return

        CoroutineScope(Dispatchers.Main).launch {
            val notes = UpdateChecker.fetchReleaseNotes(currentVersion) ?: return@launch
            showWhatsNew(activity, notes)
        }
    }
}
