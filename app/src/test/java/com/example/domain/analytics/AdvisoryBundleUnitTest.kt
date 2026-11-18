package com.example.domain.analytics

import com.example.data.ai.AuctionSheetAiAnalyzer
import com.example.data.export.NegotiationBriefExporter
import com.example.data.fx.ExchangeRateRepository
import com.example.data.seed.KenyanMarketSeed
import com.example.domain.model.ExchangeRateSnapshot
import com.example.domain.model.FilterState
import com.example.domain.model.ImportedVehicleListing
import com.example.domain.model.InspectionStatus
import com.example.domain.model.ListingProvenance
import com.example.domain.model.MarketListing
import com.example.domain.model.MarketSyncStatus
import com.example.domain.model.NotificationPreferences
import com.example.domain.model.PriceDropAlertFrequency
import com.example.ui.components.VisualSyncState
import com.example.ui.components.resolveVisualSyncState
import org.junit.Assert.assertEquals
import java.util.TimeZone
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AdvisoryBundleUnitTest {

  @Test
  fun exchangeRateParser_parsesUsdAndDerivedJpyRates() {
    val json = """
      {
        "result": "success",
        "base_code": "USD",
        "rates": {
          "KES": 129.50,
          "JPY": 152.00
        }
      }
    """.trimIndent()

    val snapshot = ExchangeRateRepository.parseOpenErApiJson(json, nowEpochMs = 1_750_000_000_000L)
    assertFalse(snapshot.isFallback)
    assertEquals(129.50, snapshot.usdToKes, 0.01)
    assertEquals(129.50 / 152.0, snapshot.jpyToKes, 0.001)
    assertEquals(1_750_000_000_000L, snapshot.fetchedAtEpochMs)
  }

  @Test
  fun exchangeRateSnapshot_isStaleChecksFallbackAndSixHourTtl() {
    val now = 1_750_050_000_000L
    val fresh = ExchangeRateSnapshot(
      usdToKes = 129.2,
      jpyToKes = 0.85,
      fetchedAtEpochMs = now - (2 * 60 * 60 * 1000L),
      isFallback = false
    )
    val expired = fresh.copy(fetchedAtEpochMs = now - (7 * 60 * 60 * 1000L))
    val fallback = fresh.copy(isFallback = true)

    assertFalse(fresh.isStale(nowEpochMs = now))
    assertTrue(expired.isStale(nowEpochMs = now))
    assertTrue(fallback.isStale(nowEpochMs = now))
  }

  @Test
  fun formatRelativeSyncTime_formatsMinutesHoursDays() {
    val now = 1_750_500_000_000L
    assertEquals("Not synced yet", KenyanMarketAnalyticsEngine.formatRelativeSyncTime(0L, now))
    assertEquals("Just now", KenyanMarketAnalyticsEngine.formatRelativeSyncTime(now - 20_000L, now))
    assertEquals("14m ago", KenyanMarketAnalyticsEngine.formatRelativeSyncTime(now - 14 * 60_000L, now))
    assertEquals("3h ago", KenyanMarketAnalyticsEngine.formatRelativeSyncTime(now - 3 * 3_600_000L, now))
    assertEquals("2d ago", KenyanMarketAnalyticsEngine.formatRelativeSyncTime(now - 2 * 86_400_000L, now))
  }

  @Test
  fun formatLastUpdatedDisplayAndVisualSyncState_handleAllSyncStates() {
    val now = 1_750_500_000_000L
    val utc = TimeZone.getTimeZone("UTC")
    assertEquals("Awaiting live sync", KenyanMarketAnalyticsEngine.formatLastUpdatedDisplay(0L, now, utc))

    val display = KenyanMarketAnalyticsEngine.formatLastUpdatedDisplay(now - 5 * 60_000L, now, utc)
    assertTrue(display.startsWith("5m ago ("))
    assertTrue(display.endsWith(")"))

    assertEquals(
      VisualSyncState.SYNCING,
      resolveVisualSyncState(MarketSyncStatus(isUsingFallbackCatalog = false, isSyncingMarket = true), isRefreshingFx = false)
    )
    assertEquals(
      VisualSyncState.SYNCING,
      resolveVisualSyncState(MarketSyncStatus(isUsingFallbackCatalog = true, isSyncingMarket = false), isRefreshingFx = true)
    )
    assertEquals(
      VisualSyncState.ERROR,
      resolveVisualSyncState(MarketSyncStatus(isUsingFallbackCatalog = false, syncErrorMessage = "Network offline"), isRefreshingFx = false)
    )
    assertEquals(
      VisualSyncState.LIVE,
      resolveVisualSyncState(MarketSyncStatus(isUsingFallbackCatalog = false, liveYardCount = 2), isRefreshingFx = false)
    )
    assertEquals(
      VisualSyncState.OFFLINE,
      resolveVisualSyncState(MarketSyncStatus(isUsingFallbackCatalog = true, offlineReferenceCount = 98), isRefreshingFx = false)
    )
  }

  @Test
  fun listingProvenanceMappingAndFiltering_separatesLiveFeedCsvAndReference() {
    val live = MarketListing(
      id = "yard_1",
      sellerUserId = "uid_1",
      sellerName = "Mombasa Road Motors",
      make = "Toyota",
      model = "Harrier",
      year = 2020,
      askingPriceKes = 3_650_000L,
      mileageKm = 52_000
    ).toVehicleListing()

    val feed = ImportedVehicleListing(
      id = "imp_1",
      sourceId = "feed_1",
      sourceName = "Feed: Nairobi Stock",
      make = "Mazda",
      model = "CX-5",
      year = 2019,
      askingPriceKes = 2_890_000L,
      mileageKm = 61_000
    ).toVehicleListing(sourceType = "feed")

    val csv = ImportedVehicleListing(
      id = "imp_2",
      sourceId = "csv_1",
      sourceName = "yard_batch.csv",
      make = "Subaru",
      model = "Forester",
      year = 2018,
      askingPriceKes = 2_450_000L,
      mileageKm = 74_000
    ).toVehicleListing(sourceType = "csv")

    val ref = KenyanMarketSeed.listings.first()

    assertEquals(ListingProvenance.LIVE_YARD, live.provenance)
    assertEquals(ListingProvenance.DEALER_FEED, feed.provenance)
    assertEquals(ListingProvenance.CSV_IMPORT, csv.provenance)
    assertEquals(ListingProvenance.OFFLINE_REFERENCE, ref.provenance)

    val all = listOf(live, feed, csv, ref)
    assertEquals(
      listOf(live.id),
      KenyanMarketAnalyticsEngine.applyFilters(all, FilterState(provenanceFilter = ListingProvenance.LIVE_YARD)).map { it.id }
    )
    assertEquals(
      listOf(feed.id),
      KenyanMarketAnalyticsEngine.applyFilters(all, FilterState(provenanceFilter = ListingProvenance.DEALER_FEED)).map { it.id }
    )
    assertEquals(
      listOf(csv.id),
      KenyanMarketAnalyticsEngine.applyFilters(all, FilterState(provenanceFilter = ListingProvenance.CSV_IMPORT)).map { it.id }
    )
    assertEquals(
      listOf(ref.id),
      KenyanMarketAnalyticsEngine.applyFilters(all, FilterState(provenanceFilter = ListingProvenance.OFFLINE_REFERENCE)).map { it.id }
    )
  }

  @Test
  fun auctionSheetJsonParser_decodesAllStructuredFields() {
    val json = """
      {
        "vehicleTitle": "Toyota Corolla Axio 2019",
        "make": "Toyota",
        "model": "Axio",
        "year": 2019,
        "chassisCode": "NKE165-7201482",
        "auctionGrade": "4.5",
        "interiorGrade": "B",
        "reportedMileageKm": 64000,
        "mileageVerdict": "Plausible",
        "ageRuleNote": "Eligible for Kenya import in 2026 (7 years old).",
        "repairRemarks": ["A1 front bumper scuff", "U1 left door dent"],
        "redFlags": ["Hybrid battery health check advised"],
        "yardVerificationChecks": ["Verify NKE165 firewall chassis plate", "Inspect JEVIC certificate"],
        "recommendedAction": "Proceed with standard yard inspection before deposit.",
        "suggestedMaxOfferKes": 1460000
      }
    """.trimIndent()

    val parsed = AuctionSheetAiAnalyzer.parseAnalysisJson(json)
    assertEquals("Toyota Corolla Axio 2019", parsed.vehicleTitle)
    assertEquals("4.5", parsed.auctionGrade)
    assertEquals("NKE165-7201482", parsed.chassisCode)
    assertEquals(64000, parsed.reportedMileageKm)
    assertEquals("Plausible", parsed.mileageVerdict)
    assertEquals(2, parsed.repairRemarks.size)
    assertEquals(1460000L, parsed.suggestedMaxOfferKes)

    val draft = YardInspectionEngine.createDraftFromAiAnalysis(parsed)
    assertEquals(1460000L, draft.askingPriceKes)
    assertTrue(draft.notes.contains("4.5/B"))
  }

  @Test
  fun yardInspectionEngine_computesScoreDeductionsAndOfferBrief() {
    val listing = KenyanMarketSeed.listings.first()
    val initialDraft = YardInspectionEngine.createDraftFromListing(listing)
    assertTrue(initialDraft.items.size >= 10)

    // Mark first two high-weight items as ISSUE
    val issue1 = initialDraft.items[0]
    val issue2 = initialDraft.items[2]
    val updated = initialDraft.copy(
      items = initialDraft.items.map { item ->
        when (item.id) {
          issue1.id -> item.copy(status = InspectionStatus.ISSUE)
          issue2.id -> item.copy(status = InspectionStatus.ISSUE)
          else -> item
        }
      }
    )

    assertTrue(updated.overallScore < 80)
    assertEquals(issue1.defaultRepairKes + issue2.defaultRepairKes, updated.estimatedRepairKes)
    assertEquals(updated.askingPriceKes - updated.estimatedRepairKes, updated.recommendedOfferKes)

    val brief = NegotiationBriefExporter.buildInspectionBriefText(updated)
    assertTrue(brief.contains("YARD INSPECTION & OFFER BRIEF"))
    assertTrue(brief.contains(issue1.title))
  }

  @Test
  fun notificationPreferences_enforcesMasterToggleAndFrequencyCooldowns() {
    assertEquals(PriceDropAlertFrequency.INSTANT, PriceDropAlertFrequency.fromKey(null))
    assertEquals(PriceDropAlertFrequency.DAILY, PriceDropAlertFrequency.fromKey("daily"))
    assertEquals(PriceDropAlertFrequency.WEEKLY, PriceDropAlertFrequency.fromKey("weekly"))
    assertEquals(PriceDropAlertFrequency.PAUSED, PriceDropAlertFrequency.fromKey("paused"))

    val now = 2_000_000_000_000L
    val disabled = NotificationPreferences(pushAlertsEnabled = false, frequency = PriceDropAlertFrequency.INSTANT)
    assertFalse(disabled.shouldSendPushNotification(now))

    val pausedFreq = NotificationPreferences(pushAlertsEnabled = true, frequency = PriceDropAlertFrequency.PAUSED)
    assertFalse(pausedFreq.shouldSendPushNotification(now))

    val instant = NotificationPreferences(
      pushAlertsEnabled = true,
      frequency = PriceDropAlertFrequency.INSTANT,
      lastNotifiedEpochMs = now - 1_000L
    )
    assertTrue(instant.shouldSendPushNotification(now))

    val dailyTooSoon = NotificationPreferences(
      pushAlertsEnabled = true,
      frequency = PriceDropAlertFrequency.DAILY,
      lastNotifiedEpochMs = now - (12L * 60L * 60L * 1000L)
    )
    assertFalse(dailyTooSoon.shouldSendPushNotification(now))

    val dailyElapsed = NotificationPreferences(
      pushAlertsEnabled = true,
      frequency = PriceDropAlertFrequency.DAILY,
      lastNotifiedEpochMs = now - (25L * 60L * 60L * 1000L)
    )
    assertTrue(dailyElapsed.shouldSendPushNotification(now))

    val weeklyTooSoon = NotificationPreferences(
      pushAlertsEnabled = true,
      frequency = PriceDropAlertFrequency.WEEKLY,
      lastNotifiedEpochMs = now - (3L * 24L * 60L * 60L * 1000L)
    )
    assertFalse(weeklyTooSoon.shouldSendPushNotification(now))

    val weeklyElapsed = NotificationPreferences(
      pushAlertsEnabled = true,
      frequency = PriceDropAlertFrequency.WEEKLY,
      lastNotifiedEpochMs = now - (7L * 24L * 60L * 60L * 1000L)
    )
    assertTrue(weeklyElapsed.shouldSendPushNotification(now))
  }
}
