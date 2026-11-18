package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.analytics.KenyanMarketAnalyticsEngine
import com.example.domain.model.OdometerSanityReport
import com.example.domain.model.OdometerSanityStatus
import com.example.domain.model.PriceWatchlist
import com.example.domain.model.WatchlistMatch
import com.example.ui.theme.LocalTelemetryColors

@Composable
fun OdometerSanityBadge(
  report: OdometerSanityReport,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  if (report.status == OdometerSanityStatus.PLAUSIBLE) return
  val telemetry = LocalTelemetryColors.current
  val badgeColor = if (report.status == OdometerSanityStatus.SUSPECT_LOW) {
    telemetry.negative
  } else {
    telemetry.warning
  }

  Surface(
    color = badgeColor.copy(alpha = 0.14f),
    shape = RoundedCornerShape(6.dp),
    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f)),
    modifier = modifier.clickable(onClick = onClick)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Icon(
        imageVector = Icons.Filled.Warning,
        contentDescription = report.status.badgeLabel,
        tint = badgeColor,
        modifier = Modifier.size(12.dp)
      )
      Text(
        text = report.status.badgeLabel,
        style = MaterialTheme.typography.labelSmall,
        color = badgeColor,
        fontWeight = FontWeight.SemiBold
      )
    }
  }
}

@Composable
fun OdometerAuditDialog(
  vehicleName: String,
  report: OdometerSanityReport,
  onDismiss: () -> Unit
) {
  val telemetry = LocalTelemetryColors.current
  val accent = if (report.status == OdometerSanityStatus.SUSPECT_LOW) telemetry.negative else telemetry.warning

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(
        imageVector = Icons.Filled.VerifiedUser,
        contentDescription = null,
        tint = accent
      )
    },
    title = { Text("Odometer & Auction Audit") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(vehicleName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(report.headline, style = MaterialTheme.typography.bodyMedium, color = accent, fontWeight = FontWeight.SemiBold)
        Text(report.verificationAdvice, style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        Text(
          text = "Recommended Verification Steps:",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold
        )
        Text("1. Request JEVIC or QISJ pre-export odometer inspection certificate.", style = MaterialTheme.typography.bodySmall)
        Text("2. Cross-check chassis number on the original Japanese Auction Sheet.", style = MaterialTheme.typography.bodySmall)
        Text("3. Confirm NTSA TIMS records and logbook ownership lineage.", style = MaterialTheme.typography.bodySmall)
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Understood")
      }
    }
  )
}

@Composable
fun WatchlistSectionCard(
  watchlistMatches: List<WatchlistMatch>,
  isGuestMode: Boolean,
  onCreateWatchlistClick: () -> Unit,
  onToggleWatchlist: (PriceWatchlist, Boolean) -> Unit,
  onDeleteWatchlist: (String) -> Unit,
  onBrowseMatchesClick: () -> Unit,
  onOpenNotificationPrefs: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val telemetry = LocalTelemetryColors.current
  val totalMatchedUnits = watchlistMatches.sumOf { it.matchingListings.size }

  HekimaCard(
    title = "Target Price Watchlists",
    subtitle = if (isGuestMode) {
      "Preview rules • Sign in to save custom KES budget alerts"
    } else {
      "${watchlistMatches.count { it.watchlist.isActive }} active rule(s) • $totalMatchedUnits under-budget match(es)"
    },
    modifier = modifier.testTag("watchlist_section_card"),
    trailingContent = {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(
          onClick = onOpenNotificationPrefs,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("watchlist_alert_prefs_button")
        ) {
          Text("Alert Prefs")
        }
        OutlinedButton(
          onClick = onCreateWatchlistClick,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("add_watchlist_button")
        ) {
          Icon(Icons.Filled.AddAlert, null, Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Add")
        }
      }
    }
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      if (watchlistMatches.isEmpty()) {
        Text(
          text = "No target watchlists yet. Set a target vehicle and maximum KES budget to flag bargains automatically across imports and feeds.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      } else {
        watchlistMatches.forEach { match ->
          val wl = match.watchlist
          val hitCount = match.matchingListings.size
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "${wl.make} ${wl.model} (${wl.minYear}–${wl.maxYear})",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold
                )
                Text(
                  text = "Max ${KenyanMarketAnalyticsEngine.formatKes(wl.maxBudgetKes)} • ${wl.preferredImportStatus}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val matchText = if (hitCount > 0 && match.bestPriceKes != null) {
                  "$hitCount match(es) • Best ${KenyanMarketAnalyticsEngine.formatCompactMillions(match.bestPriceKes)}"
                } else {
                  "0 matches in active filters"
                }
                val pushStatusLabel = if (wl.pushAlertsEnabled) "Push On" else "Push Muted"
                Text(
                  text = "$matchText • $pushStatusLabel",
                  style = MaterialTheme.typography.labelSmall,
                  color = if (hitCount > 0) telemetry.positive else MaterialTheme.colorScheme.onSurfaceVariant,
                  fontWeight = FontWeight.SemiBold
                )
              }
              Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                  checked = wl.isActive,
                  onCheckedChange = { checked -> onToggleWatchlist(wl, checked) }
                )
                if (!isGuestMode) {
                  IconButton(
                    onClick = { onDeleteWatchlist(wl.id) },
                    modifier = Modifier.minimumInteractiveComponentSize()
                  ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete watchlist")
                  }
                }
              }
            }
          }
        }
        if (totalMatchedUnits > 0) {
          Button(
            onClick = onBrowseMatchesClick,
            modifier = Modifier
              .fillMaxWidth()
              .minimumInteractiveComponentSize()
              .testTag("browse_watchlist_matches_button")
          ) {
            Icon(Icons.Filled.NotificationsActive, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Inspect $totalMatchedUnits Watchlist Match(es)")
          }
        }
      }
    }
  }
}

@Composable
fun CreateWatchlistDialog(
  availableMakes: List<String>,
  modelsForMake: (String) -> List<String>,
  onDismiss: () -> Unit,
  onSave: (
    make: String,
    model: String,
    minYear: Int,
    maxYear: Int,
    maxBudgetKes: Long,
    preferredImportStatus: String
  ) -> Unit
) {
  val makes = availableMakes.ifEmpty { listOf("Toyota", "Mazda", "Subaru", "Honda") }
  var selectedMake by remember { mutableStateOf(makes.first()) }
  val makeModels = remember(selectedMake) {
    listOf("All") + modelsForMake(selectedMake)
  }
  var selectedModel by remember(selectedMake) {
    mutableStateOf(makeModels.getOrElse(1) { "All" })
  }
  var minYearText by remember { mutableStateOf("2017") }
  var maxYearText by remember { mutableStateOf("2021") }
  var budgetText by remember { mutableStateOf("1500000") }
  var origin by remember { mutableStateOf("All") }
  var errorText by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("New Target Price Watchlist") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DropdownSelectorField(
          label = "Make",
          options = makes,
          selected = selectedMake,
          onSelect = { newMake ->
            selectedMake = newMake
            val nextModels = listOf("All") + modelsForMake(newMake)
            selectedModel = nextModels.getOrElse(1) { "All" }
          },
          testTag = "watchlist_make_selector"
        )
        DropdownSelectorField(
          label = "Model",
          options = makeModels,
          selected = selectedModel,
          onSelect = { selectedModel = it },
          testTag = "watchlist_model_selector"
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = minYearText,
            onValueChange = { minYearText = it.filter(Char::isDigit).take(4) },
            label = { Text("Min Year") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f)
          )
          OutlinedTextField(
            value = maxYearText,
            onValueChange = { maxYearText = it.filter(Char::isDigit).take(4) },
            label = { Text("Max Year") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f)
          )
        }
        OutlinedTextField(
          value = budgetText,
          onValueChange = { budgetText = it.filter(Char::isDigit).take(9) },
          label = { Text("Max Budget (KES)") },
          placeholder = { Text("e.g. 1320000") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("watchlist_budget_input")
        )
        DropdownSelectorField(
          label = "Origin",
          options = listOf("All", "Foreign Used", "Locally Used"),
          selected = origin,
          onSelect = { origin = it }
        )
        if (errorText != null) {
          Text(
            text = errorText!!,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val minY = minYearText.toIntOrNull()?.coerceIn(1995, 2026)
          val maxY = maxYearText.toIntOrNull()?.coerceIn(1995, 2026)
          val budget = budgetText.toLongOrNull()
          if (minY == null || maxY == null || minY > maxY) {
            errorText = "Enter valid Min/Max years (Min <= Max)."
          } else if (budget == null || budget !in 100_000L..150_000_000L) {
            errorText = "Budget must be between KES 100,000 and 150,000,000."
          } else {
            onSave(selectedMake, selectedModel, minY, maxY, budget, origin)
            onDismiss()
          }
        },
        modifier = Modifier.testTag("save_watchlist_confirm_button")
      ) {
        Text("Save Alert")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}
