package com.example.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.domain.model.FeedSubscription
import java.util.concurrent.TimeUnit

object FeedSyncScheduler {

  private const val UNIQUE_PERIODIC_NAME = "hekima_periodic_feed_sync"
  private const val UNIQUE_IMMEDIATE_NAME = "hekima_immediate_feed_sync"

  fun syncSchedule(context: Context, subscriptions: List<FeedSubscription>) {
    val workManager = runCatching { WorkManager.getInstance(context.applicationContext) }.getOrNull() ?: return
    val enabledSubs = subscriptions.filter { it.autoSyncEnabled }
    if (enabledSubs.isEmpty()) {
      workManager.cancelUniqueWork(UNIQUE_PERIODIC_NAME)
      return
    }
    val intervalHours = enabledSubs.minOf { it.syncIntervalHours }.coerceIn(6, 24).toLong()
    val constraints = Constraints.Builder()
      .setRequiredNetworkType(NetworkType.CONNECTED)
      .build()
    val request = PeriodicWorkRequestBuilder<FeedSyncWorker>(intervalHours, TimeUnit.HOURS)
      .setConstraints(constraints)
      .build()

    workManager.enqueueUniquePeriodicWork(
      UNIQUE_PERIODIC_NAME,
      ExistingPeriodicWorkPolicy.UPDATE,
      request
    )
  }

  fun triggerImmediateSync(context: Context) {
    val workManager = runCatching { WorkManager.getInstance(context.applicationContext) }.getOrNull() ?: return
    val constraints = Constraints.Builder()
      .setRequiredNetworkType(NetworkType.CONNECTED)
      .build()
    val request = OneTimeWorkRequestBuilder<FeedSyncWorker>()
      .setConstraints(constraints)
      .build()
    workManager.enqueueUniqueWork(
      UNIQUE_IMMEDIATE_NAME,
      ExistingWorkPolicy.REPLACE,
      request
    )
  }
}
