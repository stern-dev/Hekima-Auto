package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.analytics.KenyanMarketAnalyticsEngine
import com.example.domain.model.ImportAgeRuleStatus
import com.example.domain.model.KraDutyEstimate
import com.example.domain.model.TcoEstimate
import com.example.ui.theme.LocalTelemetryColors

@Composable
fun KraImportDutyCard(
  kra: KraDutyEstimate,
  modifier: Modifier = Modifier
) {
  val telemetry = LocalTelemetryColors.current
  val statusColor = when (kra.ageRuleStatus) {
    ImportAgeRuleStatus.ELIGIBLE -> telemetry.positive
    ImportAgeRuleStatus.APPROACHING_LIMIT -> telemetry.warning
    ImportAgeRuleStatus.OVER_LIMIT -> telemetry.negative
  }
  val statusIcon = when (kra.ageRuleStatus) {
    ImportAgeRuleStatus.ELIGIBLE -> Icons.Filled.Verified
    ImportAgeRuleStatus.APPROACHING_LIMIT -> Icons.Filled.Warning
    ImportAgeRuleStatus.OVER_LIMIT -> Icons.Filled.Block
  }

  HekimaCard(
    title = "8-Year Rule & KRA Duty Estimator",
    subtitle = "CRSP benchmark & Mombasa landed cost vs. local market",
    modifier = modifier.testTag("kra_duty_card"),
    trailingContent = {
      Icon(
        imageVector = Icons.Filled.Anchor,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary
      )
    }
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      // 8-Year Age Rule Status Banner
      Surface(
        color = statusColor.copy(alpha = 0.14f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("import_age_rule_banner")
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          verticalAlignment = Alignment.Top,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Icon(
            imageVector = statusIcon,
            contentDescription = kra.ageRuleStatus.label,
            tint = statusColor,
            modifier = Modifier.size(20.dp)
          )
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = kra.ageRuleHeadline,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = statusColor
            )
            Text(
              text = kra.ageRuleDetail,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface
            )
          }
        }
      }

      // Mombasa Landed vs Local Asking Price Comparison
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column {
              Text(
                text = "Est. Mombasa Landed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = KenyanMarketAnalyticsEngine.formatKes(kra.mombasaLandedCostKes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (kra.ageRuleStatus == ImportAgeRuleStatus.OVER_LIMIT) {
                  MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                  MaterialTheme.colorScheme.primary
                }
              )
            }
            Column(horizontalAlignment = Alignment.End) {
              Text(
                text = "Local Yard Valuation",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = KenyanMarketAnalyticsEngine.formatKes(kra.localMarketPriceKes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = telemetry.accentCyan
              )
            }
          }
          Text(
            text = kra.comparisonSummary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
          )
        }
      }

      // CRSP & KRA Tax Waterfall
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = "CRSP & KRA Tax Breakdown",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurface
        )
        KraLineRow("Est. New CRSP Benchmark", KenyanMarketAnalyticsEngine.formatKes(kra.crspBenchmarkKes))
        KraLineRow(
          "KRA Depreciated Customs Value (-${kra.kraDepreciationRatePct.toInt()}%)",
          KenyanMarketAnalyticsEngine.formatKes(kra.customsValueKes)
        )
        KraLineRow("Estimated Mombasa CIF", KenyanMarketAnalyticsEngine.formatKes(kra.estimatedCifKes))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        KraLineRow("Import Duty (25%)", KenyanMarketAnalyticsEngine.formatKes(kra.importDutyKes))
        KraLineRow("Excise Duty (${kra.exciseDutyRatePct.toInt()}%)", KenyanMarketAnalyticsEngine.formatKes(kra.exciseDutyKes))
        KraLineRow("VAT (16%)", KenyanMarketAnalyticsEngine.formatKes(kra.vatKes))
        KraLineRow("IDF (2.5%) & RDL (1.5%)", KenyanMarketAnalyticsEngine.formatKes(kra.idfKes + kra.rdlKes))
        KraLineRow(
          "Total KRA Taxes & Levies",
          KenyanMarketAnalyticsEngine.formatKes(kra.totalKraTaxesKes),
          valueColor = telemetry.warning,
          bold = true
        )
        KraLineRow("Port Clearing & NTSA Reg.", KenyanMarketAnalyticsEngine.formatKes(kra.clearingAndNtsaKes))
      }
    }
  }
}

@Composable
fun TotalCostOfOwnershipCard(
  tco: TcoEstimate,
  activeFuelType: String,
  modifier: Modifier = Modifier
) {
  val telemetry = LocalTelemetryColors.current
  val totalFloat = tco.totalFiveYearTcoKes.coerceAtLeast(1L).toFloat()
  val depShare = (tco.fiveYearDepreciationLossKes / totalFloat).coerceIn(0.05f, 0.85f)
  val fuelShare = (tco.fiveYearFuelKes / totalFloat).coerceIn(0.05f, 0.85f)
  val insShare = (tco.fiveYearInsuranceKes / totalFloat).coerceIn(0.05f, 0.85f)
  val maintShare = (tco.fiveYearMaintenanceKes / totalFloat).coerceIn(0.05f, 0.85f)

  HekimaCard(
    title = "5-Year Total Cost of Ownership (TCO)",
    subtitle = "Depreciation + Kenyan fuel, insurance & maintenance (${"%,d".format(tco.annualDistanceKm)} km/yr)",
    modifier = modifier.testTag("tco_card"),
    trailingContent = {
      Icon(
        imageVector = Icons.Filled.LocalGasStation,
        contentDescription = null,
        tint = telemetry.accentCyan
      )
    }
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      // Headline 5-Year & Monthly TCO
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
      ) {
        Column {
          Text(
            text = "Estimated 5-Year TCO",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = KenyanMarketAnalyticsEngine.formatKes(tco.totalFiveYearTcoKes),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
          )
        }
        Surface(
          color = telemetry.accentCyan.copy(alpha = 0.14f),
          shape = RoundedCornerShape(10.dp)
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.End
          ) {
            Text(
              text = "Effective Monthly",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = "${KenyanMarketAnalyticsEngine.formatKes(tco.effectiveMonthlyTcoKes)}/mo",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              color = telemetry.accentCyan
            )
          }
        }
      }

      // Proportional Cost Composition Bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(10.dp)
          .clip(RoundedCornerShape(5.dp))
      ) {
        Box(
          modifier = Modifier
            .weight(depShare)
            .fillMaxHeight()
            .background(telemetry.warning)
        )
        Box(
          modifier = Modifier
            .weight(fuelShare)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.primary)
        )
        Box(
          modifier = Modifier
            .weight(insShare)
            .fillMaxHeight()
            .background(telemetry.accentCyan)
        )
        Box(
          modifier = Modifier
            .weight(maintShare)
            .fillMaxHeight()
            .background(telemetry.positive)
        )
      }

      // Annual & 5-Year Breakdown Rows
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TcoBreakdownRow(
          label = "Fuel ($activeFuelType)",
          annualKes = tco.annualFuelCostKes,
          fiveYearKes = tco.fiveYearFuelKes,
          dotColor = MaterialTheme.colorScheme.primary
        )
        TcoBreakdownRow(
          label = "Comprehensive Insurance (${String.format("%.1f", tco.insuranceRatePct)}%)",
          annualKes = tco.annualInsuranceKes,
          fiveYearKes = tco.fiveYearInsuranceKes,
          dotColor = telemetry.accentCyan
        )
        TcoBreakdownRow(
          label = "Routine Maintenance (${tco.maintenanceTierLabel})",
          annualKes = tco.annualMaintenanceKes,
          fiveYearKes = tco.fiveYearMaintenanceKes,
          dotColor = telemetry.positive
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(telemetry.warning)
            )
            Spacer(Modifier.width(8.dp))
            Column {
              Text("5-Year Depreciation Loss", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
              Text(
                text = "Projected resale: ${KenyanMarketAnalyticsEngine.formatKes(tco.fiveYearProjectedResaleKes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
          Text(
            text = KenyanMarketAnalyticsEngine.formatKes(tco.fiveYearDepreciationLossKes),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = telemetry.warning
          )
        }
      }

      // Fuel Trim Comparison at Kenyan Pump Prices
      if (tco.fuelTrimComparison.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "Annual Fuel Trim Comparison (EPRA Benchmarks)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            tco.fuelTrimComparison.forEach { trim ->
              val isSelected = trim.fuelType.equals(activeFuelType, ignoreCase = true)
              Surface(
                color = if (isSelected) {
                  MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                  MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
              ) {
                Column(
                  modifier = Modifier.padding(8.dp),
                  verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                  Text(
                    text = trim.fuelType,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = KenyanMarketAnalyticsEngine.formatCompactMillions(trim.annualFuelCostKes) + "/yr",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = "${String.format("%.1f", trim.consumptionKmPerLiter)} km/L • ${trim.pumpPriceKesPerLiter}/L",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun KraLineRow(
  label: String,
  value: String,
  valueColor: Color = MaterialTheme.colorScheme.onSurface,
  bold: Boolean = false
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = if (bold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
      fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
      modifier = Modifier.weight(1f)
    )
    Text(
      text = value,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
      color = valueColor
    )
  }
}

@Composable
private fun TcoBreakdownRow(
  label: String,
  annualKes: Long,
  fiveYearKes: Long,
  dotColor: Color
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
      Box(
        modifier = Modifier
          .size(8.dp)
          .clip(RoundedCornerShape(4.dp))
          .background(dotColor)
      )
      Spacer(Modifier.width(8.dp))
      Column {
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        Text(
          text = "${KenyanMarketAnalyticsEngine.formatKes(annualKes)} / yr",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
    Text(
      text = "5y: ${KenyanMarketAnalyticsEngine.formatKes(fiveYearKes)}",
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold
    )
  }
}
