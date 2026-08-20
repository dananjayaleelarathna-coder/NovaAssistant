package com.nova.assistant.device

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings

data class InstalledApp(val label: String, val packageName: String)

/**
 * Launches apps by fuzzy-matching the spoken name against installed apps' labels —
 * no hard-coded package names, so it works for whatever the user actually has installed.
 */
class AppLauncher(private val context: Context) {

    fun listInstalledApps(): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0).map {
            InstalledApp(
                label = it.loadLabel(pm).toString(),
                packageName = it.activityInfo.packageName
            )
        }.distinctBy { it.packageName }
    }

    /** Best-effort fuzzy match: exact label, then contains, then Levenshtein-lite fallback. */
    fun findApp(spokenName: String): InstalledApp? {
        val apps = listInstalledApps()
        val query = spokenName.trim().lowercase()
        apps.firstOrNull { it.label.lowercase() == query }?.let { return it }
        apps.firstOrNull { it.label.lowercase().contains(query) || query.contains(it.label.lowercase()) }?.let { return it }
        return apps.minByOrNull { levenshtein(it.label.lowercase(), query) }
            ?.takeIf { levenshtein(it.label.lowercase(), query) <= 3 }
    }

    fun launch(app: InstalledApp): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    fun openPlayStore(query: String? = null) {
        val uri = if (query != null) Uri.parse("market://search?q=$query") else Uri.parse("market://")
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(webIntent)
        }
    }

    fun openAppSettings(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
            else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
        }
        return dp[a.length][b.length]
    }
}
