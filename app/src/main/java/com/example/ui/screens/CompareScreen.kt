package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.domain.model.ComparisonVerdict
import com.example.domain.model.OdometerSanityReport
import com.example.domain.model.OdometerSanityStatus
import com.example.domain.model.VehicleComparison
import com.example.domain.model.VehicleListing
import com.example.domain.model.VehicleSpecification
import com.example.ui.components.ComparisonMetricBars
import com.example.ui.components.EmptyAnalyticsState
import com.example.ui.components.HekimaCard
import com.example.ui.components.OdometerAuditDialog
import com.example.ui.components.OdometerSanityBadge
import com.example.ui.theme.LocalTelemetryColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompareScreen(
  verdict: ComparisonVerdict,
  allListings: List<VehicleListing>,
  savedComparisons: List<VehicleComparison>,
  onToggleVehicle: (String) -> Unit,
  onSaveComparison: (String) -> Unit,
  onLoadComparison: (List<String>) -> Unit,
  onDeleteComparison: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val telemetry = LocalTelemetryColors.current
  val context = LocalContext.current
  var showPicker by remember { mutableStateOf(false) }
  var saveTitle by remember { mutableStateOf("") }
  var showSaveDialog by remember { mutableStateOf(false) }
  var pickerQuery by remember { mutableStateOf("") }
  var auditedVehicle by remember { mutableStateOf<Pair<String, OdometerSanityReport>?>(null) }
  var exportNote by remember { mutableStateOf<String?>(null) }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Spacer(Modifier.height(4.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Vehicle Comparison", style = MaterialTheme.typography.headlineMedium)
          Text(
            text = "Compare up to 4 Kenyan vehicles on resale, depreciation, and demand.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        OutlinedButton(
          onClick = { showPicker = true },
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("add_compare_vehicle_button")
        ) {
          Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Pick (${verdict.metrics.size}/4)")
        }
      }
    }

    item {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        verdict.metrics.forEach { m ->
          InputChip(
            selected = true,
            onClick = { onToggleVehicle(m.vehicle.id) },
            label = { Text(m.vehicle.fullName) },
            trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp)) },
            modifier = Modifier.minimumInteractiveComponentSize()
          )
        }
      }
    }

    if (verdict.metrics.isEmpty()) {
      item {
        EmptyAnalyticsState(
          title = "No Vehicles Selected",
          message = "Pick 2 to 4 vehicles from the Kenyan market dataset to compare resale value.",
          actionLabel = "Select Vehicles",
          onAction = { showPicker = true }
        )
      }
    } else {
      // Resale Value Verdict Banner
      item {
        HekimaCard(
          title = "Resale Value Verdict",
          subtitle = "Strongest option: ${verdict.strongestResaleVehicle}",
          trailingContent = {
            Button(
              onClick = { showSaveDialog = true },
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("save_comparison_button")
            ) {
              Icon(Icons.Filled.BookmarkAdd, null, Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Save")
            }
          }
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
              Surface(
                color = telemetry.positive.copy(alpha = 0.16f),
                shape = RoundedCornerShape(10.dp)
              ) {
                Icon(
                  Icons.Filled.EmojiEvents,
                  contentDescription = "Winner",
                  tint = telemetry.positive,
                  modifier = Modifier.padding(10.dp).size(22.dp)
                )
              }
              Spacer(Modifier.width(12.dp))
              Text(
                text = verdict.verdictExplanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              OutlinedButton(
                onClick = {
                  runCatching {
                    val uri = NegotiationBriefExporter.exportComparisonBriefPdf(context, verdict)
                    NegotiationBriefExporter.shareBriefPdf(
                      context = context,
                      pdfUri = uri,
                      subject = "Hekima Auto Vehicle Comparison Brief"
                    )
                  }.onSuccess {
                    exportNote = "Comparison PDF brief ready to share."
                  }.onFailure { err ->
                    exportNote = err.localizedMessage ?: "Unable to export comparison PDF."
                  }
                },
                modifier = Modifier
                  .weight(1f)
                  .minimumInteractiveComponentSize()
                  .testTag("export_compare_pdf_button")
              ) {
                Icon(Icons.Filled.PictureAsPdf, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Export PDF")
              }
              OutlinedButton(
                onClick = {
                  runCatching {
                    val summaryText = NegotiationBriefExporter.buildComparisonBriefText(verdict)
                    NegotiationBriefExporter.shareBriefText(
                      context = context,
                      subject = "Hekima Auto Vehicle Comparison Summary",
                      text = summaryText
                    )
                  }.onSuccess {
                    exportNote = "Opened comparison text brief share sheet."
                  }.onFailure { err ->
                    exportNote = err.localizedMessage ?: "Unable to share comparison summary."
                  }
                },
                modifier = Modifier
                  .weight(1f)
                  .minimumInteractiveComponentSize()
                  .testTag("share_compare_text_button")
              ) {
                Icon(Icons.Filled.Share, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share Summary")
              }
            }
            if (exportNote != null) {
              Text(
                text = exportNote!!,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }

      // Comparison Chart
      item {
        HekimaCard(
          title = "Current Asking vs. 5-Year Projected Resale",
          subtitle = "KES market price today compared to projected resale value in 5 years"
        ) {
          ComparisonMetricBars(metrics = verdict.metrics)
        }
      }

      // Side-by-Side Metric Cards
      items(verdict.metrics, key = { it.vehicle.id }) { m ->
        val tco = remember(m.vehicle.id) {
          KenyanMarketAnalyticsEngine.estimateTco(
            spec = VehicleSpecification(
              make = m.vehicle.make,
              model = m.vehicle.model,
              year = m.vehicle.year,
              mileageKm = m.vehicle.mileageKm,
              engineCc = m.vehicle.engineCc,
              transmission = m.vehicle.transmission,
              fuelType = m.vehicle.fuelType,
              condition = m.vehicle.condition,
              importStatus = m.vehicle.importStatus
            ),
            currentValueKes = m.vehicle.askingPriceKes,
            annualDepreciationPct = m.annualDepreciationPct
          )
        }
        val ageRule = remember(m.vehicle.year) {
          KenyanMarketAnalyticsEngine.evaluateImportAgeRule(m.vehicle.year)
        }
        val odoReport = remember(m.vehicle.id) {
          KenyanMarketAnalyticsEngine.evaluateOdometerSanity(m.vehicle.year, m.vehicle.mileageKm, m.vehicle.importStatus)
        }
        HekimaCard(
          title = m.vehicle.fullName,
          subtitle = "${m.vehicle.location} • ${m.vehicle.importStatus} • ${m.vehicle.transmission} ${m.vehicle.fuelType}",
          trailingContent = {
            if (odoReport.status != OdometerSanityStatus.PLAUSIBLE) {
              OdometerSanityBadge(
                report = odoReport,
                onClick = { auditedVehicle = m.vehicle.fullName to odoReport }
              )
            }
          }
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CompareRow("Asking Price", KenyanMarketAnalyticsEngine.formatKes(m.vehicle.askingPriceKes))
            CompareRow("Odometer Mileage", "${"%,d".format(m.vehicle.mileageKm)} km")
            CompareRow("Odometer Sanity", odoReport.status.badgeLabel)
            CompareRow("8-Yr Import Status", ageRule.label)
            CompareRow("Annual Depreciation", "${String.format("%.1f", m.annualDepreciationPct)}%/yr")
            CompareRow("Est. 3-Yr Resale", KenyanMarketAnalyticsEngine.formatKes(m.threeYearResaleKes))
            CompareRow("Est. 5-Yr Resale", KenyanMarketAnalyticsEngine.formatKes(m.fiveYearResaleKes))
            CompareRow("5-Yr Value Retention", "${String.format("%.1f", m.valueRetentionPct)}%")
            CompareRow("Est. Annual Running", KenyanMarketAnalyticsEngine.formatKes(tco.annualRunningCostKes))
            CompareRow("Est. 5-Yr TCO", "${KenyanMarketAnalyticsEngine.formatKes(tco.totalFiveYearTcoKes)} (${KenyanMarketAnalyticsEngine.formatCompactMillions(tco.effectiveMonthlyTcoKes)}/mo)")
            CompareRow("Market Demand Score", "${m.vehicle.demandScore} / 100")
          }
        }
      }
    }

    if (savedComparisons.isNotEmpty()) {
      item {
        HekimaCard(
          title = "Saved Cloud Comparisons",
          subtitle = "Tap any saved benchmark to reload its vehicles"
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            savedComparisons.forEach { cmp ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(10.dp))
                  .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                  .clickable { onLoadComparison(cmp.vehicleIds) }
                  .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(cmp.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                  Text(
                    text = cmp.vehicleSummaries.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                IconButton(
                  onClick = { onDeleteComparison(cmp.id) },
                  modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                  Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete comparison")
                }
              }
            }
          }
        }
      }
    }

    item { Spacer(Modifier.height(16.dp)) }
  }

  if (showPicker) {
    val selectedIds = verdict.metrics.map { it.vehicle.id }.toSet()
    val candidates = allListings.filter {
      pickerQuery.isBlank() ||
        it.fullName.contains(pickerQuery, ignoreCase = true) ||
        it.location.contains(pickerQuery, ignoreCase = true)
    }.take(30)

    AlertDialog(
      onDismissRequest = { showPicker = false },
      title = { Text("Select Up to 4 Vehicles") },
      text = {
        Column(modifier = Modifier.fillMaxWidth()) {
          OutlinedTextField(
            value = pickerQuery,
            onValueChange = { pickerQuery = it },
            label = { Text("Search make, model, year") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          Spacer(Modifier.height(10.dp))
          LazyColumn(modifier = Modifier.height(280.dp)) {
            items(candidates, key = { it.id }) { item ->
              val isSel = item.id in selectedIds
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { onToggleVehicle(item.id) }
                  .background(
                    if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    else MaterialTheme.colorScheme.surface
                  )
                  .padding(vertical = 10.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(item.fullName, style = MaterialTheme.typography.titleSmall)
                  Text(
                    "${"%,d".format(item.mileageKm)} km • ${item.location}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                Text(
                  text = KenyanMarketAnalyticsEngine.formatCompactMillions(item.askingPriceKes),
                  style = MaterialTheme.typography.labelMedium,
                  color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
              }
              HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showPicker = false }) {
          Text("Done")
        }
      }
    )
  }

  if (showSaveDialog) {
    AlertDialog(
      onDismissRequest = { showSaveDialog = false },
      title = { Text("Save Comparison") },
      text = {
        OutlinedTextField(
          value = saveTitle,
          onValueChange = { saveTitle = it },
          label = { Text("Comparison title") },
          placeholder = { Text("e.g. 2018 Family SUV Benchmark") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onSaveComparison(saveTitle)
            saveTitle = ""
            showSaveDialog = false
          }
        ) { Text("Save to Cloud") }
      },
      dismissButton = {
        TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
      }
    )
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

@Composable
private fun CompareRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
  }
}
