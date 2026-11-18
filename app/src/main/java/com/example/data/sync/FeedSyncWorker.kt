package com.example.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.firebase.HekimaFirestoreRepository
import com.example.data.importer.VehicleSpreadsheetImporter
import com.example.data.preferences.ThemePreferencesRepository
import com.example.data.seed.KenyanMarketSeed
import com.example.domain.analytics.KenyanMarketAnalyticsEngine
import com.example.domain.model.DataSourceRecord
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.util.UUID

class FeedSyncWorker(
  appContext: Context,
  workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    val uid = Firebase.auth.currentUser?.uid ?: return Result.success()
    val repo = HekimaFirestoreRepository(applicationContext)
    val prefsRepo = ThemePreferencesRepository(applicationContext)

    return try {
      val subscriptions = repo.getFeedSubscriptionsOnce(uid).filter { it.autoSyncEnabled }
      if (subscriptions.isEmpty()) {
        return Result.success()
      }

      val notifPrefs = prefsRepo.getNotificationPreferencesOnce()
      val pushWatchlists = repo.getActiveWatchlistsOnce(uid).filter { it.pushAlertsEnabled }
      val existingListings = KenyanMarketSeed.listings +
        repo.getImportedListingsOnce(uid).map { it.toVehicleListing() }

      var totalMatches = 0
      var lastMatchedSource = ""
      var anyFailure = false

      for (sub in subscriptions) {
        try {
          val importResult = VehicleSpreadsheetImporter.importFromFeedUrl(
            url = sub.feedUrl,
            existingListings = existingListings,
            userId = uid
          )
          val nowMs = System.currentTimeMillis()
          if (importResult.importedListings.isNotEmpty()) {
            val sourceId = importResult.importedListings.firstOrNull()?.sourceId ?: UUID.randomUUID().toString()
            repo.saveImportBatch(
              sourceRecord = DataSourceRecord(
                id = sourceId,
                userId = uid,
                name = sub.name.ifBlank { importResult.sourceName },
                sourceType = "feed",
                rowsImported = importResult.importedListings.size,
                duplicatesSkipped = importResult.duplicatesSkipped,
                invalidRowsSkipped = importResult.invalidRowsSkipped,
                isActive = true
              ),
              listings = importResult.importedListings
            )
            if (sub.alertsEnabled && pushWatchlists.isNotEmpty()) {
              val newListings = importResult.importedListings.map { it.toVehicleListing() }
              val matched = KenyanMarketAnalyticsEngine.matchingListingIds(newListings, pushWatchlists)
              if (matched.isNotEmpty()) {
                totalMatches += matched.size
                lastMatchedSource = sub.name.ifBlank { importResult.sourceName }
              }
            }
          }
          repo.updateFeedSubscriptionSyncStatus(
            subscriptionId = sub.id,
            lastSyncedAtEpochMs = nowMs,
            lastStatusMessage = "Synced ${importResult.importedListings.size} new (${importResult.duplicatesSkipped} dupes)"
          )
        } catch (e: Exception) {
          anyFailure = true
          Log.w(TAG, "Subscription sync failed for ${sub.name}", e)
          runCatching {
            repo.updateFeedSubscriptionSyncStatus(
              subscriptionId = sub.id,
              lastSyncedAtEpochMs = System.currentTimeMillis(),
              lastStatusMessage = "Sync error: ${(e.localizedMessage ?: "Fetch failed").take(180)}"
            )
          }
        }
      }

      val dispatchNow = System.currentTimeMillis()
      if (totalMatches > 0 && notifPrefs.shouldSendPushNotification(dispatchNow)) {
        val posted = WatchlistNotificationHelper.notifyWatchlistMatches(
          context = applicationContext,
          matchCount = totalMatches,
          sourceName = lastMatchedSource
        )
        if (posted) {
          prefsRepo.recordNotificationDispatched(dispatchNow)
        }
      }

      if (anyFailure) Result.retry() else Result.success()
    } catch (e: Exception) {
      Log.e(TAG, "FeedSyncWorker failed", e)
      Result.retry()
    }
  }

  private companion object {
    const val TAG = "FeedSyncWorker"
  }
}
