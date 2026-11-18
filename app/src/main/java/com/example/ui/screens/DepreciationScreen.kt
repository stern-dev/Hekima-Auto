package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.analytics.KenyanMarketAnalyticsEngine
import com.example.domain.model.ModelDepreciationSeries
import com.example.domain.model.RetentionLeaderItem
import com.example.domain.model.VehicleDepreciationAnalysis
import com.example.ui.components.DepreciationCurveChart
import com.example.ui.components.DropdownSelectorField
import com.example.ui.components.HekimaCard
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.viewmodel.DepreciationTarget

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DepreciationScreen(
  targets: List<DepreciationTarget>,
  analyses: List<VehicleDepreciationAnalysis>,
  topRetention: List<RetentionLeaderItem>,
  availableMakes: List<String>,
  modelsForMake: (String) -> List<String>,
  onAddTarget: (String, String, Int) -> Unit,
  onRemoveTarget: (DepreciationTarget) -> Unit,
  modifier: Modifier = Modifier
) {
  val telemetry = LocalTelemetryColors.current
  var pickMake by remember { mutableStateOf("Toyota") }
  val makeModels = modelsForMake(pickMake)
  var pickModel by remember(pickMake, makeModels) { mutableStateOf(makeModels.firstOrNull() ?: "Axio") }
  var pickYear by remember { mutableStateOf("2018") }
  val yearOptions = (2013..2022).reversed().map { it.toString() }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Spacer(Modifier.height(4.dp))
      Text(
        text = "Depreciation & Resale Value",
        style = MaterialTheme.typography.headlineMedium
      )
      Text(
        text = "Compare observed listing baselines against 5-year modeled resale projections.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    // Add / Swap Target Selector
    item {
      HekimaCard(
        title = "Tracked Vehicles (Up to 4)",
        subtitle = "Preloaded with Kenyan 2018 market benchmarks"
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            targets.forEach { target ->
              InputChip(
                selected = true,
                onClick = { onRemoveTarget(target) },
                label = { Text("${target.make} ${target.model} ${target.year}") },
                trailingIcon = {
                  if (targets.size > 1) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                  }
                },
                modifier = Modifier.minimumInteractiveComponentSize()
              )
            }
          }

          if (targets.size < 4) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              DropdownSelectorField(
                label = "Make",
                options = availableMakes,
                selected = pickMake,
                onSelect = { pickMake = it },
                modifier = Modifier.weight(1f)
              )
              DropdownSelectorField(
                label = "Model",
                options = makeModels.ifEmpty { listOf(pickModel) },
                selected = pickModel,
                onSelect = { pickModel = it },
                modifier = Modifier.weight(1f)
              )
            }
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              DropdownSelectorField(
                label = "Year",
                options = yearOptions,
                selected = pickYear,
                onSelect = { pickYear = it },
                modifier = Modifier.weight(1f)
              )
              Button(
                onClick = {
                  onAddTarget(pickMake, pickModel, pickYear.toIntOrNull() ?: 2018)
                },
                modifier = Modifier
                  .height(54.dp)
                  .minimumInteractiveComponentSize()
                  .testTag("add_depreciation_target_button")
              ) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Curve")
              }
            }
          }
        }
      }
    }

    // Depreciation Curve Chart
    item {
      HekimaCard(
        title = "Observed Value vs. 5-Year Projection",
        subtitle = "Solid markers show observed dataset averages; dashed lines show modeled resale forecast"
      ) {
        DepreciationCurveChart(
          seriesList = analyses.map {
            ModelDepreciationSeries(title = it.title, points = it.curvePoints)
          }
        )
      }
    }

    // Per-Vehicle Breakdown Cards
    items(analyses, key = { it.key }) { item ->
      HekimaCard(
        title = item.title,
        subtitle = if (item.hasSufficientObservationData) {
          "Backed by ${item.observedSampleCount} dataset observations"
        } else {
          "Limited samples (${item.observedSampleCount}); supplemented by segment model"
        }
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            MetricMini("Est. Original", KenyanMarketAnalyticsEngine.formatCompactMillions(item.estimatedOriginalValueKes))
            MetricMini("Current Market", KenyanMarketAnalyticsEngine.formatCompactMillions(item.currentMarketPriceKes))
            MetricMini("Value Retained", "${String.format("%.1f", item.valueRetainedPct)}%")
          }
          HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = "Avg Annual Depreciation",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = "${String.format("%.1f", item.averageAnnualDepreciationPct)}%/year",
              style = MaterialTheme.typography.labelMedium,
              color = telemetry.warning
            )
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = "5-Year Projected Resale",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = KenyanMarketAnalyticsEngine.formatKes(item.fiveYearProjectedKes),
              style = MaterialTheme.typography.labelLarge,
              color = telemetry.positive
            )
          }
        }
      }
    }

    // Top 10 Value Retention Leaderboard
    item {
      HekimaCard(
        title = "Top 10 Value-Retention Vehicles",
        subtitle = "Strongest estimated 5-year resale retention in Kenya",
        trailingContent = {
          Icon(Icons.Filled.EmojiEvents, null, tint = telemetry.positive)
        }
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          topRetention.forEach { leader ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                  color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                  shape = RoundedCornerShape(8.dp)
                ) {
                  Text(
                    text = "#${leader.rank}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                  )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                  Text("${leader.make} ${leader.model}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                  Text(
                    text = "${leader.sampleCount} samples • ${String.format("%.1f", leader.annualDepreciationPct)}%/yr dep",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
              Column(horizontalAlignment = Alignment.End) {
                Text(
                  text = "${String.format("%.1f", leader.valueRetainedPct)}%",
                  style = MaterialTheme.typography.labelLarge,
                  color = telemetry.positive
                )
                Text(
                  text = "KES ${KenyanMarketAnalyticsEngine.formatCompactMillions(leader.currentAvgKes)}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }
      }
    }

    item { Spacer(Modifier.height(16.dp)) }
  }
}

@Composable
private fun MetricMini(label: String, value: String) {
  Column {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
  }
}
