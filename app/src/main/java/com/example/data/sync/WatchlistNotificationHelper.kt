package com.example.data.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object WatchlistNotificationHelper {

  const val CHANNEL_ID = "watchlist_deal_alerts"
  private const val NOTIFICATION_ID = 4102

  fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
      val existing = manager.getNotificationChannel(CHANNEL_ID)
      if (existing == null) {
        val channel = NotificationChannel(
          CHANNEL_ID,
          "Target Price Watchlist Alerts",
          NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
          description = "Notifies when newly synced dealership feeds match your target KES vehicle watchlists."
        }
        manager.createNotificationChannel(channel)
      }
    }
  }

  fun notifyWatchlistMatches(
    context: Context,
    matchCount: Int,
    sourceName: String
  ): Boolean {
    if (matchCount <= 0 || !hasNotificationPermission(context)) return false
    ensureChannel(context)

    val intent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
      putExtra(MainActivity.EXTRA_OPEN_WATCHLIST_MATCHES, true)
    }
    val pendingIntent = PendingIntent.getActivity(
      context,
      NOTIFICATION_ID,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .setContentTitle("$matchCount New Watchlist Deal Match(es)")
      .setContentText("Fresh inventory synced from $sourceName matches your target KES budget.")
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .setContentIntent(pendingIntent)
      .setAutoCancel(true)
      .build()

    return runCatching {
      NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
      true
    }.getOrDefault(false)
  }
}
