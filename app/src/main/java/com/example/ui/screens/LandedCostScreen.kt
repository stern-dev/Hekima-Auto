package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.analytics.KenyanMarketAnalyticsEngine
import com.example.domain.model.CifCurrency
import com.example.domain.model.ExchangeRateSnapshot
import com.example.domain.model.ImportAgeRuleStatus
import com.example.domain.model.LandedCostCalculation
import com.example.domain.model.LandedCostInput
import com.example.ui.components.DropdownSelectorField
import com.example.ui.components.HekimaCard
import com.example.ui.components.KraAdvisoryDisclaimerBanner
import com.example.ui.theme.LocalTelemetryColors
import java.util.Locale

@Composable
fun LandedCostScreen(
  input: LandedCostInput,
  calculation: LandedCostCalculation,
  availableMakes: List<String>,
  modelsForMake: List<String>,
  onUpdateInput: ((LandedCostInput) -> LandedCostInput) -> Unit,
  onResetOverrides: () -> Unit,
  exchangeRate: ExchangeRateSnapshot = ExchangeRateSnapshot(),
  isRefreshingFx: Boolean = false,
  onRefreshFx: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val telemetry = LocalTelemetryColors.current
  val kra = calculation.kraEstimate
  val yearOptions = (2012..2025).reversed().map { it.toString() }
  val engineOptions = listOf("660", "1000", "1200", "1300", "1400", "1500", "1600", "1800", "2000", "2400", "2500", "2800", "3000", "3500", "4000", "4500")
  val fuels = listOf("Petrol", "Hybrid", "Diesel")

  var showAdvanced by remember { mutableStateOf(false) }

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

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
      .testTag("landed_cost_lazy_column"),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Spacer(Modifier.height(4.dp))
      Text(
        text = "Mombasa Landed Cost",
        style = MaterialTheme.typography.headlineMedium
      )
      Text(
        text = "Calculate KRA taxes (Import Duty, Excise, VAT, IDF & RDL) from manufacturing year, engine capacity, and CIF.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    // Vehicle & Engine Specifications
    item {
      HekimaCard(title = "Vehicle & Engine Specification") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DropdownSelectorField(
              label = "Make",
              options = availableMakes,
              selected = input.make,
              onSelect = { newMake -> onUpdateInput { it.copy(make = newMake) } },
              modifier = Modifier.weight(1f),
              testTag = "landed_make_selector"
            )
            DropdownSelectorField(
              label = "Model",
              options = modelsForMake.ifEmpty { listOf(input.model) },
              selected = input.model,
              onSelect = { newModel -> onUpdateInput { it.copy(model = newModel) } },
              modifier = Modifier.weight(1f),
              testTag = "landed_model_selector"
            )
          }

          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DropdownSelectorField(
              label = "Mfg. Year",
              options = yearOptions,
              selected = input.year.toString(),
              onSelect = { yr -> yr.toIntOrNull()?.let { y -> onUpdateInput { it.copy(year = y) } } },
              modifier = Modifier.weight(1f),
              testTag = "landed_year_selector"
            )
            DropdownSelectorField(
              label = "Engine (cc)",
              options = engineOptions,
              selected = input.engineCc.toString(),
              onSelect = { cc -> cc.toIntOrNull()?.let { c -> onUpdateInput { it.copy(engineCc = c) } } },
              modifier = Modifier.weight(1f),
              testTag = "landed_engine_selector"
            )
          }

          DropdownSelectorField(
            label = "Fuel Type",
            options = fuels,
            selected = input.fuelType,
            onSelect = { f -> onUpdateInput { it.copy(fuelType = f) } },
            modifier = Modifier.fillMaxWidth(),
            testTag = "landed_fuel_selector"
          )
        }
      }
    }

    // 8-Year Age Rule Status Banner
    item {
      Surface(
        color = statusColor.copy(alpha = 0.14f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("landed_age_rule_banner")
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.Top,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Icon(
            imageVector = statusIcon,
            contentDescription = kra.ageRuleStatus.label,
            tint = statusColor,
            modifier = Modifier.size(22.dp)
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
    }

    // CIF & Customs Inputs Card
    item {
      HekimaCard(
        title = "CIF Quote & Customs Inputs",
        subtitle = if (calculation.isCustomCifApplied) "Using your custom CIF quote" else "Using auto-estimated Mombasa CIF (leave blank for auto)"
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("live_fx_banner")
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
              ) {
                Icon(
                  imageVector = Icons.Filled.CurrencyExchange,
                  contentDescription = null,
                  tint = if (exchangeRate.isFallback) telemetry.warning else telemetry.positive,
                  modifier = Modifier.size(18.dp)
                )
                Column {
                  Text(
                    text = "1 USD = ${String.format(Locale.US, "%.2f", exchangeRate.usdToKes)} KES • 100 JPY ≈ ${String.format(Locale.US, "%.2f", exchangeRate.jpyToKes * 100.0)} KES",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  val fxUpdatedLabel = if (exchangeRate.fetchedAtEpochMs > 0L && !exchangeRate.isFallback) {
                    "${exchangeRate.sourceLabel} • ${KenyanMarketAnalyticsEngine.formatRelativeSyncTime(exchangeRate.fetchedAtEpochMs)}"
                  } else {
                    exchangeRate.sourceLabel
                  }
                  Text(
                    text = fxUpdatedLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (exchangeRate.isFallback) telemetry.warning else telemetry.positive
                  )
                }
              }
              FilledTonalButton(
                onClick = onRefreshFx,
                enabled = !isRefreshingFx,
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("refresh_fx_button")
              ) {
                if (isRefreshingFx) {
                  CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                  Text("Refresh FX")
                }
              }
            }
          }

          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            CifCurrency.entries.forEachIndexed { idx, currency ->
              SegmentedButton(
                selected = input.cifCurrency == currency,
                onClick = { onUpdateInput { it.copy(cifCurrency = currency) } },
                shape = SegmentedButtonDefaults.itemShape(index = idx, count = CifCurrency.entries.size),
                modifier = Modifier.testTag("cif_currency_${currency.name.lowercase()}")
              ) {
                Text("CIF in ${currency.label}")
              }
            }
          }

          val autoCifLabel = if (input.cifCurrency == CifCurrency.USD) {
            "Auto ~$${"%,d".format((kra.estimatedCifKes / calculation.usdToKesRate).toInt())}"
          } else {
            "Auto ${KenyanMarketAnalyticsEngine.formatKes(kra.estimatedCifKes)}"
          }

          OutlinedTextField(
            value = input.customCifText,
            onValueChange = { txt -> onUpdateInput { it.copy(customCifText = txt) } },
            label = { Text("Custom CIF (${input.cifCurrency.label})") },
            placeholder = { Text(autoCifLabel) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("custom_cif_input")
          )

          AnimatedVisibility(visible = input.cifCurrency == CifCurrency.USD) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              OutlinedTextField(
                value = input.usdToKesRateText,
                onValueChange = { txt -> onUpdateInput { it.copy(usdToKesRateText = txt) } },
                label = { Text("USD / KES Exchange Rate") },
                supportingText = {
                  Text("Converted CIF: ${KenyanMarketAnalyticsEngine.formatKes(calculation.effectiveCifKes)}")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("usd_rate_input")
              )
            }
          }

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .clickable { showAdvanced = !showAdvanced }
              .minimumInteractiveComponentSize()
              .padding(vertical = 4.dp)
              .testTag("toggle_advanced_customs"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "CRSP Override & Port Clearing Fees",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary
            )
            Icon(
              imageVector = if (showAdvanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
              contentDescription = if (showAdvanced) "Collapse overrides" else "Expand overrides",
              tint = MaterialTheme.colorScheme.primary
            )
          }

          AnimatedVisibility(visible = showAdvanced) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              OutlinedTextField(
                value = input.customCrspText,
                onValueChange = { txt -> onUpdateInput { it.copy(customCrspText = txt) } },
                label = { Text("CRSP Override (KES)") },
                placeholder = { Text("Auto ${KenyanMarketAnalyticsEngine.formatKes(kra.crspBenchmarkKes)}") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("custom_crsp_input")
              )
              OutlinedTextField(
                value = input.clearingAndNtsaText,
                onValueChange = { txt -> onUpdateInput { it.copy(clearingAndNtsaText = txt) } },
                label = { Text("Port Clearing & NTSA Fees (KES)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("clearing_fees_input")
              )
              TextButton(
                onClick = onResetOverrides,
                modifier = Modifier
                  .align(Alignment.End)
                  .minimumInteractiveComponentSize()
                  .testTag("reset_landed_cost_button")
              ) {
                Icon(Icons.Filled.RestartAlt, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reset Overrides")
              }
            }
          }

          calculation.validationWarning?.let { warning ->
            Text(
              text = warning,
              style = MaterialTheme.typography.bodySmall,
              color = telemetry.negative
            )
          }
        }
      }
    }

    // Total Landed Cost Headline & Cost Composition Card
    item {
      val totalSafe = kra.mombasaLandedCostKes.coerceAtLeast(1L).toFloat()
      val cifWeight = (calculation.effectiveCifKes / totalSafe).coerceIn(0.05f, 0.85f)
      val taxWeight = (kra.totalKraTaxesKes / totalSafe).coerceIn(0.05f, 0.85f)
      val clearingWeight = (kra.clearingAndNtsaKes / totalSafe).coerceIn(0.02f, 0.40f)

      HekimaCard(
        title = "Estimated Total Landed Cost",
        subtitle = "${input.make} ${input.model} ${input.year} • ${input.engineCc}cc ${input.fuelType}",
        modifier = Modifier.testTag("landed_cost_summary_card"),
        trailingContent = {
          Icon(Icons.Filled.Anchor, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            text = KenyanMarketAnalyticsEngine.formatKes(kra.mombasaLandedCostKes),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("total_landed_cost_value")
          )

          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              SummaryTile(
                label = if (calculation.isCustomCifApplied) "Custom CIF" else "Est. CIF",
                value = KenyanMarketAnalyticsEngine.formatCompactMillions(calculation.effectiveCifKes),
                valueColor = MaterialTheme.colorScheme.onSurface
              )
              SummaryTile(
                label = "Total KRA Taxes",
                value = KenyanMarketAnalyticsEngine.formatCompactMillions(kra.totalKraTaxesKes),
                valueColor = telemetry.warning
              )
              SummaryTile(
                label = "Tax Load / CIF",
                value = "${String.format("%.1f", calculation.effectiveTaxLoadPct)}%",
                valueColor = telemetry.accentCyan
              )
            }
          }

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .height(10.dp)
              .clip(RoundedCornerShape(5.dp))
          ) {
            Box(Modifier.weight(cifWeight).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
            Box(Modifier.weight(taxWeight).fillMaxHeight().background(telemetry.warning))
            Box(Modifier.weight(clearingWeight).fillMaxHeight().background(telemetry.accentCyan))
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            LegendDot("CIF", MaterialTheme.colorScheme.primary)
            LegendDot("KRA Taxes", telemetry.warning)
            LegendDot("Clearing & NTSA", telemetry.accentCyan)
          }
        }
      }
    }

    // Itemized KRA Tax Waterfall Card
    item {
      HekimaCard(
        title = "KRA Tax Waterfall Breakdown",
        subtitle = "Cascading Import Duty, Excise, VAT, IDF & RDL from CRSP benchmark",
        modifier = Modifier.testTag("kra_tax_breakdown_card")
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          WaterfallRow(
            label = if (calculation.isCustomCrspApplied) "Custom CRSP Value" else "CRSP Benchmark (Auto)",
            value = KenyanMarketAnalyticsEngine.formatKes(kra.crspBenchmarkKes)
          )
          WaterfallRow(
            label = "KRA Depreciated Customs Value (-${kra.kraDepreciationRatePct.toInt()}%)",
            value = KenyanMarketAnalyticsEngine.formatKes(kra.customsValueKes)
          )
          HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
          WaterfallRow("Import Duty (25% of CV)", KenyanMarketAnalyticsEngine.formatKes(kra.importDutyKes))
          WaterfallRow(
            "Excise Duty (${kra.exciseDutyRatePct.toInt()}% of CV + ID)",
            KenyanMarketAnalyticsEngine.formatKes(kra.exciseDutyKes)
          )
          WaterfallRow("VAT (16% of CV + ID + Excise)", KenyanMarketAnalyticsEngine.formatKes(kra.vatKes))
          WaterfallRow("Import Declaration Fee (IDF 2.5%)", KenyanMarketAnalyticsEngine.formatKes(kra.idfKes))
          WaterfallRow("Railway Development Levy (RDL 1.5%)", KenyanMarketAnalyticsEngine.formatKes(kra.rdlKes))
          HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
          WaterfallRow(
            label = "Total KRA Taxes & Levies",
            value = KenyanMarketAnalyticsEngine.formatKes(kra.totalKraTaxesKes),
            valueColor = telemetry.warning,
            bold = true
          )
          WaterfallRow(
            label = "Port Clearing & NTSA Registration",
            value = KenyanMarketAnalyticsEngine.formatKes(kra.clearingAndNtsaKes)
          )
        }
      }
    }

    // Mombasa Import vs Local Kenyan Market Card
    item {
      HekimaCard(
        title = "Mombasa Import vs. Local Market",
        subtitle = "Benchmark against Kenyan yard asking prices (no cloud quote storage)",
        modifier = Modifier.testTag("local_vs_import_card")
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Column {
                  Text("Mombasa Landed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text(
                    text = KenyanMarketAnalyticsEngine.formatKes(kra.mombasaLandedCostKes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                  )
                }
                Column(horizontalAlignment = Alignment.End) {
                  Text("Local Yard Avg", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text(
                    text = KenyanMarketAnalyticsEngine.formatKes(kra.localMarketPriceKes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = telemetry.accentCyan
                  )
                }
              }
              Text(
                text = "Expected local range: ${KenyanMarketAnalyticsEngine.formatCompactMillions(calculation.localMarketLowKes)} – ${KenyanMarketAnalyticsEngine.formatCompactMillions(calculation.localMarketHighKes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = kra.comparisonSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
              )
            }
          }

          if (calculation.comparableListings.isNotEmpty()) {
            Text(
              text = "Nearest Local Market Comparables",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurface
            )
            calculation.comparableListings.forEach { comp ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(10.dp))
                  .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                  .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(comp.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                  Text(
                    text = "${"%,d".format(comp.mileageKm)} km • ${comp.importStatus} • ${comp.location}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                Text(
                  text = KenyanMarketAnalyticsEngine.formatKes(comp.askingPriceKes),
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.primary
                )
              }
            }
          }
        }
      }
    }

    item {
      KraAdvisoryDisclaimerBanner(
        title = "KRA Customs & CRSP Estimation Notice",
        detail = "Duty waterfalls apply standard East African Community CET (25%), Excise (20%–35%), VAT (16%), IDF (2.5%), and RDL (1.5%) formulas over depreciated Kenyan CRSP benchmarks. Confirm final customs assessment and 8-year registration eligibility with KRA/NTSA and a licensed Mombasa clearing agent before shipping."
      )
    }

    item { Spacer(Modifier.height(16.dp)) }
  }
}

@Composable
private fun SummaryTile(label: String, value: String, valueColor: Color) {
  Column {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = valueColor)
  }
}

@Composable
private fun LegendDot(label: String, color: Color) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(
      modifier = Modifier
        .size(8.dp)
        .clip(RoundedCornerShape(4.dp))
        .background(color)
    )
    Spacer(Modifier.width(6.dp))
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun WaterfallRow(
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
