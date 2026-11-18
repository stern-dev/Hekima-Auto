package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AuctionSheetAiAnalyzer
import com.example.data.fx.ExchangeRateRepository
import com.example.data.importer.VehicleSpreadsheetImporter
import com.example.data.preferences.ThemePreferencesRepository
import com.example.data.seed.KenyanMarketSeed
import com.example.domain.analytics.KenyanMarketAnalyticsEngine
import com.example.domain.analytics.YardInspectionEngine
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
import com.example.domain.model.ListingProvenance
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pure local in-memory analytics controller used when unauthenticated (Guest Preview Mode).
 * Never instantiates Firestore or executes any database queries/listeners.
 */
class GuestPreviewViewModel(
  private val themePrefsRepo: ThemePreferencesRepository,
  private val exchangeRateRepo: ExchangeRateRepository = ExchangeRateRepository(themePrefsRepo),
  private val aiAnalyzer: AuctionSheetAiAnalyzer = AuctionSheetAiAnalyzer()
) : ViewModel(), HekimaDashboardController {

  override val isGuestMode: Boolean = true
  override val currentUserIdOrNull: String? = null

  private val _pendingAuthPrompt = MutableStateFlow<AuthPromptReason?>(null)
  val pendingAuthPrompt: StateFlow<AuthPromptReason?> = _pendingAuthPrompt.asStateFlow()

  private val _includeSeedOverride = MutableStateFlow<Boolean?>(null)
  private val _guestFeedListings = MutableStateFlow<List<VehicleListing>>(emptyList())
  private val _lastMarketSyncEpochMs = MutableStateFlow(0L)
  override val accountDeletionState: StateFlow<AccountDeletionState> = MutableStateFlow(AccountDeletionState.Idle)

  private val _filterState = MutableStateFlow(FilterState())
  override val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

  private val _pricingSpec = MutableStateFlow(VehicleSpecification())
  override val pricingSpec: StateFlow<VehicleSpecification> = _pricingSpec.asStateFlow()

  private val _landedCostInput = MutableStateFlow(LandedCostInput())
  override val landedCostInput: StateFlow<LandedCostInput> = _landedCostInput.asStateFlow()

  private val _depreciationTargets = MutableStateFlow(
    listOf(
      DepreciationTarget("Toyota", "Axio", 2018),
      DepreciationTarget("Mazda", "CX-5", 2018),
      DepreciationTarget("Subaru", "Forester", 2018),
      DepreciationTarget("Toyota", "Harrier", 2018)
    )
  )
  override val depreciationTargets: StateFlow<List<DepreciationTarget>> = _depreciationTargets.asStateFlow()

  private val _selectedCompareIds = MutableStateFlow<List<String>>(emptyList())

  override val importResult: StateFlow<ImportResult?> = MutableStateFlow(null)
  override val isImporting: StateFlow<Boolean> = MutableStateFlow(false)

  private val _statusBanner = MutableStateFlow<String?>(null)
  override val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

  private val _lastImportWatchlistAlert = MutableStateFlow<String?>(null)
  override val lastImportWatchlistAlert: StateFlow<String?> = _lastImportWatchlistAlert.asStateFlow()

  private val _exchangeRate = MutableStateFlow(ExchangeRateSnapshot())
  override val exchangeRate: StateFlow<ExchangeRateSnapshot> = _exchangeRate.asStateFlow()

  private val _isRefreshingFx = MutableStateFlow(false)
  override val isRefreshingFx: StateFlow<Boolean> = _isRefreshingFx.asStateFlow()

  private val _aiAnalyzerState = MutableStateFlow<AiAnalyzerUiState>(AiAnalyzerUiState.Idle)
  override val aiAnalyzerState: StateFlow<AiAnalyzerUiState> = _aiAnalyzerState.asStateFlow()

  private val _inspectionDraft = MutableStateFlow(
    YardInspectionDraft(items = YardInspectionEngine.defaultChecklist())
  )
  override val inspectionDraft: StateFlow<YardInspectionDraft> = _inspectionDraft.asStateFlow()

  override val savedInspections: StateFlow<List<YardInspectionReport>> = MutableStateFlow(emptyList())
  override val feedSubscriptions: StateFlow<List<FeedSubscription>> = MutableStateFlow(emptyList())

  override val notificationPreferences: StateFlow<NotificationPreferences> =
    themePrefsRepo.notificationPreferences
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), NotificationPreferences())

  override val userProfile: StateFlow<UserProfile?> = MutableStateFlow(null)
  override val savedVehiclesState: StateFlow<UiState<List<SavedVehicle>>> =
    MutableStateFlow(UiState.Success(emptyList()))
  override val comparisons: StateFlow<List<VehicleComparison>> = MutableStateFlow(emptyList())
  override val marketAnalyses: StateFlow<List<MarketAnalysis>> = MutableStateFlow(emptyList())
  override val dataSources: StateFlow<List<DataSourceRecord>> = MutableStateFlow(emptyList())

  private val _sampleWatchlists = MutableStateFlow(
    listOf(
      PriceWatchlist(
        id = "preview_wl_axio",
        userId = "guest",
        make = "Toyota",
        model = "Axio",
        minYear = 2017,
        maxYear = 2019,
        maxBudgetKes = 1_320_000L,
        preferredImportStatus = "All",
        isActive = true
      ),
      PriceWatchlist(
        id = "preview_wl_cx5",
        userId = "guest",
        make = "Mazda",
        model = "CX-5",
        minYear = 2016,
        maxYear = 2019,
        maxBudgetKes = 2_750_000L,
        preferredImportStatus = "All",
        isActive = true
      )
    )
  )
  override val watchlists: StateFlow<List<PriceWatchlist>> = _sampleWatchlists.asStateFlow()

  override val includeSeedData: StateFlow<Boolean> = combine(
    _includeSeedOverride,
    _guestFeedListings
  ) { overrideVal, feedList ->
    overrideVal ?: feedList.isEmpty()
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), true)

  override val activeListings: StateFlow<List<VehicleListing>> = combine(
    _guestFeedListings,
    includeSeedData
  ) { feedList, include ->
    if (include) feedList + KenyanMarketSeed.listings else feedList
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), KenyanMarketSeed.listings)

  override val marketSyncStatus: StateFlow<MarketSyncStatus> = combine(
    activeListings,
    _guestFeedListings,
    _lastMarketSyncEpochMs,
    _isRefreshingFx,
    _exchangeRate
  ) { active, guestFeeds, lastSyncMs, refreshing, fx ->
    val fxSyncMs = if (!fx.isFallback) fx.fetchedAtEpochMs else 0L
    MarketSyncStatus(
      isUsingFallbackCatalog = guestFeeds.isEmpty(),
      liveYardCount = 0,
      dealerFeedCount = guestFeeds.size,
      csvImportCount = 0,
      offlineReferenceCount = active.count { it.provenance == ListingProvenance.OFFLINE_REFERENCE },
      lastMarketSyncEpochMs = maxOf(lastSyncMs, fxSyncMs),
      isSyncingMarket = refreshing
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), MarketSyncStatus(offlineReferenceCount = KenyanMarketSeed.listings.size))

  override val watchlistMatches: StateFlow<List<WatchlistMatch>> = combine(
    activeListings,
    watchlists
  ) { listings, wls ->
    KenyanMarketAnalyticsEngine.evaluateWatchlists(listings, wls)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  override val matchedListingIds: StateFlow<Set<String>> = combine(
    activeListings,
    watchlists
  ) { listings, wls ->
    KenyanMarketAnalyticsEngine.matchingListingIds(listings, wls)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

  override val filteredListings: StateFlow<List<VehicleListing>> = combine(
    activeListings,
    _filterState,
    watchlists
  ) { listings, filter, wls ->
    KenyanMarketAnalyticsEngine.applyFilters(listings, filter, wls)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), KenyanMarketSeed.listings)

  override val dashboardSummary: StateFlow<DashboardSummary> = filteredListings
    .map { KenyanMarketAnalyticsEngine.computeDashboardSummary(it) }
    .stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000L),
      KenyanMarketAnalyticsEngine.computeDashboardSummary(KenyanMarketSeed.listings)
    )

  override val pricingEstimate: StateFlow<PricingEstimate> = combine(
    activeListings,
    _pricingSpec
  ) { listings, spec ->
    KenyanMarketAnalyticsEngine.estimatePrice(listings, spec)
  }.stateIn(
    viewModelScope,
    SharingStarted.WhileSubscribed(5000L),
    KenyanMarketAnalyticsEngine.estimatePrice(KenyanMarketSeed.listings, VehicleSpecification())
  )

  override val landedCostCalculation: StateFlow<LandedCostCalculation> = combine(
    activeListings,
    _landedCostInput
  ) { listings, input ->
    KenyanMarketAnalyticsEngine.calculateLandedCost(listings, input)
  }.stateIn(
    viewModelScope,
    SharingStarted.WhileSubscribed(5000L),
    KenyanMarketAnalyticsEngine.calculateLandedCost(KenyanMarketSeed.listings, LandedCostInput())
  )

  override val depreciationAnalyses: StateFlow<List<VehicleDepreciationAnalysis>> = combine(
    activeListings,
    _depreciationTargets
  ) { listings, targets ->
    targets.map { t ->
      KenyanMarketAnalyticsEngine.analyzeVehicleDepreciation(listings, t.make, t.model, t.year)
    }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  override val topRetentionVehicles: StateFlow<List<RetentionLeaderItem>> = activeListings
    .map { KenyanMarketAnalyticsEngine.topTenValueRetentionVehicles(it) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  override val comparisonVerdict: StateFlow<ComparisonVerdict> = combine(
    activeListings,
    _selectedCompareIds
  ) { listings, selectedIds ->
    val effectiveIds = if (selectedIds.isEmpty()) {
      listings.filter { it.year == 2018 && it.model in listOf("Axio", "CX-5", "Forester") }
        .distinctBy { it.model }
        .take(3)
        .map { it.id }
    } else selectedIds
    val selected = effectiveIds.mapNotNull { id -> listings.firstOrNull { it.id == id } }
    KenyanMarketAnalyticsEngine.compareVehicles(selected)
  }.stateIn(
    viewModelScope,
    SharingStarted.WhileSubscribed(5000L),
    ComparisonVerdict(emptyList(), "None", "")
  )

  override val insightsAndAnomalies: StateFlow<Pair<List<MarketInsight>, List<PriceAnomalyListing>>> =
    filteredListings
      .map { KenyanMarketAnalyticsEngine.generateInsights(it) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList<MarketInsight>() to emptyList())

  init {
    viewModelScope.launch {
      val cached = exchangeRateRepo.loadInitialRate()
      _exchangeRate.value = cached
      if (!cached.isFallback && _landedCostInput.value.usdToKesRateText == "129") {
        _landedCostInput.value = _landedCostInput.value.copy(
          usdToKesRateText = String.format(Locale.US, "%.2f", cached.usdToKes)
        )
      }
      if (cached.isStale()) {
        _isRefreshingFx.value = true
        try {
          val refreshed = exchangeRateRepo.refreshIfStale()
          _exchangeRate.value = refreshed
          if (!refreshed.isFallback && _landedCostInput.value.usdToKesRateText == "129") {
            _landedCostInput.value = _landedCostInput.value.copy(
              usdToKesRateText = String.format(Locale.US, "%.2f", refreshed.usdToKes)
            )
          }
        } finally {
          _isRefreshingFx.value = false
        }
      }
    }
  }

  fun promptSignIn(reason: AuthPromptReason) {
    _pendingAuthPrompt.value = reason
  }

  fun dismissAuthPrompt() {
    _pendingAuthPrompt.value = null
  }

  override fun updateFilter(transform: (FilterState) -> FilterState) {
    _filterState.value = transform(_filterState.value)
  }

  override fun resetFilters() {
    _filterState.value = FilterState()
  }

  override fun toggleIncludeSeedData(include: Boolean) {
    _includeSeedOverride.value = include
  }

  override fun updatePricingSpec(transform: (VehicleSpecification) -> VehicleSpecification) {
    _pricingSpec.value = transform(_pricingSpec.value)
  }

  override fun prefillPricingFromListing(listing: VehicleListing) {
    _pricingSpec.value = VehicleSpecification(
      make = listing.make,
      model = listing.model,
      year = listing.year,
      mileageKm = listing.mileageKm,
      engineCc = listing.engineCc,
      transmission = listing.transmission,
      fuelType = listing.fuelType,
      condition = listing.condition,
      importStatus = listing.importStatus
    )
  }

  override fun updateLandedCostInput(transform: (LandedCostInput) -> LandedCostInput) {
    _landedCostInput.value = transform(_landedCostInput.value)
  }

  override fun resetLandedCostOverrides() {
    val liveRateStr = String.format(Locale.US, "%.2f", _exchangeRate.value.usdToKes)
    _landedCostInput.value = _landedCostInput.value.copy(
      customCifText = "",
      usdToKesRateText = if (_exchangeRate.value.isFallback) "129" else liveRateStr,
      customCrspText = "",
      clearingAndNtsaText = "85000"
    )
  }

  override fun refreshExchangeRates(forceApplyToLandedCost: Boolean) {
    if (_isRefreshingFx.value) return
    viewModelScope.launch {
      _isRefreshingFx.value = true
      try {
        val snapshot = exchangeRateRepo.fetchLatestRates()
        _exchangeRate.value = snapshot
        if (forceApplyToLandedCost || _landedCostInput.value.usdToKesRateText == "129") {
          _landedCostInput.value = _landedCostInput.value.copy(
            usdToKesRateText = String.format(Locale.US, "%.2f", snapshot.usdToKes)
          )
        }
        _statusBanner.value = if (snapshot.isFallback) {
          "Live FX unreachable — using ${snapshot.sourceLabel}."
        } else {
          "FX updated: 1 USD = ${String.format(Locale.US, "%.2f", snapshot.usdToKes)} KES"
        }
      } finally {
        _isRefreshingFx.value = false
      }
    }
  }

  override fun analyzeAuctionListing(listingText: String, imageBitmap: Bitmap?) {
    viewModelScope.launch {
      _aiAnalyzerState.value = AiAnalyzerUiState.Loading
      try {
        val result = aiAnalyzer.analyze(listingText, imageBitmap)
        _aiAnalyzerState.value = AiAnalyzerUiState.Success(result)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _aiAnalyzerState.value = AiAnalyzerUiState.Error(
          e.localizedMessage ?: "Unable to analyze auction sheet."
        )
      }
    }
  }

  override fun clearAiAnalysis() {
    _aiAnalyzerState.value = AiAnalyzerUiState.Idle
  }

  override fun prefillInspectionFromListing(listing: VehicleListing) {
    _inspectionDraft.value = YardInspectionEngine.createDraftFromListing(listing)
  }

  override fun prefillInspectionFromAi(analysis: AuctionSheetAnalysis) {
    _inspectionDraft.value = YardInspectionEngine.createDraftFromAiAnalysis(analysis)
  }

  override fun updateInspectionHeader(
    vehicleTitle: String,
    location: String,
    askingPriceKes: Long,
    notes: String
  ) {
    _inspectionDraft.value = _inspectionDraft.value.copy(
      vehicleTitle = vehicleTitle,
      location = location,
      askingPriceKes = askingPriceKes.coerceAtLeast(100_000L),
      notes = notes
    )
  }

  override fun updateInspectionItem(itemId: String, status: InspectionStatus, customRepairKes: Long?) {
    _inspectionDraft.value = _inspectionDraft.value.copy(
      items = _inspectionDraft.value.items.map { item ->
        if (item.id == itemId) {
          item.copy(status = status, customRepairKes = customRepairKes ?: item.customRepairKes)
        } else item
      }
    )
  }

  override fun resetInspectionDraft() {
    _inspectionDraft.value = YardInspectionDraft(items = YardInspectionEngine.defaultChecklist())
  }

  override fun saveYardInspection() {
    promptSignIn(
      AuthPromptReason(
        title = "Sign In to Save Yard Inspections",
        message = "Back up vehicle inspection scorecards, notes, and repair deductions to your Google account."
      )
    )
  }

  override fun deleteYardInspection(inspectionId: String) {
    promptSignIn(
      AuthPromptReason(
        title = "Sign In to Manage Inspections",
        message = "Sign in with Google to manage saved yard inspection scorecards."
      )
    )
  }

  override fun saveFeedSubscription(
    name: String,
    feedUrl: String,
    intervalHours: Int,
    alertsEnabled: Boolean
  ) {
    promptSignIn(
      AuthPromptReason(
        title = "Sign In for Auto-Sync Feeds",
        message = "Connect your Google account to schedule background dealership feed syncs and watchlist deal notifications."
      )
    )
  }

  override fun toggleFeedSubscriptionAutoSync(subscription: FeedSubscription, autoSyncEnabled: Boolean) {
    promptSignIn(
      AuthPromptReason(
        title = "Sign In to Manage Feed Sync",
        message = "Sign in with Google to configure recurring dealership feed sync schedules."
      )
    )
  }

  override fun toggleFeedSubscriptionAlerts(subscription: FeedSubscription, alertsEnabled: Boolean) {
    promptSignIn(
      AuthPromptReason(
        title = "Sign In for Watchlist Alerts",
        message = "Sign in with Google to enable push alerts on synced dealership feeds."
      )
    )
  }

  override fun deleteFeedSubscription(subscriptionId: String) {
    promptSignIn(
      AuthPromptReason(
        title = "Sign In to Manage Feed Sync",
        message = "Sign in with Google to remove saved dealership feed subscriptions."
      )
    )
  }

  override fun syncSubscribedFeedsNow(context: Context) {
    promptSignIn(
      AuthPromptReason(
        title = "Sign In to Sync Dealership Feeds",
        message = "Sign in with Google to run background feed synchronization across your saved sources."
      )
    )
  }

  override fun addDepreciationTarget(make: String, model: String, year: Int) {
    val target = DepreciationTarget(make, model, year)
    val current = _depreciationTargets.value
    if (current.none { it == target } && current.size < 4) {
      _depreciationTargets.value = current + target
    }
  }

  override fun removeDepreciationTarget(target: DepreciationTarget) {
    if (_depreciationTargets.value.size > 1) {
      _depreciationTargets.value = _depreciationTargets.value.filterNot { it == target }
    }
  }

  override fun toggleCompareVehicle(listingId: String) {
    val current = if (_selectedCompareIds.value.isEmpty()) {
      comparisonVerdict.value.metrics.map { it.vehicle.id }
    } else _selectedCompareIds.value

    _selectedCompareIds.value = if (listingId in current) {
      current.filterNot { it == listingId }
    } else if (current.size < 4) {
      current + listingId
    } else {
      _statusBanner.value = "Compare supports up to 4 vehicles at once. Remove one first."
      current
    }
  }

  override fun loadComparisonSet(vehicleIds: List<String>) {
    _selectedCompareIds.value = vehicleIds.take(4)
  }

  override fun saveCurrentComparison(title: String) {
    promptSignIn(
      AuthPromptReason(
        title = "Sign In to Save Comparisons",
        message = "Save side-by-side Kenyan resale comparisons to your Google account and reload them anytime."
      )
    )
  }

  override fun deleteComparison(id: String) {
    promptSignIn(
      AuthPromptReason(
        title = "Sign In to Manage Saved Comparisons",
        message = "Access and manage your saved cloud comparisons with Google Sign-In."
      )
    )
  }

  override fun savePricingAnalysis() {
    promptSignIn(
      AuthPromptReason(
        title = "Sign In to Save Valuation Reports",
        message = "Save estimated KES market valuations, low/high price spreads, and 5-year projections to your account."
      )
    )
  }

  override fun toggleSaveVehicle(listing: VehicleListing) {
    promptSignIn(
      AuthPromptReason(
        title = "Sign In to Save to Cloud Garage",
        message = "Bookmark ${listing.fullName} and track your favorite Kenyan listings across sessions."
      )
    )
  }

  override fun importSpreadsheet(context: Context, uri: Uri) {
    promptSignIn(
      AuthPromptReason(
        title = "Sign In to Import Dealership Files",
        message = "Upload Kenyan CSV & Excel spreadsheets with automatic KES standardization, cleaning, and cloud sync."
      )
    )
  }

  override fun importSampleCsvFeed() {
    promptSignIn(
      AuthPromptReason(
        title = "Sign In to Import Dealership Feeds",
        message = "Sign in with Google to run the dealership CSV cleaning pipeline and sync imported listings."
      )
    )
  }

  override fun importFromFeedUrl(url: String) {
    promptSignIn(
      AuthPromptReason(
        title = "Sign In to Fetch Remote Feeds",
        message = "Connect your Google account to pull external HTTPS CSV or JSON yard feeds into your analytics workspace."
      )
    )
  }

  override fun toggleDataSource(source: DataSourceRecord, active: Boolean) {
    promptSignIn(
      AuthPromptReason(
        title = "Sign In to Manage Data Sources",
        message = "Sign in with Google to activate or remove imported marketplace feeds."
      )
    )
  }

  override fun deleteDataSource(sourceId: String) {
    promptSignIn(
      AuthPromptReason(
        title = "Sign In to Manage Data Sources",
        message = "Sign in with Google to manage imported marketplace datasets."
      )
    )
  }

  override fun saveWatchlist(
    make: String,
    model: String,
    minYear: Int,
    maxYear: Int,
    maxBudgetKes: Long,
    preferredImportStatus: String
  ) {
    promptSignIn(
      AuthPromptReason(
        title = "Sign In to Save Target Watchlists",
        message = "Track your target vehicles & max KES budget and get instant alerts when new CSV or Feed batches match."
      )
    )
  }

  override fun toggleWatchlistActive(watchlist: PriceWatchlist, isActive: Boolean) {
    promptSignIn(
      AuthPromptReason(
        title = "Sign In to Manage Watchlists",
        message = "Sign in with Google to enable or pause persistent price watchlists."
      )
    )
  }

  override fun toggleWatchlistPushAlerts(watchlist: PriceWatchlist, enabled: Boolean) {
    promptSignIn(
      AuthPromptReason(
        title = "Sign In for Watchlist Push Alerts",
        message = "Sign in with Google to customize push notifications for individual vehicle watchlists."
      )
    )
  }

  override fun setPushAlertsEnabled(enabled: Boolean) {
    viewModelScope.launch {
      themePrefsRepo.setPushAlertsEnabled(enabled)
      _statusBanner.value = if (enabled) {
        "Price-drop push notifications enabled."
      } else {
        "Price-drop push notifications paused."
      }
    }
  }

  override fun setPriceDropAlertFrequency(frequency: PriceDropAlertFrequency) {
    viewModelScope.launch {
      themePrefsRepo.setAlertFrequency(frequency)
      _statusBanner.value = "Alert cadence updated: ${frequency.label}."
    }
  }

  override fun deleteWatchlist(watchlistId: String) {
    promptSignIn(
      AuthPromptReason(
        title = "Sign In to Manage Watchlists",
        message = "Sign in with Google to manage your saved target watchlists."
      )
    )
  }

  override fun publishLiveYardListing(
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
    sellerName: String
  ) {
    promptSignIn(
      AuthPromptReason(
        title = "Sign In to Publish Live Yard Listing",
        message = "Sign in with Google to publish verified Kenyan car yard inventory to the realtime marketplace."
      )
    )
  }

  override fun deleteLiveYardListing(listingId: String) {
    promptSignIn(
      AuthPromptReason(
        title = "Sign In to Manage Yard Listings",
        message = "Sign in with Google to edit or remove your live marketplace listings."
      )
    )
  }

  override fun refreshAllLiveData(context: Context) {
    refreshExchangeRates(forceApplyToLandedCost = false)
  }

  override fun deleteAccountAndAllData(context: Context, credentialManager: CredentialManager) = Unit
  override fun reauthenticateAndRetryDelete(context: Context, credentialManager: CredentialManager) = Unit
  override fun dismissAccountDeletionState() = Unit

  override fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch {
      themePrefsRepo.setThemeMode(mode)
    }
  }

  override fun clearStatusBanner() {
    _statusBanner.value = null
  }

  override fun clearWatchlistAlert() {
    _lastImportWatchlistAlert.value = null
  }
}
