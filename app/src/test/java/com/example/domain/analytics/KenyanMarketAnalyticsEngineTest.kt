package com.example.domain.analytics

import com.example.data.seed.KenyanMarketSeed
import com.example.domain.model.CifCurrency
import com.example.domain.model.FilterState
import com.example.domain.model.LandedCostInput
import com.example.domain.model.OdometerSanityStatus
import com.example.domain.model.PriceWatchlist
import com.example.domain.model.VehicleSegment
import com.example.domain.model.VehicleSpecification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KenyanMarketAnalyticsEngineTest {

  @Test
  fun dashboardSummary_handlesEmptyDatasetGracefully() {
    val summary = KenyanMarketAnalyticsEngine.computeDashboardSummary(emptyList())
    assertEquals(0, summary.totalVehicles)
    assertEquals(0L, summary.averagePriceKes)
    assertEquals("N/A", summary.bestRetentionBrand)
  }

  @Test
  fun pricingEstimate_isReproducibleAndOrdersMileageCorrectly() {
    val listings = KenyanMarketSeed.listings
    val lowMileageSpec = VehicleSpecification(make = "Toyota", model = "Axio", year = 2018, mileageKm = 45000)
    val highMileageSpec = VehicleSpecification(make = "Toyota", model = "Axio", year = 2018, mileageKm = 155000)

    val est1 = KenyanMarketAnalyticsEngine.estimatePrice(listings, lowMileageSpec)
    val est1Repeat = KenyanMarketAnalyticsEngine.estimatePrice(listings, lowMileageSpec)
    val est2 = KenyanMarketAnalyticsEngine.estimatePrice(listings, highMileageSpec)

    assertEquals(est1.estimatedPriceKes, est1Repeat.estimatedPriceKes)
    assertTrue("Lower mileage should yield higher estimate", est1.estimatedPriceKes > est2.estimatedPriceKes)
    assertTrue(est1.lowPriceKes < est1.estimatedPriceKes)
    assertTrue(est1.highPriceKes > est1.estimatedPriceKes)
  }

  @Test
  fun depreciationAndTopTenRetention_returnObservedAndProjectedPoints() {
    val listings = KenyanMarketSeed.listings
    val axioDep = KenyanMarketAnalyticsEngine.analyzeVehicleDepreciation(listings, "Toyota", "Axio", 2018)
    assertTrue(axioDep.curvePoints.any { !it.isProjected })
    assertTrue(axioDep.curvePoints.count { it.isProjected } == 5)
    assertTrue(axioDep.fiveYearProjectedKes < axioDep.currentMarketPriceKes)

    val top10 = KenyanMarketAnalyticsEngine.topTenValueRetentionVehicles(listings)
    assertEquals(10, top10.size)
    assertTrue(top10.first().valueRetainedPct >= top10.last().valueRetainedPct)
  }

  @Test
  fun compareVehicles_generatesVerdictForUpToFourVehicles() {
    val sample = KenyanMarketSeed.listings.filter { it.year == 2018 }.distinctBy { it.make }.take(4)
    val verdict = KenyanMarketAnalyticsEngine.compareVehicles(sample)
    assertEquals(4, verdict.metrics.size)
    assertTrue(verdict.strongestResaleVehicle.isNotBlank())
    assertTrue(verdict.verdictExplanation.contains("retention"))
  }

  @Test
  fun insightsEngine_computesDynamicFindingsAndFiltersWork() {
    val listings = KenyanMarketSeed.listings
    val (insights, anomalies) = KenyanMarketAnalyticsEngine.generateInsights(listings)
    assertTrue(insights.isNotEmpty())
    assertTrue(anomalies.isNotEmpty())
    assertTrue(insights.any { it.id == "ins_odometer_sanity" })

    val toyotaOnly = KenyanMarketAnalyticsEngine.applyFilters(
      listings,
      FilterState(selectedMake = "Toyota")
    )
    assertTrue(toyotaOnly.isNotEmpty())
    assertTrue(toyotaOnly.all { it.make == "Toyota" })
  }

  @Test
  fun kraDutyAndEightYearRule_flagAgeLimitsAndComputeTaxesAccurately() {
    assertEquals(com.example.domain.model.ImportAgeRuleStatus.ELIGIBLE, KenyanMarketAnalyticsEngine.evaluateImportAgeRule(2021))
    assertEquals(com.example.domain.model.ImportAgeRuleStatus.APPROACHING_LIMIT, KenyanMarketAnalyticsEngine.evaluateImportAgeRule(2019))
    assertEquals(com.example.domain.model.ImportAgeRuleStatus.OVER_LIMIT, KenyanMarketAnalyticsEngine.evaluateImportAgeRule(2018))

    val importSpec = VehicleSpecification(
      make = "Mazda",
      model = "CX-5",
      year = 2021,
      mileageKm = 55000,
      engineCc = 2000,
      fuelType = "Petrol"
    )
    val est = KenyanMarketAnalyticsEngine.estimatePrice(KenyanMarketSeed.listings, importSpec)
    val kra = est.kraDutyEstimate!!
    assertEquals(com.example.domain.model.ImportAgeRuleStatus.ELIGIBLE, kra.ageRuleStatus)
    assertTrue(kra.crspBenchmarkKes > kra.localMarketPriceKes)
    assertTrue(kra.importDutyKes > 0L)
    assertEquals(25.0, kra.exciseDutyRatePct, 0.01)
    assertTrue(kra.exciseDutyKes > 0L)
    assertTrue(kra.vatKes > 0L)
    assertTrue(kra.idfKes > 0L && kra.rdlKes > 0L)
    assertEquals(
      kra.importDutyKes + kra.exciseDutyKes + kra.vatKes + kra.idfKes + kra.rdlKes,
      kra.totalKraTaxesKes
    )
    assertEquals(
      kra.estimatedCifKes + kra.totalKraTaxesKes + kra.clearingAndNtsaKes,
      kra.mombasaLandedCostKes
    )
  }

  @Test
  fun tcoEstimate_comparesFuelTrimsAndIncludesInsuranceMaintenanceDepreciation() {
    val spec = VehicleSpecification(
      make = "Toyota",
      model = "Axio",
      year = 2019,
      mileageKm = 65000,
      engineCc = 1500,
      fuelType = "Petrol"
    )
    val est = KenyanMarketAnalyticsEngine.estimatePrice(KenyanMarketSeed.listings, spec)
    val tco = est.tcoEstimate!!

    assertEquals(3, tco.fuelTrimComparison.size)
    val petrolFuel = tco.fuelTrimComparison.first { it.fuelType == "Petrol" }.annualFuelCostKes
    val dieselFuel = tco.fuelTrimComparison.first { it.fuelType == "Diesel" }.annualFuelCostKes
    val hybridFuel = tco.fuelTrimComparison.first { it.fuelType == "Hybrid" }.annualFuelCostKes
    assertTrue("Hybrid should be cheaper on fuel than Petrol", hybridFuel < petrolFuel)
    assertTrue("Diesel should be cheaper on fuel than Petrol", dieselFuel < petrolFuel)

    assertTrue(tco.insuranceRatePct in 3.5..4.5)
    assertTrue(tco.annualInsuranceKes >= 45_000L)
    assertTrue(tco.annualMaintenanceKes > 0L)
    assertEquals(
      tco.fiveYearDepreciationLossKes + tco.fiveYearFuelKes + tco.fiveYearInsuranceKes + tco.fiveYearMaintenanceKes,
      tco.totalFiveYearTcoKes
    )
    assertTrue(tco.effectiveMonthlyTcoKes > 0L)
  }

  @Test
  fun calculateLandedCost_supportsUsdCifCrspOverrideAndEngineExciseTiers() {
    val usdInput = LandedCostInput(
      make = "Toyota",
      model = "Axio",
      year = 2021,
      engineCc = 1500,
      fuelType = "Petrol",
      cifCurrency = CifCurrency.USD,
      customCifText = "8000",
      usdToKesRateText = "130",
      customCrspText = "2600000",
      clearingAndNtsaText = "95000"
    )
    val calc1500 = KenyanMarketAnalyticsEngine.calculateLandedCost(KenyanMarketSeed.listings, usdInput)
    assertEquals(1_040_000L, calc1500.effectiveCifKes)
    assertTrue(calc1500.isCustomCifApplied)
    assertTrue(calc1500.isCustomCrspApplied)
    assertEquals(2_600_000L, calc1500.kraEstimate.crspBenchmarkKes)
    assertEquals(20.0, calc1500.kraEstimate.exciseDutyRatePct, 0.01)
    assertEquals(95_000L, calc1500.kraEstimate.clearingAndNtsaKes)
    assertEquals(
      calc1500.effectiveCifKes + calc1500.kraEstimate.totalKraTaxesKes + 95_000L,
      calc1500.kraEstimate.mombasaLandedCostKes
    )
    assertTrue(calc1500.comparableListings.isNotEmpty())

    val calc3500 = KenyanMarketAnalyticsEngine.calculateLandedCost(
      KenyanMarketSeed.listings,
      usdInput.copy(engineCc = 3500)
    )
    assertEquals(35.0, calc3500.kraEstimate.exciseDutyRatePct, 0.01)
    assertTrue(calc3500.kraEstimate.exciseDutyKes > calc1500.kraEstimate.exciseDutyKes)
  }

  @Test
  fun expandedKenyanCatalog_coversAllFiveSegmentsAndFiltersCorrectly() {
    val models = KenyanMarketSeed.models
    val listings = KenyanMarketSeed.listings
    assertTrue("Expected >= 60 Kenyan models, found ${models.size}", models.size >= 60)
    assertTrue("Expected >= 300 listings, found ${listings.size}", listings.size >= 300)

    listOf(
      VehicleSegment.CARS,
      VehicleSegment.SUVS,
      VehicleSegment.VANS_MPVS,
      VehicleSegment.PICKUPS,
      VehicleSegment.LUXURY
    ).forEach { seg ->
      val matching = KenyanMarketAnalyticsEngine.applyFilters(
        listings,
        FilterState(selectedSegment = seg)
      )
      assertTrue("Segment ${seg.label} should have listings", matching.isNotEmpty())
      assertTrue(matching.all { it.segment == seg.label })
    }
  }

  @Test
  fun odometerSanity_flagsSuspiciouslyLowMileageAndFiltersFlaggedUnits() {
    val suspect = KenyanMarketAnalyticsEngine.evaluateOdometerSanity(2016, 22_000, "Foreign Used")
    assertEquals(OdometerSanityStatus.SUSPECT_LOW, suspect.status)
    assertTrue(suspect.verificationAdvice.contains("JEVIC", ignoreCase = true))

    val checkDocs = KenyanMarketAnalyticsEngine.evaluateOdometerSanity(2016, 48_000, "Foreign Used")
    assertEquals(OdometerSanityStatus.CHECK_DOCS, checkDocs.status)

    val plausible = KenyanMarketAnalyticsEngine.evaluateOdometerSanity(2018, 86_000, "Foreign Used")
    assertEquals(OdometerSanityStatus.PLAUSIBLE, plausible.status)

    val flaggedListings = KenyanMarketAnalyticsEngine.applyFilters(
      KenyanMarketSeed.listings,
      FilterState(onlyOdometerFlagged = true)
    )
    assertTrue(flaggedListings.isNotEmpty())
    assertTrue(
      flaggedListings.all {
        KenyanMarketAnalyticsEngine.evaluateOdometerSanity(it.year, it.mileageKm, it.importStatus).status != OdometerSanityStatus.PLAUSIBLE
      }
    )
  }

  @Test
  fun targetPriceWatchlists_matchesUnderBudgetListingsAndFiltersExplorer() {
    val axioWatchlist = PriceWatchlist(
      id = "wl_axio_test",
      userId = "u1",
      make = "Toyota",
      model = "Axio",
      minYear = 2017,
      maxYear = 2019,
      maxBudgetKes = 1_320_000L,
      preferredImportStatus = "All",
      isActive = true
    )
    val matches = KenyanMarketAnalyticsEngine.evaluateWatchlists(
      KenyanMarketSeed.listings,
      listOf(axioWatchlist)
    )
    assertEquals(1, matches.size)
    assertTrue("Expected at least one matching 2018 Axio under 1.32M", matches.first().matchingListings.isNotEmpty())
    assertTrue(matches.first().matchingListings.all { it.askingPriceKes <= 1_320_000L })

    val filtered = KenyanMarketAnalyticsEngine.applyFilters(
      KenyanMarketSeed.listings,
      FilterState(onlyWatchlistMatches = true),
      listOf(axioWatchlist)
    )
    assertEquals(
      KenyanMarketAnalyticsEngine.matchingListingIds(KenyanMarketSeed.listings, listOf(axioWatchlist)).size,
      filtered.size
    )
  }
}
