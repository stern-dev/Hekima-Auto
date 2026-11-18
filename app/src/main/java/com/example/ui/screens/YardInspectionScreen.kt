package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.export.NegotiationBriefExporter
import com.example.domain.analytics.KenyanMarketAnalyticsEngine
import com.example.domain.model.InspectionStatus
import com.example.domain.model.VehicleListing
import com.example.domain.model.YardInspectionDraft
import com.example.domain.model.YardInspectionReport
import com.example.ui.components.DropdownSelectorField
import com.example.ui.components.HekimaCard
import com.example.ui.theme.LocalTelemetryColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YardInspectionScreen(
  draft: YardInspectionDraft,
  savedInspections: List<YardInspectionReport>,
  availableListings: List<VehicleListing>,
  onPrefillFromListing: (VehicleListing) -> Unit,
  onUpdateHeader: (vehicleTitle: String, location: String, askingPriceKes: Long, notes: String) -> Unit,
  onUpdateItem: (itemId: String, status: InspectionStatus, customRepairKes: Long?) -> Unit,
  onResetDraft: () -> Unit,
  onSaveInspection: () -> Unit,
  onDeleteInspection: (String) -> Unit,
  isGuestMode: Boolean,
  onRequestSignIn: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val telemetry = LocalTelemetryColors.current

  val tierColor = when {
    draft.overallScore >= 80 -> telemetry.positive
    draft.overallScore >= 55 -> telemetry.warning
    else -> telemetry.negative
  }

  val groupedItems = remember(draft.items) {
    draft.items.groupBy { it.category }
  }

  val listingChoices = remember(availableListings) {
    availableListings.take(15)
  }
  var askingText by remember(draft.listingId, draft.askingPriceKes) {
    mutableStateOf(draft.askingPriceKes.toString())
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
      .testTag("yard_inspection_lazy_column"),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Spacer(Modifier.height(4.dp))
      Text(
        text = "On-Site Yard Inspection",
        style = MaterialTheme.typography.headlineMedium
      )
      Text(
        text = "Score 6 Kenyan buyer checkpoints on-site, auto-deduct reconditioning costs in KES, and export an offer brief.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    // Live Scorecard KPI Card
    item {
      HekimaCard(
        title = "Live Inspection Scorecard",
        subtitle = "${draft.vehicleTitle} • ${draft.location}",
        modifier = Modifier.testTag("yard_scorecard_card"),
        trailingContent = {
          Surface(
            color = tierColor.copy(alpha = 0.16f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, tierColor.copy(alpha = 0.4f))
          ) {
            Text(
              text = "${draft.overallScore}/100",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = tierColor,
              modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("yard_overall_score_value")
            )
          }
        }
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "Readiness Verdict",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = draft.verdictTier,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = tierColor,
                modifier = Modifier.testTag("yard_verdict_tier_text")
              )
            }
            Column(horizontalAlignment = Alignment.End) {
              Text(
                text = "Recommended Adjusted Offer",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = KenyanMarketAnalyticsEngine.formatKes(draft.recommendedOfferKes),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("yard_recommended_offer_value")
              )
            }
          }

          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Column {
                Text("Seller Asking", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                  KenyanMarketAnalyticsEngine.formatKes(draft.askingPriceKes),
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold
                )
              }
              Column(horizontalAlignment = Alignment.End) {
                Text("Repair Deduction", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                  text = "-${KenyanMarketAnalyticsEngine.formatKes(draft.estimatedRepairKes)}",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = if (draft.estimatedRepairKes > 0L) telemetry.negative else telemetry.positive
                )
              }
            }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = {
                if (isGuestMode) onRequestSignIn() else onSaveInspection()
              },
              modifier = Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("save_inspection_button")
            ) {
              Icon(Icons.Filled.CloudDone, null, Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Save Cloud")
            }
            OutlinedButton(
              onClick = {
                val uri = NegotiationBriefExporter.exportInspectionBriefPdf(context, draft)
                NegotiationBriefExporter.shareBriefPdf(
                  context = context,
                  pdfUri = uri,
                  subject = "Hekima Yard Inspection: ${draft.vehicleTitle}"
                )
              },
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("export_inspection_pdf_button")
            ) {
              Icon(Icons.Filled.PictureAsPdf, null, Modifier.size(18.dp))
              Spacer(Modifier.width(4.dp))
              Text("PDF")
            }
            OutlinedButton(
              onClick = {
                val text = NegotiationBriefExporter.buildInspectionBriefText(draft)
                NegotiationBriefExporter.shareBriefText(
                  context = context,
                  subject = "Yard Offer Brief: ${draft.vehicleTitle}",
                  text = text
                )
              },
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("share_inspection_text_button")
            ) {
              Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
              Spacer(Modifier.width(4.dp))
              Text("Text")
            }
          }
        }
      }
    }

    // Vehicle Details & Listing Prefill Card
    item {
      HekimaCard(
        title = "Vehicle & Yard Details",
        trailingContent = {
          TextButton(
            onClick = onResetDraft,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("reset_inspection_button")
          ) {
            Icon(Icons.Filled.RestartAlt, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Reset")
          }
        }
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          if (listingChoices.isNotEmpty()) {
            DropdownSelectorField(
              label = "Quick-Load Market Listing",
              options = listingChoices.map { "${it.fullName} (${KenyanMarketAnalyticsEngine.formatCompactMillions(it.askingPriceKes)})" },
              selected = draft.vehicleTitle,
              onSelect = { selectedLabel ->
                val chosen = listingChoices.firstOrNull {
                  selectedLabel.startsWith(it.fullName)
                }
                if (chosen != null) {
                  onPrefillFromListing(chosen)
                }
              },
              testTag = "inspection_listing_selector"
            )
          }

          OutlinedTextField(
            value = draft.vehicleTitle,
            onValueChange = { onUpdateHeader(it, draft.location, draft.askingPriceKes, draft.notes) },
            label = { Text("Vehicle Title") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("inspection_vehicle_title_input")
          )

          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
              value = draft.location,
              onValueChange = { onUpdateHeader(draft.vehicleTitle, it, draft.askingPriceKes, draft.notes) },
              label = { Text("Yard / City") },
              singleLine = true,
              modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
              value = askingText,
              onValueChange = { raw ->
                val filtered = raw.filter { it.isDigit() }
                askingText = filtered
                filtered.toLongOrNull()?.let { kes ->
                  if (kes >= 100_000L) {
                    onUpdateHeader(draft.vehicleTitle, draft.location, kes, draft.notes)
                  }
                }
              },
              label = { Text("Asking (KES)") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier
                .weight(1f)
                .testTag("inspection_asking_price_input")
            )
          }

          OutlinedTextField(
            value = draft.notes,
            onValueChange = { onUpdateHeader(draft.vehicleTitle, draft.location, draft.askingPriceKes, it) },
            label = { Text("Inspector Observations / Chassis Note") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("inspection_notes_input")
          )
        }
      }
    }

    // 6 Categorized Checklists
    groupedItems.forEach { (category, categoryItems) ->
      item(key = "cat_$category") {
        HekimaCard(
          title = category,
          subtitle = "${categoryItems.count { it.status == InspectionStatus.PASS }}/${categoryItems.size} passed"
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            categoryItems.forEach { checkItem ->
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = checkItem.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                  )
                  Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(6.dp)
                  ) {
                    Text(
                      text = "${checkItem.weight} pts",
                      style = MaterialTheme.typography.labelSmall,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                  }
                }
                Text(
                  text = checkItem.guidance,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                  InspectionStatus.entries.forEachIndexed { idx, status ->
                    SegmentedButton(
                      selected = checkItem.status == status,
                      onClick = { onUpdateItem(checkItem.id, status, null) },
                      shape = SegmentedButtonDefaults.itemShape(
                        index = idx,
                        count = InspectionStatus.entries.size
                      ),
                      modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("inspection_item_${checkItem.id}_${status.name.lowercase()}")
                    ) {
                      Text(status.label)
                    }
                  }
                }

                if (checkItem.status != InspectionStatus.PASS && checkItem.effectiveRepairKes > 0L) {
                  Text(
                    text = "Repair deduction applied: -${KenyanMarketAnalyticsEngine.formatKes(checkItem.effectiveRepairKes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (checkItem.status == InspectionStatus.ISSUE) telemetry.negative else telemetry.warning
                  )
                }
              }
            }
          }
        }
      }
    }

    // Saved Cloud Inspections
    item {
      Text(
        text = "Saved Cloud Inspections (${savedInspections.size})",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
      )
    }

    if (savedInspections.isEmpty()) {
      item {
        HekimaCard {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(Icons.AutoMirrored.Filled.FactCheck, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
              text = if (isGuestMode) {
                "Sign in with Google to store yard inspection scorecards and offer briefs to the cloud."
              } else {
                "No saved yard inspections yet. Tap Save Cloud above to store your first inspection."
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    } else {
      items(savedInspections, key = { it.id }) { report ->
        val reportColor = when {
          report.overallScore >= 80 -> telemetry.positive
          report.overallScore >= 55 -> telemetry.warning
          else -> telemetry.negative
        }
        HekimaCard {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(report.vehicleTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                  text = "${report.location} • Score ${report.overallScore}/100 (${report.verdictTier})",
                  style = MaterialTheme.typography.labelMedium,
                  color = reportColor
                )
              }
              IconButton(
                onClick = { onDeleteInspection(report.id) },
                modifier = Modifier.minimumInteractiveComponentSize()
              ) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete yard inspection")
              }
            }

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .padding(10.dp),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "Asking: ${KenyanMarketAnalyticsEngine.formatCompactMillions(report.askingPriceKes)}",
                style = MaterialTheme.typography.labelSmall
              )
              Text(
                text = "Offer: ${KenyanMarketAnalyticsEngine.formatKes(report.recommendedOfferKes)}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
              )
            }

            if (report.checklistSummary.isNotEmpty()) {
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                report.checklistSummary.forEach { summaryLine ->
                  Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                  ) {
                    Text(
                      text = summaryLine,
                      style = MaterialTheme.typography.labelSmall,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
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
