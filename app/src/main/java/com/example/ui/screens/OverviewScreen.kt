package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.analytics.KenyanMarketAnalyticsEngine
import com.example.domain.model.DashboardSummary
import com.example.domain.model.ExchangeRateSnapshot
import com.example.domain.model.FilterState
import com.example.domain.model.MarketSyncStatus
import com.example.domain.model.WatchlistMatch
import com.example.ui.components.BrandPriceBarChart
import com.example.ui.components.DepreciationCurveChart
import com.example.ui.components.EmptyAnalyticsState
import com.example.ui.components.FilterStripBar
import com.example.ui.components.HekimaCard
import com.example.ui.components.KpiMetricTile
import com.example.ui.components.MarketShareDonutChart
import com.example.ui.components.MarketSyncStatusBar
import com.example.ui.components.PriceDistributionChart
import com.example.ui.components.ScatterPlotChart
import com.example.ui.components.SyncStatusIndicatorPill
import com.example.ui.components.resolveVisualSyncState
import com.example.ui.theme.LocalTelemetryColors

@Composable
fun OverviewScreen(
  summary: DashboardSummary,
  filterState: FilterState,
  availableMakes: List<String>,
  watchlistMatches: List<WatchlistMatch> = emptyList(),
  marketSyncStatus: MarketSyncStatus = MarketSyncStatus(),
  exchangeRate: ExchangeRateSnapshot = ExchangeRateSnapshot(),
  isRefreshingFx: Boolean = false,
  onRefreshLiveData: () -> Unit = {},
  onBrowseWatchlistMatches: () -> Unit = {},
  onUpdateFilter: ((FilterState) -> FilterState) -> Unit,
  onResetFilters: () -> Unit,
  onNavigateModule: (String) -> Unit,
  isGuestMode: Boolean = false,
  onRequestSignIn: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val telemetry = LocalTelemetryColors.current
  val visualSyncState = resolveVisualSyncState(marketSyncStatus, isRefreshingFx)

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Spacer(Modifier.height(4.dp))
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(132.dp)
          .clip(RoundedCornerShape(18.dp))
      ) {
        Image(
          painter = painterResource(id = R.drawable.img_hero_telemetry),
          contentDescription = "Kenyan market overview banner",
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop
        )
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.horizontalGradient(
                listOf(Color(0xE6090F1C), Color(0x88090F1C))
              )
            )
        )
        SyncStatusIndicatorPill(
          syncState = visualSyncState,
          onDarkBackdrop = true,
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(12.dp)
            .testTag("hero_sync_status_indicator")
        )
        Column(
          modifier = Modifier
            .align(Alignment.CenterStart)
            .padding(18.dp)
        ) {
          Text(
            text = "Kenyan Market Pulse",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
          )
          Spacer(Modifier.height(4.dp))
          Text(
            text = "${summary.totalVehicles} ${if (marketSyncStatus.isUsingFallbackCatalog) "reference" else "live"} listings • Avg ${KenyanMarketAnalyticsEngine.formatKes(summary.averagePriceKes)}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFCBD5E1)
          )
        }
      }
    }

    item {
      MarketSyncStatusBar(
        syncStatus = marketSyncStatus,
        exchangeRate = exchangeRate,
        isRefreshingFx = isRefreshingFx,
        onRefreshClick = onRefreshLiveData,
        onManageSourcesClick = { onNavigateModule("data") }
      )
    }

    if (isGuestMode) {
      item {
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.VerifiedUser,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Guest Preview • Browsing ${summary.totalVehicles} Kenyan listings",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "Sign in to sync saved vehicles, reports & CSV files",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            FilledTonalButton(
              onClick = onRequestSignIn,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("overview_sign_in_button")
            ) {
              Text("Sign In")
            }
          }
        }
      }
    }

    // Quick-access chips to all specialized modules on compact screens
    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        AssistChip(
          onClick = { onNavigateModule("ai_analyzer") },
          label = { Text("AI Analyzer") },
          leadingIcon = { Icon(Icons.Filled.AutoAwesome, null, Modifier.size(16.dp)) },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("chip_ai_analyzer")
        )
        AssistChip(
          onClick = { onNavigateModule("yard_inspection") },
          label = { Text("Yard Inspect") },
          leadingIcon = { Icon(Icons.AutoMirrored.Filled.FactCheck, null, Modifier.size(16.dp)) },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("chip_yard_inspection")
        )
        AssistChip(
          onClick = { onNavigateModule("landed_cost") },
          label = { Text("Landed Cost") },
          leadingIcon = { Icon(Icons.Filled.Anchor, null, Modifier.size(16.dp)) },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("chip_landed_cost")
        )
        AssistChip(
          onClick = onBrowseWatchlistMatches,
          label = { Text("Watchlists") },
          leadingIcon = { Icon(Icons.Filled.NotificationsActive, null, Modifier.size(16.dp)) },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("chip_watchlists")
        )
        AssistChip(
          onClick = { onNavigateModule("depreciation") },
          label = { Text("Depreciation") },
          leadingIcon = { Icon(Icons.AutoMirrored.Filled.TrendingDown, null, Modifier.size(16.dp)) },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("chip_depreciation")
        )
        AssistChip(
          onClick = { onNavigateModule("insights") },
          label = { Text("Insights") },
          leadingIcon = { Icon(Icons.Outlined.Lightbulb, null, Modifier.size(16.dp)) },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("chip_insights")
        )
        AssistChip(
          onClick = { onNavigateModule("data") },
          label = { Text("Import CSV / Feed") },
          leadingIcon = { Icon(Icons.Outlined.CloudUpload, null, Modifier.size(16.dp)) },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("chip_data")
        )
        AssistChip(
          onClick = { onNavigateModule("settings") },
          label = { Text("Settings") },
          leadingIcon = { Icon(Icons.Outlined.Settings, null, Modifier.size(16.dp)) },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("chip_settings")
        )
      }
    }

    item {
      FilterStripBar(
        filterState = filterState,
        availableMakes = availableMakes,
        onUpdateFilter = onUpdateFilter,
        onReset = onResetFilters
      )
    }

    if (watchlistMatches.isNotEmpty()) {
      val totalMatched = watchlistMatches.sumOf { it.matchingListings.size }
      item {
        Surface(
          color = telemetry.positive.copy(alpha = 0.12f),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.NotificationsActive,
              contentDescription = null,
              tint = telemetry.positive,
              modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = if (isGuestMode) "Preview Watchlists • $totalMatched Deal Match(es)" else "${watchlistMatches.count { it.watchlist.isActive }} Active Watchlist(s) • $totalMatched Match(es)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
              )
              val topMatch = watchlistMatches.firstOrNull { it.bestPriceKes != null }
              Text(
                text = if (topMatch != null && topMatch.bestPriceKes != null) {
                  "Best deal: ${topMatch.watchlist.make} ${topMatch.watchlist.model} at ${KenyanMarketAnalyticsEngine.formatCompactMillions(topMatch.bestPriceKes)}"
                } else {
                  "Tap to manage KES budget alerts in Explorer"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            FilledTonalButton(
              onClick = onBrowseWatchlistMatches,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("overview_inspect_watchlist_button")
            ) {
              Text("Inspect")
            }
          }
        }
      }
    }

    if (summary.totalVehicles == 0) {
      item {
        EmptyAnalyticsState(
          title = "No Listings Match Filters",
          message = "Clear your active filters or enable the Kenyan market seed dataset in Data Management.",
          actionLabel = "Reset Filters",
          onAction = onResetFilters
        )
      }
      return@LazyColumn
    }

    // KPI Grid (7 Core Metrics)
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          KpiMetricTile(
            label = "Vehicles Analyzed",
            value = "${summary.totalVehicles}",
            subValue = "Active Kenyan dataset",
            icon = Icons.Filled.DirectionsCar,
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
          )
          KpiMetricTile(
            label = "Average Price",
            value = "KES ${KenyanMarketAnalyticsEngine.formatCompactMillions(summary.averagePriceKes)}",
            subValue = "Med: ${KenyanMarketAnalyticsEngine.formatCompactMillions(summary.medianPriceKes)}",
            icon = Icons.Filled.Payments,
            accentColor = telemetry.accentCyan,
            modifier = Modifier.weight(1f)
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          KpiMetricTile(
            label = "Avg Depreciation",
            value = "${String.format("%.1f", summary.averageAnnualDepreciationPct)}%/yr",
            subValue = "Annual market curve",
            icon = Icons.AutoMirrored.Filled.TrendingDown,
            accentColor = telemetry.warning,
            modifier = Modifier.weight(1f)
          )
          KpiMetricTile(
            label = "Best Retention",
            value = summary.bestRetentionBrand,
            subValue = "${String.format("%.1f", summary.bestRetentionPct)}% 5y retained",
            icon = Icons.Filled.EmojiEvents,
            accentColor = telemetry.positive,
            modifier = Modifier.weight(1f)
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          KpiMetricTile(
            label = "Highest Avg Brand",
            value = summary.mostExpensiveBrand,
            subValue = "KES ${KenyanMarketAnalyticsEngine.formatCompactMillions(summary.mostExpensiveBrandAvgKes)}",
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
          )
          KpiMetricTile(
            label = "Most Listed",
            value = summary.mostListedVehicle,
            subValue = "${summary.mostListedCount} active units",
            icon = Icons.Filled.LocalOffer,
            accentColor = telemetry.accentCyan,
            modifier = Modifier.weight(1f)
          )
        }
      }
    }

    // 1. Average Vehicle Prices by Brand
    item {
      HekimaCard(
        title = "Average Asking Price by Brand",
        subtitle = "Tap any brand bar to inspect median KES price & 5-year retention"
      ) {
        BrandPriceBarChart(stats = summary.brandPriceStats)
      }
    }

    // 2. Price vs Mileage
    item {
      HekimaCard(
        title = "Price vs. Odometer Mileage",
        subtitle = "KES millions against 1,000 km increments with regression trendline"
      ) {
        ScatterPlotChart(
          points = summary.priceVsMileagePoints,
          xAxisUnit = "k km",
          yAxisLabel = "Price (KES M)"
        )
      }
    }

    // 3. Price vs Vehicle Age
    item {
      HekimaCard(
        title = "Price vs. Vehicle Age",
        subtitle = "Mean Kenyan asking price (KES M) by vehicle age in years"
      ) {
        ScatterPlotChart(
          points = summary.priceVsAgePoints,
          xAxisUnit = "yrs",
          yAxisLabel = "Avg Price (KES M)"
        )
      }
    }

    // 4. Depreciation Curves
    item {
      HekimaCard(
        title = "Benchmark Depreciation Curves",
        subtitle = "Popular 2018 Kenyan models • Observed baseline & 5-year projection",
        trailingContent = {
          Text(
            text = "Explore →",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .clickable { onNavigateModule("depreciation") }
              .padding(6.dp)
          )
        }
      ) {
        DepreciationCurveChart(seriesList = summary.depreciationSeries)
      }
    }

    // 5. Vehicle Price Distribution
    item {
      HekimaCard(
        title = "KES Price Distribution",
        subtitle = "Listing volume across Kenyan market price brackets"
      ) {
        PriceDistributionChart(buckets = summary.priceDistribution)
      }
    }

    // 6. Market Share by Brand
    item {
      HekimaCard(
        title = "Market Share by Brand",
        subtitle = "Listing volume distribution across top Kenyan makes"
      ) {
        MarketShareDonutChart(
          slices = summary.marketShare,
          totalVehicles = summary.totalVehicles
        )
      }
    }

    item { Spacer(Modifier.height(16.dp)) }
  }
}
