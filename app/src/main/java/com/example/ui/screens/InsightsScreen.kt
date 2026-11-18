package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.analytics.KenyanMarketAnalyticsEngine
import com.example.domain.model.FilterState
import com.example.domain.model.MarketInsight
import com.example.domain.model.PriceAnomalyListing
import com.example.ui.components.EmptyAnalyticsState
import com.example.ui.components.FilterStripBar
import com.example.ui.components.HekimaCard
import com.example.ui.theme.LocalTelemetryColors
import kotlin.math.roundToInt

@Composable
fun InsightsScreen(
  insights: List<MarketInsight>,
  anomalies: List<PriceAnomalyListing>,
  filterState: FilterState,
  availableMakes: List<String>,
  onUpdateFilter: ((FilterState) -> FilterState) -> Unit,
  onResetFilters: () -> Unit,
  modifier: Modifier = Modifier
) {
  val telemetry = LocalTelemetryColors.current

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Spacer(Modifier.height(4.dp))
      Text("Market Insights Engine", style = MaterialTheme.typography.headlineMedium)
      Text(
        text = "Statistical findings & price anomalies computed directly from active Kenyan listings.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    item {
      HekimaCard(title = "Insight Filters") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          FilterStripBar(
            filterState = filterState,
            availableMakes = availableMakes,
            onUpdateFilter = onUpdateFilter,
            onReset = onResetFilters
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Max Mileage", style = MaterialTheme.typography.labelLarge)
            Text("%,d km".format(filterState.maxMileageKm), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
          }
          Slider(
            value = filterState.maxMileageKm.toFloat(),
            onValueChange = { v ->
              val rounded = ((v / 10000f).roundToInt() * 10000).coerceIn(30000, 250000)
              onUpdateFilter { it.copy(maxMileageKm = rounded) }
            },
            valueRange = 30000f..250000f
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Max Price Cap", style = MaterialTheme.typography.labelLarge)
            Text(
              "KES ${KenyanMarketAnalyticsEngine.formatCompactMillions(filterState.maxPriceKes)}",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary
            )
          }
          Slider(
            value = (filterState.maxPriceKes / 1_000_000f).coerceIn(1f, 25f),
            onValueChange = { m ->
              val cap = (m.roundToInt() * 1_000_000L).coerceIn(1_000_000L, 25_000_000L)
              onUpdateFilter { it.copy(maxPriceKes = cap) }
            },
            valueRange = 1f..25f
          )
        }
      }
    }

    if (insights.isEmpty()) {
      item {
        EmptyAnalyticsState(
          title = "Insufficient Filtered Listings",
          message = "Widen your brand, mileage, or price filters so at least 3 vehicles can be analyzed.",
          actionLabel = "Reset Filters",
          onAction = onResetFilters
        )
      }
    } else {
      items(insights, key = { it.id }) { insight ->
        val badgeColor = if (insight.isPositiveSignal) telemetry.positive else telemetry.warning
        HekimaCard(
          title = insight.title,
          subtitle = insight.category.label,
          trailingContent = {
            Surface(
              color = badgeColor.copy(alpha = 0.15f),
              shape = RoundedCornerShape(8.dp)
            ) {
              Text(
                text = insight.metricHighlight,
                style = MaterialTheme.typography.labelMedium,
                color = badgeColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }
        ) {
          Row(verticalAlignment = Alignment.Top) {
            Icon(
              imageVector = Icons.Outlined.Lightbulb,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
              text = insight.finding,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface
            )
          }
        }
      }

      if (anomalies.isNotEmpty()) {
        item {
          HekimaCard(
            title = "Detected Price Outliers",
            subtitle = "Vehicles priced ≥12% below or above comparable peer listings"
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              anomalies.forEach { anomaly ->
                val isDeal = anomaly.deviationPct < 0
                val tint = if (isDeal) telemetry.positive else telemetry.negative
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(10.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(anomaly.listing.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                      text = "${anomaly.listing.location} • Peer avg ${KenyanMarketAnalyticsEngine.formatCompactMillions(anomaly.peerAverageKes)}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                      text = anomaly.label,
                      style = MaterialTheme.typography.labelSmall,
                      color = tint
                    )
                  }
                  Column(horizontalAlignment = Alignment.End) {
                    Text(
                      text = KenyanMarketAnalyticsEngine.formatKes(anomaly.listing.askingPriceKes),
                      style = MaterialTheme.typography.labelLarge
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(
                        imageVector = if (isDeal) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(14.dp)
                      )
                      Spacer(Modifier.width(3.dp))
                      Text(
                        text = "${String.format("%+.1f", anomaly.deviationPct)}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = tint
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    item { Spacer(Modifier.height(16.dp)) }
  }
}
