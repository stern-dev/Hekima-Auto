package com.example.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

enum class ThemeMode(val key: String, val label: String) {
  SYSTEM("system", "Follow System"),
  DARK("dark", "Dark Navy"),
  LIGHT("light", "Light Analytical");

  companion object {
    fun fromKey(key: String?): ThemeMode =
      entries.firstOrNull { it.key == key } ?: SYSTEM
  }
}

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

enum class VehicleSegment(val label: String) {
  ALL("All Segments"),
  CARS("Cars & Sedans"),
  SUVS("SUVs & Crossovers"),
  VANS_MPVS("MPVs & Vans"),
  PICKUPS("Pickups & Utility"),
  LUXURY("Executive & Luxury")
}

data class VehicleMake(
  val id: String,
  val name: String,
  val country: String,
  val popularityTier: String
)

data class VehicleModel(
  val id: String,
  val make: String,
  val name: String,
  val bodyType: String,
  val typicalEngineCc: Int,
  val annualDepreciationRate: Double,
  val segment: VehicleSegment = VehicleSegment.CARS
)

data class VehicleSpecification(
  val make: String = "Toyota",
  val model: String = "Axio",
  val year: Int = 2018,
  val mileageKm: Int = 75000,
  val engineCc: Int = 1500,
  val transmission: String = "Automatic",
  val fuelType: String = "Petrol",
  val condition: String = "Good",
  val importStatus: String = "Foreign Used"
)

enum class ListingProvenance(val badgeLabel: String, val filterLabel: String) {
  LIVE_YARD("Live Yard", "Live Yard"),
  DEALER_FEED("Dealer Feed", "Dealer Feeds"),
  CSV_IMPORT("CSV / Excel", "CSV / XLSX"),
  OFFLINE_REFERENCE("Offline Reference", "Offline Reference")
}

data class VehicleListing(
  val id: String,
  val make: String,
  val model: String,
  val year: Int,
  val askingPriceKes: Long,
  val mileageKm: Int,
  val engineCc: Int,
  val transmission: String,
  val fuelType: String,
  val condition: String,
  val importStatus: String,
  val location: String,
  val demandScore: Int,
  val sourceId: String,
  val sourceName: String,
  val isImported: Boolean = false,
  val segment: String = "",
  val provenance: ListingProvenance = ListingProvenance.OFFLINE_REFERENCE,
  val updatedAtEpochMs: Long = 0L,
  val sellerUserId: String? = null
) {
  val fullName: String get() = "$make $model $year"
}

data class PriceObservation(
  val modelKey: String,
  val year: Int,
  val averagePriceKes: Long,
  val sampleSize: Int,
  val observedMonth: String
)

data class FilterState(
  val selectedMake: String = "All",
  val selectedSegment: VehicleSegment = VehicleSegment.ALL,
  val onlyWatchlistMatches: Boolean = false,
  val onlyOdometerFlagged: Boolean = false,
  val minYear: Int = 2012,
  val maxYear: Int = 2025,
  val maxMileageKm: Int = 250000,
  val maxPriceKes: Long = 25000000L,
  val importStatus: String = "All",
  val sourceFilter: String = "All",
  val searchQuery: String = "",
  val provenanceFilter: ListingProvenance? = null
)

data class BrandPriceStat(
  val brand: String,
  val averagePriceKes: Long,
  val medianPriceKes: Long,
  val listingCount: Int,
  val avgRetentionPct: Double
)

data class ScatterPoint(
  val x: Float,
  val y: Float,
  val label: String,
  val make: String,
  val priceKes: Long
)

data class HistogramBucket(
  val label: String,
  val minKes: Long,
  val maxKes: Long,
  val count: Int
)

data class BrandShareSlice(
  val brand: String,
  val count: Int,
  val percentage: Double
)

data class DepreciationCurvePoint(
  val yearIndex: Int,
  val label: String,
  val valueKes: Long,
  val isProjected: Boolean
)

data class ModelDepreciationSeries(
  val title: String,
  val points: List<DepreciationCurvePoint>
)

data class DashboardSummary(
  val totalVehicles: Int,
  val averagePriceKes: Long,
  val medianPriceKes: Long,
  val averageAnnualDepreciationPct: Double,
  val mostExpensiveBrand: String,
  val mostExpensiveBrandAvgKes: Long,
  val bestRetentionBrand: String,
  val bestRetentionPct: Double,
  val mostListedVehicle: String,
  val mostListedCount: Int,
  val brandPriceStats: List<BrandPriceStat>,
  val priceVsMileagePoints: List<ScatterPoint>,
  val priceVsAgePoints: List<ScatterPoint>,
  val depreciationSeries: List<ModelDepreciationSeries>,
  val priceDistribution: List<HistogramBucket>,
  val marketShare: List<BrandShareSlice>
)

data class PriceFactorImpact(
  val factorName: String,
  val detail: String,
  val impactKes: Long
)

enum class ImportAgeRuleStatus(val label: String) {
  ELIGIBLE("Import Eligible"),
  APPROACHING_LIMIT("Approaching 8-Yr Limit"),
  OVER_LIMIT("Over 8-Yr Limit (Blocked)")
}

data class KraDutyEstimate(
  val year: Int = 2018,
  val vehicleAgeYears: Int = 8,
  val ageRuleStatus: ImportAgeRuleStatus = ImportAgeRuleStatus.OVER_LIMIT,
  val ageRuleHeadline: String = "",
  val ageRuleDetail: String = "",
  val crspBenchmarkKes: Long = 0L,
  val kraDepreciationRatePct: Double = 0.0,
  val customsValueKes: Long = 0L,
  val estimatedCifKes: Long = 0L,
  val importDutyKes: Long = 0L,
  val exciseDutyRatePct: Double = 20.0,
  val exciseDutyKes: Long = 0L,
  val vatKes: Long = 0L,
  val idfKes: Long = 0L,
  val rdlKes: Long = 0L,
  val totalKraTaxesKes: Long = 0L,
  val clearingAndNtsaKes: Long = 85_000L,
  val mombasaLandedCostKes: Long = 0L,
  val localMarketPriceKes: Long = 0L,
  val importVsLocalDeltaKes: Long = 0L,
  val comparisonSummary: String = ""
)

data class FuelTrimEstimate(
  val fuelType: String,
  val consumptionKmPerLiter: Double,
  val pumpPriceKesPerLiter: Int,
  val annualFuelCostKes: Long
)

data class TcoEstimate(
  val annualDistanceKm: Int = 15_000,
  val annualFuelCostKes: Long = 0L,
  val annualInsuranceKes: Long = 0L,
  val insuranceRatePct: Double = 4.0,
  val maintenanceTierLabel: String = "Mid-Size Tier",
  val annualMaintenanceKes: Long = 0L,
  val annualRunningCostKes: Long = 0L,
  val fiveYearFuelKes: Long = 0L,
  val fiveYearInsuranceKes: Long = 0L,
  val fiveYearMaintenanceKes: Long = 0L,
  val fiveYearDepreciationLossKes: Long = 0L,
  val fiveYearProjectedResaleKes: Long = 0L,
  val totalFiveYearTcoKes: Long = 0L,
  val effectiveMonthlyTcoKes: Long = 0L,
  val fuelTrimComparison: List<FuelTrimEstimate> = emptyList()
)

data class PricingEstimate(
  val spec: VehicleSpecification,
  val estimatedPriceKes: Long,
  val comparableAverageKes: Long,
  val lowPriceKes: Long,
  val highPriceKes: Long,
  val annualDepreciationPct: Double,
  val cumulativeDepreciationPct: Double,
  val confidenceLabel: String,
  val comparableListings: List<VehicleListing>,
  val factorImpacts: List<PriceFactorImpact>,
  val methodologyExplanation: String,
  val kraDutyEstimate: KraDutyEstimate? = null,
  val tcoEstimate: TcoEstimate? = null
)

enum class CifCurrency(val label: String) {
  KES("KES"),
  USD("USD")
}

data class LandedCostInput(
  val make: String = "Toyota",
  val model: String = "Harrier",
  val year: Int = 2021,
  val engineCc: Int = 2000,
  val fuelType: String = "Petrol",
  val cifCurrency: CifCurrency = CifCurrency.KES,
  val customCifText: String = "",
  val usdToKesRateText: String = "129",
  val customCrspText: String = "",
  val clearingAndNtsaText: String = "85000"
)

data class LandedCostCalculation(
  val input: LandedCostInput = LandedCostInput(),
  val kraEstimate: KraDutyEstimate = KraDutyEstimate(),
  val effectiveCifKes: Long = 0L,
  val isCustomCifApplied: Boolean = false,
  val cifInputUsd: Double? = null,
  val usdToKesRate: Double = 129.0,
  val isCustomCrspApplied: Boolean = false,
  val effectiveTaxLoadPct: Double = 0.0,
  val localMarketLowKes: Long = 0L,
  val localMarketHighKes: Long = 0L,
  val comparableListings: List<VehicleListing> = emptyList(),
  val validationWarning: String? = null
)

data class VehicleDepreciationAnalysis(
  val key: String,
  val title: String,
  val make: String,
  val model: String,
  val year: Int,
  val estimatedOriginalValueKes: Long,
  val currentMarketPriceKes: Long,
  val valueRetainedPct: Double,
  val averageAnnualDepreciationPct: Double,
  val fiveYearProjectedKes: Long,
  val hasSufficientObservationData: Boolean,
  val observedSampleCount: Int,
  val curvePoints: List<DepreciationCurvePoint>
)

data class RetentionLeaderItem(
  val rank: Int,
  val make: String,
  val model: String,
  val referenceYear: Int,
  val currentAvgKes: Long,
  val valueRetainedPct: Double,
  val annualDepreciationPct: Double,
  val sampleCount: Int
)

data class ComparisonMetric(
  val vehicle: VehicleListing,
  val annualDepreciationPct: Double,
  val threeYearResaleKes: Long,
  val fiveYearResaleKes: Long,
  val valueRetentionPct: Double
)

data class ComparisonVerdict(
  val metrics: List<ComparisonMetric>,
  val strongestResaleVehicle: String,
  val verdictExplanation: String
)

enum class InsightCategory(val label: String) {
  RETENTION("Resale Retention"),
  MILEAGE("Mileage Sensitivity"),
  DEPRECIATION("Depreciation Velocity"),
  VALUE_YEAR("Sweet-Spot Year"),
  PRICE_ANOMALY("Deal Anomaly"),
  IMPORT_RULE("8-Yr Import Rule"),
  ODOMETER_SANITY("Odometer & Auction Audit")
}

enum class OdometerSanityStatus(val badgeLabel: String) {
  PLAUSIBLE("Usage Curve OK"),
  CHECK_DOCS("Verify Auction Sheet"),
  SUSPECT_LOW("Suspect Low Odometer")
}

data class OdometerSanityReport(
  val status: OdometerSanityStatus,
  val expectedMinKm: Int,
  val expectedMedianKm: Int,
  val ratioToExpected: Double,
  val headline: String,
  val verificationAdvice: String
)

data class MarketInsight(
  val id: String,
  val category: InsightCategory,
  val title: String,
  val finding: String,
  val metricHighlight: String,
  val relatedMake: String? = null,
  val isPositiveSignal: Boolean = true
)

data class PriceAnomalyListing(
  val listing: VehicleListing,
  val peerAverageKes: Long,
  val deviationPct: Double,
  val label: String
)

data class ImportResult(
  val sourceName: String,
  val sourceType: String,
  val totalRowsRead: Int,
  val importedListings: List<ImportedVehicleListing>,
  val duplicatesSkipped: Int,
  val invalidRowsSkipped: Int,
  val cleaningNotes: List<String>
)

// --- Firestore Serialized Models (All properties have default values, @DocumentId on id) ---

@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "",
  val email: String = "",
  val themeMode: String = ThemeMode.SYSTEM.key,
  val preferredCurrency: String = "KES",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SavedVehicle(
  @DocumentId val id: String = "",
  val userId: String = "",
  val listingId: String = "",
  val make: String = "",
  val model: String = "",
  val year: Int = 2018,
  val askingPriceKes: Long = 0L,
  val mileageKm: Int = 0,
  val transmission: String = "Automatic",
  val fuelType: String = "Petrol",
  val engineCc: Int = 1500,
  val condition: String = "Good",
  val importStatus: String = "Foreign Used",
  val location: String = "Nairobi",
  val notes: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class VehicleComparison(
  @DocumentId val id: String = "",
  val userId: String = "",
  val title: String = "",
  val vehicleIds: List<String> = emptyList(),
  val vehicleSummaries: List<String> = emptyList(),
  val winnerSummary: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ImportedVehicleListing(
  @DocumentId val id: String = "",
  val userId: String = "",
  val sourceId: String = "",
  val sourceName: String = "",
  val make: String = "",
  val model: String = "",
  val year: Int = 2018,
  val askingPriceKes: Long = 0L,
  val mileageKm: Int = 0,
  val engineCc: Int = 1500,
  val transmission: String = "Automatic",
  val fuelType: String = "Petrol",
  val condition: String = "Good",
  val importStatus: String = "Foreign Used",
  val location: String = "Nairobi",
  val demandScore: Int = 70,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  fun toVehicleListing(sourceType: String = "csv"): VehicleListing {
    val isFeed = sourceType.equals("feed", ignoreCase = true) ||
      sourceName.startsWith("Feed:", ignoreCase = true)
    return VehicleListing(
      id = "imp_$id",
      make = make,
      model = model,
      year = year,
      askingPriceKes = askingPriceKes,
      mileageKm = mileageKm,
      engineCc = engineCc,
      transmission = transmission,
      fuelType = fuelType,
      condition = condition,
      importStatus = importStatus,
      location = location,
      demandScore = demandScore,
      sourceId = sourceId,
      sourceName = sourceName,
      isImported = true,
      segment = com.example.data.seed.KenyanMarketSeed.segmentFor(make, model).label,
      provenance = if (isFeed) ListingProvenance.DEALER_FEED else ListingProvenance.CSV_IMPORT,
      updatedAtEpochMs = updatedAt?.toDate()?.time ?: createdAt?.toDate()?.time ?: 0L
    )
  }
}

@IgnoreExtraProperties
data class MarketAnalysis(
  @DocumentId val id: String = "",
  val userId: String = "",
  val make: String = "",
  val model: String = "",
  val year: Int = 2018,
  val mileageKm: Int = 0,
  val estimatedPriceKes: Long = 0L,
  val lowPriceKes: Long = 0L,
  val highPriceKes: Long = 0L,
  val annualDepreciationPct: Double = 0.0,
  val fiveYearProjectedKes: Long = 0L,
  val summary: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DataSourceRecord(
  @DocumentId val id: String = "",
  val userId: String = "",
  val name: String = "",
  val sourceType: String = "csv",
  val rowsImported: Int = 0,
  val duplicatesSkipped: Int = 0,
  val invalidRowsSkipped: Int = 0,
  @get:PropertyName("isActive") @field:PropertyName("isActive") val isActive: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PriceWatchlist(
  @DocumentId val id: String = "",
  val userId: String = "",
  val make: String = "Toyota",
  val model: String = "Axio",
  val minYear: Int = 2016,
  val maxYear: Int = 2020,
  val maxBudgetKes: Long = 1_320_000L,
  val preferredImportStatus: String = "All",
  @get:PropertyName("isActive") @field:PropertyName("isActive") val isActive: Boolean = true,
  val pushAlertsEnabled: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

data class WatchlistMatch(
  val watchlist: PriceWatchlist,
  val matchingListings: List<VehicleListing>,
  val bestPriceKes: Long? = matchingListings.minOfOrNull { it.askingPriceKes }
)

@IgnoreExtraProperties
data class MarketListing(
  @DocumentId val id: String = "",
  val sellerUserId: String = "",
  val sellerName: String = "",
  val make: String = "",
  val model: String = "",
  val year: Int = 2019,
  val askingPriceKes: Long = 0L,
  val mileageKm: Int = 0,
  val engineCc: Int = 1500,
  val transmission: String = "Automatic",
  val fuelType: String = "Petrol",
  val condition: String = "Good",
  val importStatus: String = "Foreign Used",
  val location: String = "Nairobi",
  val demandScore: Int = 82,
  val listingOrigin: String = "live_yard",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  fun toVehicleListing(): VehicleListing = VehicleListing(
    id = "mkt_$id",
    make = make,
    model = model,
    year = year,
    askingPriceKes = askingPriceKes,
    mileageKm = mileageKm,
    engineCc = engineCc,
    transmission = transmission,
    fuelType = fuelType,
    condition = condition,
    importStatus = importStatus,
    location = location,
    demandScore = demandScore,
    sourceId = "market_$listingOrigin",
    sourceName = sellerName.ifBlank { "Live Kenyan Yard" },
    isImported = false,
    segment = com.example.data.seed.KenyanMarketSeed.segmentFor(make, model).label,
    provenance = ListingProvenance.LIVE_YARD,
    updatedAtEpochMs = updatedAt?.toDate()?.time ?: createdAt?.toDate()?.time ?: 0L,
    sellerUserId = sellerUserId
  )
}

data class MarketSyncStatus(
  val isUsingFallbackCatalog: Boolean = true,
  val liveYardCount: Int = 0,
  val dealerFeedCount: Int = 0,
  val csvImportCount: Int = 0,
  val offlineReferenceCount: Int = 0,
  val lastMarketSyncEpochMs: Long = 0L,
  val isSyncingMarket: Boolean = false,
  val syncErrorMessage: String? = null
) {
  val realListingsCount: Int get() = liveYardCount + dealerFeedCount + csvImportCount
}

sealed interface AccountDeletionState {
  data object Idle : AccountDeletionState
  data object Deleting : AccountDeletionState
  data object RequiresRecentLogin : AccountDeletionState
  data class Error(val message: String) : AccountDeletionState
  data object Completed : AccountDeletionState
}

