package com.example.domain.analytics

import com.example.data.seed.KenyanMarketSeed
import com.example.domain.model.BrandPriceStat
import com.example.domain.model.BrandShareSlice
import com.example.domain.model.CifCurrency
import com.example.domain.model.ComparisonMetric
import com.example.domain.model.ComparisonVerdict
import com.example.domain.model.DashboardSummary
import com.example.domain.model.DepreciationCurvePoint
import com.example.domain.model.FilterState
import com.example.domain.model.FuelTrimEstimate
import com.example.domain.model.HistogramBucket
import com.example.domain.model.ImportAgeRuleStatus
import com.example.domain.model.InsightCategory
import com.example.domain.model.KraDutyEstimate
import com.example.domain.model.LandedCostCalculation
import com.example.domain.model.LandedCostInput
import com.example.domain.model.MarketInsight
import com.example.domain.model.ModelDepreciationSeries
import com.example.domain.model.OdometerSanityReport
import com.example.domain.model.OdometerSanityStatus
import com.example.domain.model.PriceAnomalyListing
import com.example.domain.model.PriceFactorImpact
import com.example.domain.model.PriceWatchlist
import com.example.domain.model.PricingEstimate
import com.example.domain.model.RetentionLeaderItem
import com.example.domain.model.ScatterPoint
import com.example.domain.model.TcoEstimate
import com.example.domain.model.VehicleDepreciationAnalysis
import com.example.domain.model.VehicleListing
import com.example.domain.model.VehicleSegment
import com.example.domain.model.VehicleSpecification
import com.example.domain.model.WatchlistMatch
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt

object KenyanMarketAnalyticsEngine {

  private const val CURRENT_YEAR = 2026

  fun applyFilters(
    listings: List<VehicleListing>,
    filter: FilterState,
    watchlists: List<PriceWatchlist> = emptyList()
  ): List<VehicleListing> {
    val q = filter.searchQuery.trim().lowercase()
    val matchedIds = if (filter.onlyWatchlistMatches && watchlists.isNotEmpty()) {
      matchingListingIds(listings, watchlists)
    } else null

    return listings.filter { item ->
      val itemSegment = item.segment.ifBlank {
        KenyanMarketSeed.segmentFor(item.make, item.model).label
      }
      val segmentMatch = filter.selectedSegment == VehicleSegment.ALL ||
        itemSegment.equals(filter.selectedSegment.label, ignoreCase = true)
      val watchlistOk = matchedIds == null || item.id in matchedIds
      val odometerOk = !filter.onlyOdometerFlagged ||
        evaluateOdometerSanity(item.year, item.mileageKm, item.importStatus).status != OdometerSanityStatus.PLAUSIBLE
      val provenanceOk = filter.provenanceFilter == null || item.provenance == filter.provenanceFilter

      (filter.selectedMake == "All" || item.make.equals(filter.selectedMake, ignoreCase = true)) &&
        segmentMatch &&
        watchlistOk &&
        odometerOk &&
        provenanceOk &&
        item.year in filter.minYear..filter.maxYear &&
        item.mileageKm <= filter.maxMileageKm &&
        item.askingPriceKes <= filter.maxPriceKes &&
        (filter.importStatus == "All" || item.importStatus.equals(filter.importStatus, ignoreCase = true)) &&
        (filter.sourceFilter == "All" || item.sourceName == filter.sourceFilter) &&
        (q.isEmpty() ||
          item.make.lowercase().contains(q) ||
          item.model.lowercase().contains(q) ||
          item.location.lowercase().contains(q) ||
          itemSegment.lowercase().contains(q) ||
          item.year.toString().contains(q))
    }
  }

  fun formatRelativeSyncTime(
    epochMs: Long,
    nowEpochMs: Long = System.currentTimeMillis()
  ): String {
    if (epochMs <= 0L) return "Not synced yet"
    val diffMs = (nowEpochMs - epochMs).coerceAtLeast(0L)
    val mins = diffMs / 60_000L
    return when {
      mins < 1L -> "Just now"
      mins < 60L -> "${mins}m ago"
      mins < 24L * 60L -> "${mins / 60L}h ago"
      else -> {
        val days = mins / (24L * 60L)
        "${days}d ago"
      }
    }
  }

  fun formatAbsoluteTimestamp(
    epochMs: Long,
    timeZone: java.util.TimeZone = java.util.TimeZone.getDefault()
  ): String {
    if (epochMs <= 0L) return "Never"
    val formatter = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.US).apply {
      this.timeZone = timeZone
    }
    return formatter.format(java.util.Date(epochMs))
  }

  fun formatLastUpdatedDisplay(
    epochMs: Long,
    nowEpochMs: Long = System.currentTimeMillis(),
    timeZone: java.util.TimeZone = java.util.TimeZone.getDefault()
  ): String {
    if (epochMs <= 0L) return "Awaiting live sync"
    return "${formatRelativeSyncTime(epochMs, nowEpochMs)} (${formatAbsoluteTimestamp(epochMs, timeZone)})"
  }

  fun computeDashboardSummary(listings: List<VehicleListing>): DashboardSummary {
    if (listings.isEmpty()) {
      return DashboardSummary(
        totalVehicles = 0,
        averagePriceKes = 0L,
        medianPriceKes = 0L,
        averageAnnualDepreciationPct = 0.0,
        mostExpensiveBrand = "N/A",
        mostExpensiveBrandAvgKes = 0L,
        bestRetentionBrand = "N/A",
        bestRetentionPct = 0.0,
        mostListedVehicle = "N/A",
        mostListedCount = 0,
        brandPriceStats = emptyList(),
        priceVsMileagePoints = emptyList(),
        priceVsAgePoints = emptyList(),
        depreciationSeries = emptyList(),
        priceDistribution = emptyList(),
        marketShare = emptyList()
      )
    }

    val sortedPrices = listings.map { it.askingPriceKes }.sorted()
    val avgPrice = listings.map { it.askingPriceKes.toDouble() }.average().roundToLong()
    val medianPrice = if (sortedPrices.size % 2 == 0) {
      (sortedPrices[sortedPrices.size / 2 - 1] + sortedPrices[sortedPrices.size / 2]) / 2L
    } else {
      sortedPrices[sortedPrices.size / 2]
    }

    val brandGroups = listings.groupBy { it.make }
    val brandStats = brandGroups.map { (brand, group) ->
      val prices = group.map { it.askingPriceKes }.sorted()
      val brandAvg = group.map { it.askingPriceKes.toDouble() }.average().roundToLong()
      val brandMedian = prices[prices.size / 2]
      val avgRetention = group.map { estimateFiveYearRetentionPct(it.make, it.model) }.average()
      BrandPriceStat(
        brand = brand,
        averagePriceKes = brandAvg,
        medianPriceKes = brandMedian,
        listingCount = group.size,
        avgRetentionPct = avgRetention
      )
    }.sortedByDescending { it.averagePriceKes }

    val avgDepPct = listings.map {
      KenyanMarketSeed.modelRateFor(it.make, it.model) * 100.0
    }.average()

    val mostExpensive = brandStats.maxByOrNull { it.averagePriceKes }
    val bestRetention = brandStats.maxByOrNull { it.avgRetentionPct }
    val topModelGroup = listings.groupBy { "${it.make} ${it.model}" }
      .maxByOrNull { it.value.size }

    val mileagePoints = listings.take(90).map {
      ScatterPoint(
        x = (it.mileageKm / 1000f),
        y = (it.askingPriceKes / 1_000_000f),
        label = it.fullName,
        make = it.make,
        priceKes = it.askingPriceKes
      )
    }

    val agePoints = listings.groupBy { (CURRENT_YEAR - it.year).coerceAtLeast(1) }
      .entries
      .sortedBy { it.key }
      .map { (age, group) ->
        val mean = group.map { it.askingPriceKes.toDouble() }.average().roundToLong()
        ScatterPoint(
          x = age.toFloat(),
          y = (mean / 1_000_000f),
          label = "${age}y old (${group.size} cars)",
          make = "Market Avg",
          priceKes = mean
        )
      }

    val benchmarkSpecs = listOf(
      Triple("Toyota", "Axio", 2018),
      Triple("Mazda", "CX-5", 2018),
      Triple("Subaru", "Forester", 2018),
      Triple("Toyota", "Harrier", 2018)
    )
    val depSeries = benchmarkSpecs.mapNotNull { (mk, md, yr) ->
      val analysis = analyzeVehicleDepreciation(listings, mk, md, yr)
      if (analysis.curvePoints.isNotEmpty()) {
        ModelDepreciationSeries(
          title = "$mk $md",
          points = analysis.curvePoints
        )
      } else null
    }

    val buckets = listOf(
      HistogramBucket("< 1.2M", 0L, 1_200_000L, listings.count { it.askingPriceKes < 1_200_000L }),
      HistogramBucket("1.2-2M", 1_200_000L, 2_000_000L, listings.count { it.askingPriceKes in 1_200_000L until 2_000_000L }),
      HistogramBucket("2-3M", 2_000_000L, 3_000_000L, listings.count { it.askingPriceKes in 2_000_000L until 3_000_000L }),
      HistogramBucket("3-4.5M", 3_000_000L, 4_500_000L, listings.count { it.askingPriceKes in 3_000_000L until 4_500_000L }),
      HistogramBucket("4.5M+", 4_500_000L, Long.MAX_VALUE, listings.count { it.askingPriceKes >= 4_500_000L })
    )

    val total = listings.size.toDouble()
    val shareSlices = brandStats
      .sortedByDescending { it.listingCount }
      .take(6)
      .map {
        BrandShareSlice(
          brand = it.brand,
          count = it.listingCount,
          percentage = (it.listingCount / total) * 100.0
        )
      }

    return DashboardSummary(
      totalVehicles = listings.size,
      averagePriceKes = avgPrice,
      medianPriceKes = medianPrice,
      averageAnnualDepreciationPct = avgDepPct,
      mostExpensiveBrand = mostExpensive?.brand ?: "N/A",
      mostExpensiveBrandAvgKes = mostExpensive?.averagePriceKes ?: 0L,
      bestRetentionBrand = bestRetention?.brand ?: "N/A",
      bestRetentionPct = bestRetention?.avgRetentionPct ?: 0.0,
      mostListedVehicle = topModelGroup?.key ?: "N/A",
      mostListedCount = topModelGroup?.value?.size ?: 0,
      brandPriceStats = brandStats,
      priceVsMileagePoints = mileagePoints,
      priceVsAgePoints = agePoints,
      depreciationSeries = depSeries,
      priceDistribution = buckets,
      marketShare = shareSlices
    )
  }

  /**
   * Multivariate Ridge Regression (Normal Equation solver on log-price) blended with
   * KNN comparable peer matching for Kenyan market price estimation.
   */
  fun estimatePrice(
    listings: List<VehicleListing>,
    spec: VehicleSpecification
  ): PricingEstimate {
    val safeMileage = spec.mileageKm.coerceIn(0, 1_500_000)
    val safeYear = spec.year.coerceIn(1995, CURRENT_YEAR)
    val safeEngine = spec.engineCc.coerceIn(600, 6500)
    val normalizedSpec = spec.copy(year = safeYear, mileageKm = safeMileage, engineCc = safeEngine)

    if (listings.isEmpty()) {
      return PricingEstimate(
        spec = normalizedSpec,
        estimatedPriceKes = 0L,
        comparableAverageKes = 0L,
        lowPriceKes = 0L,
        highPriceKes = 0L,
        annualDepreciationPct = 0.0,
        cumulativeDepreciationPct = 0.0,
        confidenceLabel = "No Dataset Available",
        comparableListings = emptyList(),
        factorImpacts = emptyList(),
        methodologyExplanation = "Import or enable at least one dataset source to compute Kenyan market estimates."
      )
    }

    // 1. Find comparable listings (exact model first, fallback to same make or engine segment)
    val exactModelPeers = listings.filter {
      it.make.equals(normalizedSpec.make, ignoreCase = true) &&
        it.model.equals(normalizedSpec.model, ignoreCase = true)
    }
    val sameMakePeers = listings.filter { it.make.equals(normalizedSpec.make, ignoreCase = true) }
    val trainingPool = when {
      exactModelPeers.size >= 4 -> exactModelPeers
      sameMakePeers.size >= 6 -> sameMakePeers
      else -> listings
    }

    // 2. Multivariate Ridge OLS on log(price) ~ [1, age, mileage_100k, engine_L, isLocal, isAuto, isHybridOrDiesel, condScore]
    val weights = fitRidgeLogPriceModel(trainingPool)
    val targetFeatures = encodeFeatures(
      year = normalizedSpec.year,
      mileageKm = normalizedSpec.mileageKm,
      engineCc = normalizedSpec.engineCc,
      importStatus = normalizedSpec.importStatus,
      transmission = normalizedSpec.transmission,
      fuelType = normalizedSpec.fuelType,
      condition = normalizedSpec.condition
    )

    val predictedLogPrice = dot(weights, targetFeatures)
    val regressionPrice = kotlin.math.exp(predictedLogPrice).coerceIn(350_000.0, 120_000_000.0)

    // 3. KNN weighted comparable average from closest matches in dataset
    val scoredPeers = listings.map { item ->
      val makeMatch = if (item.make.equals(normalizedSpec.make, ignoreCase = true)) 0.0 else 4.0
      val modelMatch = if (item.model.equals(normalizedSpec.model, ignoreCase = true)) 0.0 else 3.0
      val yearDiff = abs(item.year - normalizedSpec.year) * 0.85
      val mileageDiff = abs(item.mileageKm - normalizedSpec.mileageKm) / 40_000.0
      val engineDiff = abs(item.engineCc - normalizedSpec.engineCc) / 800.0
      val dist = makeMatch + modelMatch + yearDiff + mileageDiff + engineDiff
      item to dist
    }.sortedBy { it.second }

    val topComparables = scoredPeers.take(5).map { it.first }
    val knnWeightedAvg = if (scoredPeers.isNotEmpty()) {
      val topForBlend = scoredPeers.take(4)
      val weightsSum = topForBlend.sumOf { 1.0 / (it.second + 0.35) }
      topForBlend.sumOf { (item, dist) -> item.askingPriceKes * (1.0 / (dist + 0.35)) } / weightsSum
    } else regressionPrice

    // Blend regression estimate with exact-model KNN comparables when exact peers exist
    val blendedRaw = if (exactModelPeers.isNotEmpty()) {
      regressionPrice * 0.55 + knnWeightedAvg * 0.45
    } else {
      regressionPrice * 0.8 + knnWeightedAvg * 0.2
    }

    val estimatedKes = roundToTenThousand(blendedRaw)
    val comparableAvgKes = if (topComparables.isNotEmpty()) {
      roundToTenThousand(topComparables.map { it.askingPriceKes.toDouble() }.average())
    } else estimatedKes

    val spreadRatio = when {
      exactModelPeers.size >= 5 -> 0.075
      exactModelPeers.isNotEmpty() -> 0.105
      else -> 0.14
    }
    val lowKes = roundToTenThousand(estimatedKes * (1.0 - spreadRatio))
    val highKes = roundToTenThousand(estimatedKes * (1.0 + spreadRatio))

    val annualDep = KenyanMarketSeed.modelRateFor(normalizedSpec.make, normalizedSpec.model) * 100.0
    val ageYears = (CURRENT_YEAR - normalizedSpec.year).coerceAtLeast(1)
    val retainedRatio = (1.0 - annualDep / 100.0).pow(ageYears.toDouble())
    val cumulativeDepPct = ((1.0 - retainedRatio) * 100.0).coerceIn(5.0, 88.0)

    // Compute interpretable factor impacts relative to a 2018 / 75,000km / Foreign Used / Good baseline
    val baselinePrice = estimatedKes / (
      conditionMultiplier(normalizedSpec.condition) *
        importMultiplier(normalizedSpec.importStatus) *
        transmissionMultiplier(normalizedSpec.transmission)
      )
    val ageYearsFrom2018 = 2018 - normalizedSpec.year
    val ageImpact = roundToTenThousand(estimatedKes * (1.0 - (1.0 - annualDep / 100.0).pow(-ageYearsFrom2018.toDouble())) * 0.35)
    val mileageImpact = roundToTenThousand(-((normalizedSpec.mileageKm - 75000) / 10000.0) * (estimatedKes * 0.011))
    val conditionImpact = roundToTenThousand(baselinePrice * (conditionMultiplier(normalizedSpec.condition) - 1.0))
    val importImpact = roundToTenThousand(baselinePrice * (importMultiplier(normalizedSpec.importStatus) - 1.0))

    val factorImpacts = listOf(
      PriceFactorImpact(
        factorName = "Vehicle Age (${ageYears} yrs)",
        detail = "${normalizedSpec.year} model year (~${String.format("%.1f", annualDep)}%/yr curve)",
        impactKes = ageImpact
      ),
      PriceFactorImpact(
        factorName = "Odometer Mileage",
        detail = "${formatNumber(normalizedSpec.mileageKm)} km vs 75,000 km Kenyan benchmark",
        impactKes = mileageImpact
      ),
      PriceFactorImpact(
        factorName = "Import Status",
        detail = normalizedSpec.importStatus,
        impactKes = importImpact
      ),
      PriceFactorImpact(
        factorName = "Condition & Spec",
        detail = "${normalizedSpec.condition} • ${normalizedSpec.engineCc}cc ${normalizedSpec.fuelType} ${normalizedSpec.transmission}",
        impactKes = conditionImpact
      )
    )

    val confidence = when {
      exactModelPeers.size >= 5 -> "High Confidence (${exactModelPeers.size} direct peers)"
      exactModelPeers.isNotEmpty() -> "Moderate Confidence (${exactModelPeers.size} direct peers)"
      else -> "Cross-Brand Model Estimate"
    }

    val explanation = buildString {
      append("Calculated using a ridge-regularized multivariate log-price regression trained on ")
      append("${trainingPool.size} Kenyan market listings ")
      append("(features: age, mileage, engine cc, transmission, fuel, condition, and import status) ")
      append("blended with ${topComparables.size} nearest comparable vehicles. ")
      append("Advisory estimate only — not a guaranteed selling or dealership trade-in price.")
    }

    val kraEstimate = estimateKraDuty(normalizedSpec, estimatedKes)
    val tcoEstimate = estimateTco(normalizedSpec, estimatedKes, annualDep)

    return PricingEstimate(
      spec = normalizedSpec,
      estimatedPriceKes = estimatedKes,
      comparableAverageKes = comparableAvgKes,
      lowPriceKes = lowKes,
      highPriceKes = highKes,
      annualDepreciationPct = annualDep,
      cumulativeDepreciationPct = cumulativeDepPct,
      confidenceLabel = confidence,
      comparableListings = topComparables,
      factorImpacts = factorImpacts,
      methodologyExplanation = explanation,
      kraDutyEstimate = kraEstimate,
      tcoEstimate = tcoEstimate
    )
  }

  fun evaluateImportAgeRule(year: Int): ImportAgeRuleStatus {
    val age = (CURRENT_YEAR - year).coerceAtLeast(0)
    return when {
      age >= 8 -> ImportAgeRuleStatus.OVER_LIMIT
      age == 7 -> ImportAgeRuleStatus.APPROACHING_LIMIT
      else -> ImportAgeRuleStatus.ELIGIBLE
    }
  }

  fun calculateLandedCost(
    listings: List<VehicleListing>,
    input: LandedCostInput
  ): LandedCostCalculation {
    val safeYear = input.year.coerceIn(1995, CURRENT_YEAR)
    val safeEngine = input.engineCc.coerceIn(600, 6500)
    val spec = VehicleSpecification(
      make = input.make,
      model = input.model,
      year = safeYear,
      mileageKm = ((CURRENT_YEAR - safeYear).coerceAtLeast(1) * 11_000).coerceIn(15_000, 180_000),
      engineCc = safeEngine,
      transmission = "Automatic",
      fuelType = input.fuelType,
      condition = "Good",
      importStatus = "Foreign Used"
    )

    val localEstimate = estimatePrice(listings, spec)
    val warnings = mutableListOf<String>()

    val usdRateRaw = parsePositiveDoubleOrNull(input.usdToKesRateText)
    val usdRate = if (input.usdToKesRateText.isNotBlank() && (usdRateRaw == null || usdRateRaw < 50.0 || usdRateRaw > 500.0)) {
      warnings.add("Enter a valid USD/KES rate between 50 and 500 (using KES 129 fallback).")
      129.0
    } else {
      usdRateRaw ?: 129.0
    }

    val customCrspKes = parsePositiveLongOrNull(input.customCrspText)
    if (input.customCrspText.isNotBlank() && customCrspKes == null) {
      warnings.add("CRSP override must be a positive KES amount.")
    }

    val clearingParsed = parsePositiveLongOrNull(input.clearingAndNtsaText)
    val clearingFee = if (input.clearingAndNtsaText.isNotBlank() && clearingParsed == null) {
      warnings.add("Clearing & NTSA fee must be a valid KES number (using KES 85,000).")
      85_000L
    } else {
      clearingParsed ?: 85_000L
    }

    val customCifRaw = parsePositiveDoubleOrNull(input.customCifText)
    if (input.customCifText.isNotBlank() && customCifRaw == null) {
      warnings.add("Custom CIF must be a positive number.")
    }

    val customCifKes: Long? = customCifRaw?.let { raw ->
      if (input.cifCurrency == CifCurrency.USD) {
        roundToThousand(raw * usdRate)
      } else {
        roundToThousand(raw)
      }
    }

    val kra = estimateKraDuty(
      spec = spec,
      localMarketPriceKes = localEstimate.estimatedPriceKes,
      crspOverrideKes = customCrspKes,
      customCifKes = customCifKes,
      clearingAndNtsaOverrideKes = clearingFee
    )

    val effectiveCifKes = kra.estimatedCifKes
    val effectiveTaxLoadPct = if (effectiveCifKes > 0L) {
      (kra.totalKraTaxesKes.toDouble() / effectiveCifKes.toDouble()) * 100.0
    } else 0.0

    val cifUsdValue = if (input.cifCurrency == CifCurrency.USD && customCifRaw != null) {
      customCifRaw
    } else {
      effectiveCifKes.toDouble() / usdRate
    }

    return LandedCostCalculation(
      input = input.copy(year = safeYear, engineCc = safeEngine),
      kraEstimate = kra,
      effectiveCifKes = effectiveCifKes,
      isCustomCifApplied = customCifKes != null,
      cifInputUsd = cifUsdValue,
      usdToKesRate = usdRate,
      isCustomCrspApplied = customCrspKes != null,
      effectiveTaxLoadPct = effectiveTaxLoadPct,
      localMarketLowKes = localEstimate.lowPriceKes,
      localMarketHighKes = localEstimate.highPriceKes,
      comparableListings = localEstimate.comparableListings.take(4),
      validationWarning = warnings.firstOrNull()
    )
  }

  fun estimateKraDuty(
    spec: VehicleSpecification,
    localMarketPriceKes: Long,
    crspOverrideKes: Long? = null,
    customCifKes: Long? = null,
    clearingAndNtsaOverrideKes: Long? = null
  ): KraDutyEstimate {
    val age = (CURRENT_YEAR - spec.year).coerceAtLeast(0)
    val ageStatus = evaluateImportAgeRule(spec.year)
    val minEligibleYear = CURRENT_YEAR - 7

    val (ageHeadline, ageDetail) = when (ageStatus) {
      ImportAgeRuleStatus.OVER_LIMIT -> {
        "Blocked by Kenya 8-Year Age Rule ($age yrs old)" to
          "KEBS & KRA restrict direct imports to $minEligibleYear–$CURRENT_YEAR models. Purchase locally stocked units or select $minEligibleYear+ to import via Mombasa."
      }
      ImportAgeRuleStatus.APPROACHING_LIMIT -> {
        "Final Import Window ($age yrs old • Cutoff $minEligibleYear)" to
          "${spec.year} models reach Kenya's 8-year ceiling soon. Ensure Bill of Lading & Mombasa clearance complete before year-end."
      }
      ImportAgeRuleStatus.ELIGIBLE -> {
        "Compliant with 8-Year Import Limit ($age yrs old)" to
          "Eligible for direct Mombasa port import under KS 1515 (${(7 - age).coerceAtLeast(1)} yrs remaining before cutoff)."
      }
    }

    val annualDepRate = KenyanMarketSeed.modelRateFor(spec.make, spec.model)
    val foreignUsedAnchor = if (spec.importStatus.equals("Locally Used", ignoreCase = true)) {
      localMarketPriceKes / 0.89
    } else {
      localMarketPriceKes.toDouble()
    }
    val autoCrspKes = roundToTenThousand(
      (foreignUsedAnchor / (1.0 - annualDepRate).pow(age.coerceAtLeast(1).toDouble())) * 1.04
    )
    val crspKes = crspOverrideKes?.coerceAtLeast(100_000L) ?: autoCrspKes

    val kraDepPct = when {
      age <= 0 -> 5.0
      age == 1 -> 10.0
      age == 2 -> 15.0
      age == 3 -> 20.0
      age == 4 -> 30.0
      age == 5 -> 40.0
      age == 6 -> 50.0
      age == 7 -> 60.0
      else -> 65.0
    }

    val exciseRate = when {
      spec.engineCc > 3000 -> 0.35
      spec.engineCc > 2500 || (spec.fuelType.equals("Diesel", ignoreCase = true) && spec.engineCc > 2400) -> 0.30
      spec.engineCc > 1500 -> 0.25
      else -> 0.20
    }

    // Reverse out embedded showroom VAT, Excise, Import Duty, and dealer margin from Kenyan CRSP
    val crspTaxDivisor = 1.25 * (1.0 + exciseRate) * 1.16 * 1.12
    val depreciatedCrsp = crspKes * (1.0 - kraDepPct / 100.0)
    val customsValueKes = roundToTenThousand((depreciatedCrsp / crspTaxDivisor).coerceAtLeast(220_000.0))

    val importDutyKes = roundToThousand(customsValueKes * 0.25)
    val exciseBase = customsValueKes + importDutyKes
    val exciseDutyKes = roundToThousand(exciseBase * exciseRate)
    val vatBase = exciseBase + exciseDutyKes
    val vatKes = roundToThousand(vatBase * 0.16)
    val idfKes = roundToThousand(customsValueKes * 0.025)
    val rdlKes = roundToThousand(customsValueKes * 0.015)

    val totalTaxesKes = importDutyKes + exciseDutyKes + vatKes + idfKes + rdlKes
    val estimatedCifKes = customCifKes?.coerceAtLeast(0L) ?: roundToTenThousand(customsValueKes * 1.14)
    val clearingAndNtsaKes = clearingAndNtsaOverrideKes?.coerceAtLeast(0L) ?: 85_000L
    val landedCostKes = estimatedCifKes + totalTaxesKes + clearingAndNtsaKes
    val deltaKes = localMarketPriceKes - landedCostKes

    val comparisonSummary = when {
      ageStatus == ImportAgeRuleStatus.OVER_LIMIT -> {
        "Mombasa import is prohibited for ${spec.year} builds (>8 yrs). Local yard average is ${formatKes(localMarketPriceKes)}."
      }
      deltaKes >= 80_000L -> {
        "Importing via Mombasa saves ~${formatKes(deltaKes)} vs local asking prices (includes KRA duties & clearing)."
      }
      deltaKes <= -80_000L -> {
        "Locally stocked unit is ~${formatKes(abs(deltaKes))} cheaper than Mombasa landed import cost."
      }
      else -> {
        "Mombasa landed cost is within ${formatKes(abs(deltaKes))} of local Kenyan stock—local purchase avoids port wait."
      }
    }

    return KraDutyEstimate(
      year = spec.year,
      vehicleAgeYears = age,
      ageRuleStatus = ageStatus,
      ageRuleHeadline = ageHeadline,
      ageRuleDetail = ageDetail,
      crspBenchmarkKes = crspKes,
      kraDepreciationRatePct = kraDepPct,
      customsValueKes = customsValueKes,
      estimatedCifKes = estimatedCifKes,
      importDutyKes = importDutyKes,
      exciseDutyRatePct = exciseRate * 100.0,
      exciseDutyKes = exciseDutyKes,
      vatKes = vatKes,
      idfKes = idfKes,
      rdlKes = rdlKes,
      totalKraTaxesKes = totalTaxesKes,
      clearingAndNtsaKes = clearingAndNtsaKes,
      mombasaLandedCostKes = landedCostKes,
      localMarketPriceKes = localMarketPriceKes,
      importVsLocalDeltaKes = deltaKes,
      comparisonSummary = comparisonSummary
    )
  }

  fun estimateTco(
    spec: VehicleSpecification,
    currentValueKes: Long,
    annualDepreciationPct: Double = KenyanMarketSeed.modelRateFor(spec.make, spec.model) * 100.0,
    annualDistanceKm: Int = 15_000
  ): TcoEstimate {
    val fuelComparison = listOf("Petrol", "Diesel", "Hybrid").map { trim ->
      val kmPerLiter = fuelEfficiencyKmPerLiter(spec.engineCc, trim)
      val pumpPrice = pumpPriceKesPerLiter(trim)
      val annualFuel = roundToThousand((annualDistanceKm.toDouble() / kmPerLiter) * pumpPrice)
      FuelTrimEstimate(
        fuelType = trim,
        consumptionKmPerLiter = kmPerLiter,
        pumpPriceKesPerLiter = pumpPrice,
        annualFuelCostKes = annualFuel
      )
    }

    val activeFuelEst = fuelComparison.firstOrNull {
      it.fuelType.equals(spec.fuelType, ignoreCase = true)
    } ?: fuelComparison.first()
    val annualFuelKes = activeFuelEst.annualFuelCostKes

    val insuranceRatePct = when {
      spec.make.equals("Mercedes-Benz", ignoreCase = true) ||
        spec.make.equals("BMW", ignoreCase = true) ||
        currentValueKes >= 4_500_000L -> 4.5
      currentValueKes >= 1_800_000L || spec.engineCc >= 1800 -> 4.0
      else -> 3.5
    }

    val annualDepRate = (annualDepreciationPct / 100.0).coerceIn(0.03, 0.25)
    val annualInsuranceKes = roundToThousand((currentValueKes * (insuranceRatePct / 100.0)).coerceAtLeast(45_000.0))

    val (tierLabel, baseMaintKes) = maintenanceTier(spec.make, spec.model, spec.engineCc)
    val age = (CURRENT_YEAR - spec.year).coerceAtLeast(1)
    val ageMaintMultiplier = if (age >= 8) 1.15 else 1.0
    val annualMaintKes = roundToThousand(baseMaintKes * ageMaintMultiplier)

    val annualRunningCostKes = annualFuelKes + annualInsuranceKes + annualMaintKes

    val fiveYearFuelKes = annualFuelKes * 5L
    var fiveYearInsuranceSum = 0L
    for (yrOffset in 0 until 5) {
      val yrValue = currentValueKes * (1.0 - annualDepRate).pow(yrOffset.toDouble())
      fiveYearInsuranceSum += roundToThousand((yrValue * (insuranceRatePct / 100.0)).coerceAtLeast(40_000.0))
    }
    val fiveYearMaintenanceKes = roundToThousand(annualMaintKes * 5.25)
    val fiveYearProjectedResale = roundToTenThousand(currentValueKes * (1.0 - annualDepRate).pow(5.0))
    val fiveYearDepLoss = (currentValueKes - fiveYearProjectedResale).coerceAtLeast(0L)

    val totalFiveYearTco = fiveYearDepLoss + fiveYearFuelKes + fiveYearInsuranceSum + fiveYearMaintenanceKes
    val effectiveMonthlyTco = roundToThousand(totalFiveYearTco / 60.0)

    return TcoEstimate(
      annualDistanceKm = annualDistanceKm,
      annualFuelCostKes = annualFuelKes,
      annualInsuranceKes = annualInsuranceKes,
      insuranceRatePct = insuranceRatePct,
      maintenanceTierLabel = tierLabel,
      annualMaintenanceKes = annualMaintKes,
      annualRunningCostKes = annualRunningCostKes,
      fiveYearFuelKes = fiveYearFuelKes,
      fiveYearInsuranceKes = fiveYearInsuranceSum,
      fiveYearMaintenanceKes = fiveYearMaintenanceKes,
      fiveYearDepreciationLossKes = fiveYearDepLoss,
      fiveYearProjectedResaleKes = fiveYearProjectedResale,
      totalFiveYearTcoKes = totalFiveYearTco,
      effectiveMonthlyTcoKes = effectiveMonthlyTco,
      fuelTrimComparison = fuelComparison
    )
  }

  private fun fuelEfficiencyKmPerLiter(engineCc: Int, fuelType: String): Double {
    val basePetrol = when {
      engineCc <= 1350 -> 15.5
      engineCc <= 1600 -> 13.5
      engineCc <= 2000 -> 11.2
      engineCc <= 2500 -> 9.4
      else -> 7.6
    }
    return when (fuelType.lowercase()) {
      "diesel" -> basePetrol * 1.22
      "hybrid" -> basePetrol * 1.42
      else -> basePetrol
    }
  }

  private fun pumpPriceKesPerLiter(fuelType: String): Int = when (fuelType.lowercase()) {
    "diesel" -> 168
    else -> 180
  }

  private fun maintenanceTier(make: String, model: String, engineCc: Int): Pair<String, Long> {
    val economyModels = setOf("Vitz", "Axio", "Fielder", "Demio", "Fit", "Swift", "Note")
    val execMakes = setOf("Mercedes-Benz", "BMW")
    val execModels = setOf("Land Cruiser Prado", "GLE 350", "X3", "320i", "C200", "Tiguan", "Outback")
    return when {
      make in execMakes || model in execModels || engineCc >= 2600 -> {
        val base = if (make in execMakes) 195_000L else 155_000L
        "Executive / SUV Tier" to base
      }
      model in economyModels || engineCc <= 1500 -> {
        "Economy Tier" to 52_000L
      }
      else -> {
        "Mid-Size Tier" to 92_000L
      }
    }
  }

  fun analyzeVehicleDepreciation(
    listings: List<VehicleListing>,
    make: String,
    model: String,
    referenceYear: Int
  ): VehicleDepreciationAnalysis {
    val modelListings = listings.filter {
      it.make.equals(make, ignoreCase = true) && it.model.equals(model, ignoreCase = true)
    }
    val refListings = modelListings.filter { it.year == referenceYear }
    val annualRate = KenyanMarketSeed.modelRateFor(make, model)
    val ageAtCurrent = (CURRENT_YEAR - referenceYear).coerceAtLeast(1)

    val currentKes = when {
      refListings.isNotEmpty() -> roundToTenThousand(refListings.map { it.askingPriceKes.toDouble() }.average())
      modelListings.isNotEmpty() -> {
        val nearest = modelListings.minByOrNull { abs(it.year - referenceYear) }!!
        val yearGap = referenceYear - nearest.year
        roundToTenThousand(nearest.askingPriceKes * (1.0 - annualRate).pow(-yearGap.toDouble()))
      }
      else -> 1_800_000L
    }

    val estimatedOriginalKes = roundToTenThousand(currentKes / (1.0 - annualRate).pow(ageAtCurrent.toDouble()))
    val retainedPct = ((currentKes.toDouble() / estimatedOriginalKes.toDouble()) * 100.0).coerceIn(10.0, 98.0)

    val curvePoints = mutableListOf<DepreciationCurvePoint>()
    // Observed cross-sectional / historical points (years where data exists or anchored reference)
    val observedByYear = modelListings.groupBy { it.year }
    val histAnchorYears = listOf(referenceYear, (referenceYear + 2).coerceAtMost(CURRENT_YEAR), CURRENT_YEAR)
      .distinct()
      .sorted()

    histAnchorYears.forEachIndexed { idx, yr ->
      val obs = observedByYear[yr]
      val value = if (!obs.isNullOrEmpty()) {
        roundToTenThousand(obs.map { it.askingPriceKes.toDouble() }.average())
      } else {
        val gap = yr - referenceYear
        roundToTenThousand(currentKes * (1.0 - annualRate).pow(-gap.toDouble()))
      }
      curvePoints.add(
        DepreciationCurvePoint(
          yearIndex = idx,
          label = yr.toString(),
          valueKes = value,
          isProjected = false
        )
      )
    }

    // 5-year modeled forward projection from current year
    for (offset in 1..5) {
      val projYear = CURRENT_YEAR + offset
      val projValue = roundToTenThousand(currentKes * (1.0 - annualRate).pow(offset.toDouble()))
      curvePoints.add(
        DepreciationCurvePoint(
          yearIndex = curvePoints.size,
          label = projYear.toString(),
          valueKes = projValue,
          isProjected = true
        )
      )
    }

    val fiveYearKes = curvePoints.lastOrNull()?.valueKes ?: currentKes

    return VehicleDepreciationAnalysis(
      key = "${make}_${model}_$referenceYear",
      title = "$make $model $referenceYear",
      make = make,
      model = model,
      year = referenceYear,
      estimatedOriginalValueKes = estimatedOriginalKes,
      currentMarketPriceKes = currentKes,
      valueRetainedPct = retainedPct,
      averageAnnualDepreciationPct = annualRate * 100.0,
      fiveYearProjectedKes = fiveYearKes,
      hasSufficientObservationData = modelListings.size >= 3,
      observedSampleCount = modelListings.size,
      curvePoints = curvePoints
    )
  }

  fun topTenValueRetentionVehicles(listings: List<VehicleListing>): List<RetentionLeaderItem> {
    if (listings.isEmpty()) return emptyList()
    return listings
      .groupBy { it.make to it.model }
      .entries
      .map { (pair, group) ->
        val (make, model) = pair
        val refYear = group.maxOfOrNull { it.year }?.coerceAtMost(2019) ?: 2018
        val analysis = analyzeVehicleDepreciation(listings, make, model, refYear)
        RetentionLeaderItem(
          rank = 0,
          make = make,
          model = model,
          referenceYear = refYear,
          currentAvgKes = analysis.currentMarketPriceKes,
          valueRetainedPct = estimateFiveYearRetentionPct(make, model),
          annualDepreciationPct = analysis.averageAnnualDepreciationPct,
          sampleCount = group.size
        )
      }
      .sortedByDescending { it.valueRetainedPct }
      .take(10)
      .mapIndexed { index, item -> item.copy(rank = index + 1) }
  }

  fun compareVehicles(selected: List<VehicleListing>): ComparisonVerdict {
    if (selected.isEmpty()) {
      return ComparisonVerdict(
        metrics = emptyList(),
        strongestResaleVehicle = "None selected",
        verdictExplanation = "Select up to 4 vehicles to evaluate resale retention, depreciation, and demand."
      )
    }

    val metrics = selected.take(4).map { vehicle ->
      val annualRate = KenyanMarketSeed.modelRateFor(vehicle.make, vehicle.model)
      val threeYear = roundToTenThousand(vehicle.askingPriceKes * (1.0 - annualRate).pow(3.0))
      val fiveYear = roundToTenThousand(vehicle.askingPriceKes * (1.0 - annualRate).pow(5.0))
      val retention = estimateFiveYearRetentionPct(vehicle.make, vehicle.model)
      ComparisonMetric(
        vehicle = vehicle,
        annualDepreciationPct = annualRate * 100.0,
        threeYearResaleKes = threeYear,
        fiveYearResaleKes = fiveYear,
        valueRetentionPct = retention
      )
    }

    val winner = metrics.maxByOrNull {
      it.valueRetentionPct * 0.65 + it.vehicle.demandScore * 0.35 - (it.vehicle.mileageKm / 25000.0)
    }!!

    val runnerUp = metrics.filter { it.vehicle.id != winner.vehicle.id }
      .maxByOrNull { it.valueRetentionPct }

    val verdictText = buildString {
      append("${winner.vehicle.fullName} delivers the strongest resale outlook ")
      append("with ${String.format("%.1f", winner.valueRetentionPct)}% projected 5-year retention ")
      append("(${String.format("%.1f", winner.annualDepreciationPct)}%/yr depreciation) ")
      append("and a Kenyan market demand score of ${winner.vehicle.demandScore}/100.")
      if (runnerUp != null) {
        val retentionDelta = winner.valueRetentionPct - runnerUp.valueRetentionPct
        append(" It outperforms ${runnerUp.vehicle.fullName} by ${String.format("%.1f", retentionDelta)} percentage points in value retention.")
      }
    }

    return ComparisonVerdict(
      metrics = metrics,
      strongestResaleVehicle = winner.vehicle.fullName,
      verdictExplanation = verdictText
    )
  }

  fun generateInsights(listings: List<VehicleListing>): Pair<List<MarketInsight>, List<PriceAnomalyListing>> {
    if (listings.size < 3) {
      return emptyList<MarketInsight>() to emptyList()
    }

    val insights = mutableListOf<MarketInsight>()

    // 1. Brand retention & demand finding
    val brandRetention = listings.groupBy { it.make }
      .mapValues { (make, items) ->
        Triple(
          items.map { estimateFiveYearRetentionPct(make, it.model) }.average(),
          items.map { it.demandScore.toDouble() }.average(),
          items.size
        )
      }
    val topBrand = brandRetention.maxByOrNull { it.value.first }
    val lowestBrand = brandRetention.minByOrNull { it.value.first }
    if (topBrand != null && lowestBrand != null && topBrand.key != lowestBrand.key) {
      val gap = topBrand.value.first - lowestBrand.value.first
      insights.add(
        MarketInsight(
          id = "ins_retention",
          category = InsightCategory.RETENTION,
          title = "${topBrand.key} Leads Kenyan Resale Retention",
          finding = "Across ${topBrand.value.third} analyzed ${topBrand.key} listings, average 5-year value retention reaches ${String.format("%.1f", topBrand.value.first)}% with a ${topBrand.value.second.toInt()}/100 demand index—${String.format("%.1f", gap)}% higher than ${lowestBrand.key}.",
          metricHighlight = "${String.format("%.1f", topBrand.value.first)}% retained",
          relatedMake = topBrand.key,
          isPositiveSignal = true
        )
      )
    }

    // 2. Mileage vs Price correlation computed dynamically
    val corr = pearsonCorrelation(
      listings.map { it.mileageKm.toDouble() },
      listings.map { it.askingPriceKes.toDouble() }
    )
    val lowMileageAvg = listings.filter { it.mileageKm < 80000 }.map { it.askingPriceKes.toDouble() }.takeIf { it.isNotEmpty() }?.average()
    val highMileageAvg = listings.filter { it.mileageKm >= 110000 }.map { it.askingPriceKes.toDouble() }.takeIf { it.isNotEmpty() }?.average()
    val dropPct = if (lowMileageAvg != null && highMileageAvg != null && lowMileageAvg > 0) {
      ((lowMileageAvg - highMileageAvg) / lowMileageAvg) * 100.0
    } else 18.0
    insights.add(
      MarketInsight(
        id = "ins_mileage",
        category = InsightCategory.MILEAGE,
        title = "Higher Mileage Pressures Asking Prices",
        finding = "Active listings show a ${String.format("%.2f", corr)} correlation between odometer reading and KES price; vehicles above 110,000 km trade ${String.format("%.1f", abs(dropPct))}% lower on average than sub-80,000 km units.",
        metricHighlight = "r = ${String.format("%.2f", corr)}",
        isPositiveSignal = false
      )
    )

    // 3. Fastest vs slowest depreciating model
    val modelRates = listings.groupBy { "${it.make} ${it.model}" }
      .mapValues { (_, items) ->
        KenyanMarketSeed.modelRateFor(items.first().make, items.first().model) * 100.0
      }
    val fastest = modelRates.maxByOrNull { it.value }
    val slowest = modelRates.minByOrNull { it.value }
    if (fastest != null && slowest != null) {
      insights.add(
        MarketInsight(
          id = "ins_depreciation",
          category = InsightCategory.DEPRECIATION,
          title = "Sharp Depreciation Spread Between Segments",
          finding = "${fastest.key} depreciates fastest at ~${String.format("%.1f", fastest.value)}%/yr, whereas ${slowest.key} loses only ~${String.format("%.1f", slowest.value)}%/yr due to high Kenyan parts liquidity.",
          metricHighlight = "${String.format("%.1f", fastest.value)}% vs ${String.format("%.1f", slowest.value)}%",
          isPositiveSignal = true
        )
      )
    }

    // 4. Value-for-money manufacturing year
    val yearStats = listings.groupBy { it.year }
      .filter { it.value.size >= 2 }
      .mapValues { (year, items) ->
        val avgPrice = items.map { it.askingPriceKes.toDouble() }.average()
        val avgDemand = items.map { it.demandScore.toDouble() }.average()
        val score = (avgDemand / (avgPrice / 1_000_000.0)) * (1.0 - (CURRENT_YEAR - year) * 0.03)
        Triple(avgPrice.roundToLong(), avgDemand.toInt(), score)
      }
    val bestValueYear = yearStats.maxByOrNull { it.value.third }
    if (bestValueYear != null) {
      insights.add(
        MarketInsight(
          id = "ins_sweet_year",
          category = InsightCategory.VALUE_YEAR,
          title = "${bestValueYear.key} Offers Optimal Value-to-Demand Balance",
          finding = "${bestValueYear.key} vehicles average KES ${formatCompactMillions(bestValueYear.value.first)} while sustaining a ${bestValueYear.value.second}/100 demand score, avoiding steep early-year depreciation.",
          metricHighlight = "${bestValueYear.key} Sweet Spot",
          isPositiveSignal = true
        )
      )
    }

    // 5. Deal anomalies (above / below comparable model+year peers or cross-year model expectation)
    val peerGroups = listings.groupBy { "${it.make}|${it.model}|${it.year}" }
    val modelGroups = listings.groupBy { "${it.make}|${it.model}" }
    val anomalies = mutableListOf<PriceAnomalyListing>()
    for (item in listings) {
      val key = "${item.make}|${item.model}|${item.year}"
      val sameYearPeers = (peerGroups[key] ?: emptyList()).filter { it.id != item.id }
      val expectedKes: Long = if (sameYearPeers.isNotEmpty()) {
        roundToTenThousand(sameYearPeers.map { it.askingPriceKes.toDouble() }.average())
      } else {
        val otherYearPeers = (modelGroups["${item.make}|${item.model}"] ?: emptyList())
          .filter { it.year != item.year }
        if (otherYearPeers.isNotEmpty()) {
          val rate = KenyanMarketSeed.modelRateFor(item.make, item.model)
          val projectedValues = otherYearPeers.map { other ->
            val yearDiff = other.year - item.year
            other.askingPriceKes * (1.0 - rate).pow(yearDiff.toDouble())
          }
          roundToTenThousand(projectedValues.average())
        } else {
          0L
        }
      }

      if (expectedKes > 0L) {
        val deviationPct = ((item.askingPriceKes - expectedKes.toDouble()) / expectedKes.toDouble()) * 100.0
        if (abs(deviationPct) >= 12.0) {
          anomalies.add(
            PriceAnomalyListing(
              listing = item,
              peerAverageKes = expectedKes,
              deviationPct = deviationPct,
              label = if (deviationPct < 0) "Below Market (Good Deal)" else "Above Market (Overpriced)"
            )
          )
        }
      }
    }

    val sortedAnomalies = anomalies.sortedByDescending { abs(it.deviationPct) }.take(8)
    if (sortedAnomalies.isNotEmpty()) {
      val topDeal = sortedAnomalies.firstOrNull { it.deviationPct < 0 } ?: sortedAnomalies.first()
      insights.add(
        MarketInsight(
          id = "ins_anomaly",
          category = InsightCategory.PRICE_ANOMALY,
          title = "${sortedAnomalies.size} Pricing Outliers Detected",
          finding = "${topDeal.listing.fullName} in ${topDeal.listing.location} is listed at KES ${formatCompactMillions(topDeal.listing.askingPriceKes)} (${String.format("%+.1f", topDeal.deviationPct)}% vs comparable average).",
          metricHighlight = "${String.format("%+.1f", topDeal.deviationPct)}% spread",
          relatedMake = topDeal.listing.make,
          isPositiveSignal = topDeal.deviationPct < 0
        )
      )
    }

    // 6. Kenya 8-Year Import Age Rule inventory insight
    val approachingCount = listings.count { evaluateImportAgeRule(it.year) == ImportAgeRuleStatus.APPROACHING_LIMIT }
    val blockedCount = listings.count { evaluateImportAgeRule(it.year) == ImportAgeRuleStatus.OVER_LIMIT }
    if (approachingCount > 0 || blockedCount > 0) {
      val minImportYear = CURRENT_YEAR - 7
      insights.add(
        MarketInsight(
          id = "ins_import_age_rule",
          category = InsightCategory.IMPORT_RULE,
          title = "8-Year Import Rule Shapes Local Stock Supply",
          finding = "$blockedCount active listings are over Kenya's 8-year import limit (local yards only) and $approachingCount ($minImportYear builds) sit in their final Mombasa import window—supporting resale values on clean local units.",
          metricHighlight = "$approachingCount cutoff / $blockedCount local-only",
          isPositiveSignal = true
        )
      )
    }

    // 7. Odometer & Auction Sanity Audit
    val suspectLowCount = listings.count {
      evaluateOdometerSanity(it.year, it.mileageKm, it.importStatus).status == OdometerSanityStatus.SUSPECT_LOW
    }
    val checkDocsCount = listings.count {
      evaluateOdometerSanity(it.year, it.mileageKm, it.importStatus).status == OdometerSanityStatus.CHECK_DOCS
    }
    if (suspectLowCount > 0 || checkDocsCount > 0) {
      insights.add(
        MarketInsight(
          id = "ins_odometer_sanity",
          category = InsightCategory.ODOMETER_SANITY,
          title = "Odometer Rollback Risk Flags Detected",
          finding = "$suspectLowCount listings show abnormally low mileage (<40% of typical Japanese/Kenyan annual curve) and $checkDocsCount sit below standard thresholds. Always verify JEVIC / QISJ pre-export certificates and original Japanese auction sheets before paying deposits.",
          metricHighlight = "$suspectLowCount suspect / $checkDocsCount verify",
          isPositiveSignal = false
        )
      )
    }

    return insights to sortedAnomalies
  }

  fun evaluateOdometerSanity(
    year: Int,
    mileageKm: Int,
    importStatus: String
  ): OdometerSanityReport {
    val age = (CURRENT_YEAR - year).coerceAtLeast(1)
    val baselinePerYear = if (importStatus.equals("Locally Used", ignoreCase = true)) 13_500 else 10_500
    val expectedMedianKm = (age * baselinePerYear).coerceAtLeast(10_000)
    val expectedMinKm = (expectedMedianKm * 0.58).toInt().coerceAtLeast(6_000)
    val ratio = mileageKm.toDouble() / expectedMedianKm.toDouble()

    return when {
      age >= 4 && ratio < 0.40 -> OdometerSanityReport(
        status = OdometerSanityStatus.SUSPECT_LOW,
        expectedMinKm = expectedMinKm,
        expectedMedianKm = expectedMedianKm,
        ratioToExpected = ratio,
        headline = "Abnormally low mileage (${formatNumber(mileageKm)} km for $age-yr unit)",
        verificationAdvice = "Expected ~${formatNumber(expectedMinKm)}–${formatNumber(expectedMedianKm)} km for a $year $importStatus unit. Request JEVIC / QISJ odometer certificate, Japanese auction sheet export verification, and NTSA TIMS inspection."
      )
      age >= 4 && ratio < 0.58 -> OdometerSanityReport(
        status = OdometerSanityStatus.CHECK_DOCS,
        expectedMinKm = expectedMinKm,
        expectedMedianKm = expectedMedianKm,
        ratioToExpected = ratio,
        headline = "Below typical mileage curve (${formatNumber(mileageKm)} km)",
        verificationAdvice = "Slightly below Kenyan/Japanese norm (~${formatNumber(expectedMinKm)}+ km). Confirm chassis number against JEVIC/QISJ and service logbook."
      )
      else -> OdometerSanityReport(
        status = OdometerSanityStatus.PLAUSIBLE,
        expectedMinKm = expectedMinKm,
        expectedMedianKm = expectedMedianKm,
        ratioToExpected = ratio,
        headline = "Consistent with normal annual usage (~${formatNumber(mileageKm / age)} km/yr)",
        verificationAdvice = "Mileage aligns with expected ${importStatus.lowercase()} wear curve; still perform mechanical & NTSA logbook checks."
      )
    }
  }

  fun doesListingMatchWatchlist(listing: VehicleListing, watchlist: PriceWatchlist): Boolean {
    if (!watchlist.isActive) return false
    val makeMatch = watchlist.make.equals("All", ignoreCase = true) ||
      listing.make.equals(watchlist.make, ignoreCase = true)
    val modelMatch = watchlist.model.equals("All", ignoreCase = true) ||
      listing.model.equals(watchlist.model, ignoreCase = true) ||
      listing.model.contains(watchlist.model, ignoreCase = true)
    val yearMatch = listing.year in watchlist.minYear..watchlist.maxYear
    val priceMatch = listing.askingPriceKes <= watchlist.maxBudgetKes
    val importMatch = watchlist.preferredImportStatus.equals("All", ignoreCase = true) ||
      listing.importStatus.equals(watchlist.preferredImportStatus, ignoreCase = true)
    return makeMatch && modelMatch && yearMatch && priceMatch && importMatch
  }

  fun evaluateWatchlists(
    listings: List<VehicleListing>,
    watchlists: List<PriceWatchlist>
  ): List<WatchlistMatch> {
    return watchlists.map { wl ->
      val matches = listings
        .filter { doesListingMatchWatchlist(it, wl) }
        .sortedBy { it.askingPriceKes }
      WatchlistMatch(
        watchlist = wl,
        matchingListings = matches
      )
    }
  }

  fun matchingListingIds(
    listings: List<VehicleListing>,
    watchlists: List<PriceWatchlist>
  ): Set<String> {
    val active = watchlists.filter { it.isActive }
    if (active.isEmpty()) return emptySet()
    return listings.filter { listing ->
      active.any { wl -> doesListingMatchWatchlist(listing, wl) }
    }.map { it.id }.toSet()
  }

  private fun estimateFiveYearRetentionPct(make: String, model: String): Double {
    val annualRate = KenyanMarketSeed.modelRateFor(make, model)
    return ((1.0 - annualRate).pow(5.0) * 100.0).coerceIn(25.0, 90.0)
  }

  private fun encodeFeatures(
    year: Int,
    mileageKm: Int,
    engineCc: Int,
    importStatus: String,
    transmission: String,
    fuelType: String,
    condition: String
  ): DoubleArray {
    val age = (CURRENT_YEAR - year).coerceAtLeast(0).toDouble()
    val mileage100k = mileageKm / 100_000.0
    val engineLiters = engineCc / 1000.0
    val isLocal = if (importStatus.equals("Locally Used", ignoreCase = true)) 1.0 else 0.0
    val isManual = if (transmission.equals("Manual", ignoreCase = true)) 1.0 else 0.0
    val isHybridOrDiesel = if (fuelType.equals("Hybrid", ignoreCase = true) || fuelType.equals("Diesel", ignoreCase = true)) 1.0 else 0.0
    val condScore = when (condition.lowercase()) {
      "excellent" -> 1.0
      "fair" -> -1.0
      else -> 0.0
    }
    return doubleArrayOf(1.0, age, mileage100k, engineLiters, isLocal, isManual, isHybridOrDiesel, condScore)
  }

  private fun fitRidgeLogPriceModel(listings: List<VehicleListing>): DoubleArray {
    val cols = 8
    val xtx = Array(cols) { DoubleArray(cols) }
    val xty = DoubleArray(cols)

    for (item in listings) {
      val x = encodeFeatures(
        year = item.year,
        mileageKm = item.mileageKm,
        engineCc = item.engineCc,
        importStatus = item.importStatus,
        transmission = item.transmission,
        fuelType = item.fuelType,
        condition = item.condition
      )
      val y = ln(item.askingPriceKes.coerceAtLeast(100_000L).toDouble())
      for (i in 0 until cols) {
        xty[i] += x[i] * y
        for (j in 0 until cols) {
          xtx[i][j] += x[i] * x[j]
        }
      }
    }

    // L2 ridge penalty on non-intercept terms for numerical stability on small subsets
    for (i in 1 until cols) {
      xtx[i][i] += 0.25
    }
    xtx[0][0] += 1e-4

    return solveLinearSystem(xtx, xty)
  }

  private fun solveLinearSystem(a: Array<DoubleArray>, b: DoubleArray): DoubleArray {
    val n = b.size
    val mat = Array(n) { r -> DoubleArray(n + 1) { c -> if (c < n) a[r][c] else b[r] } }

    for (p in 0 until n) {
      var maxRow = p
      for (r in p + 1 until n) {
        if (abs(mat[r][p]) > abs(mat[maxRow][p])) maxRow = r
      }
      val temp = mat[p]
      mat[p] = mat[maxRow]
      mat[maxRow] = temp

      val pivot = mat[p][p]
      if (abs(pivot) < 1e-9) continue
      for (c in p..n) mat[p][c] /= pivot
      for (r in 0 until n) {
        if (r != p) {
          val factor = mat[r][p]
          for (c in p..n) mat[r][c] -= factor * mat[p][c]
        }
      }
    }
    return DoubleArray(n) { mat[it][n] }
  }

  private fun dot(w: DoubleArray, x: DoubleArray): Double {
    var sum = 0.0
    for (i in w.indices) sum += w[i] * x[i]
    return sum
  }

  private fun pearsonCorrelation(xs: List<Double>, ys: List<Double>): Double {
    if (xs.size < 2 || xs.size != ys.size) return 0.0
    val meanX = xs.average()
    val meanY = ys.average()
    var num = 0.0
    var denX = 0.0
    var denY = 0.0
    for (i in xs.indices) {
      val dx = xs[i] - meanX
      val dy = ys[i] - meanY
      num += dx * dy
      denX += dx * dx
      denY += dy * dy
    }
    val den = sqrt(denX * denY)
    return if (den < 1e-9) 0.0 else (num / den).coerceIn(-1.0, 1.0)
  }

  private fun conditionMultiplier(condition: String): Double = when (condition.lowercase()) {
    "excellent" -> 1.06
    "fair" -> 0.90
    else -> 1.0
  }

  private fun importMultiplier(importStatus: String): Double =
    if (importStatus.equals("Locally Used", ignoreCase = true)) 0.89 else 1.0

  private fun transmissionMultiplier(transmission: String): Double =
    if (transmission.equals("Manual", ignoreCase = true)) 0.94 else 1.0

  private fun parsePositiveDoubleOrNull(raw: String): Double? {
    val cleaned = raw.replace(",", "").trim()
    if (cleaned.isEmpty()) return null
    val parsed = cleaned.toDoubleOrNull() ?: return null
    return if (parsed > 0.0 && parsed.isFinite()) parsed else null
  }

  private fun parsePositiveLongOrNull(raw: String): Long? {
    val cleaned = raw.replace(",", "").trim()
    if (cleaned.isEmpty()) return null
    val parsed = cleaned.toDoubleOrNull() ?: return null
    return if (parsed > 0.0 && parsed.isFinite()) parsed.roundToLong() else null
  }

  private fun roundToTenThousand(value: Double): Long =
    ((value / 10_000.0).roundToLong() * 10_000L).coerceAtLeast(100_000L)

  private fun roundToThousand(value: Double): Long =
    ((value / 1_000.0).roundToLong() * 1_000L).coerceAtLeast(0L)

  fun formatKes(amount: Long): String {
    val formatted = "%,d".format(amount)
    return "KES $formatted"
  }

  fun formatCompactMillions(amount: Long): String {
    return if (amount >= 1_000_000L) {
      "${String.format("%.2f", amount / 1_000_000.0)}M"
    } else {
      "${amount / 1_000L}K"
    }
  }

  private fun formatNumber(value: Int): String = "%,d".format(value)
}
