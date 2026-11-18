package com.example.ui.navigation

import android.app.Application
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.data.firebase.HekimaFirestoreRepository
import com.example.data.preferences.ThemePreferencesRepository
import com.example.data.seed.KenyanMarketSeed
import com.example.domain.model.ThemeMode
import com.example.domain.model.UiState
import com.example.ui.components.GoogleSignInModalSheet
import com.example.ui.screens.AiAuctionAnalyzerScreen
import com.example.ui.screens.CompareScreen
import com.example.ui.screens.DataManagementScreen
import com.example.ui.screens.DepreciationScreen
import com.example.ui.screens.ExplorerScreen
import com.example.ui.screens.InsightsScreen
import com.example.ui.screens.LandedCostScreen
import com.example.ui.screens.NotificationPreferencesScreen
import com.example.ui.screens.OverviewScreen
import com.example.ui.screens.PricingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.YardInspectionScreen
import com.example.ui.viewmodel.AuthPromptReason
import com.example.ui.viewmodel.GuestPreviewViewModel
import com.example.ui.viewmodel.HekimaDashboardController
import com.example.ui.viewmodel.HekimaViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

enum class HekimaDestination(
  val route: String,
  val label: String,
  val icon: ImageVector,
  val inBottomBar: Boolean
) {
  OVERVIEW("overview", "Overview", Icons.Filled.Analytics, true),
  PRICING("pricing", "Pricing", Icons.Filled.PriceCheck, true),
  AI_ANALYZER("ai_analyzer", "AI Analyzer", Icons.Filled.AutoAwesome, false),
  YARD_INSPECTION("yard_inspection", "Yard Inspect", Icons.AutoMirrored.Filled.FactCheck, false),
  LANDED_COST("landed_cost", "Landed Cost", Icons.Filled.Anchor, false),
  DEPRECIATION("depreciation", "Depreciation", Icons.AutoMirrored.Filled.TrendingDown, false),
  COMPARE("compare", "Compare", Icons.AutoMirrored.Filled.CompareArrows, true),
  INSIGHTS("insights", "Insights", Icons.Filled.Lightbulb, false),
  EXPLORER("explorer", "Explorer", Icons.Filled.DirectionsCar, true),
  DATA("data", "Data Mgmt", Icons.Filled.CloudUpload, false),
  NOTIFICATIONS("notifications", "Alerts", Icons.Filled.NotificationsActive, false),
  SETTINGS("settings", "Settings", Icons.Filled.Settings, false)
}

@Composable
fun HekimaRootNavigation(
  themePrefsRepo: ThemePreferencesRepository,
  currentThemeMode: ThemeMode,
  openWatchlistMatches: Boolean = false,
  onConsumeWatchlistDeepLink: () -> Unit = {},
  auth: FirebaseAuth = Firebase.auth
) {
  val context = LocalContext.current
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val user = currentUser

  // Silently check for previously authorized Google credentials without blocking the UI.
  LaunchedEffect(Unit) {
    if (auth.currentUser == null) {
      val credentialManager = CredentialManager.create(context)
      attemptAutoSignIn(
        context = context,
        credentialManager = credentialManager,
        onAuthSuccess = {},
        onUnauthenticated = {},
        scope = this
      )
    }
  }

  if (user == null) {
    HekimaGuestApp(
      themePrefsRepo = themePrefsRepo,
      currentThemeMode = currentThemeMode,
      openWatchlistMatches = openWatchlistMatches,
      onConsumeWatchlistDeepLink = onConsumeWatchlistDeepLink
    )
  } else {
    key(user.uid) {
      HekimaAuthenticatedApp(
        currentUserId = user.uid,
        userDisplayName = user.displayName ?: "Hekima Analyst",
        userEmail = user.email,
        themePrefsRepo = themePrefsRepo,
        currentThemeMode = currentThemeMode,
        openWatchlistMatches = openWatchlistMatches,
        onConsumeWatchlistDeepLink = onConsumeWatchlistDeepLink
      )
    }
  }
}

@Composable
private fun HekimaGuestApp(
  themePrefsRepo: ThemePreferencesRepository,
  currentThemeMode: ThemeMode,
  openWatchlistMatches: Boolean,
  onConsumeWatchlistDeepLink: () -> Unit
) {
  val guestVm: GuestPreviewViewModel = viewModel(
    key = "guest_preview",
    factory = viewModelFactory {
      initializer { GuestPreviewViewModel(themePrefsRepo) }
    }
  )
  val pendingAuthPrompt by guestVm.pendingAuthPrompt.collectAsStateWithLifecycle()

  HekimaAppShell(
    controller = guestVm,
    userDisplayName = "Guest Preview",
    userEmail = null,
    currentThemeMode = currentThemeMode,
    openWatchlistMatches = openWatchlistMatches,
    onConsumeWatchlistDeepLink = onConsumeWatchlistDeepLink,
    onRequestSignIn = guestVm::promptSignIn
  )

  val activePrompt = pendingAuthPrompt
  if (activePrompt != null) {
    GoogleSignInModalSheet(
      reason = activePrompt,
      onDismiss = guestVm::dismissAuthPrompt,
      onAuthSuccess = guestVm::dismissAuthPrompt
    )
  }
}

@Composable
private fun HekimaAuthenticatedApp(
  currentUserId: String,
  userDisplayName: String,
  userEmail: String?,
  themePrefsRepo: ThemePreferencesRepository,
  currentThemeMode: ThemeMode,
  openWatchlistMatches: Boolean,
  onConsumeWatchlistDeepLink: () -> Unit
) {
  val viewModel: HekimaViewModel = viewModel(
    key = currentUserId,
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY]) as Application
        val databaseId = app.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        HekimaViewModel(
          firestoreRepo = HekimaFirestoreRepository(db),
          themePrefsRepo = themePrefsRepo,
          currentUserId = currentUserId,
          userDisplayName = userDisplayName,
          userEmail = userEmail,
          appContext = app.applicationContext
        )
      }
    }
  )

  HekimaAppShell(
    controller = viewModel,
    userDisplayName = userDisplayName,
    userEmail = userEmail,
    currentThemeMode = currentThemeMode,
    openWatchlistMatches = openWatchlistMatches,
    onConsumeWatchlistDeepLink = onConsumeWatchlistDeepLink,
    onRequestSignIn = {}
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HekimaAppShell(
  controller: HekimaDashboardController,
  userDisplayName: String,
  userEmail: String?,
  currentThemeMode: ThemeMode,
  openWatchlistMatches: Boolean,
  onConsumeWatchlistDeepLink: () -> Unit,
  onRequestSignIn: (AuthPromptReason) -> Unit
) {
  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route ?: HekimaDestination.OVERVIEW.route
  val drawerState = rememberDrawerState(DrawerValue.Closed)
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  val statusBanner by controller.statusBanner.collectAsStateWithLifecycle()
  LaunchedEffect(statusBanner) {
    val msg = statusBanner
    if (msg != null) {
      snackbarHostState.showSnackbar(msg)
      controller.clearStatusBanner()
    }
  }

  val activeListings by controller.activeListings.collectAsStateWithLifecycle()
  val filteredListings by controller.filteredListings.collectAsStateWithLifecycle()
  val filterState by controller.filterState.collectAsStateWithLifecycle()
  val dashboardSummary by controller.dashboardSummary.collectAsStateWithLifecycle()
  val pricingSpec by controller.pricingSpec.collectAsStateWithLifecycle()
  val pricingEstimate by controller.pricingEstimate.collectAsStateWithLifecycle()
  val landedCostInput by controller.landedCostInput.collectAsStateWithLifecycle()
  val landedCostCalculation by controller.landedCostCalculation.collectAsStateWithLifecycle()
  val savedAnalyses by controller.marketAnalyses.collectAsStateWithLifecycle()
  val depreciationTargets by controller.depreciationTargets.collectAsStateWithLifecycle()
  val depreciationAnalyses by controller.depreciationAnalyses.collectAsStateWithLifecycle()
  val topRetention by controller.topRetentionVehicles.collectAsStateWithLifecycle()
  val comparisonVerdict by controller.comparisonVerdict.collectAsStateWithLifecycle()
  val savedComparisons by controller.comparisons.collectAsStateWithLifecycle()
  val insightsAndAnomalies by controller.insightsAndAnomalies.collectAsStateWithLifecycle()
  val savedVehiclesState by controller.savedVehiclesState.collectAsStateWithLifecycle()
  val includeSeedData by controller.includeSeedData.collectAsStateWithLifecycle()
  val dataSources by controller.dataSources.collectAsStateWithLifecycle()
  val importResult by controller.importResult.collectAsStateWithLifecycle()
  val isImporting by controller.isImporting.collectAsStateWithLifecycle()
  val watchlistMatches by controller.watchlistMatches.collectAsStateWithLifecycle()
  val matchedListingIds by controller.matchedListingIds.collectAsStateWithLifecycle()
  val lastImportWatchlistAlert by controller.lastImportWatchlistAlert.collectAsStateWithLifecycle()
  val userProfile by controller.userProfile.collectAsStateWithLifecycle()
  val exchangeRate by controller.exchangeRate.collectAsStateWithLifecycle()
  val isRefreshingFx by controller.isRefreshingFx.collectAsStateWithLifecycle()
  val marketSyncStatus by controller.marketSyncStatus.collectAsStateWithLifecycle()
  val accountDeletionState by controller.accountDeletionState.collectAsStateWithLifecycle()
  val aiAnalyzerState by controller.aiAnalyzerState.collectAsStateWithLifecycle()
  val inspectionDraft by controller.inspectionDraft.collectAsStateWithLifecycle()
  val savedInspections by controller.savedInspections.collectAsStateWithLifecycle()
  val feedSubscriptions by controller.feedSubscriptions.collectAsStateWithLifecycle()
  val watchlists by controller.watchlists.collectAsStateWithLifecycle()
  val notificationPreferences by controller.notificationPreferences.collectAsStateWithLifecycle()
  val navContext = LocalContext.current

  val availableMakes = remember(activeListings) {
    (KenyanMarketSeed.makes.map { it.name } + activeListings.map { it.make }).distinct().sorted()
  }

  fun modelsFor(make: String): List<String> {
    val fromSeed = KenyanMarketSeed.models.filter { it.make.equals(make, ignoreCase = true) }.map { it.name }
    val fromActive = activeListings.filter { it.make.equals(make, ignoreCase = true) }.map { it.model }
    return (fromSeed + fromActive).distinct().sorted()
  }

  fun navigateTo(route: String) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  LaunchedEffect(openWatchlistMatches) {
    if (openWatchlistMatches) {
      controller.updateFilter { it.copy(onlyWatchlistMatches = true) }
      navigateTo(HekimaDestination.EXPLORER.route)
      onConsumeWatchlistDeepLink()
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isWideScreen = maxWidth >= 600.dp

    ModalNavigationDrawer(
      drawerState = drawerState,
      gesturesEnabled = !isWideScreen,
      drawerContent = {
        ModalDrawerSheet {
          Text(
            text = "Hekima Auto Intelligence",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
          )
          HekimaDestination.entries.forEach { dest ->
            NavigationDrawerItem(
              icon = { Icon(dest.icon, contentDescription = dest.label) },
              label = { Text(dest.label) },
              selected = currentRoute == dest.route,
              onClick = {
                scope.launch { drawerState.close() }
                navigateTo(dest.route)
              },
              modifier = Modifier
                .padding(horizontal = 12.dp)
                .testTag("drawer_${dest.route}")
            )
          }
        }
      }
    ) {
      Scaffold(
        topBar = {
          TopAppBar(
            title = {
              val destTitle = HekimaDestination.entries.firstOrNull { it.route == currentRoute }?.label ?: "Hekima Auto"
              Text(destTitle, style = MaterialTheme.typography.titleLarge)
            },
            navigationIcon = {
              if (!isWideScreen) {
                IconButton(
                  onClick = { scope.launch { drawerState.open() } },
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("open_nav_drawer_button")
                ) {
                  Icon(Icons.Filled.Menu, contentDescription = "Open navigation menu")
                }
              }
            },
            actions = {
              if (controller.isGuestMode) {
                FilledTonalButton(
                  onClick = {
                    onRequestSignIn(
                      AuthPromptReason(
                        title = "Sign In with Google",
                        message = "Sync saved Kenyan vehicles, KES valuation reports, and CSV/Excel dealership imports across devices."
                      )
                    )
                  },
                  modifier = Modifier
                    .padding(end = 8.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("topbar_sign_in_button")
                ) {
                  Text("Sign In")
                }
              }
            },
            colors = TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.background
            )
          )
        },
        bottomBar = {
          if (!isWideScreen) {
            NavigationBar(windowInsets = WindowInsets.navigationBars) {
              HekimaDestination.entries.filter { it.inBottomBar }.forEach { dest ->
                NavigationBarItem(
                  selected = currentRoute == dest.route,
                  onClick = { navigateTo(dest.route) },
                  icon = { Icon(dest.icon, contentDescription = dest.label) },
                  label = { Text(dest.label) },
                  modifier = Modifier.testTag("bottom_nav_${dest.route}")
                )
              }
            }
          }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
      ) { innerPadding ->
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        ) {
          if (isWideScreen) {
            NavigationRail(modifier = Modifier.fillMaxHeight()) {
              HekimaDestination.entries.forEach { dest ->
                NavigationRailItem(
                  selected = currentRoute == dest.route,
                  onClick = { navigateTo(dest.route) },
                  icon = { Icon(dest.icon, contentDescription = dest.label) },
                  label = { Text(dest.label) },
                  modifier = Modifier.testTag("rail_nav_${dest.route}")
                )
              }
            }
          }

          val contentModifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .widthIn(max = 960.dp)

          NavHost(
            navController = navController,
            startDestination = HekimaDestination.OVERVIEW.route,
            modifier = contentModifier
          ) {
            composable(HekimaDestination.OVERVIEW.route) {
              OverviewScreen(
                summary = dashboardSummary,
                filterState = filterState,
                availableMakes = availableMakes,
                watchlistMatches = watchlistMatches,
                marketSyncStatus = marketSyncStatus,
                exchangeRate = exchangeRate,
                isRefreshingFx = isRefreshingFx,
                onRefreshLiveData = { controller.refreshAllLiveData(navContext) },
                onBrowseWatchlistMatches = {
                  controller.updateFilter { it.copy(onlyWatchlistMatches = true) }
                  navigateTo(HekimaDestination.EXPLORER.route)
                },
                onUpdateFilter = controller::updateFilter,
                onResetFilters = controller::resetFilters,
                onNavigateModule = { route -> navigateTo(route) },
                isGuestMode = controller.isGuestMode,
                onRequestSignIn = {
                  onRequestSignIn(
                    AuthPromptReason(
                      title = "Sign In with Google",
                      message = "Sync saved Kenyan vehicles, KES valuation reports, and CSV/Excel dealership imports across devices."
                    )
                  )
                }
              )
            }
            composable(HekimaDestination.PRICING.route) {
              val makeModels = modelsFor(pricingSpec.make)
              LaunchedEffect(pricingSpec.make) {
                if (pricingSpec.model !in makeModels && makeModels.isNotEmpty()) {
                  controller.updatePricingSpec { it.copy(model = makeModels.first()) }
                }
              }
              PricingScreen(
                spec = pricingSpec,
                estimate = pricingEstimate,
                availableMakes = availableMakes,
                modelsForMake = makeModels,
                savedAnalyses = savedAnalyses,
                onUpdateSpec = controller::updatePricingSpec,
                onSaveAnalysis = controller::savePricingAnalysis,
                onSaveComparable = controller::toggleSaveVehicle
              )
            }
            composable(HekimaDestination.AI_ANALYZER.route) {
              AiAuctionAnalyzerScreen(
                uiState = aiAnalyzerState,
                onAnalyze = controller::analyzeAuctionListing,
                onClearAnalysis = controller::clearAiAnalysis,
                onSendToYardInspection = { analysis ->
                  controller.prefillInspectionFromAi(analysis)
                  navigateTo(HekimaDestination.YARD_INSPECTION.route)
                },
                onCheckLandedCost = { analysis ->
                  controller.updateLandedCostInput {
                    it.copy(
                      make = analysis.make,
                      model = analysis.model,
                      year = analysis.year.coerceIn(2012, 2025)
                    )
                  }
                  navigateTo(HekimaDestination.LANDED_COST.route)
                }
              )
            }
            composable(HekimaDestination.YARD_INSPECTION.route) {
              YardInspectionScreen(
                draft = inspectionDraft,
                savedInspections = savedInspections,
                availableListings = activeListings,
                onPrefillFromListing = controller::prefillInspectionFromListing,
                onUpdateHeader = controller::updateInspectionHeader,
                onUpdateItem = controller::updateInspectionItem,
                onResetDraft = controller::resetInspectionDraft,
                onSaveInspection = controller::saveYardInspection,
                onDeleteInspection = controller::deleteYardInspection,
                isGuestMode = controller.isGuestMode,
                onRequestSignIn = {
                  onRequestSignIn(
                    AuthPromptReason(
                      title = "Sign In to Save Yard Inspections",
                      message = "Store physical car-yard scorecards, repair deductions, and adjusted KES offers to your Google account."
                    )
                  )
                }
              )
            }
            composable(HekimaDestination.LANDED_COST.route) {
              val makeModels = modelsFor(landedCostInput.make)
              LaunchedEffect(landedCostInput.make) {
                if (landedCostInput.model !in makeModels && makeModels.isNotEmpty()) {
                  controller.updateLandedCostInput { it.copy(model = makeModels.first()) }
                }
              }
              LandedCostScreen(
                input = landedCostInput,
                calculation = landedCostCalculation,
                availableMakes = availableMakes,
                modelsForMake = makeModels,
                onUpdateInput = controller::updateLandedCostInput,
                onResetOverrides = controller::resetLandedCostOverrides,
                exchangeRate = exchangeRate,
                isRefreshingFx = isRefreshingFx,
                onRefreshFx = { controller.refreshExchangeRates(forceApplyToLandedCost = true) }
              )
            }
            composable(HekimaDestination.DEPRECIATION.route) {
              DepreciationScreen(
                targets = depreciationTargets,
                analyses = depreciationAnalyses,
                topRetention = topRetention,
                availableMakes = availableMakes,
                modelsForMake = ::modelsFor,
                onAddTarget = controller::addDepreciationTarget,
                onRemoveTarget = controller::removeDepreciationTarget
              )
            }
            composable(HekimaDestination.COMPARE.route) {
              CompareScreen(
                verdict = comparisonVerdict,
                allListings = activeListings,
                savedComparisons = savedComparisons,
                onToggleVehicle = controller::toggleCompareVehicle,
                onSaveComparison = controller::saveCurrentComparison,
                onLoadComparison = controller::loadComparisonSet,
                onDeleteComparison = controller::deleteComparison
              )
            }
            composable(HekimaDestination.INSIGHTS.route) {
              InsightsScreen(
                insights = insightsAndAnomalies.first,
                anomalies = insightsAndAnomalies.second,
                filterState = filterState,
                availableMakes = availableMakes,
                onUpdateFilter = controller::updateFilter,
                onResetFilters = controller::resetFilters
              )
            }
            composable(HekimaDestination.EXPLORER.route) {
              val compareSet = comparisonVerdict.metrics.map { it.vehicle.id }.toSet()
              ExplorerScreen(
                listings = filteredListings,
                savedState = savedVehiclesState,
                filterState = filterState,
                availableMakes = availableMakes,
                compareIds = compareSet,
                watchlistMatches = watchlistMatches,
                matchedListingIds = matchedListingIds,
                lastImportWatchlistAlert = lastImportWatchlistAlert,
                marketSyncStatus = marketSyncStatus,
                exchangeRate = exchangeRate,
                isRefreshingFx = isRefreshingFx,
                currentUserId = controller.currentUserIdOrNull,
                defaultSellerName = userProfile?.displayName?.ifBlank { userDisplayName } ?: userDisplayName,
                availableModelsForMake = ::modelsFor,
                onSaveWatchlist = controller::saveWatchlist,
                onToggleWatchlist = controller::toggleWatchlistActive,
                onDeleteWatchlist = controller::deleteWatchlist,
                onClearWatchlistAlert = controller::clearWatchlistAlert,
                onPublishLiveYardListing = controller::publishLiveYardListing,
                onDeleteLiveYardListing = controller::deleteLiveYardListing,
                onRefreshLiveData = { controller.refreshAllLiveData(navContext) },
                onManageSourcesClick = { navigateTo(HekimaDestination.DATA.route) },
                onUpdateFilter = controller::updateFilter,
                onResetFilters = controller::resetFilters,
                onToggleSave = controller::toggleSaveVehicle,
                onAnalyzePrice = { listing ->
                  controller.prefillPricingFromListing(listing)
                  navigateTo(HekimaDestination.PRICING.route)
                },
                onToggleCompare = controller::toggleCompareVehicle,
                onInspectListing = { listing ->
                  controller.prefillInspectionFromListing(listing)
                  navigateTo(HekimaDestination.YARD_INSPECTION.route)
                },
                onOpenNotificationPrefs = { navigateTo(HekimaDestination.NOTIFICATIONS.route) },
                isGuestMode = controller.isGuestMode,
                onRequestSignIn = {
                  onRequestSignIn(
                    AuthPromptReason(
                      title = "Sign In to Sync Cloud Garage & Live Yard",
                      message = "Save favorite Kenyan listings, set target-price alerts, and publish live yard units."
                    )
                  )
                }
              )
            }
            composable(HekimaDestination.DATA.route) {
              DataManagementScreen(
                includeSeedData = includeSeedData,
                totalActiveCount = activeListings.size,
                dataSources = dataSources,
                lastImportResult = importResult,
                isImporting = isImporting,
                onToggleSeedData = controller::toggleIncludeSeedData,
                onImportUri = controller::importSpreadsheet,
                onImportSampleCsv = controller::importSampleCsvFeed,
                onImportFeedUrl = controller::importFromFeedUrl,
                lastImportWatchlistAlert = lastImportWatchlistAlert,
                onViewWatchlistMatches = {
                  controller.updateFilter { it.copy(onlyWatchlistMatches = true) }
                  navigateTo(HekimaDestination.EXPLORER.route)
                },
                onClearWatchlistAlert = controller::clearWatchlistAlert,
                onToggleSourceActive = controller::toggleDataSource,
                onDeleteSource = controller::deleteDataSource,
                feedSubscriptions = feedSubscriptions,
                onSaveFeedSubscription = controller::saveFeedSubscription,
                onToggleSubscriptionAutoSync = controller::toggleFeedSubscriptionAutoSync,
                onToggleSubscriptionAlerts = controller::toggleFeedSubscriptionAlerts,
                onDeleteFeedSubscription = controller::deleteFeedSubscription,
                onSyncSubscribedFeedsNow = controller::syncSubscribedFeedsNow,
                isGuestMode = controller.isGuestMode,
                onRequestSignIn = {
                  onRequestSignIn(
                    AuthPromptReason(
                      title = "Sign In to Import Dealership Files & Feeds",
                      message = "Upload Kenyan CSV/Excel files or sync live URL feeds with automatic KES standardization and watchlist alerts."
                    )
                  )
                }
              )
            }
            composable(HekimaDestination.NOTIFICATIONS.route) {
              NotificationPreferencesScreen(
                preferences = notificationPreferences,
                watchlists = watchlists,
                watchlistMatches = watchlistMatches,
                feedSubscriptions = feedSubscriptions,
                isGuestMode = controller.isGuestMode,
                onToggleMasterPush = controller::setPushAlertsEnabled,
                onSelectFrequency = controller::setPriceDropAlertFrequency,
                onToggleWatchlistPush = controller::toggleWatchlistPushAlerts,
                onOpenExplorerWatchlists = { navigateTo(HekimaDestination.EXPLORER.route) },
                onOpenDataFeeds = { navigateTo(HekimaDestination.DATA.route) },
                onRequestSignIn = {
                  onRequestSignIn(
                    AuthPromptReason(
                      title = "Sign In for Watchlist Push Alerts",
                      message = "Connect your Google account to customize per-watchlist push alerts and background deal notifications."
                    )
                  )
                }
              )
            }
            composable(HekimaDestination.SETTINGS.route) {
              val savedCount = (savedVehiclesState as? UiState.Success)?.data?.size ?: 0
              SettingsScreen(
                userProfile = userProfile,
                userEmail = userEmail,
                userDisplayName = userDisplayName,
                themeMode = currentThemeMode,
                totalActiveListings = activeListings.size,
                savedVehiclesCount = savedCount,
                marketSyncStatus = marketSyncStatus,
                exchangeRate = exchangeRate,
                notificationPreferences = notificationPreferences,
                activeWatchlistAlertCount = watchlists.count { it.isActive && it.pushAlertsEnabled },
                accountDeletionState = accountDeletionState,
                onSelectTheme = controller::setThemeMode,
                onOpenNotificationPreferences = { navigateTo(HekimaDestination.NOTIFICATIONS.route) },
                onRefreshLiveData = { controller.refreshAllLiveData(navContext) },
                onDeleteAccount = controller::deleteAccountAndAllData,
                onReauthenticateAndRetryDelete = controller::reauthenticateAndRetryDelete,
                onDismissAccountDeletionState = controller::dismissAccountDeletionState,
                isGuestMode = controller.isGuestMode,
                onRequestSignIn = {
                  onRequestSignIn(
                    AuthPromptReason(
                      title = "Sign In with Google",
                      message = "Connect your Google account to back up saved vehicles, comparisons, valuations, and custom datasets."
                    )
                  )
                }
              )
            }
          }
        }
      }
    }
  }
}
