package com.aura.led.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.aura.led.R
import java.io.File

/**
 * Hands the downloaded APK to the system installer.
 *
 * Aura installed from a sideloaded APK, so the "install unknown apps" grant belongs to the
 * browser or file manager that installed it, not to Aura: the flow checks for the grant and
 * routes the user to the right settings screen when it is missing.
 */
object ApkInstaller {
    private const val TAG = "AuraUpdate"

    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, applicationUri(context))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            Log.w(TAG, "unknown-app-sources screen failed, falling back to app details", it)
            openAppDetails(context)
        }
    }

    /** Opens the release page in a browser: the fallback when the installer cannot be used. */
    fun openReleasePage(context: Context, url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            Log.w(TAG, "release page could not be opened", it)
            Toast.makeText(context, context.getString(R.string.update_failed), Toast.LENGTH_SHORT).show()
        }
    }

    fun install(context: Context, apk: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure { Log.w(TAG, "install intent failed", it) }.isSuccess

    private fun applicationUri(context: Context): Uri =
        Uri.parse("package:${context.packageName}")

    private fun openAppDetails(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, applicationUri(context))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { Log.w(TAG, "app details screen failed", it) }
    }
}
