package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.domain.model.DataSourceRecord
import com.example.domain.model.FeedSubscription
import com.example.domain.model.ImportedVehicleListing
import com.example.domain.model.MarketAnalysis
import com.example.domain.model.MarketListing
import com.example.domain.model.PriceWatchlist
import com.example.domain.model.SavedVehicle
import com.example.domain.model.ThemeMode
import com.example.domain.model.UserProfile
import com.example.domain.model.VehicleComparison
import com.example.domain.model.YardInspectionReport
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class HekimaFirestoreRepository(private val db: FirebaseFirestore) {

  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private val auth get() = Firebase.auth

  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

  private fun <T> Flow<T>.catchFirestore(operation: OperationType, path: String, uid: String): Flow<T> =
    catch { error ->
      val firestoreError = error.unwrapFirestoreException()
      if (firestoreError == null && error is CancellationException) throw error
      val exceptionToLog = firestoreError ?: (error as? Exception)
      if (exceptionToLog != null && auth.currentUser?.uid == uid) {
        handleFirestoreError(exceptionToLog, operation, path)
      }
      throw error
    }

  suspend fun ensureUserProfile(
    userId: String,
    displayName: String,
    email: String?,
    defaultTheme: ThemeMode = ThemeMode.SYSTEM
  ) {
    val docRef = db.collection("users").document(userId)
    val safeName = displayName.ifBlank { "Hekima Analyst" }.take(80)
    try {
      val existing = docRef.get().await()
      if (!existing.exists()) {
        val payload = mutableMapOf<String, Any>(
          "userId" to userId,
          "displayName" to safeName,
          "themeMode" to defaultTheme.key,
          "preferredCurrency" to "KES",
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!email.isNullOrBlank()) {
          payload["email"] = email.take(120)
        }
        docRef.set(payload).await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      throw e
    }
  }

  fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
    val path = "users/$userId"
    emitAll(
      db.collection("users").document(userId)
        .snapshots()
        .map { snap ->
          snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catchFirestore(OperationType.GET, path, userId)
    )
  }

  suspend fun updateUserTheme(userId: String, mode: ThemeMode) {
    val docRef = db.collection("users").document(userId)
    try {
      docRef.update(
        mapOf(
          "themeMode" to mode.key,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  fun observeSavedVehicles(userId: String): Flow<List<SavedVehicle>> = flow {
    val path = "users/$userId/savedVehicles"
    emitAll(
      db.collection("users").document(userId).collection("savedVehicles")
        .snapshots()
        .map { snap ->
          snap.toObjects(SavedVehicle::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .catchFirestore(OperationType.LIST, path, userId)
    )
  }

  suspend fun saveVehicle(vehicle: SavedVehicle): String {
    val uid = requireUserId()
    val docId = vehicle.id.ifBlank { UUID.randomUUID().toString() }
    val docRef = db.collection("users").document(uid).collection("savedVehicles").document(docId)
    val payload = mutableMapOf<String, Any>(
      "userId" to uid,
      "listingId" to vehicle.listingId.take(128),
      "make" to vehicle.make.take(60),
      "model" to vehicle.model.take(80),
      "year" to vehicle.year.coerceIn(1990, 2030),
      "askingPriceKes" to vehicle.askingPriceKes.coerceIn(100000L, 150000000L),
      "mileageKm" to vehicle.mileageKm.coerceIn(0, 2000000),
      "transmission" to vehicle.transmission.take(40),
      "fuelType" to vehicle.fuelType.take(40),
      "engineCc" to vehicle.engineCc.coerceIn(500, 8000),
      "condition" to vehicle.condition.take(40),
      "importStatus" to vehicle.importStatus.take(40),
      "location" to vehicle.location.take(80),
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp()
    )
    if (!vehicle.notes.isNullOrBlank()) {
      payload["notes"] = vehicle.notes.take(500)
    }
    try {
      docRef.set(payload).await()
      return docId
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  suspend fun deleteSavedVehicle(savedId: String) {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("savedVehicles").document(savedId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  fun observeComparisons(userId: String): Flow<List<VehicleComparison>> = flow {
    val path = "users/$userId/comparisons"
    emitAll(
      db.collection("users").document(userId).collection("comparisons")
        .snapshots()
        .map { snap ->
          snap.toObjects(VehicleComparison::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .catchFirestore(OperationType.LIST, path, userId)
    )
  }

  suspend fun saveComparison(comparison: VehicleComparison): String {
    val uid = requireUserId()
    val docId = comparison.id.ifBlank { UUID.randomUUID().toString() }
    val docRef = db.collection("users").document(uid).collection("comparisons").document(docId)
    val payload = mapOf(
      "userId" to uid,
      "title" to comparison.title.take(120),
      "vehicleIds" to comparison.vehicleIds.take(4),
      "vehicleSummaries" to comparison.vehicleSummaries.take(4).map { it.take(160) },
      "winnerSummary" to comparison.winnerSummary.take(400),
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp()
    )
    try {
      docRef.set(payload).await()
      return docId
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  suspend fun deleteComparison(comparisonId: String) {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("comparisons").document(comparisonId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  fun observeMarketAnalyses(userId: String): Flow<List<MarketAnalysis>> = flow {
    val path = "users/$userId/marketAnalyses"
    emitAll(
      db.collection("users").document(userId).collection("marketAnalyses")
        .snapshots()
        .map { snap ->
          snap.toObjects(MarketAnalysis::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .catchFirestore(OperationType.LIST, path, userId)
    )
  }

  suspend fun saveMarketAnalysis(analysis: MarketAnalysis): String {
    val uid = requireUserId()
    val docId = analysis.id.ifBlank { UUID.randomUUID().toString() }
    val docRef = db.collection("users").document(uid).collection("marketAnalyses").document(docId)
    val payload = mapOf(
      "userId" to uid,
      "make" to analysis.make.take(60),
      "model" to analysis.model.take(80),
      "year" to analysis.year.coerceIn(1990, 2030),
      "mileageKm" to analysis.mileageKm.coerceIn(0, 2000000),
      "estimatedPriceKes" to analysis.estimatedPriceKes.coerceIn(50000L, 200000000L),
      "lowPriceKes" to analysis.lowPriceKes.coerceIn(50000L, 200000000L),
      "highPriceKes" to analysis.highPriceKes.coerceIn(50000L, 200000000L),
      "annualDepreciationPct" to analysis.annualDepreciationPct.coerceIn(0.0, 60.0),
      "fiveYearProjectedKes" to analysis.fiveYearProjectedKes.coerceIn(0L, 200000000L),
      "summary" to analysis.summary.take(600),
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp()
    )
    try {
      docRef.set(payload).await()
      return docId
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  fun observeImportedListings(userId: String): Flow<List<ImportedVehicleListing>> = flow {
    val path = "users/$userId/importedListings"
    emitAll(
      db.collection("users").document(userId).collection("importedListings")
        .snapshots()
        .map { snap ->
          snap.toObjects(ImportedVehicleListing::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .catchFirestore(OperationType.LIST, path, userId)
    )
  }

  fun observeDataSources(userId: String): Flow<List<DataSourceRecord>> = flow {
    val path = "users/$userId/dataSources"
    emitAll(
      db.collection("users").document(userId).collection("dataSources")
        .snapshots()
        .map { snap ->
          snap.toObjects(DataSourceRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .catchFirestore(OperationType.LIST, path, userId)
    )
  }

  suspend fun saveImportBatch(
    sourceRecord: DataSourceRecord,
    listings: List<ImportedVehicleListing>
  ): String {
    val uid = requireUserId()
    val sourceId = sourceRecord.id.ifBlank { UUID.randomUUID().toString() }
    val userDoc = db.collection("users").document(uid)
    val sourceRef = userDoc.collection("dataSources").document(sourceId)
    val batch = db.batch()

    batch.set(
      sourceRef,
      mapOf(
        "userId" to uid,
        "name" to sourceRecord.name.take(100),
        "sourceType" to sourceRecord.sourceType,
        "rowsImported" to listings.size.coerceIn(0, 10000),
        "duplicatesSkipped" to sourceRecord.duplicatesSkipped.coerceIn(0, 10000),
        "invalidRowsSkipped" to sourceRecord.invalidRowsSkipped.coerceIn(0, 10000),
        "isActive" to sourceRecord.isActive,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
      )
    )

    listings.take(400).forEach { item ->
      val docId = item.id.ifBlank { UUID.randomUUID().toString() }
      val listingRef = userDoc.collection("importedListings").document(docId)
      batch.set(
        listingRef,
        mapOf(
          "userId" to uid,
          "sourceId" to sourceId,
          "sourceName" to sourceRecord.name.take(80),
          "make" to item.make.take(60),
          "model" to item.model.take(80),
          "year" to item.year.coerceIn(1990, 2030),
          "askingPriceKes" to item.askingPriceKes.coerceIn(100000L, 150000000L),
          "mileageKm" to item.mileageKm.coerceIn(0, 2000000),
          "engineCc" to item.engineCc.coerceIn(500, 8000),
          "transmission" to item.transmission.take(40),
          "fuelType" to item.fuelType.take(40),
          "condition" to item.condition.take(40),
          "importStatus" to item.importStatus.take(40),
          "location" to item.location.take(80),
          "demandScore" to item.demandScore.coerceIn(0, 100),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp()
        )
      )
    }

    try {
      batch.commit().await()
      return sourceId
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, sourceRef.path)
      throw e
    }
  }

  suspend fun toggleDataSourceActive(source: DataSourceRecord, isActive: Boolean) {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("dataSources").document(source.id)
    try {
      docRef.update(
        mapOf(
          "isActive" to isActive,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun deleteDataSourceAndListings(sourceId: String) {
    val uid = requireUserId()
    val userDoc = db.collection("users").document(uid)
    val sourceRef = userDoc.collection("dataSources").document(sourceId)
    try {
      val matchingListings = userDoc.collection("importedListings")
        .whereEqualTo("sourceId", sourceId)
        .get()
        .await()
      val batch = db.batch()
      matchingListings.documents.forEach { doc -> batch.delete(doc.reference) }
      batch.delete(sourceRef)
      batch.commit().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, sourceRef.path)
      throw e
    }
  }

  fun observeWatchlists(userId: String): Flow<List<PriceWatchlist>> = flow {
    val path = "users/$userId/watchlists"
    emitAll(
      db.collection("users").document(userId).collection("watchlists")
        .snapshots()
        .map { snap ->
          snap.toObjects(PriceWatchlist::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .catchFirestore(OperationType.LIST, path, userId)
    )
  }

  suspend fun saveWatchlist(watchlist: PriceWatchlist): String {
    val uid = requireUserId()
    val docId = watchlist.id.ifBlank { UUID.randomUUID().toString() }
    val docRef = db.collection("users").document(uid).collection("watchlists").document(docId)
    val safeMinYear = watchlist.minYear.coerceIn(1990, 2030)
    val safeMaxYear = watchlist.maxYear.coerceIn(safeMinYear, 2030)
    val safeOrigin = if (watchlist.preferredImportStatus in listOf("All", "Foreign Used", "Locally Used")) {
      watchlist.preferredImportStatus
    } else "All"

    val payload = mapOf(
      "userId" to uid,
      "make" to watchlist.make.ifBlank { "Toyota" }.take(60),
      "model" to watchlist.model.ifBlank { "All" }.take(80),
      "minYear" to safeMinYear,
      "maxYear" to safeMaxYear,
      "maxBudgetKes" to watchlist.maxBudgetKes.coerceIn(100_000L, 150_000_000L),
      "preferredImportStatus" to safeOrigin,
      "isActive" to watchlist.isActive,
      "pushAlertsEnabled" to watchlist.pushAlertsEnabled,
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp()
    )
    try {
      docRef.set(payload).await()
      return docId
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  suspend fun toggleWatchlistActive(watchlist: PriceWatchlist, isActive: Boolean) {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("watchlists").document(watchlist.id)
    try {
      docRef.update(
        mapOf(
          "isActive" to isActive,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun toggleWatchlistPushAlerts(watchlist: PriceWatchlist, enabled: Boolean) {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("watchlists").document(watchlist.id)
    try {
      docRef.update(
        mapOf(
          "pushAlertsEnabled" to enabled,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun deleteWatchlist(watchlistId: String) {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("watchlists").document(watchlistId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  fun observeInspections(userId: String): Flow<List<YardInspectionReport>> = flow {
    val path = "users/$userId/inspections"
    emitAll(
      db.collection("users").document(userId).collection("inspections")
        .snapshots()
        .map { snap ->
          snap.toObjects(YardInspectionReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .catchFirestore(OperationType.LIST, path, userId)
    )
  }

  suspend fun saveInspection(report: YardInspectionReport): String {
    val uid = requireUserId()
    val docId = report.id.ifBlank { UUID.randomUUID().toString() }
    val docRef = db.collection("users").document(uid).collection("inspections").document(docId)
    val safeTier = if (report.verdictTier in listOf("Buy Ready", "Negotiate Repairs", "Walk Away Risk")) {
      report.verdictTier
    } else "Negotiate Repairs"
    val safeChecklist = report.checklistSummary.ifEmpty { listOf("Inspection recorded") }.take(12).map { it.take(160) }

    val payload = mutableMapOf<String, Any>(
      "userId" to uid,
      "listingId" to report.listingId.ifBlank { "custom_yard_unit" }.take(128),
      "vehicleTitle" to report.vehicleTitle.ifBlank { "Vehicle Inspection" }.take(120),
      "location" to report.location.ifBlank { "Nairobi" }.take(80),
      "askingPriceKes" to report.askingPriceKes.coerceIn(100_000L, 150_000_000L),
      "overallScore" to report.overallScore.coerceIn(0, 100),
      "verdictTier" to safeTier,
      "estimatedRepairKes" to report.estimatedRepairKes.coerceIn(0L, 50_000_000L),
      "recommendedOfferKes" to report.recommendedOfferKes.coerceIn(0L, 150_000_000L),
      "checklistSummary" to safeChecklist,
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp()
    )
    if (!report.notes.isNullOrBlank()) {
      payload["notes"] = report.notes.take(1000)
    }

    try {
      docRef.set(payload).await()
      return docId
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  suspend fun deleteInspection(inspectionId: String) {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("inspections").document(inspectionId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  fun observeFeedSubscriptions(userId: String): Flow<List<FeedSubscription>> = flow {
    val path = "users/$userId/feedSubscriptions"
    emitAll(
      db.collection("users").document(userId).collection("feedSubscriptions")
        .snapshots()
        .map { snap ->
          snap.toObjects(FeedSubscription::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .catchFirestore(OperationType.LIST, path, userId)
    )
  }

  suspend fun getFeedSubscriptionsOnce(userId: String): List<FeedSubscription> {
    val path = "users/$userId/feedSubscriptions"
    return try {
      db.collection("users").document(userId).collection("feedSubscriptions")
        .get()
        .await()
        .toObjects(FeedSubscription::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.LIST, path)
      throw e
    }
  }

  suspend fun getActiveWatchlistsOnce(userId: String): List<PriceWatchlist> {
    val path = "users/$userId/watchlists"
    return try {
      db.collection("users").document(userId).collection("watchlists")
        .get()
        .await()
        .toObjects(PriceWatchlist::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        .filter { it.isActive }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.LIST, path)
      throw e
    }
  }

  suspend fun getImportedListingsOnce(userId: String): List<ImportedVehicleListing> {
    val path = "users/$userId/importedListings"
    return try {
      db.collection("users").document(userId).collection("importedListings")
        .get()
        .await()
        .toObjects(ImportedVehicleListing::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.LIST, path)
      throw e
    }
  }

  suspend fun saveFeedSubscription(subscription: FeedSubscription): String {
    val uid = requireUserId()
    val docId = subscription.id.ifBlank { UUID.randomUUID().toString() }
    val docRef = db.collection("users").document(uid).collection("feedSubscriptions").document(docId)
    val safeInterval = if (subscription.syncIntervalHours in listOf(6, 12, 24)) subscription.syncIntervalHours else 12
    val safeUrl = subscription.feedUrl.trim().take(500)
    require(safeUrl.startsWith("https://")) { "Feed subscription URL must start with https://" }

    val payload = mapOf(
      "userId" to uid,
      "name" to subscription.name.ifBlank { "Dealership Feed" }.take(100),
      "feedUrl" to safeUrl,
      "syncIntervalHours" to safeInterval,
      "autoSyncEnabled" to subscription.autoSyncEnabled,
      "alertsEnabled" to subscription.alertsEnabled,
      "lastSyncedAtEpochMs" to subscription.lastSyncedAtEpochMs.coerceAtLeast(0L),
      "lastStatusMessage" to subscription.lastStatusMessage.take(240),
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp()
    )
    try {
      docRef.set(payload).await()
      return docId
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  suspend fun updateFeedSubscriptionSyncStatus(
    subscriptionId: String,
    autoSyncEnabled: Boolean? = null,
    alertsEnabled: Boolean? = null,
    lastSyncedAtEpochMs: Long? = null,
    lastStatusMessage: String? = null
  ) {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("feedSubscriptions").document(subscriptionId)
    val updates = mutableMapOf<String, Any>(
      "updatedAt" to FieldValue.serverTimestamp()
    )
    autoSyncEnabled?.let { updates["autoSyncEnabled"] = it }
    alertsEnabled?.let { updates["alertsEnabled"] = it }
    lastSyncedAtEpochMs?.let { updates["lastSyncedAtEpochMs"] = it.coerceAtLeast(0L) }
    lastStatusMessage?.let { updates["lastStatusMessage"] = it.take(240) }
    try {
      docRef.update(updates).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun deleteFeedSubscription(subscriptionId: String) {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("feedSubscriptions").document(subscriptionId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  fun observeMarketListings(): Flow<List<MarketListing>> = flow {
    val uid = requireUserId()
    val path = "marketListings"
    emitAll(
      db.collection("marketListings")
        .snapshots()
        .map { snap ->
          snap.toObjects(MarketListing::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
        }
        .catchFirestore(OperationType.LIST, path, uid)
    )
  }

  suspend fun publishMarketListing(listing: MarketListing): String {
    val uid = requireUserId()
    val docId = listing.id.removePrefix("mkt_").ifBlank { UUID.randomUUID().toString() }
    val docRef = db.collection("marketListings").document(docId)
    val safeOrigin = if (listing.listingOrigin in listOf("live_yard", "verified_dealer")) {
      listing.listingOrigin
    } else "live_yard"

    val payload = mapOf(
      "sellerUserId" to uid,
      "sellerName" to listing.sellerName.ifBlank { "Kenyan Yard Seller" }.take(80),
      "make" to listing.make.ifBlank { "Toyota" }.take(60),
      "model" to listing.model.ifBlank { "Axio" }.take(80),
      "year" to listing.year.coerceIn(1990, 2030),
      "askingPriceKes" to listing.askingPriceKes.coerceIn(100_000L, 150_000_000L),
      "mileageKm" to listing.mileageKm.coerceIn(0, 2_000_000),
      "engineCc" to listing.engineCc.coerceIn(500, 8000),
      "transmission" to listing.transmission.ifBlank { "Automatic" }.take(40),
      "fuelType" to listing.fuelType.ifBlank { "Petrol" }.take(40),
      "condition" to listing.condition.ifBlank { "Good" }.take(40),
      "importStatus" to listing.importStatus.ifBlank { "Foreign Used" }.take(40),
      "location" to listing.location.ifBlank { "Nairobi" }.take(80),
      "demandScore" to listing.demandScore.coerceIn(0, 100),
      "listingOrigin" to safeOrigin,
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp()
    )
    try {
      docRef.set(payload).await()
      return docId
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  suspend fun deleteMarketListing(listingId: String) {
    requireUserId()
    val cleanId = listingId.removePrefix("mkt_")
    val docRef = db.collection("marketListings").document(cleanId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  suspend fun deleteAllUserCloudData(userId: String) {
    val uid = requireUserId()
    require(uid == userId) { "Authenticated user mismatch during account deletion." }
    val userDoc = db.collection("users").document(uid)
    val subcollections = listOf(
      "savedVehicles",
      "comparisons",
      "importedListings",
      "marketAnalyses",
      "dataSources",
      "watchlists",
      "inspections",
      "feedSubscriptions"
    )
    try {
      val refsToDelete = mutableListOf<com.google.firebase.firestore.DocumentReference>()
      for (col in subcollections) {
        val snap = userDoc.collection(col).get().await()
        snap.documents.forEach { doc -> refsToDelete.add(doc.reference) }
      }
      val authoredMarket = db.collection("marketListings")
        .whereEqualTo("sellerUserId", uid)
        .get()
        .await()
      authoredMarket.documents.forEach { doc -> refsToDelete.add(doc.reference) }
      refsToDelete.add(userDoc)

      refsToDelete.chunked(350).forEach { chunk ->
        val batch = db.batch()
        chunk.forEach { ref -> batch.delete(ref) }
        batch.commit().await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, userDoc.path)
      throw e
    }
  }
}
