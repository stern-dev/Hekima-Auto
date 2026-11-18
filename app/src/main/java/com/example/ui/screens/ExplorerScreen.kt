package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.analytics.KenyanMarketAnalyticsEngine
import com.example.domain.model.ExchangeRateSnapshot
import com.example.domain.model.FilterState
import com.example.domain.model.ImportAgeRuleStatus
import com.example.domain.model.ListingProvenance
import com.example.domain.model.MarketSyncStatus
import com.example.domain.model.OdometerSanityReport
import com.example.domain.model.PriceWatchlist
import com.example.domain.model.SavedVehicle
import com.example.domain.model.UiState
import com.example.domain.model.VehicleListing
import com.example.domain.model.WatchlistMatch
import com.example.ui.components.CreateWatchlistDialog
import com.example.ui.components.EmptyAnalyticsState
import com.example.ui.components.FilterStripBar
import com.example.ui.components.HekimaCard
import com.example.ui.components.MarketSyncStatusBar
import com.example.ui.components.OdometerAuditDialog
import com.example.ui.components.OdometerSanityBadge
import com.example.ui.components.PostLiveYardListingDialog
import com.example.ui.components.ProvenanceBadge
import com.example.ui.components.WatchlistSectionCard
import com.example.ui.theme.LocalTelemetryColors

private enum class ExplorerSort(val label: String) {
  PRICE_ASC("Price ↑"),
  PRICE_DESC("Price ↓"),
  MILEAGE_ASC("Low km"),
  DEMAND_DESC("Demand")
}

@Composable
fun ExplorerScreen(
  listings: List<VehicleListing>,
  savedState: UiState<List<SavedVehicle>>,
  filterState: FilterState,
  availableMakes: List<String>,
  compareIds: Set<String>,
  watchlistMatches: List<WatchlistMatch> = emptyList(),
  matchedListingIds: Set<String> = emptySet(),
  lastImportWatchlistAlert: String? = null,
  marketSyncStatus: MarketSyncStatus = MarketSyncStatus(),
  exchangeRate: ExchangeRateSnapshot = ExchangeRateSnapshot(),
  isRefreshingFx: Boolean = false,
  currentUserId: String? = null,
  defaultSellerName: String = "",
  availableModelsForMake: (String) -> List<String> = { emptyList() },
  onSaveWatchlist: (String, String, Int, Int, Long, String) -> Unit = { _, _, _, _, _, _ -> },
  onToggleWatchlist: (PriceWatchlist, Boolean) -> Unit = { _, _ -> },
  onDeleteWatchlist: (String) -> Unit = {},
  onClearWatchlistAlert: () -> Unit = {},
  onPublishLiveYardListing: (
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
  ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
  onDeleteLiveYardListing: (String) -> Unit = {},
  onRefreshLiveData: () -> Unit = {},
  onManageSourcesClick: () -> Unit = {},
  onUpdateFilter: ((FilterState) -> FilterState) -> Unit,
  onResetFilters: () -> Unit,
  onToggleSave: (VehicleListing) -> Unit,
  onAnalyzePrice: (VehicleListing) -> Unit,
  onToggleCompare: (String) -> Unit,
  onInspectListing: (VehicleListing) -> Unit = {},
  onOpenNotificationPrefs: () -> Unit = {},
  isGuestMode: Boolean = false,
  onRequestSignIn: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val telemetry = LocalTelemetryColors.current
  var activeTab by remember { mutableIntStateOf(0) }
  var sortMode by remember { mutableStateOf(ExplorerSort.DEMAND_DESC) }
  var showCreateWatchlistDialog by remember { mutableStateOf(false) }
  var showPostYardListingDialog by remember { mutableStateOf(false) }
  var auditedListing by remember { mutableStateOf<Pair<String, OdometerSanityReport>?>(null) }

  val savedList = (savedState as? UiState.Success)?.data ?: emptyList()
  val savedListingIds = savedList.map { it.listingId }.toSet()

  val sortedListings = remember(listings, sortMode) {
    when (sortMode) {
      ExplorerSort.PRICE_ASC -> listings.sortedBy { it.askingPriceKes }
      ExplorerSort.PRICE_DESC -> listings.sortedByDescending { it.askingPriceKes }
      ExplorerSort.MILEAGE_ASC -> listings.sortedBy { it.mileageKm }
      ExplorerSort.DEMAND_DESC -> listings.sortedByDescending { it.demandScore }
    }
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    item {
      Spacer(Modifier.height(4.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Vehicle Explorer", style = MaterialTheme.typography.headlineMedium)
          Text(
            text = "Stream live yard posts, synced feeds, and verified Kenyan market stock.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(
          onClick = {
            if (isGuestMode) {
              onRequestSignIn()
            } else {
              showPostYardListingDialog = true
            }
          },
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("post_yard_listing_button")
        ) {
          Icon(Icons.Filled.AddBusiness, null, Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Post Unit")
        }
      }
    }

    item {
      MarketSyncStatusBar(
        syncStatus = marketSyncStatus,
        exchangeRate = exchangeRate,
        isRefreshingFx = isRefreshingFx,
        onRefreshClick = onRefreshLiveData,
        onManageSourcesClick = onManageSourcesClick
      )
    }

    item {
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
          selected = activeTab == 0,
          onClick = { activeTab = 0 },
          shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
          modifier = Modifier.testTag("explorer_tab_all")
        ) {
          Text("Market (${sortedListings.size})")
        }
        SegmentedButton(
          selected = activeTab == 1,
          onClick = { activeTab = 1 },
          shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
          modifier = Modifier.testTag("explorer_tab_saved")
        ) {
          Text("Saved Garage (${savedList.size})")
        }
      }
    }

    if (activeTab == 0) {
      item {
        OutlinedTextField(
          value = filterState.searchQuery,
          onValueChange = { q -> onUpdateFilter { it.copy(searchQuery = q) } },
          label = { Text("Search make, model, city, or year") },
          leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("explorer_search_input")
        )
      }

      item {
        FilterStripBar(
          filterState = filterState,
          availableMakes = availableMakes,
          onUpdateFilter = onUpdateFilter,
          onReset = onResetFilters
        )
      }

      if (lastImportWatchlistAlert != null) {
        item {
          Surface(
            color = telemetry.positive.copy(alpha = 0.14f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("explorer_watchlist_alert_banner")
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(Icons.Filled.NotificationsActive, null, tint = telemetry.positive, modifier = Modifier.size(18.dp))
                Text(
                  text = lastImportWatchlistAlert,
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurface
                )
              }
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                  onClick = { onUpdateFilter { it.copy(onlyWatchlistMatches = true) } },
                  modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                  Text("Show Matches")
                }
                TextButton(
                  onClick = onClearWatchlistAlert,
                  modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                  Text("Dismiss")
                }
              }
            }
          }
        }
      }

      item {
        WatchlistSectionCard(
          watchlistMatches = watchlistMatches,
          isGuestMode = isGuestMode,
          onCreateWatchlistClick = {
            if (isGuestMode) {
              onRequestSignIn()
            } else {
              showCreateWatchlistDialog = true
            }
          },
          onToggleWatchlist = onToggleWatchlist,
          onDeleteWatchlist = onDeleteWatchlist,
          onBrowseMatchesClick = {
            onUpdateFilter { it.copy(onlyWatchlistMatches = true) }
          },
          onOpenNotificationPrefs = onOpenNotificationPrefs
        )
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          ExplorerSort.entries.forEach { mode ->
            FilterChip(
              selected = sortMode == mode,
              onClick = { sortMode = mode },
              label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
              modifier = Modifier.minimumInteractiveComponentSize()
            )
          }
        }
      }

      if (sortedListings.isEmpty()) {
        item {
          EmptyAnalyticsState(
            title = "No Vehicles Found",
            message = "Try adjusting your search query or filters.",
            actionLabel = "Clear Search & Filters",
            onAction = onResetFilters
          )
        }
      } else {
        items(sortedListings, key = { it.id }) { item ->
          val isSaved = item.id in savedListingIds
          val isCompared = item.id in compareIds
          val isWatchlistMatch = item.id in matchedListingIds
          VehicleListingCard(
            listing = item,
            isSaved = isSaved,
            isCompared = isCompared,
            isWatchlistMatch = isWatchlistMatch,
            isOwnedLiveListing = item.provenance == ListingProvenance.LIVE_YARD &&
              currentUserId != null &&
              item.sellerUserId == currentUserId,
            positiveColor = telemetry.positive,
            warningColor = telemetry.warning,
            negativeColor = telemetry.negative,
            onToggleSave = { onToggleSave(item) },
            onAnalyzePrice = { onAnalyzePrice(item) },
            onToggleCompare = { onToggleCompare(item.id) },
            onInspectListing = { onInspectListing(item) },
            onDeleteOwnedLiveListing = { onDeleteLiveYardListing(item.id) },
            onAuditOdometer = { report -> auditedListing = item.fullName to report }
          )
        }
      }
    } else {
      when (savedState) {
        is UiState.Loading -> {
          item {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
              horizontalArrangement = Arrangement.Center
            ) {
              CircularProgressIndicator()
            }
          }
        }
        is UiState.Error -> {
          item {
            EmptyAnalyticsState(
              title = "Cloud Sync Error",
              message = savedState.message,
              actionLabel = "View Market",
              onAction = { activeTab = 0 }
            )
          }
        }
        is UiState.Success -> {
          if (isGuestMode) {
            item {
              EmptyAnalyticsState(
                title = "Sign in to Sync Your Cloud Garage",
                message = "Sign in with Google to bookmark Kenyan listings and sync your garage across devices.",
                actionLabel = "Sign in with Google",
                onAction = onRequestSignIn
              )
            }
          } else if (savedList.isEmpty()) {
            item {
              EmptyAnalyticsState(
                title = "Your Cloud Garage is Empty",
                message = "Tap the bookmark icon on any vehicle in Market Explorer to sync it here.",
                actionLabel = "Browse Market",
                onAction = { activeTab = 0 }
              )
            }
          } else {
            items(savedList, key = { it.id }) { saved ->
              val asListing = VehicleListing(
                id = saved.listingId,
                make = saved.make,
                model = saved.model,
                year = saved.year,
                askingPriceKes = saved.askingPriceKes,
                mileageKm = saved.mileageKm,
                engineCc = saved.engineCc,
                transmission = saved.transmission,
                fuelType = saved.fuelType,
                condition = saved.condition,
                importStatus = saved.importStatus,
                location = saved.location,
                demandScore = 85,
                sourceId = "saved",
                sourceName = "Cloud Garage"
              )
              VehicleListingCard(
                listing = asListing,
                isSaved = true,
                isCompared = saved.listingId in compareIds,
                isWatchlistMatch = saved.listingId in matchedListingIds,
                positiveColor = telemetry.positive,
                warningColor = telemetry.warning,
                negativeColor = telemetry.negative,
                onToggleSave = { onToggleSave(asListing) },
                onAnalyzePrice = { onAnalyzePrice(asListing) },
                onToggleCompare = { onToggleCompare(saved.listingId) },
                onInspectListing = { onInspectListing(asListing) },
                onAuditOdometer = { report -> auditedListing = asListing.fullName to report }
              )
            }
          }
        }
      }
    }

    item { Spacer(Modifier.height(16.dp)) }
  }

  if (showCreateWatchlistDialog) {
    CreateWatchlistDialog(
      availableMakes = availableMakes,
      modelsForMake = availableModelsForMake,
      onDismiss = { showCreateWatchlistDialog = false },
      onSave = onSaveWatchlist
    )
  }

  if (showPostYardListingDialog) {
    PostLiveYardListingDialog(
      availableMakes = availableMakes,
      modelsForMake = availableModelsForMake,
      defaultSellerName = defaultSellerName,
      onDismiss = { showPostYardListingDialog = false },
      onPublish = onPublishLiveYardListing
    )
  }

  val activeAudit = auditedListing
  if (activeAudit != null) {
    OdometerAuditDialog(
      vehicleName = activeAudit.first,
      report = activeAudit.second,
      onDismiss = { auditedListing = null }
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VehicleListingCard(
  listing: VehicleListing,
  isSaved: Boolean,
  isCompared: Boolean,
  isWatchlistMatch: Boolean = false,
  isOwnedLiveListing: Boolean = false,
  positiveColor: androidx.compose.ui.graphics.Color,
  warningColor: androidx.compose.ui.graphics.Color,
  negativeColor: androidx.compose.ui.graphics.Color,
  onToggleSave: () -> Unit,
  onAnalyzePrice: () -> Unit,
  onToggleCompare: () -> Unit,
  onInspectListing: () -> Unit = {},
  onDeleteOwnedLiveListing: () -> Unit = {},
  onAuditOdometer: (OdometerSanityReport) -> Unit = {}
) {
  val ageRuleStatus = KenyanMarketAnalyticsEngine.evaluateImportAgeRule(listing.year)
  val odoReport = remember(listing.id, listing.year, listing.mileageKm, listing.importStatus) {
    KenyanMarketAnalyticsEngine.evaluateOdometerSanity(listing.year, listing.mileageKm, listing.importStatus)
  }
  val ageBadgeText = when (ageRuleStatus) {
    ImportAgeRuleStatus.ELIGIBLE -> "Import OK"
    ImportAgeRuleStatus.APPROACHING_LIMIT -> "Final Import Yr"
    ImportAgeRuleStatus.OVER_LIMIT -> ">8 Yrs • Local"
  }
  val ageBadgeColor = when (ageRuleStatus) {
    ImportAgeRuleStatus.ELIGIBLE -> positiveColor
    ImportAgeRuleStatus.APPROACHING_LIMIT -> warningColor
    ImportAgeRuleStatus.OVER_LIMIT -> negativeColor
  }

  HekimaCard {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = listing.fullName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            if (isWatchlistMatch) {
              Surface(
                color = positiveColor.copy(alpha = 0.18f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, positiveColor.copy(alpha = 0.45f))
              ) {
                Text(
                  text = "Watchlist Match",
                  style = MaterialTheme.typography.labelSmall,
                  color = positiveColor,
                  fontWeight = FontWeight.SemiBold,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }
            Surface(
              color = ageBadgeColor.copy(alpha = 0.14f),
              shape = RoundedCornerShape(6.dp)
            ) {
              Text(
                text = ageBadgeText,
                style = MaterialTheme.typography.labelSmall,
                color = ageBadgeColor,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
            if (listing.segment.isNotBlank()) {
              Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                shape = RoundedCornerShape(6.dp)
              ) {
                Text(
                  text = listing.segment,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }
            ProvenanceBadge(
              provenance = listing.provenance,
              updatedAtEpochMs = listing.updatedAtEpochMs
            )
            OdometerSanityBadge(
              report = odoReport,
              onClick = { onAuditOdometer(odoReport) }
            )
          }
          Text(
            text = "${"%,d".format(listing.mileageKm)} km • ${listing.engineCc}cc ${listing.fuelType} • ${listing.transmission}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = "${listing.importStatus} • ${listing.location} • ${listing.condition}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (isOwnedLiveListing) {
            IconButton(
              onClick = onDeleteOwnedLiveListing,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("delete_live_listing_button_${listing.id}")
            ) {
              Icon(
                imageVector = Icons.Filled.DeleteOutline,
                contentDescription = "Delete your live yard listing",
                tint = negativeColor
              )
            }
          }
          IconButton(
            onClick = onToggleSave,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("save_vehicle_button_${listing.id}")
          ) {
            Icon(
              imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
              contentDescription = if (isSaved) "Remove from saved" else "Save vehicle",
              tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = KenyanMarketAnalyticsEngine.formatKes(listing.askingPriceKes),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "Demand ${listing.demandScore}/100 • ${listing.sourceName}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          OutlinedButton(
            onClick = onToggleCompare,
            modifier = Modifier.minimumInteractiveComponentSize()
          ) {
            Icon(Icons.AutoMirrored.Filled.CompareArrows, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (isCompared) "In Compare" else "Compare")
          }
          OutlinedButton(
            onClick = onAnalyzePrice,
            modifier = Modifier.minimumInteractiveComponentSize()
          ) {
            Icon(Icons.Filled.PriceCheck, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Price")
          }
          OutlinedButton(
            onClick = onInspectListing,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("inspect_vehicle_button_${listing.id}")
          ) {
            Icon(Icons.AutoMirrored.Filled.FactCheck, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Inspect")
          }
        }
      }
    }
  }
}
