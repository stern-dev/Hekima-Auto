package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.firebase.HekimaFirestoreRepository
import com.example.domain.model.DataSourceRecord
import com.example.domain.model.FeedSubscription
import com.example.domain.model.ImportedVehicleListing
import com.example.domain.model.MarketAnalysis
import com.example.domain.model.MarketListing
import com.example.domain.model.SavedVehicle
import com.example.domain.model.ThemeMode
import com.example.domain.model.VehicleComparison
import com.example.domain.model.YardInspectionReport
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class HekimaFirestoreRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun userProfile_saveAndObserve_succeedsForAuthenticatedOwner() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = HekimaFirestoreRepository(firestore)

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.ensureUserProfile(uid, "Amina Otieno", ALICE_EMAIL, ThemeMode.SYSTEM)
      repo.updateUserTheme(uid, ThemeMode.DARK)
    }

    val profile = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeUserProfile(uid).first { it != null }
    }
    assertEquals(ThemeMode.DARK.key, profile?.themeMode)
    assertEquals("KES", profile?.preferredCurrency)
  }

  @Test
  fun savedVehicle_createObserveAndDelete_succeedsForOwner() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = HekimaFirestoreRepository(firestore)
    val savedId = UUID.randomUUID().toString()

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.saveVehicle(
        SavedVehicle(
          id = savedId,
          userId = uid,
          listingId = "seed_axio_2018",
          make = "Toyota",
          model = "Axio",
          year = 2018,
          askingPriceKes = 1390000L,
          mileageKm = 74000,
          transmission = "Automatic",
          fuelType = "Petrol",
          engineCc = 1500,
          condition = "Good",
          importStatus = "Foreign Used",
          location = "Nairobi",
          notes = "Strong resale"
        )
      )
    }

    val items = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeSavedVehicles(uid).first { list -> list.any { it.id == savedId } }
    }
    assertTrue(items.any { it.id == savedId && it.make == "Toyota" })

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.deleteSavedVehicle(savedId)
    }
  }

  @Test
  fun comparisonsAnalysesAndImportBatch_succeedForOwner() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = HekimaFirestoreRepository(firestore)
    val cmpId = UUID.randomUUID().toString()
    val analysisId = UUID.randomUUID().toString()
    val sourceId = UUID.randomUUID().toString()
    val impId = UUID.randomUUID().toString()

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.saveComparison(
        VehicleComparison(
          id = cmpId,
          userId = uid,
          title = "2018 Kenyan Crossover Benchmark",
          vehicleIds = listOf("v1", "v2"),
          vehicleSummaries = listOf("Toyota Axio 2018", "Mazda CX-5 2018"),
          winnerSummary = "Toyota Axio retains higher value."
        )
      )
      repo.saveMarketAnalysis(
        MarketAnalysis(
          id = analysisId,
          userId = uid,
          make = "Subaru",
          model = "Forester",
          year = 2018,
          mileageKm = 82000,
          estimatedPriceKes = 2780000L,
          lowPriceKes = 2590000L,
          highPriceKes = 2980000L,
          annualDepreciationPct = 8.4,
          fiveYearProjectedKes = 1790000L,
          summary = "AWD SUV benchmark"
        )
      )
      repo.saveImportBatch(
        sourceRecord = DataSourceRecord(
          id = sourceId,
          userId = uid,
          name = "Mombasa Yard CSV",
          sourceType = "csv",
          rowsImported = 1,
          duplicatesSkipped = 0,
          invalidRowsSkipped = 0,
          isActive = true
        ),
        listings = listOf(
          ImportedVehicleListing(
            id = impId,
            userId = uid,
            sourceId = sourceId,
            sourceName = "Mombasa Yard CSV",
            make = "Honda",
            model = "Vezel",
            year = 2018,
            askingPriceKes = 2250000L,
            mileageKm = 68000,
            engineCc = 1500,
            transmission = "Automatic",
            fuelType = "Hybrid",
            condition = "Good",
            importStatus = "Foreign Used",
            location = "Mombasa",
            demandScore = 84
          )
        )
      )
    }

    val comparisons = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeComparisons(uid).first { it.any { c -> c.id == cmpId } }
    }
    assertTrue(comparisons.any { it.id == cmpId })
  }

  @Test
  fun observeSavedVehicles_crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repo = HekimaFirestoreRepository(firestore)
    val savedId = UUID.randomUUID().toString()
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.saveVehicle(
        SavedVehicle(
          id = savedId,
          userId = aliceUid,
          listingId = "seed_harrier_2018",
          make = "Toyota",
          model = "Harrier",
          year = 2018,
          askingPriceKes = 3650000L,
          mileageKm = 64000,
          transmission = "Automatic",
          fuelType = "Petrol",
          engineCc = 2000,
          condition = "Excellent",
          importStatus = "Foreign Used",
          location = "Nairobi"
        )
      )
    }

    signInTestUser(BOB_EMAIL)
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeSavedVehicles(aliceUid).first()
      }
      fail("Expected PERMISSION_DENIED for cross-user read")
    } catch (e: Exception) {
      val fsEx = checkNotNull(e.unwrapFirestoreException()) {
        "Expected FirebaseFirestoreException, got $e"
      }
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
    }
  }

  @Test
  fun observeSavedVehicles_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = HekimaFirestoreRepository(firestore)
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeSavedVehicles("some_uid").first()
      }
      fail("Expected PERMISSION_DENIED for unauthenticated read")
    } catch (e: Exception) {
      val fsEx = checkNotNull(e.unwrapFirestoreException()) {
        "Expected FirebaseFirestoreException, got $e"
      }
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
    }
  }

  @Test
  fun watchlistsAndFeedSource_crudAndCrossUserIsolation_enforcedForOwner() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repo = HekimaFirestoreRepository(firestore)
    val wlId = UUID.randomUUID().toString()
    val feedId = UUID.randomUUID().toString()

    val watchlist = com.example.domain.model.PriceWatchlist(
      id = wlId,
      userId = aliceUid,
      make = "Toyota",
      model = "Axio",
      minYear = 2017,
      maxYear = 2019,
      maxBudgetKes = 1_320_000L,
      preferredImportStatus = "Foreign Used",
      isActive = true
    )

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.saveWatchlist(watchlist)
      repo.toggleWatchlistActive(watchlist, false)
      repo.toggleWatchlistPushAlerts(watchlist, false)
      repo.saveImportBatch(
        sourceRecord = DataSourceRecord(
          id = feedId,
          userId = aliceUid,
          name = "Feed: yards.example.co.ke",
          sourceType = "feed",
          rowsImported = 0,
          duplicatesSkipped = 0,
          invalidRowsSkipped = 0,
          isActive = true
        ),
        listings = emptyList()
      )
    }

    val list = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeWatchlists(aliceUid).first {
        val found = it.firstOrNull { w -> w.id == wlId }
        found != null && !found.isActive && !found.pushAlertsEnabled
      }
    }
    val updatedWl = list.first { it.id == wlId }
    assertEquals(false, updatedWl.isActive)
    assertEquals(false, updatedWl.pushAlertsEnabled)

    signInTestUser(BOB_EMAIL)
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeWatchlists(aliceUid).first()
      }
      fail("Expected PERMISSION_DENIED for cross-user watchlist read")
    } catch (e: Exception) {
      val fsEx = checkNotNull(e.unwrapFirestoreException()) {
        "Expected FirebaseFirestoreException, got $e"
      }
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
    }

    signInTestUser(ALICE_EMAIL)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.deleteWatchlist(wlId)
    }
  }

  @Test
  fun inspectionsAndFeedSubscriptions_crudAndCrossUserIsolation_enforced() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repo = HekimaFirestoreRepository(firestore)
    val inspId = UUID.randomUUID().toString()
    val subId = UUID.randomUUID().toString()

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.saveInspection(
        YardInspectionReport(
          id = inspId,
          userId = aliceUid,
          listingId = "seed_axio_1",
          vehicleTitle = "Toyota Axio 2018",
          location = "Nairobi",
          askingPriceKes = 1_380_000L,
          overallScore = 82,
          verdictTier = "Buy Ready",
          estimatedRepairKes = 45_000L,
          recommendedOfferKes = 1_335_000L,
          checklistSummary = listOf("Engine & CVT: Pass", "JEVIC & TIMS: Watch"),
          notes = "Needs CVT fluid service."
        )
      )
      repo.saveFeedSubscription(
        FeedSubscription(
          id = subId,
          userId = aliceUid,
          name = "Mombasa Yard Feed",
          feedUrl = "https://example.co.ke/listings.json",
          syncIntervalHours = 12,
          autoSyncEnabled = true,
          alertsEnabled = true,
          lastSyncedAtEpochMs = 1_700_000_000_000L,
          lastStatusMessage = "Ready"
        )
      )
      repo.updateFeedSubscriptionSyncStatus(
        subscriptionId = subId,
        autoSyncEnabled = false,
        lastStatusMessage = "Paused by user"
      )
    }

    val inspections = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeInspections(aliceUid).first { it.any { r -> r.id == inspId } }
    }
    assertEquals(82, inspections.first { it.id == inspId }.overallScore)

    val subs = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeFeedSubscriptions(aliceUid).first { it.any { s -> s.id == subId } }
    }
    assertEquals(false, subs.first { it.id == subId }.autoSyncEnabled)

    signInTestUser(BOB_EMAIL)
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeInspections(aliceUid).first()
      }
      fail("Expected PERMISSION_DENIED for cross-user inspections read")
    } catch (e: Exception) {
      val fsEx = checkNotNull(e.unwrapFirestoreException()) {
        "Expected FirebaseFirestoreException, got $e"
      }
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
    }

    signInTestUser(ALICE_EMAIL)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.deleteInspection(inspId)
      repo.deleteFeedSubscription(subId)
    }
  }

  @Test
  fun marketListings_publishObserveAndCrossUserRestrictions_enforced() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repo = HekimaFirestoreRepository(firestore)
    val marketId = UUID.randomUUID().toString()

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.publishMarketListing(
        MarketListing(
          id = marketId,
          sellerUserId = aliceUid,
          sellerName = "Kiambu Road Yard",
          make = "Toyota",
          model = "Harrier",
          year = 2020,
          askingPriceKes = 4_150_000L,
          mileageKm = 49_000,
          engineCc = 2000,
          transmission = "Automatic",
          fuelType = "Petrol",
          condition = "Excellent",
          importStatus = "Foreign Used",
          location = "Nairobi",
          demandScore = 88,
          listingOrigin = "live_yard"
        )
      )
    }

    // Another authenticated user (Bob) can observe shared market listings
    signInTestUser(BOB_EMAIL)
    val observedByBob = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeMarketListings().first { list -> list.any { it.id == marketId } }
    }
    assertEquals("Harrier", observedByBob.first { it.id == marketId }.model)

    // Bob cannot delete Alice's market listing
    try {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.deleteMarketListing(marketId)
      }
      fail("Expected PERMISSION_DENIED when Bob deletes Alice's market listing")
    } catch (e: Exception) {
      val fsEx = checkNotNull(e.unwrapFirestoreException()) {
        "Expected FirebaseFirestoreException, got $e"
      }
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
    }

    // Alice can delete her own listing
    signInTestUser(ALICE_EMAIL)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.deleteMarketListing(marketId)
    }
  }

  @Test
  fun deleteAllUserCloudData_wipesProfileSubcollectionsAndAuthoredMarketListings() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repo = HekimaFirestoreRepository(firestore)
    val savedId = UUID.randomUUID().toString()
    val marketId = UUID.randomUUID().toString()

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.ensureUserProfile(aliceUid, "Amina Otieno", ALICE_EMAIL, ThemeMode.DARK)
      repo.saveVehicle(
        SavedVehicle(
          id = savedId,
          userId = aliceUid,
          listingId = "seed_axio_1",
          make = "Toyota",
          model = "Axio",
          year = 2018,
          askingPriceKes = 1_350_000L,
          mileageKm = 70_000,
          transmission = "Automatic",
          fuelType = "Petrol",
          engineCc = 1500,
          condition = "Good",
          importStatus = "Foreign Used",
          location = "Nairobi"
        )
      )
      repo.publishMarketListing(
        MarketListing(
          id = marketId,
          sellerUserId = aliceUid,
          sellerName = "Amina Motors",
          make = "Mazda",
          model = "CX-5",
          year = 2019,
          askingPriceKes = 3_150_000L,
          mileageKm = 55_000,
          engineCc = 2000,
          transmission = "Automatic",
          fuelType = "Petrol",
          condition = "Excellent",
          importStatus = "Foreign Used",
          location = "Mombasa",
          demandScore = 85,
          listingOrigin = "live_yard"
        )
      )
    }

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.deleteAllUserCloudData(aliceUid)
    }

    val profileAfter = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeUserProfile(aliceUid).first()
    }
    assertNull(profileAfter)

    val savedAfter = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeSavedVehicles(aliceUid).first()
    }
    assertTrue(savedAfter.isEmpty())

    val marketAfter = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeMarketListings().first()
    }
    assertFalse(marketAfter.any { it.id == marketId })
  }

  private companion object {
    const val ALICE_EMAIL = "alice_hekima@test.com"
    const val BOB_EMAIL = "bob_hekima@test.com"
    const val DEFAULT_TIMEOUT_MS = 15000L
    const val FLOW_TIMEOUT_MS = 10000L
  }
}
