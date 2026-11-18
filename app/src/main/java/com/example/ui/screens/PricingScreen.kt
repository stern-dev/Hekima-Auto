package com.example.ui.screens

import android.content.ActivityNotFoundException
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.export.NegotiationBriefExporter
import com.example.domain.analytics.KenyanMarketAnalyticsEngine
import com.example.domain.model.MarketAnalysis
import com.example.domain.model.OdometerSanityReport
import com.example.domain.model.OdometerSanityStatus
import com.example.domain.model.PricingEstimate
import com.example.domain.model.VehicleListing
import com.example.domain.model.VehicleSpecification
import com.example.ui.components.DropdownSelectorField
import com.example.ui.components.EmptyAnalyticsState
import com.example.ui.components.HekimaCard
import com.example.ui.components.KraAdvisoryDisclaimerBanner
import com.example.ui.components.KraImportDutyCard
import com.example.ui.components.OdometerAuditDialog
import com.example.ui.components.OdometerSanityBadge
import com.example.ui.components.ProvenanceBadge
import com.example.ui.components.TotalCostOfOwnershipCard
import com.example.ui.theme.LocalTelemetryColors
import kotlin.math.roundToInt

@Composable
fun PricingScreen(
  spec: VehicleSpecification,
  estimate: PricingEstimate,
  availableMakes: List<String>,
  modelsForMake: List<String>,
  savedAnalyses: List<MarketAnalysis>,
  onUpdateSpec: ((VehicleSpecification) -> VehicleSpecification) -> Unit,
  onSaveAnalysis: () -> Unit,
  onSaveComparable: (VehicleListing) -> Unit,
  modifier: Modifier = Modifier
) {
  val telemetry = LocalTelemetryColors.current
  val context = LocalContext.current
  var auditedVehicle by remember { mutableStateOf<Pair<String, OdometerSanityReport>?>(null) }
  var exportStatus by remember { mutableStateOf<String?>(null) }
  val odoReport = remember(spec.year, spec.mileageKm, spec.importStatus) {
    KenyanMarketAnalyticsEngine.evaluateOdometerSanity(spec.year, spec.mileageKm, spec.importStatus)
  }
  val yearOptions = (2012..2025).reversed().map { it.toString() }
  val transmissions = listOf("Automatic", "CVT", "Manual")
  val fuels = listOf("Petrol", "Hybrid", "Diesel")
  val conditions = listOf("Excellent", "Good", "Fair")
  val importStatuses = listOf("Foreign Used", "Locally Used")
  val engineOptions = listOf("1200", "1300", "1400", "1500", "1600", "1800", "2000", "2400", "2500", "2800", "3000")

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
      .testTag("pricing_lazy_column"),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Spacer(Modifier.height(4.dp))
      Text(
        text = "Vehicle Pricing Analysis",
        style = MaterialTheme.typography.headlineMedium
      )
      Text(
        text = "Configure vehicle specifications to compute multivariate KES valuation & comparables.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    item {
      HekimaCard(title = "Specification Inputs") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DropdownSelectorField(
              label = "Make",
              options = availableMakes,
              selected = spec.make,
              onSelect = { newMake ->
                onUpdateSpec { it.copy(make = newMake) }
              },
              modifier = Modifier.weight(1f),
              testTag = "pricing_make_selector"
            )
            DropdownSelectorField(
              label = "Model",
              options = modelsForMake.ifEmpty { listOf(spec.model) },
              selected = spec.model,
              onSelect = { newModel ->
                onUpdateSpec { it.copy(model = newModel) }
              },
              modifier = Modifier.weight(1f),
              testTag = "pricing_model_selector"
            )
          }

          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DropdownSelectorField(
              label = "Year",
              options = yearOptions,
              selected = spec.year.toString(),
              onSelect = { yr ->
                yr.toIntOrNull()?.let { y -> onUpdateSpec { it.copy(year = y) } }
              },
              modifier = Modifier.weight(1f),
              testTag = "pricing_year_selector"
            )
            DropdownSelectorField(
              label = "Engine (cc)",
              options = engineOptions,
              selected = spec.engineCc.toString(),
              onSelect = { cc ->
                cc.toIntOrNull()?.let { c -> onUpdateSpec { it.copy(engineCc = c) } }
              },
              modifier = Modifier.weight(1f),
              testTag = "pricing_engine_selector"
            )
          }

          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text("Odometer Mileage", style = MaterialTheme.typography.labelLarge)
              Text(
                text = "%,d km".format(spec.mileageKm),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
              )
            }
            Slider(
              value = spec.mileageKm.toFloat(),
              onValueChange = { km ->
                val rounded = ((km / 5000f).roundToInt() * 5000).coerceIn(5000, 220000)
                onUpdateSpec { it.copy(mileageKm = rounded) }
              },
              valueRange = 5000f..220000f,
              modifier = Modifier.testTag("pricing_mileage_slider")
            )
          }

          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DropdownSelectorField(
              label = "Transmission",
              options = transmissions,
              selected = spec.transmission,
              onSelect = { t -> onUpdateSpec { it.copy(transmission = t) } },
              modifier = Modifier.weight(1f)
            )
            DropdownSelectorField(
              label = "Fuel Type",
              options = fuels,
              selected = spec.fuelType,
              onSelect = { f -> onUpdateSpec { it.copy(fuelType = f) } },
              modifier = Modifier.weight(1f)
            )
          }

          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DropdownSelectorField(
              label = "Condition",
              options = conditions,
              selected = spec.condition,
              onSelect = { c -> onUpdateSpec { it.copy(condition = c) } },
              modifier = Modifier.weight(1f)
            )
            DropdownSelectorField(
              label = "Import Status",
              options = importStatuses,
              selected = spec.importStatus,
              onSelect = { s -> onUpdateSpec { it.copy(importStatus = s) } },
              modifier = Modifier.weight(1f)
            )
          }
        }
      }
    }

    if (estimate.estimatedPriceKes <= 0L) {
      item {
        EmptyAnalyticsState(
          title = "Insufficient Dataset",
          message = estimate.methodologyExplanation,
          actionLabel = "Reset Specs",
          onAction = { onUpdateSpec { VehicleSpecification() } }
        )
      }
    } else {
      // Primary Valuation Card
      item {
        HekimaCard(
          title = "${spec.make} ${spec.model} ${spec.year}",
          subtitle = estimate.confidenceLabel,
          trailingContent = {
            Button(
              onClick = onSaveAnalysis,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("save_analysis_button")
            ) {
              Icon(Icons.Filled.BookmarkAdd, null, Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Save")
            }
          }
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
              text = "Estimated Current Market Price",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = KenyanMarketAnalyticsEngine.formatKes(estimate.estimatedPriceKes),
              style = MaterialTheme.typography.displaySmall,
              color = MaterialTheme.colorScheme.primary
            )

            // Low / Avg / High Bar
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Column {
                  Text("Low Range", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text(KenyanMarketAnalyticsEngine.formatCompactMillions(estimate.lowPriceKes), style = MaterialTheme.typography.titleSmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Text("Peer Average", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text(KenyanMarketAnalyticsEngine.formatCompactMillions(estimate.comparableAverageKes), style = MaterialTheme.typography.titleSmall, color = telemetry.accentCyan)
                }
                Column(horizontalAlignment = Alignment.End) {
                  Text("High Range", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text(KenyanMarketAnalyticsEngine.formatCompactMillions(estimate.highPriceKes), style = MaterialTheme.typography.titleSmall)
                }
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "Expected Annual Depreciation",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = "${String.format("%.1f", estimate.annualDepreciationPct)}%/yr (${String.format("%.0f", estimate.cumulativeDepreciationPct)}% total)",
                style = MaterialTheme.typography.labelMedium,
                color = telemetry.warning
              )
            }

            // Advisory Disclaimer
            Surface(
              color = telemetry.warning.copy(alpha = 0.12f),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Filled.Info, contentDescription = "Disclaimer", tint = telemetry.warning, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                  text = "Advisory estimate only — actual selling prices vary with inspection history and negotiation.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurface
                )
              }
            }

            // Shareable Negotiation Brief Actions
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              OutlinedButton(
                onClick = {
                  runCatching {
                    val uri = NegotiationBriefExporter.exportPricingBriefPdf(context, estimate)
                    NegotiationBriefExporter.shareBriefPdf(
                      context = context,
                      pdfUri = uri,
                      subject = "${spec.make} ${spec.model} ${spec.year} Valuation Brief"
                    )
                  }.onSuccess {
                    exportStatus = "Generated PDF brief ready to share."
                  }.onFailure { err ->
                    exportStatus = if (err is ActivityNotFoundException) {
                      "No app found to share PDF brief."
                    } else {
                      err.localizedMessage ?: "Failed to export PDF brief."
                    }
                  }
                },
                modifier = Modifier
                  .weight(1f)
                  .minimumInteractiveComponentSize()
                  .testTag("export_pricing_pdf_button")
              ) {
                Icon(Icons.Filled.PictureAsPdf, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share PDF Brief")
              }
              OutlinedButton(
                onClick = {
                  runCatching {
                    val briefText = NegotiationBriefExporter.buildPricingBriefText(estimate)
                    NegotiationBriefExporter.shareBriefText(
                      context = context,
                      subject = "${spec.make} ${spec.model} ${spec.year} Negotiation Brief",
                      text = briefText
                    )
                  }.onSuccess {
                    exportStatus = "Opened text brief share sheet."
                  }.onFailure { err ->
                    exportStatus = err.localizedMessage ?: "Unable to share text brief."
                  }
                },
                modifier = Modifier
                  .weight(1f)
                  .minimumInteractiveComponentSize()
                  .testTag("share_pricing_text_button")
              ) {
                Icon(Icons.Filled.Share, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share Text")
              }
            }
            if (exportStatus != null) {
              Text(
                text = exportStatus!!,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }

      if (odoReport.status != OdometerSanityStatus.PLAUSIBLE) {
        item {
          val odoColor = if (odoReport.status == OdometerSanityStatus.SUSPECT_LOW) telemetry.negative else telemetry.warning
          HekimaCard(
            title = "Odometer & Auction Sanity Check",
            subtitle = odoReport.status.badgeLabel,
            modifier = Modifier.testTag("pricing_odometer_warning_card"),
            trailingContent = {
              OutlinedButton(
                onClick = { auditedVehicle = "${spec.make} ${spec.model} ${spec.year}" to odoReport },
                modifier = Modifier.minimumInteractiveComponentSize()
              ) {
                Text("Checklist")
              }
            }
          ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Icon(Icons.Filled.Warning, contentDescription = odoReport.status.badgeLabel, tint = odoColor, modifier = Modifier.size(20.dp))
              Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                  text = odoReport.headline,
                  style = MaterialTheme.typography.titleSmall,
                  color = odoColor,
                  fontWeight = FontWeight.SemiBold
                )
                Text(
                  text = odoReport.verificationAdvice,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurface
                )
              }
            }
          }
        }
      }

      // 8-Year Age Rule & KRA Duty Estimator
      estimate.kraDutyEstimate?.let { kra ->
        item {
          KraImportDutyCard(kra = kra)
        }
      }

      // 5-Year Total Cost of Ownership (TCO)
      estimate.tcoEstimate?.let { tco ->
        item {
          TotalCostOfOwnershipCard(
            tco = tco,
            activeFuelType = spec.fuelType
          )
        }
      }

      // Factors Influencing Estimate & Methodology
      item {
        HekimaCard(
          title = "Valuation Drivers & Methodology",
          subtitle = "How each specification shifts the Kenyan market price"
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            estimate.factorImpacts.forEach { factor ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(factor.factorName, style = MaterialTheme.typography.titleSmall)
                  Text(factor.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val impactColor = when {
                  factor.impactKes > 0L -> telemetry.positive
                  factor.impactKes < 0L -> telemetry.negative
                  else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val prefix = if (factor.impactKes > 0L) "+" else ""
                Text(
                  text = "${prefix}KES ${KenyanMarketAnalyticsEngine.formatCompactMillions(kotlin.math.abs(factor.impactKes))}".let {
                    if (factor.impactKes < 0L) "-KES ${KenyanMarketAnalyticsEngine.formatCompactMillions(kotlin.math.abs(factor.impactKes))}" else it
                  },
                  style = MaterialTheme.typography.labelMedium,
                  color = impactColor
                )
              }
              HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            }

            Text(
              text = estimate.methodologyExplanation,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      // Comparable Vehicles in Dataset
      item {
        HekimaCard(
          title = "Similar Vehicles in Dataset",
          subtitle = "Nearest Kenyan comparable listings matched on model, year, and mileage"
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            estimate.comparableListings.forEach { comp ->
              val compOdo = KenyanMarketAnalyticsEngine.evaluateOdometerSanity(
                year = comp.year,
                mileageKm = comp.mileageKm,
                importStatus = comp.importStatus
              )
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(10.dp))
                  .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                  .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                  Text(comp.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                  Text(
                    text = "${"%,d".format(comp.mileageKm)} km • ${comp.importStatus} • ${comp.location}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    ProvenanceBadge(
                      provenance = comp.provenance,
                      updatedAtEpochMs = comp.updatedAtEpochMs
                    )
                    OdometerSanityBadge(
                      report = compOdo,
                      onClick = { auditedVehicle = comp.fullName to compOdo }
                    )
                  }
                }
                Column(horizontalAlignment = Alignment.End) {
                  Text(
                    text = KenyanMarketAnalyticsEngine.formatKes(comp.askingPriceKes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                  )
                  OutlinedButton(
                    onClick = { onSaveComparable(comp) },
                    modifier = Modifier.minimumInteractiveComponentSize()
                  ) {
                    Text("Save", style = MaterialTheme.typography.labelSmall)
                  }
                }
              }
            }
          }
        }
      }

      item {
        KraAdvisoryDisclaimerBanner()
      }
    }

    if (savedAnalyses.isNotEmpty()) {
      item {
        HekimaCard(
          title = "Saved Cloud Valuations",
          subtitle = "Synced to your Google account via Firestore"
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            savedAnalyses.take(5).forEach { saved ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                  Icon(Icons.Outlined.CloudDone, null, tint = telemetry.positive, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(8.dp))
                  Column {
                    Text("${saved.make} ${saved.model} ${saved.year}", style = MaterialTheme.typography.titleSmall)
                    Text("${"%,d".format(saved.mileageKm)} km • ${String.format("%.1f", saved.annualDepreciationPct)}%/yr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
                Text(
                  text = KenyanMarketAnalyticsEngine.formatCompactMillions(saved.estimatedPriceKes),
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.primary
                )
              }
            }
          }
        }
      }
    }

    item { Spacer(Modifier.height(16.dp)) }
  }

  val activeAudit = auditedVehicle
  if (activeAudit != null) {
    OdometerAuditDialog(
      vehicleName = activeAudit.first,
      report = activeAudit.second,
      onDismiss = { auditedVehicle = null }
    )
  }
}
