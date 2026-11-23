package com.goalscanner.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ReleaseUpdateWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        return try {
            val connection = (URL("https://api.github.com/repos/Ndumiso19894/goal-scanner-2.0/releases/latest").openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Goal-Scanner-Pro-Android")
            }
            val payload = try {
                val code = connection.responseCode
                if (code !in 200..299) throw IllegalStateException("Release check HTTP $code")
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally { connection.disconnect() }
            val release = JSONObject(payload)
            val latest = release.optString("tag_name").removePrefix("v")
            val installed = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).versionName ?: "1.0.0"
            if (isNewer(latest, installed)) postUpdateNotification(release)
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val b = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun postUpdateNotification(release: JSONObject) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "goal_scanner_updates"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(channelId, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notifications when a new signed Goal Scanner Pro release is available"
            })
        }
        val page = release.optString("html_url", "https://github.com/Ndumiso19894/goal-scanner-2.0/releases")
        val open = PendingIntent.getActivity(applicationContext, 71,
            Intent(Intent.ACTION_VIEW, Uri.parse(page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val version = release.optString("tag_name", "New version")
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Goal Scanner Pro update available")
            .setContentText("Version $version is ready. Tap to view and install the update.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("A newer signed version of Goal Scanner Pro ($version) is available. Tap to download it. Android will ask you to confirm installation."))
            .setContentIntent(open).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT).build()
        manager.notify(7101, notification)
    }
}
