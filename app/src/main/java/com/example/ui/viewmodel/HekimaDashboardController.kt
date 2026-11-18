package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.credentials.CredentialManager
import com.example.domain.model.AccountDeletionState
import com.example.domain.model.AiAnalyzerUiState
import com.example.domain.model.AuctionSheetAnalysis
import com.example.domain.model.ComparisonVerdict
import com.example.domain.model.DashboardSummary
import com.example.domain.model.DataSourceRecord
import com.example.domain.model.ExchangeRateSnapshot
import com.example.domain.model.FeedSubscription
import com.example.domain.model.FilterState
import com.example.domain.model.ImportResult
import com.example.domain.model.InspectionStatus
import com.example.domain.model.LandedCostCalculation
import com.example.domain.model.LandedCostInput
import com.example.domain.model.MarketAnalysis
import com.example.domain.model.MarketInsight
import com.example.domain.model.MarketSyncStatus
import com.example.domain.model.NotificationPreferences
import com.example.domain.model.PriceAnomalyListing
import com.example.domain.model.PriceDropAlertFrequency
import com.example.domain.model.PriceWatchlist
import com.example.domain.model.PricingEstimate
import com.example.domain.model.RetentionLeaderItem
import com.example.domain.model.SavedVehicle
import com.example.domain.model.ThemeMode
import com.example.domain.model.UiState
import com.example.domain.model.UserProfile
import com.example.domain.model.VehicleComparison
import com.example.domain.model.VehicleDepreciationAnalysis
import com.example.domain.model.VehicleListing
import com.example.domain.model.VehicleSpecification
import com.example.domain.model.WatchlistMatch
import com.example.domain.model.YardInspectionDraft
import com.example.domain.model.YardInspectionReport
import kotlinx.coroutines.flow.StateFlow

data class AuthPromptReason(
  val title: String,
  val message: String,
  val ctaLabel: String = "Sign in with Google"
)

interface HekimaDashboardController {
  val isGuestMode: Boolean
  val currentUserIdOrNull: String?
  val includeSeedData: StateFlow<Boolean>
  val marketSyncStatus: StateFlow<MarketSyncStatus>
  val accountDeletionState: StateFlow<AccountDeletionState>
  val filterState: StateFlow<FilterState>
  val pricingSpec: StateFlow<VehicleSpecification>
  val landedCostInput: StateFlow<LandedCostInput>
  val depreciationTargets: StateFlow<List<DepreciationTarget>>
  val importResult: StateFlow<ImportResult?>
  val isImporting: StateFlow<Boolean>
  val statusBanner: StateFlow<String?>
  val lastImportWatchlistAlert: StateFlow<String?>
  val userProfile: StateFlow<UserProfile?>
  val savedVehiclesState: StateFlow<UiState<List<SavedVehicle>>>
  val comparisons: StateFlow<List<VehicleComparison>>
  val marketAnalyses: StateFlow<List<MarketAnalysis>>
  val dataSources: StateFlow<List<DataSourceRecord>>
  val watchlists: StateFlow<List<PriceWatchlist>>
  val watchlistMatches: StateFlow<List<WatchlistMatch>>
  val matchedListingIds: StateFlow<Set<String>>
  val activeListings: StateFlow<List<VehicleListing>>
  val filteredListings: StateFlow<List<VehicleListing>>
  val dashboardSummary: StateFlow<DashboardSummary>
  val pricingEstimate: StateFlow<PricingEstimate>
  val landedCostCalculation: StateFlow<LandedCostCalculation>
  val depreciationAnalyses: StateFlow<List<VehicleDepreciationAnalysis>>
  val topRetentionVehicles: StateFlow<List<RetentionLeaderItem>>
  val comparisonVerdict: StateFlow<ComparisonVerdict>
  val insightsAndAnomalies: StateFlow<Pair<List<MarketInsight>, List<PriceAnomalyListing>>>

  // Live Exchange Rates
  val exchangeRate: StateFlow<ExchangeRateSnapshot>
  val isRefreshingFx: StateFlow<Boolean>

  // AI Auction Sheet & Listing Analyzer
  val aiAnalyzerState: StateFlow<AiAnalyzerUiState>

  // On-Site Yard Inspection Scorecard
  val inspectionDraft: StateFlow<YardInspectionDraft>
  val savedInspections: StateFlow<List<YardInspectionReport>>

  // Feed Subscriptions
  val feedSubscriptions: StateFlow<List<FeedSubscription>>

  // Notification Preferences
  val notificationPreferences: StateFlow<NotificationPreferences>

  fun updateFilter(transform: (FilterState) -> FilterState)
  fun resetFilters()
  fun toggleIncludeSeedData(include: Boolean)
  fun updatePricingSpec(transform: (VehicleSpecification) -> VehicleSpecification)
  fun prefillPricingFromListing(listing: VehicleListing)
  fun updateLandedCostInput(transform: (LandedCostInput) -> LandedCostInput)
  fun resetLandedCostOverrides()
  fun refreshExchangeRates(forceApplyToLandedCost: Boolean = false)
  fun addDepreciationTarget(make: String, model: String, year: Int)
  fun removeDepreciationTarget(target: DepreciationTarget)
  fun toggleCompareVehicle(listingId: String)
  fun loadComparisonSet(vehicleIds: List<String>)
  fun saveCurrentComparison(title: String)
  fun deleteComparison(id: String)
  fun savePricingAnalysis()
  fun toggleSaveVehicle(listing: VehicleListing)
  fun importSpreadsheet(context: Context, uri: Uri)
  fun importSampleCsvFeed()
  fun importFromFeedUrl(url: String)
  fun toggleDataSource(source: DataSourceRecord, active: Boolean)
  fun deleteDataSource(sourceId: String)
  fun saveWatchlist(
    make: String,
    model: String,
    minYear: Int,
    maxYear: Int,
    maxBudgetKes: Long,
    preferredImportStatus: String
  )
  fun toggleWatchlistActive(watchlist: PriceWatchlist, isActive: Boolean)
  fun toggleWatchlistPushAlerts(watchlist: PriceWatchlist, enabled: Boolean)
  fun deleteWatchlist(watchlistId: String)
  fun setPushAlertsEnabled(enabled: Boolean)
  fun setPriceDropAlertFrequency(frequency: PriceDropAlertFrequency)

  fun analyzeAuctionListing(listingText: String, imageBitmap: Bitmap?)
  fun clearAiAnalysis()

  fun prefillInspectionFromListing(listing: VehicleListing)
  fun prefillInspectionFromAi(analysis: AuctionSheetAnalysis)
  fun updateInspectionHeader(vehicleTitle: String, location: String, askingPriceKes: Long, notes: String)
  fun updateInspectionItem(itemId: String, status: InspectionStatus, customRepairKes: Long? = null)
  fun resetInspectionDraft()
  fun saveYardInspection()
  fun deleteYardInspection(inspectionId: String)

  fun saveFeedSubscription(name: String, feedUrl: String, intervalHours: Int, alertsEnabled: Boolean)
  fun toggleFeedSubscriptionAutoSync(subscription: FeedSubscription, autoSyncEnabled: Boolean)
  fun toggleFeedSubscriptionAlerts(subscription: FeedSubscription, alertsEnabled: Boolean)
  fun deleteFeedSubscription(subscriptionId: String)
  fun syncSubscribedFeedsNow(context: Context)

  fun publishLiveYardListing(
    make: String,
    model: String,
    year: Int,
    askingPriceKes: Long,
    mileageKm: Int,
    engineCc: Int,
    transmission: String,
    fuelType: String,
    condition: String,
    importStatus: String,
    location: String,
    sellerName: String = ""
  )
  fun deleteLiveYardListing(listingId: String)
  fun refreshAllLiveData(context: Context)

  fun deleteAccountAndAllData(context: Context, credentialManager: CredentialManager)
  fun reauthenticateAndRetryDelete(context: Context, credentialManager: CredentialManager)
  fun dismissAccountDeletionState()

  fun setThemeMode(mode: ThemeMode)
  fun clearStatusBanner()
  fun clearWatchlistAlert()
}
