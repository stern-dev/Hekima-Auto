package com.example.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

data class AuctionSheetAnalysis(
  val vehicleTitle: String,
  val make: String,
  val model: String,
  val year: Int,
  val chassisCode: String,
  val auctionGrade: String,
  val interiorGrade: String,
  val reportedMileageKm: Int,
  val mileageVerdict: String,
  val ageRuleNote: String,
  val repairRemarks: List<String>,
  val redFlags: List<String>,
  val yardVerificationChecks: List<String>,
  val recommendedAction: String,
  val suggestedMaxOfferKes: Long
)

sealed interface AiAnalyzerUiState {
  data object Idle : AiAnalyzerUiState
  data object Loading : AiAnalyzerUiState
  data class Success(val analysis: AuctionSheetAnalysis) : AiAnalyzerUiState
  data class Error(val message: String) : AiAnalyzerUiState
}

enum class InspectionStatus(val label: String, val pointsMultiplier: Double) {
  PASS("Pass", 1.0),
  WATCH("Watch", 0.55),
  ISSUE("Issue", 0.0)
}

data class InspectionCheckItem(
  val id: String,
  val category: String,
  val title: String,
  val guidance: String,
  val weight: Int,
  val status: InspectionStatus = InspectionStatus.PASS,
  val defaultRepairKes: Long = 0L,
  val customRepairKes: Long? = null
) {
  val effectiveRepairKes: Long
    get() = when (status) {
      InspectionStatus.PASS -> 0L
      InspectionStatus.WATCH -> customRepairKes ?: (defaultRepairKes / 2L)
      InspectionStatus.ISSUE -> customRepairKes ?: defaultRepairKes
    }
}

data class YardInspectionDraft(
  val listingId: String = "custom_yard_unit",
  val vehicleTitle: String = "Toyota Axio 2018",
  val location: String = "Nairobi",
  val askingPriceKes: Long = 1_380_000L,
  val items: List<InspectionCheckItem> = emptyList(),
  val notes: String = ""
) {
  val totalWeight: Int get() = items.sumOf { it.weight }.coerceAtLeast(1)
  val earnedWeight: Double get() = items.sumOf { it.weight * it.status.pointsMultiplier }
  val overallScore: Int get() = ((earnedWeight / totalWeight) * 100.0).toInt().coerceIn(0, 100)
  val verdictTier: String
    get() = when {
      overallScore >= 80 -> "Buy Ready"
      overallScore >= 55 -> "Negotiate Repairs"
      else -> "Walk Away Risk"
    }
  val estimatedRepairKes: Long get() = items.sumOf { it.effectiveRepairKes }.coerceIn(0L, 50_000_000L)
  val recommendedOfferKes: Long get() = (askingPriceKes - estimatedRepairKes).coerceAtLeast(100_000L)
}

data class ExchangeRateSnapshot(
  val usdToKes: Double = 129.0,
  val jpyToKes: Double = 0.85,
  val fetchedAtEpochMs: Long = 0L,
  val sourceLabel: String = "Baseline Benchmark",
  val isFallback: Boolean = true
) {
  fun isStale(
    nowEpochMs: Long = System.currentTimeMillis(),
    ttlMs: Long = 6L * 60L * 60L * 1000L
  ): Boolean {
    return isFallback || fetchedAtEpochMs <= 0L || (nowEpochMs - fetchedAtEpochMs) >= ttlMs
  }
}

@IgnoreExtraProperties
data class YardInspectionReport(
  @DocumentId val id: String = "",
  val userId: String = "",
  val listingId: String = "",
  val vehicleTitle: String = "",
  val location: String = "Nairobi",
  val askingPriceKes: Long = 0L,
  val overallScore: Int = 100,
  val verdictTier: String = "Buy Ready",
  val estimatedRepairKes: Long = 0L,
  val recommendedOfferKes: Long = 0L,
  val checklistSummary: List<String> = emptyList(),
  val notes: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FeedSubscription(
  @DocumentId val id: String = "",
  val userId: String = "",
  val name: String = "",
  val feedUrl: String = "",
  val syncIntervalHours: Int = 12,
  val autoSyncEnabled: Boolean = true,
  val alertsEnabled: Boolean = true,
  val lastSyncedAtEpochMs: Long = 0L,
  val lastStatusMessage: String = "Scheduled",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class PriceDropAlertFrequency(
  val key: String,
  val label: String,
  val description: String,
  val minCooldownMs: Long
) {
  INSTANT(
    key = "instant",
    label = "Instant Alerts",
    description = "Notify immediately whenever newly synced listings drop below target KES",
    minCooldownMs = 0L
  ),
  DAILY(
    key = "daily",
    label = "Daily Digest",
    description = "Receive at most one consolidated price-drop alert every 24 hours",
    minCooldownMs = 24L * 60L * 60L * 1000L
  ),
  WEEKLY(
    key = "weekly",
    label = "Weekly Summary",
    description = "Receive a weekly price-drop roundup every 7 days",
    minCooldownMs = 7L * 24L * 60L * 60L * 1000L
  ),
  PAUSED(
    key = "paused",
    label = "In-App Only (Muted)",
    description = "Suppress system push notifications and highlight matches inside Explorer",
    minCooldownMs = Long.MAX_VALUE
  );

  companion object {
    fun fromKey(key: String?): PriceDropAlertFrequency =
      entries.firstOrNull { it.key == key } ?: INSTANT
  }
}

data class NotificationPreferences(
  val pushAlertsEnabled: Boolean = true,
  val frequency: PriceDropAlertFrequency = PriceDropAlertFrequency.INSTANT,
  val lastNotifiedEpochMs: Long = 0L
) {
  fun shouldSendPushNotification(nowEpochMs: Long = System.currentTimeMillis()): Boolean {
    if (!pushAlertsEnabled || frequency == PriceDropAlertFrequency.PAUSED) return false
    if (frequency.minCooldownMs <= 0L || lastNotifiedEpochMs <= 0L) return true
    return (nowEpochMs - lastNotifiedEpochMs) >= frequency.minCooldownMs
  }
}
