package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ai.AuctionSheetAiAnalyzer
import com.example.domain.analytics.KenyanMarketAnalyticsEngine
import com.example.domain.model.AiAnalyzerUiState
import com.example.domain.model.AuctionSheetAnalysis
import com.example.ui.components.HekimaCard
import com.example.ui.theme.LocalTelemetryColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiAuctionAnalyzerScreen(
  uiState: AiAnalyzerUiState,
  onAnalyze: (listingText: String, imageBitmap: Bitmap?) -> Unit,
  onClearAnalysis: () -> Unit,
  onSendToYardInspection: (AuctionSheetAnalysis) -> Unit,
  onCheckLandedCost: (AuctionSheetAnalysis) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val telemetry = LocalTelemetryColors.current
  val scope = rememberCoroutineScope()

  var listingText by remember { mutableStateOf("") }
  var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      scope.launch {
        selectedBitmap = AuctionSheetAiAnalyzer.loadScaledBitmap(context, uri)
      }
    }
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
      .testTag("ai_analyzer_lazy_column"),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Spacer(Modifier.height(4.dp))
      Text(
        text = "AI Auction & Listing Analyzer",
        style = MaterialTheme.typography.headlineMedium
      )
      Text(
        text = "Upload a Japanese auction sheet photo or paste dealership text to decode grades, panel repairs (XX/W2/A3), 8-year import eligibility, and odometer plausibility.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    // Input Card
    item {
      HekimaCard(
        title = "Auction Sheet or Dealership Listing",
        subtitle = "Zero-permission Photo Picker • Powered by Gemini Multimodal AI"
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            AssistChip(
              onClick = {
                listingText = "2019 Toyota Corolla Axio Hybrid (NKE165-7201482), Grade 4.5 / Interior B, 64,000 km.Remarks: A1 front bumper, U1 door. Mombasa duty paid KES 1,490,000."
              },
              label = { Text("Sample: 2019 Axio 4.5") },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("sample_auction_clean")
            )
            AssistChip(
              onClick = {
                listingText = "2018 Toyota Harrier 2.0 Elegance (ZSU60-0194421), Auction Grade R / C, 31,000 km claimed. Marks: XX rear quarter, W3 bonnet repaint, S2 underbody rust. Asking KES 3,450,000 Nairobi."
              },
              label = { Text("Sample: Grade R Harrier") },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("sample_auction_suspect")
            )
          }

          if (selectedBitmap != null) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
              Image(
                bitmap = selectedBitmap!!.asImageBitmap(),
                contentDescription = "Selected auction sheet photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )
              IconButton(
                onClick = { selectedBitmap = null },
                modifier = Modifier
                  .align(Alignment.TopEnd)
                  .padding(6.dp)
                  .minimumInteractiveComponentSize()
                  .clip(RoundedCornerShape(20.dp))
                  .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
              ) {
                Icon(Icons.Filled.Close, contentDescription = "Remove selected image")
              }
            }
          }

          OutlinedButton(
            onClick = {
              photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            modifier = Modifier
              .fillMaxWidth()
              .minimumInteractiveComponentSize()
              .testTag("pick_auction_photo_button")
          ) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (selectedBitmap == null) "Attach Auction Sheet or Yard Photo" else "Replace Photo")
          }

          OutlinedTextField(
            value = listingText,
            onValueChange = { listingText = it },
            label = { Text("Paste Auction Remarks, Chassis, or Seller Ad") },
            placeholder = { Text("e.g. 2019 Mazda CX-5 KF2P, Grade 4, 78,000 km, W2 door...") },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("ai_listing_text_input")
          )

          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
              onClick = { onAnalyze(listingText, selectedBitmap) },
              enabled = uiState !is AiAnalyzerUiState.Loading && (listingText.isNotBlank() || selectedBitmap != null),
              modifier = Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("analyze_auction_button")
            ) {
              Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Analyze with Gemini")
            }
            if (listingText.isNotBlank() || selectedBitmap != null || uiState !is AiAnalyzerUiState.Idle) {
              OutlinedButton(
                onClick = {
                  listingText = ""
                  selectedBitmap = null
                  onClearAnalysis()
                },
                modifier = Modifier.minimumInteractiveComponentSize()
              ) {
                Text("Clear")
              }
            }
          }
        }
      }
    }

    // State Output
    when (uiState) {
      is AiAnalyzerUiState.Idle -> {
        item {
          HekimaCard(
            title = "Auction Sheet Code Quick Reference",
            subtitle = "How Japanese inspectors grade imports bound for Mombasa"
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              LegendRow("Grades 4.5 / 4", "Clean unit with minor wear; ideal for Kenyan import.")
              LegendRow("Grades R / RA", "Repaired accident history — demand chassis weld check.")
              LegendRow("XX / X", "Panel replaced (X = needs replacement, XX = replaced).")
              LegendRow("W2 / W3", "Noticeable repaint wave — inspect for filler & colour mismatch.")
              LegendRow("S2 / C2", "Heavy undercarriage rust / corrosion (snow-region hazard).")
            }
          }
        }
      }

      is AiAnalyzerUiState.Loading -> {
        item {
          HekimaCard(modifier = Modifier.testTag("ai_loading_card")) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically
            ) {
              CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
              Spacer(Modifier.width(12.dp))
              Text(
                text = "Decoding grades, chassis & Kenya import rules...",
                style = MaterialTheme.typography.bodyMedium
              )
            }
          }
        }
      }

      is AiAnalyzerUiState.Error -> {
        item {
          Surface(
            color = telemetry.negative.copy(alpha = 0.14f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, telemetry.negative.copy(alpha = 0.4f)),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("ai_error_card")
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = telemetry.negative)
                Text(
                  text = "AI Analysis Failed",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = telemetry.negative
                )
              }
              Text(
                text = uiState.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
              )
              OutlinedButton(
                onClick = { onAnalyze(listingText, selectedBitmap) },
                enabled = listingText.isNotBlank() || selectedBitmap != null,
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("retry_ai_analysis_button")
              ) {
                Text("Retry Analysis")
              }
            }
          }
        }
      }

      is AiAnalyzerUiState.Success -> {
        val analysis = uiState.analysis
        val verdictColor = when {
          analysis.mileageVerdict.contains("Plausible", ignoreCase = true) -> telemetry.positive
          analysis.mileageVerdict.contains("Suspect", ignoreCase = true) -> telemetry.negative
          else -> telemetry.warning
        }

        item {
          HekimaCard(
            title = analysis.vehicleTitle,
            subtitle = "Chassis: ${analysis.chassisCode} • Reported ${"%,d".format(analysis.reportedMileageKm)} km",
            modifier = Modifier.testTag("ai_analysis_result_card")
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                BadgePill("Grade ${analysis.auctionGrade}", MaterialTheme.colorScheme.primary)
                BadgePill("Interior ${analysis.interiorGrade}", telemetry.accentCyan)
                BadgePill(analysis.mileageVerdict, verdictColor)
              }

              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(
                    text = "Suggested Max Offer Ceiling",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Text(
                    text = KenyanMarketAnalyticsEngine.formatKes(analysis.suggestedMaxOfferKes),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                  )
                  Text(
                    text = analysis.recommendedAction,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                }
              }

              Surface(
                color = telemetry.accentCyan.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Text(
                  text = "8-Year Import Rule: ${analysis.ageRuleNote}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.padding(10.dp)
                )
              }

              if (analysis.repairRemarks.isNotEmpty()) {
                SectionBulletList(
                  title = "Decoded Repair & Body Marks",
                  items = analysis.repairRemarks,
                  accent = telemetry.warning,
                  icon = Icons.Filled.Warning
                )
              }

              if (analysis.redFlags.isNotEmpty()) {
                SectionBulletList(
                  title = "Kenyan Buyer Red Flags",
                  items = analysis.redFlags,
                  accent = telemetry.negative,
                  icon = Icons.Filled.ErrorOutline
                )
              }

              if (analysis.yardVerificationChecks.isNotEmpty()) {
                SectionBulletList(
                  title = "On-Site Yard Verification Steps",
                  items = analysis.yardVerificationChecks,
                  accent = telemetry.positive,
                  icon = Icons.Filled.CheckCircle
                )
              }

              Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                  onClick = { onSendToYardInspection(analysis) },
                  modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("ai_to_yard_inspection_button")
                ) {
                  Icon(Icons.AutoMirrored.Filled.FactCheck, null, Modifier.size(18.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Yard Inspect")
                }
                OutlinedButton(
                  onClick = { onCheckLandedCost(analysis) },
                  modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("ai_to_landed_cost_button")
                ) {
                  Icon(Icons.Filled.Anchor, null, Modifier.size(18.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Landed Cost")
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

@Composable
private fun LegendRow(code: String, meaning: String) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = code,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.width(92.dp)
    )
    Text(
      text = meaning,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f)
    )
  }
}

@Composable
private fun BadgePill(text: String, color: androidx.compose.ui.graphics.Color) {
  Surface(
    color = color.copy(alpha = 0.14f),
    shape = RoundedCornerShape(8.dp),
    border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
      color = color,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
    )
  }
}

@Composable
private fun SectionBulletList(
  title: String,
  items: List<String>,
  accent: androidx.compose.ui.graphics.Color,
  icon: androidx.compose.ui.graphics.vector.ImageVector
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
      color = accent
    )
    items.forEach { entry ->
      Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = accent,
          modifier = Modifier
            .padding(top = 2.dp)
            .size(15.dp)
        )
        Text(
          text = entry,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface
        )
      }
    }
  }
}
