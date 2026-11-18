package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.clearCredentialStateSafe
import com.example.auth.reauthenticateCurrentUserWithGoogle
import com.example.data.ai.AuctionSheetAiAnalyzer
import com.example.data.firebase.HekimaFirestoreRepository
import com.example.data.firebase.unwrapFirestoreException
import com.example.data.fx.ExchangeRateRepository
import com.example.data.importer.VehicleSpreadsheetImporter
import com.example.data.preferences.ThemePreferencesRepository
import com.example.data.seed.KenyanMarketSeed
import com.example.data.sync.FeedSyncScheduler
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
import com.example.domain.model.ImportedVehicleListing
import com.example.domain.model.InspectionStatus
import com.example.domain.model.LandedCostCalculation
import com.example.domain.model.LandedCostInput
import com.example.domain.model.ListingProvenance
import com.example.domain.model.MarketAnalysis
import com.example.domain.model.MarketInsight
import com.example.domain.model.MarketListing
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
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class DepreciationTarget(
  val make: String,
  val model: String,
  val year: Int
)

class HekimaViewModel(
  private val firestoreRepo: HekimaFirestoreRepository,
  private val themePrefsRepo: ThemePreferencesRepository,
  private val currentUserId: String,
  userDisplayName: String,
  userEmail: String?,
  private val appContext: Context? = null,
  private val exchangeRateRepo: ExchangeRateRepository = ExchangeRateRepository(themePrefsRepo),
  private val aiAnalyzer: AuctionSheetAiAnalyzer = AuctionSheetAiAnalyzer()
) : ViewModel(), HekimaDashboardController {

  override val isGuestMode: Boolean = false
  override val currentUserIdOrNull: String? = currentUserId

  private val _includeSeedOverride = MutableStateFlow<Boolean?>(null)
  private val _isSyncingMarket = MutableStateFlow(false)
  private val _marketSyncError = MutableStateFlow<String?>(null)
  private val _lastMarketSyncEpochMs = MutableStateFlow(0L)
  private val _accountDeletionState = MutableStateFlow<AccountDeletionState>(AccountDeletionState.Idle)
  override val accountDeletionState: StateFlow<AccountDeletionState> = _accountDeletionState.asStateFlow()

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
  val selectedCompareIds: StateFlow<List<String>> = _selectedCompareIds.asStateFlow()

  private val _importResult = MutableStateFlow<ImportResult?>(null)
  override val importResult: StateFlow<ImportResult?> = _importResult.asStateFlow()

  private val _isImporting = MutableStateFlow(false)
  override val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

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

  override val userProfile: StateFlow<UserProfile?> = firestoreRepo.observeUserProfile(currentUserId)
    .catch { error ->
      val fsErr = error.unwrapFirestoreException()
      if (fsErr == null && error is CancellationException) throw error
      Log.w(TAG, "Error observing profile", fsErr ?: error)
      emit(null)
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

  override val savedVehiclesState: StateFlow<UiState<List<SavedVehicle>>> =
    firestoreRepo.observeSavedVehicles(currentUserId)
      .map<List<SavedVehicle>, UiState<List<SavedVehicle>>> { UiState.Success(it) }
      .catch { error ->
        val fsErr = error.unwrapFirestoreException()
        if (fsErr == null && error is CancellationException) throw error
        val root = fsErr ?: error
        Log.w(TAG, "Error observing saved vehicles", root)
        emit(UiState.Error(root.message ?: "Failed to load saved vehicles"))
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  override val comparisons: StateFlow<List<VehicleComparison>> =
    firestoreRepo.observeComparisons(currentUserId)
      .catch { error ->
        val fsErr = error.unwrapFirestoreException()
        if (fsErr == null && error is CancellationException) throw error
        Log.w(TAG, "Error observing comparisons", fsErr ?: error)
        emit(emptyList())
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  override val marketAnalyses: StateFlow<List<MarketAnalysis>> =
    firestoreRepo.observeMarketAnalyses(currentUserId)
      .catch { error ->
        val fsErr = error.unwrapFirestoreException()
        if (fsErr == null && error is CancellationException) throw error
        Log.w(TAG, "Error observing analyses", fsErr ?: error)
        emit(emptyList())
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  override val dataSources: StateFlow<List<DataSourceRecord>> =
    firestoreRepo.observeDataSources(currentUserId)
      .catch { error ->
        val fsErr = error.unwrapFirestoreException()
        if (fsErr == null && error is CancellationException) throw error
        Log.w(TAG, "Error observing data sources", fsErr ?: error)
        emit(emptyList())
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  private val importedListings: StateFlow<List<ImportedVehicleListing>> =
    firestoreRepo.observeImportedListings(currentUserId)
      .catch { error ->
        val fsErr = error.unwrapFirestoreException()
        if (fsErr == null && error is CancellationException) throw error
        Log.w(TAG, "Error observing imported listings", fsErr ?: error)
        emit(emptyList())
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  override val watchlists: StateFlow<List<PriceWatchlist>> =
    firestoreRepo.observeWatchlists(currentUserId)
      .catch { error ->
        val fsErr = error.unwrapFirestoreException()
        if (fsErr == null && error is CancellationException) throw error
        Log.w(TAG, "Error observing watchlists", fsErr ?: error)
        emit(emptyList())
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  override val savedInspections: StateFlow<List<YardInspectionReport>> =
    firestoreRepo.observeInspections(currentUserId)
      .catch { error ->
        val fsErr = error.unwrapFirestoreException()
        if (fsErr == null && error is CancellationException) throw error
        Log.w(TAG, "Error observing inspections", fsErr ?: error)
        emit(emptyList())
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  override val feedSubscriptions: StateFlow<List<FeedSubscription>> =
    firestoreRepo.observeFeedSubscriptions(currentUserId)
      .catch { error ->
        val fsErr = error.unwrapFirestoreException()
        if (fsErr == null && error is CancellationException) throw error
        Log.w(TAG, "Error observing feed subscriptions", fsErr ?: error)
        emit(emptyList())
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  override val notificationPreferences: StateFlow<NotificationPreferences> =
    themePrefsRepo.notificationPreferences
      .catch { error ->
        if (error is CancellationException) throw error
        Log.w(TAG, "Error observing notification preferences", error)
        emit(NotificationPreferences())
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), NotificationPreferences())

  private val liveMarketListings: StateFlow<List<MarketListing>> =
    firestoreRepo.observeMarketListings()
      .catch { error ->
        val fsErr = error.unwrapFirestoreException()
        if (fsErr == null && error is CancellationException) throw error
        Log.w(TAG, "Error observing live market listings", fsErr ?: error)
        _marketSyncError.value = fsErr?.localizedMessage ?: error.localizedMessage
        emit(emptyList())
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  private val realWorldListings: StateFlow<List<VehicleListing>> = combine(
    liveMarketListings,
    importedListings,
    dataSources
  ) { liveMarket, imported, sources ->
    val activeSourceIds = sources.filter { it.isActive }.map { it.id }.toSet()
    val sourceTypes = sources.associate { it.id to it.sourceType }
    val liveMapped = liveMarket.map { it.toVehicleListing() }
    val importedMapped = imported
      .filter { activeSourceIds.isEmpty() || it.sourceId in activeSourceIds }
      .map { it.toVehicleListing(sourceType = sourceTypes[it.sourceId] ?: "csv") }
    liveMapped + importedMapped
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  override val includeSeedData: StateFlow<Boolean> = combine(
    _includeSeedOverride,
    realWorldListings
  ) { overrideVal, real ->
    overrideVal ?: real.isEmpty()
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), true)

  override val activeListings: StateFlow<List<VehicleListing>> = combine(
    realWorldListings,
    includeSeedData
  ) { real, includeSeed ->
    if (includeSeed) real + KenyanMarketSeed.listings else real
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), KenyanMarketSeed.listings)

  override val marketSyncStatus: StateFlow<MarketSyncStatus> = combine(
    activeListings,
    realWorldListings,
    feedSubscriptions,
    _lastMarketSyncEpochMs,
    combine(_isSyncingMarket, _marketSyncError) { syncing, err -> syncing to err }
  ) { active, real, subs, lastSyncMs, (syncing, syncErr) ->
    val liveCount = real.count { it.provenance == ListingProvenance.LIVE_YARD }
    val feedCount = real.count { it.provenance == ListingProvenance.DEALER_FEED }
    val csvCount = real.count { it.provenance == ListingProvenance.CSV_IMPORT }
    val refCount = active.count { it.provenance == ListingProvenance.OFFLINE_REFERENCE }
    val newestItemMs = real.maxOfOrNull { it.updatedAtEpochMs } ?: 0L
    val newestFeedMs = subs.maxOfOrNull { it.lastSyncedAtEpochMs } ?: 0L
    MarketSyncStatus(
      isUsingFallbackCatalog = real.isEmpty(),
      liveYardCount = liveCount,
      dealerFeedCount = feedCount,
      csvImportCount = csvCount,
      offlineReferenceCount = refCount,
      lastMarketSyncEpochMs = maxOf(lastSyncMs, newestItemMs, newestFeedMs),
      isSyncingMarket = syncing,
      syncErrorMessage = syncErr
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
      try {
        firestoreRepo.ensureUserProfile(
          userId = currentUserId,
          displayName = userDisplayName,
          email = userEmail
        )
      } catch (e: Exception) {
        Log.w(TAG, "Failed to initialize user profile: ${e.message}")
      }
    }
    viewModelScope.launch {
      val cached = exchangeRateRepo.loadInitialRate()
      _exchangeRate.value = cached
      if (!cached.isFallback && _landedCostInput.value.usdToKesRateText == "129") {
        _landedCostInput.value = _landedCostInput.value.copy(
          usdToKesRateText = String.format(Locale.US, "%.2f", cached.usdToKes)
        )
      }
      if (!cached.isFallback && cached.fetchedAtEpochMs > 0L) {
        _lastMarketSyncEpochMs.value = maxOf(_lastMarketSyncEpochMs.value, cached.fetchedAtEpochMs)
      }
      if (cached.isStale()) {
        _isRefreshingFx.value = true
        try {
          val refreshed = exchangeRateRepo.refreshIfStale()
          _exchangeRate.value = refreshed
          if (!refreshed.isFallback) {
            _lastMarketSyncEpochMs.value = maxOf(_lastMarketSyncEpochMs.value, refreshed.fetchedAtEpochMs)
            if (_landedCostInput.value.usdToKesRateText == "129") {
              _landedCostInput.value = _landedCostInput.value.copy(
                usdToKesRateText = String.format(Locale.US, "%.2f", refreshed.usdToKes)
              )
            }
          }
        } finally {
          _isRefreshingFx.value = false
        }
      }
    }
    appContext?.let { ctx ->
      viewModelScope.launch {
        feedSubscriptions.collect { subs ->
          FeedSyncScheduler.syncSchedule(ctx, subs)
        }
      }
    }
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
    val draft = _inspectionDraft.value
    viewModelScope.launch {
      try {
        val report = YardInspectionEngine.toReport(currentUserId, draft)
        firestoreRepo.saveInspection(report)
        _statusBanner.value = "Yard inspection saved (${report.overallScore}/100 • ${report.verdictTier})."
      } catch (e: Exception) {
        _statusBanner.value = "Could not save inspection: ${e.localizedMessage}"
      }
    }
  }

  override fun deleteYardInspection(inspectionId: String) {
    viewModelScope.launch {
      try {
        firestoreRepo.deleteInspection(inspectionId)
        _statusBanner.value = "Removed saved yard inspection."
      } catch (e: Exception) {
        _statusBanner.value = "Could not delete inspection: ${e.localizedMessage}"
      }
    }
  }

  override fun saveFeedSubscription(
    name: String,
    feedUrl: String,
    intervalHours: Int,
    alertsEnabled: Boolean
  ) {
    val cleanUrl = feedUrl.trim()
    if (!cleanUrl.startsWith("https://")) {
      _statusBanner.value = "Auto-sync feed URL must begin with https://"
      return
    }
    viewModelScope.launch {
      try {
        firestoreRepo.saveFeedSubscription(
          FeedSubscription(
            userId = currentUserId,
            name = name.ifBlank { cleanUrl.removePrefix("https://").substringBefore("/") },
            feedUrl = cleanUrl,
            syncIntervalHours = intervalHours,
            autoSyncEnabled = true,
            alertsEnabled = alertsEnabled,
            lastSyncedAtEpochMs = System.currentTimeMillis(),
            lastStatusMessage = "Auto-sync scheduled every ${intervalHours}h"
          )
        )
        _statusBanner.value = "Saved recurring feed subscription (${intervalHours}h schedule)."
      } catch (e: Exception) {
        _statusBanner.value = "Failed to save subscription: ${e.localizedMessage}"
      }
    }
  }

  override fun toggleFeedSubscriptionAutoSync(subscription: FeedSubscription, autoSyncEnabled: Boolean) {
    viewModelScope.launch {
      try {
        firestoreRepo.updateFeedSubscriptionSyncStatus(
          subscriptionId = subscription.id,
          autoSyncEnabled = autoSyncEnabled,
          lastStatusMessage = if (autoSyncEnabled) "Auto-sync active (${subscription.syncIntervalHours}h)" else "Auto-sync paused"
        )
      } catch (e: Exception) {
        _statusBanner.value = "Could not update feed schedule: ${e.localizedMessage}"
      }
    }
  }

  override fun toggleFeedSubscriptionAlerts(subscription: FeedSubscription, alertsEnabled: Boolean) {
    viewModelScope.launch {
      try {
        firestoreRepo.updateFeedSubscriptionSyncStatus(
          subscriptionId = subscription.id,
          alertsEnabled = alertsEnabled
        )
      } catch (e: Exception) {
        _statusBanner.value = "Could not update feed alerts: ${e.localizedMessage}"
      }
    }
  }

  override fun deleteFeedSubscription(subscriptionId: String) {
    viewModelScope.launch {
      try {
        firestoreRepo.deleteFeedSubscription(subscriptionId)
        _statusBanner.value = "Feed subscription removed."
      } catch (e: Exception) {
        _statusBanner.value = "Could not remove feed subscription: ${e.localizedMessage}"
      }
    }
  }

  override fun syncSubscribedFeedsNow(context: Context) {
    val activeSubs = feedSubscriptions.value.filter { it.autoSyncEnabled }
    if (activeSubs.isEmpty()) {
      _statusBanner.value = "No active auto-sync subscriptions enabled."
      return
    }
    FeedSyncScheduler.triggerImmediateSync(context)
    _statusBanner.value = "Background sync queued for ${activeSubs.size} active dealership feed(s)."
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
    val verdict = comparisonVerdict.value
    if (verdict.metrics.isEmpty()) return
    viewModelScope.launch {
      try {
        firestoreRepo.saveComparison(
          VehicleComparison(
            userId = currentUserId,
            title = title.ifBlank { "${verdict.metrics.first().vehicle.make} Comparison" },
            vehicleIds = verdict.metrics.map { it.vehicle.id },
            vehicleSummaries = verdict.metrics.map { "${it.vehicle.fullName} • ${KenyanMarketAnalyticsEngine.formatKes(it.vehicle.askingPriceKes)}" },
            winnerSummary = verdict.verdictExplanation
          )
        )
        _statusBanner.value = "Comparison saved to cloud."
      } catch (e: Exception) {
        _statusBanner.value = "Could not save comparison: ${e.localizedMessage}"
      }
    }
  }

  override fun deleteComparison(id: String) {
    viewModelScope.launch {
      try {
        firestoreRepo.deleteComparison(id)
      } catch (e: Exception) {
        _statusBanner.value = "Failed to delete comparison: ${e.localizedMessage}"
      }
    }
  }

  override fun savePricingAnalysis() {
    val est = pricingEstimate.value
    if (est.estimatedPriceKes <= 0L) return
    val depAnalysis = KenyanMarketAnalyticsEngine.analyzeVehicleDepreciation(
      activeListings.value,
      est.spec.make,
      est.spec.model,
      est.spec.year
    )
    viewModelScope.launch {
      try {
        firestoreRepo.saveMarketAnalysis(
          MarketAnalysis(
            userId = currentUserId,
            make = est.spec.make,
            model = est.spec.model,
            year = est.spec.year,
            mileageKm = est.spec.mileageKm,
            estimatedPriceKes = est.estimatedPriceKes,
            lowPriceKes = est.lowPriceKes,
            highPriceKes = est.highPriceKes,
            annualDepreciationPct = est.annualDepreciationPct,
            fiveYearProjectedKes = depAnalysis.fiveYearProjectedKes,
            summary = est.methodologyExplanation
          )
        )
        _statusBanner.value = "Valuation report saved to cloud."
      } catch (e: Exception) {
        _statusBanner.value = "Could not save valuation: ${e.localizedMessage}"
      }
    }
  }

  override fun toggleSaveVehicle(listing: VehicleListing) {
    val savedList = (savedVehiclesState.value as? UiState.Success)?.data ?: emptyList()
    val existing = savedList.firstOrNull { it.listingId == listing.id }
    viewModelScope.launch {
      try {
        if (existing != null) {
          firestoreRepo.deleteSavedVehicle(existing.id)
          _statusBanner.value = "Removed ${listing.fullName} from saved vehicles."
        } else {
          firestoreRepo.saveVehicle(
            SavedVehicle(
              userId = currentUserId,
              listingId = listing.id,
              make = listing.make,
              model = listing.model,
              year = listing.year,
              askingPriceKes = listing.askingPriceKes,
              mileageKm = listing.mileageKm,
              transmission = listing.transmission,
              fuelType = listing.fuelType,
              engineCc = listing.engineCc,
              condition = listing.condition,
              importStatus = listing.importStatus,
              location = listing.location
            )
          )
          _statusBanner.value = "Saved ${listing.fullName} to your cloud garage."
        }
      } catch (e: Exception) {
        _statusBanner.value = "Sync failed: ${e.localizedMessage}"
      }
    }
  }

  override fun importSpreadsheet(context: Context, uri: Uri) {
    viewModelScope.launch {
      _isImporting.value = true
      try {
        val result = withContext(Dispatchers.IO) {
          VehicleSpreadsheetImporter.importFromUri(
            context = context,
            uri = uri,
            existingListings = activeListings.value,
            userId = currentUserId
          )
        }
        _importResult.value = result
        if (result.importedListings.isNotEmpty()) {
          val sourceId = result.importedListings.first().sourceId
          firestoreRepo.saveImportBatch(
            sourceRecord = DataSourceRecord(
              id = sourceId,
              userId = currentUserId,
              name = result.sourceName,
              sourceType = result.sourceType,
              rowsImported = result.importedListings.size,
              duplicatesSkipped = result.duplicatesSkipped,
              invalidRowsSkipped = result.invalidRowsSkipped,
              isActive = true
            ),
            listings = result.importedListings
          )
          notifyWatchlistHits(result.importedListings, result.sourceName)
          _statusBanner.value = "Imported ${result.importedListings.size} cleaned listings from ${result.sourceName}."
        } else {
          _statusBanner.value = result.cleaningNotes.firstOrNull() ?: "No new valid listings found."
        }
      } catch (e: Exception) {
        _statusBanner.value = "Import failed: ${e.localizedMessage ?: "Invalid file format"}"
      } finally {
        _isImporting.value = false
      }
    }
  }

  override fun importSampleCsvFeed() {
    val sampleCsv = """
      make,model,year,price,mileage,engine_cc,transmission,fuel_type,condition,import_status,location
      Toyta,Axio NZE,2018,KSh 1.35M,69000,1500,Automatic,Petrol,Excellent,Foreign Used,Nairobi
      Mazd,CX5,2019,KES 3180000,54000,2000,Automatic,Petrol,Excellent,Foreign Used,Mombasa
      VW,Golf,2017,1590000,88000,1400,Automatic,Petrol,Good,Foreign Used,Nakuru
      Subaru,Forester,2018,KSh 2.84M,72000,2000,Automatic,Petrol,Good,Foreign Used,Kisumu
      Honda,Vezel,2018,KES 2290000,61000,1500,Automatic,Hybrid,Excellent,Foreign Used,Nairobi
    """.trimIndent()
    viewModelScope.launch {
      _isImporting.value = true
      try {
        val result = withContext(Dispatchers.IO) {
          VehicleSpreadsheetImporter.importCsvText(
            csvText = sampleCsv,
            sourceName = "Nairobi Dealership Batch CSV",
            existingListings = activeListings.value,
            userId = currentUserId
          )
        }
        _importResult.value = result
        if (result.importedListings.isNotEmpty()) {
          val sourceId = result.importedListings.first().sourceId
          firestoreRepo.saveImportBatch(
            sourceRecord = DataSourceRecord(
              id = sourceId,
              userId = currentUserId,
              name = result.sourceName,
              sourceType = "csv",
              rowsImported = result.importedListings.size,
              duplicatesSkipped = result.duplicatesSkipped,
              invalidRowsSkipped = result.invalidRowsSkipped,
              isActive = true
            ),
            listings = result.importedListings
          )
          notifyWatchlistHits(result.importedListings, result.sourceName)
          _statusBanner.value = "Imported ${result.importedListings.size} cleaned dealership listings."
        } else {
          _statusBanner.value = "All rows in sample batch were already imported (deduplicated)."
        }
      } catch (e: Exception) {
        _statusBanner.value = "Batch import failed: ${e.localizedMessage}"
      } finally {
        _isImporting.value = false
      }
    }
  }

  override fun importFromFeedUrl(url: String) {
    viewModelScope.launch {
      _isImporting.value = true
      try {
        val result = withContext(Dispatchers.IO) {
          VehicleSpreadsheetImporter.importFromFeedUrl(
            url = url,
            existingListings = activeListings.value,
            userId = currentUserId
          )
        }
        _importResult.value = result
        if (result.importedListings.isNotEmpty()) {
          val sourceId = result.importedListings.first().sourceId
          firestoreRepo.saveImportBatch(
            sourceRecord = DataSourceRecord(
              id = sourceId,
              userId = currentUserId,
              name = result.sourceName,
              sourceType = "feed",
              rowsImported = result.importedListings.size,
              duplicatesSkipped = result.duplicatesSkipped,
              invalidRowsSkipped = result.invalidRowsSkipped,
              isActive = true
            ),
            listings = result.importedListings
          )
          notifyWatchlistHits(result.importedListings, result.sourceName)
          _statusBanner.value = "Synced ${result.importedListings.size} listings from ${result.sourceName}."
        } else {
          _statusBanner.value = result.cleaningNotes.firstOrNull() ?: "No new listings found in feed."
        }
      } catch (e: Exception) {
        _statusBanner.value = "Feed sync failed: ${e.localizedMessage ?: "Unable to fetch feed"}"
      } finally {
        _isImporting.value = false
      }
    }
  }

  override fun toggleDataSource(source: DataSourceRecord, active: Boolean) {
    viewModelScope.launch {
      try {
        firestoreRepo.toggleDataSourceActive(source, active)
      } catch (e: Exception) {
        _statusBanner.value = "Failed to toggle source: ${e.localizedMessage}"
      }
    }
  }

  override fun deleteDataSource(sourceId: String) {
    viewModelScope.launch {
      try {
        firestoreRepo.deleteDataSourceAndListings(sourceId)
        _statusBanner.value = "Removed imported source and its listings."
      } catch (e: Exception) {
        _statusBanner.value = "Failed to remove source: ${e.localizedMessage}"
      }
    }
  }

  override fun saveWatchlist(
    make: String,
    model: String,
    minYear: Int,
    maxYear: Int,
    maxBudgetKes: Long,
    preferredImportStatus: String
  ) {
    viewModelScope.launch {
      try {
        val wl = PriceWatchlist(
          userId = currentUserId,
          make = make,
          model = model,
          minYear = minYear,
          maxYear = maxYear,
          maxBudgetKes = maxBudgetKes,
          preferredImportStatus = preferredImportStatus,
          isActive = true
        )
        firestoreRepo.saveWatchlist(wl)
        val hits = KenyanMarketAnalyticsEngine.matchingListingIds(activeListings.value, listOf(wl)).size
        _statusBanner.value = "Watchlist saved ($hits current matching listings)."
      } catch (e: Exception) {
        _statusBanner.value = "Could not save watchlist: ${e.localizedMessage}"
      }
    }
  }

  override fun toggleWatchlistActive(watchlist: PriceWatchlist, isActive: Boolean) {
    viewModelScope.launch {
      try {
        firestoreRepo.toggleWatchlistActive(watchlist, isActive)
      } catch (e: Exception) {
        _statusBanner.value = "Could not update watchlist: ${e.localizedMessage}"
      }
    }
  }

  override fun toggleWatchlistPushAlerts(watchlist: PriceWatchlist, enabled: Boolean) {
    viewModelScope.launch {
      try {
        firestoreRepo.toggleWatchlistPushAlerts(watchlist, enabled)
        _statusBanner.value = if (enabled) {
          "Push alerts enabled for ${watchlist.make} ${watchlist.model}."
        } else {
          "Push alerts muted for ${watchlist.make} ${watchlist.model}."
        }
      } catch (e: Exception) {
        _statusBanner.value = "Could not update watchlist push setting: ${e.localizedMessage}"
      }
    }
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
    viewModelScope.launch {
      try {
        firestoreRepo.deleteWatchlist(watchlistId)
        _statusBanner.value = "Watchlist deleted."
      } catch (e: Exception) {
        _statusBanner.value = "Could not delete watchlist: ${e.localizedMessage}"
      }
    }
  }

  private fun notifyWatchlistHits(
    imported: List<ImportedVehicleListing>,
    sourceName: String
  ) {
    val activeWls = watchlists.value.filter { it.isActive }
    if (activeWls.isEmpty()) return
    val newListings = imported.map { it.toVehicleListing() }
    val matchedIds = KenyanMarketAnalyticsEngine.matchingListingIds(newListings, activeWls)
    if (matchedIds.isNotEmpty()) {
      _lastImportWatchlistAlert.value = "${matchedIds.size} newly imported listing(s) from $sourceName match your target price watchlists!"
    }
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
    if (make.isBlank() || model.isBlank() || askingPriceKes !in 100_000L..150_000_000L) {
      _statusBanner.value = "Enter a valid make, model, and KES asking price (100K – 150M)."
      return
    }
    viewModelScope.launch {
      _isSyncingMarket.value = true
      try {
        val displaySeller = sellerName.ifBlank {
          userProfile.value?.displayName?.ifBlank { "Kenyan Yard Seller" } ?: "Kenyan Yard Seller"
        }
        val listing = MarketListing(
          sellerUserId = currentUserId,
          sellerName = displaySeller,
          make = make.trim(),
          model = model.trim(),
          year = year.coerceIn(1995, 2026),
          askingPriceKes = askingPriceKes.coerceIn(100_000L, 150_000_000L),
          mileageKm = mileageKm.coerceIn(0, 1_500_000),
          engineCc = engineCc.coerceIn(600, 7500),
          transmission = transmission,
          fuelType = fuelType,
          condition = condition,
          importStatus = importStatus,
          location = location.ifBlank { "Nairobi" }.trim(),
          demandScore = if (year >= 2019) 86 else 78,
          listingOrigin = "live_yard"
        )
        firestoreRepo.publishMarketListing(listing)
        _lastMarketSyncEpochMs.value = System.currentTimeMillis()
        _marketSyncError.value = null
        _statusBanner.value = "Published ${listing.make} ${listing.model} ${listing.year} to Live Market."
      } catch (e: Exception) {
        _marketSyncError.value = e.localizedMessage
        _statusBanner.value = "Failed to publish listing: ${e.localizedMessage}"
      } finally {
        _isSyncingMarket.value = false
      }
    }
  }

  override fun deleteLiveYardListing(listingId: String) {
    viewModelScope.launch {
      try {
        firestoreRepo.deleteMarketListing(listingId)
        _lastMarketSyncEpochMs.value = System.currentTimeMillis()
        _statusBanner.value = "Removed live yard listing."
      } catch (e: Exception) {
        _statusBanner.value = "Could not delete listing: ${e.localizedMessage}"
      }
    }
  }

  override fun refreshAllLiveData(context: Context) {
    viewModelScope.launch {
      _isSyncingMarket.value = true
      try {
        val snapshot = exchangeRateRepo.fetchLatestRates()
        _exchangeRate.value = snapshot
        if (!snapshot.isFallback && _landedCostInput.value.usdToKesRateText == "129") {
          _landedCostInput.value = _landedCostInput.value.copy(
            usdToKesRateText = String.format(Locale.US, "%.2f", snapshot.usdToKes)
          )
        }
        val activeSubs = feedSubscriptions.value.filter { it.autoSyncEnabled }
        if (activeSubs.isNotEmpty()) {
          FeedSyncScheduler.triggerImmediateSync(context)
        }
        _lastMarketSyncEpochMs.value = System.currentTimeMillis()
        _marketSyncError.value = null
        _statusBanner.value = if (snapshot.isFallback) {
          "Live market synced (FX using ${snapshot.sourceLabel})."
        } else {
          "Live FX (1 USD = ${String.format(Locale.US, "%.2f", snapshot.usdToKes)} KES) & market synced."
        }
      } catch (e: Exception) {
        _marketSyncError.value = e.localizedMessage
        _statusBanner.value = "Refresh failed: ${e.localizedMessage}"
      } finally {
        _isSyncingMarket.value = false
      }
    }
  }

  override fun deleteAccountAndAllData(context: Context, credentialManager: CredentialManager) {
    if (_accountDeletionState.value == AccountDeletionState.Deleting) return
    viewModelScope.launch {
      _accountDeletionState.value = AccountDeletionState.Deleting
      try {
        firestoreRepo.deleteAllUserCloudData(currentUserId)
        themePrefsRepo.clearFxCache()
        val user = Firebase.auth.currentUser
        if (user != null) {
          try {
            user.delete().await()
          } catch (_: FirebaseAuthRecentLoginRequiredException) {
            _accountDeletionState.value = AccountDeletionState.RequiresRecentLogin
            return@launch
          }
        }
        clearCredentialStateSafe(credentialManager)
        _accountDeletionState.value = AccountDeletionState.Completed
      } catch (e: Exception) {
        _accountDeletionState.value = AccountDeletionState.Error(
          e.localizedMessage ?: "Failed to delete account and cloud data."
        )
      }
    }
  }

  override fun reauthenticateAndRetryDelete(context: Context, credentialManager: CredentialManager) {
    if (_accountDeletionState.value == AccountDeletionState.Deleting) return
    viewModelScope.launch {
      _accountDeletionState.value = AccountDeletionState.Deleting
      val reauthResult = reauthenticateCurrentUserWithGoogle(context, credentialManager)
      if (reauthResult.isFailure) {
        _accountDeletionState.value = AccountDeletionState.Error(
          reauthResult.exceptionOrNull()?.localizedMessage ?: "Google verification failed."
        )
        return@launch
      }
      try {
        runCatching { firestoreRepo.deleteAllUserCloudData(currentUserId) }
        themePrefsRepo.clearFxCache()
        Firebase.auth.currentUser?.delete()?.await()
        clearCredentialStateSafe(credentialManager)
        _accountDeletionState.value = AccountDeletionState.Completed
      } catch (e: Exception) {
        _accountDeletionState.value = AccountDeletionState.Error(
          e.localizedMessage ?: "Could not complete account deletion."
        )
      }
    }
  }

  override fun dismissAccountDeletionState() {
    _accountDeletionState.value = AccountDeletionState.Idle
  }

  override fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch {
      themePrefsRepo.setThemeMode(mode)
      try {
        firestoreRepo.updateUserTheme(currentUserId, mode)
      } catch (e: Exception) {
        Log.w(TAG, "Cloud theme sync skipped: ${e.message}")
      }
    }
  }

  override fun clearStatusBanner() {
    _statusBanner.value = null
  }

  override fun clearWatchlistAlert() {
    _lastImportWatchlistAlert.value = null
  }

  private companion object {
    const val TAG = "HekimaVM"
  }
}
