package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.core.content.ContextCompat
import com.example.domain.model.DataSourceRecord
import com.example.domain.model.FeedSubscription
import com.example.domain.model.ImportResult
import com.example.ui.components.HekimaCard
import com.example.ui.theme.LocalTelemetryColors

@Composable
fun DataManagementScreen(
  includeSeedData: Boolean,
  totalActiveCount: Int,
  dataSources: List<DataSourceRecord>,
  lastImportResult: ImportResult?,
  isImporting: Boolean,
  onToggleSeedData: (Boolean) -> Unit,
  onImportUri: (android.content.Context, android.net.Uri) -> Unit,
  onImportSampleCsv: () -> Unit,
  onImportFeedUrl: (String) -> Unit = {},
  lastImportWatchlistAlert: String? = null,
  onViewWatchlistMatches: () -> Unit = {},
  onClearWatchlistAlert: () -> Unit = {},
  onToggleSourceActive: (DataSourceRecord, Boolean) -> Unit,
  onDeleteSource: (String) -> Unit,
  feedSubscriptions: List<FeedSubscription> = emptyList(),
  onSaveFeedSubscription: (name: String, feedUrl: String, intervalHours: Int, alertsEnabled: Boolean) -> Unit = { _, _, _, _ -> },
  onToggleSubscriptionAutoSync: (FeedSubscription, Boolean) -> Unit = { _, _ -> },
  onToggleSubscriptionAlerts: (FeedSubscription, Boolean) -> Unit = { _, _ -> },
  onDeleteFeedSubscription: (String) -> Unit = {},
  onSyncSubscribedFeedsNow: (android.content.Context) -> Unit = {},
  isGuestMode: Boolean = false,
  onRequestSignIn: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val telemetry = LocalTelemetryColors.current
  var feedUrl by remember { mutableStateOf("") }
  var subscriptionName by remember { mutableStateOf("") }
  var selectedIntervalHours by remember { mutableIntStateOf(12) }
  var alertsEnabledDraft by remember { mutableStateOf(true) }

  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    if (!granted) {
      alertsEnabledDraft = false
    }
  }

  fun requestNotificationPermissionIfNeeded() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
      if (!granted) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  val filePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null) {
      onImportUri(context, uri)
    }
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
      .testTag("data_management_lazy_column"),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Spacer(Modifier.height(4.dp))
      Text("Data Management", style = MaterialTheme.typography.headlineMedium)
      Text(
        text = "Import Kenyan CSV/XLSX spreadsheets with automatic normalization, KES standardization, and deduplication.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    // Import Controls Card
    item {
      HekimaCard(
        title = "Import Marketplace Listings",
        subtitle = "Zero-permission Storage Access Framework • Supports .csv & .xlsx"
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Expected columns: make, model, year, price (supports KSh 1.45M / KES), mileage, engine_cc, transmission, fuel_type, condition, import_status, location.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
              onClick = {
                if (isGuestMode) {
                  onRequestSignIn()
                } else {
                  filePickerLauncher.launch(
                    arrayOf(
                      "text/csv",
                      "text/comma-separated-values",
                      "text/plain",
                      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                      "*/*"
                    )
                  )
                }
              },
              enabled = !isImporting,
              modifier = Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("import_csv_button")
            ) {
              if (isImporting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
              } else {
                Icon(Icons.Filled.CloudUpload, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Select File")
              }
            }

            OutlinedButton(
              onClick = {
                if (isGuestMode) {
                  onRequestSignIn()
                } else {
                  onImportSampleCsv()
                }
              },
              enabled = !isImporting,
              modifier = Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("import_sample_csv_button")
            ) {
              Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
              Spacer(Modifier.width(4.dp))
              Text("Sample Batch")
            }
          }
        }
      }
    }

    // Remote Market Feed URL (CSV / JSON)
    item {
      HekimaCard(
        title = "Live Market Feed URL",
        subtitle = "Fetch & normalize Kenyan inventory directly from an HTTPS CSV or JSON feed"
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = feedUrl,
            onValueChange = { feedUrl = it },
            label = { Text("HTTPS Feed URL (.csv or .json)") },
            placeholder = { Text("https://example.co.ke/inventory.json") },
            leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("feed_url_input")
          )
          Button(
            onClick = {
              if (isGuestMode) {
                onRequestSignIn()
              } else {
                onImportFeedUrl(feedUrl.trim())
              }
            },
            enabled = !isImporting && (isGuestMode || feedUrl.isNotBlank()),
            modifier = Modifier
              .fillMaxWidth()
              .minimumInteractiveComponentSize()
              .testTag("fetch_feed_url_button")
          ) {
            Icon(Icons.Filled.Link, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Fetch & Sync Remote Feed")
          }
        }
      }
    }

    // Recurring Auto-Sync Dealership Feed Subscriptions
    item {
      HekimaCard(
        title = "Auto-Sync Subscriptions & Alerts",
        subtitle = "Schedule WorkManager syncs and push alerts when new listings match your Watchlists"
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = subscriptionName,
            onValueChange = { subscriptionName = it },
            label = { Text("Dealership / Feed Label (Optional)") },
            placeholder = { Text("e.g. Mombasa Yard Daily Stock") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("feed_subscription_name_input")
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Filled.Schedule, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Interval:", style = MaterialTheme.typography.labelMedium)
            listOf(6, 12, 24).forEach { hrs ->
              FilterChip(
                selected = selectedIntervalHours == hrs,
                onClick = { selectedIntervalHours = hrs },
                label = { Text("${hrs}h") },
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("sync_interval_${hrs}h")
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Push Alert on Watchlist Match", style = MaterialTheme.typography.labelLarge)
              Text(
                text = "Notify when newly synced cars fall under your budget",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Switch(
              checked = alertsEnabledDraft,
              onCheckedChange = { checked ->
                alertsEnabledDraft = checked
                if (checked) requestNotificationPermissionIfNeeded()
              },
              modifier = Modifier.testTag("subscription_alerts_switch")
            )
          }

          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
              onClick = {
                if (isGuestMode) {
                  onRequestSignIn()
                } else {
                  if (alertsEnabledDraft) requestNotificationPermissionIfNeeded()
                  onSaveFeedSubscription(
                    subscriptionName.trim(),
                    feedUrl.trim(),
                    selectedIntervalHours,
                    alertsEnabledDraft
                  )
                }
              },
              enabled = isGuestMode || feedUrl.isNotBlank(),
              modifier = Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("save_feed_subscription_button")
            ) {
              Icon(Icons.Filled.NotificationsActive, null, Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Save Auto-Sync")
            }
            OutlinedButton(
              onClick = {
                if (isGuestMode) {
                  onRequestSignIn()
                } else {
                  onSyncSubscribedFeedsNow(context)
                }
              },
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("sync_feeds_now_button")
            ) {
              Icon(Icons.Filled.Sync, null, Modifier.size(18.dp))
              Spacer(Modifier.width(4.dp))
              Text("Sync Now")
            }
          }
        }
      }
    }

    if (feedSubscriptions.isNotEmpty()) {
      items(feedSubscriptions, key = { "sub_${it.id}" }) { sub ->
        HekimaCard {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(sub.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                  text = sub.feedUrl,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary
                )
                Text(
                  text = "Every ${sub.syncIntervalHours}h • ${sub.lastStatusMessage}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              IconButton(
                onClick = { onDeleteFeedSubscription(sub.id) },
                modifier = Modifier.minimumInteractiveComponentSize()
              ) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete feed subscription")
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Auto-Sync", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(6.dp))
                Switch(
                  checked = sub.autoSyncEnabled,
                  onCheckedChange = { onToggleSubscriptionAutoSync(sub, it) }
                )
              }
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Alerts", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(6.dp))
                Switch(
                  checked = sub.alertsEnabled,
                  onCheckedChange = { enabled ->
                    if (enabled) requestNotificationPermissionIfNeeded()
                    onToggleSubscriptionAlerts(sub, enabled)
                  }
                )
              }
            }
          }
        }
      }
    }

    if (lastImportWatchlistAlert != null) {
      item {
        Surface(
          color = telemetry.positive.copy(alpha = 0.14f),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("data_watchlist_alert_banner")
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(Icons.Filled.NotificationsActive, null, tint = telemetry.positive, modifier = Modifier.size(20.dp))
              Text(
                text = lastImportWatchlistAlert,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
              )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Button(
                onClick = onViewWatchlistMatches,
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("data_view_watchlist_matches_button")
              ) {
                Text("View Matches")
              }
              TextButton(
                onClick = onClearWatchlistAlert,
                modifier = Modifier.minimumInteractiveComponentSize()
              ) {
                Text("Dismiss")
              }
            }
          }
        }
      }
    }

    // Cleaning & Normalization Report
    if (lastImportResult != null) {
      item {
        HekimaCard(
          title = "Last Cleaning Pipeline Report",
          subtitle = "${lastImportResult.sourceName} (${lastImportResult.sourceType.uppercase()})"
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              PipelineStat("Rows Read", "${lastImportResult.totalRowsRead}")
              PipelineStat("Cleaned & Synced", "${lastImportResult.importedListings.size}")
              PipelineStat("Duplicates Skipped", "${lastImportResult.duplicatesSkipped}")
              PipelineStat("Invalid", "${lastImportResult.invalidRowsSkipped}")
            }
            lastImportResult.cleaningNotes.forEach { note ->
              Text(
                text = "• $note",
                style = MaterialTheme.typography.bodySmall,
                color = telemetry.positive
              )
            }
          }
        }
      }
    }

    // Offline Reference Fallback Catalog Card
    item {
      HekimaCard(
        title = "Active Data Sources ($totalActiveCount listings)",
        subtitle = "Offline reference catalog automatically yields once live yard, feed, or CSV data loads"
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Dataset, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column {
              Text("Offline Reference Catalog (Fallback)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
              Text(
                text = "Kenyan multi-segment baseline • Auto-disabled when live data is active",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
          Switch(
            checked = includeSeedData,
            onCheckedChange = onToggleSeedData,
            modifier = Modifier.testTag("toggle_seed_data_switch")
          )
        }
      }
    }

    items(dataSources, key = { it.id }) { source ->
      HekimaCard {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(source.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
              text = "${source.rowsImported} listings • ${source.duplicatesSkipped} dupes skipped • ${source.sourceType.uppercase()}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
              checked = source.isActive,
              onCheckedChange = { checked -> onToggleSourceActive(source, checked) }
            )
            IconButton(
              onClick = { onDeleteSource(source.id) },
              modifier = Modifier.minimumInteractiveComponentSize()
            ) {
              Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete data source")
            }
          }
        }
      }
    }

    item { Spacer(Modifier.height(16.dp)) }
  }
}

@Composable
private fun PipelineStat(label: String, value: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
