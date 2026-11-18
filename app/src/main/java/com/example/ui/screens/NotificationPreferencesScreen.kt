package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.sync.WatchlistNotificationHelper
import com.example.domain.analytics.KenyanMarketAnalyticsEngine
import com.example.domain.model.FeedSubscription
import com.example.domain.model.NotificationPreferences
import com.example.domain.model.PriceDropAlertFrequency
import com.example.domain.model.PriceWatchlist
import com.example.domain.model.WatchlistMatch
import com.example.ui.components.EmptyAnalyticsState
import com.example.ui.components.HekimaCard
import com.example.ui.theme.LocalTelemetryColors

@Composable
fun NotificationPreferencesScreen(
  preferences: NotificationPreferences,
  watchlists: List<PriceWatchlist>,
  watchlistMatches: List<WatchlistMatch>,
  feedSubscriptions: List<FeedSubscription>,
  isGuestMode: Boolean,
  onToggleMasterPush: (Boolean) -> Unit,
  onSelectFrequency: (PriceDropAlertFrequency) -> Unit,
  onToggleWatchlistPush: (PriceWatchlist, Boolean) -> Unit,
  onOpenExplorerWatchlists: () -> Unit,
  onOpenDataFeeds: () -> Unit,
  onRequestSignIn: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val telemetry = LocalTelemetryColors.current

  var hasPermission by remember {
    mutableStateOf(WatchlistNotificationHelper.hasNotificationPermission(context))
  }
  var testAlertMessage by remember { mutableStateOf<String?>(null) }

  DisposableEffect(lifecycleOwner, context) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        hasPermission = WatchlistNotificationHelper.hasNotificationPermission(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    hasPermission = granted || WatchlistNotificationHelper.hasNotificationPermission(context)
    testAlertMessage = if (granted) {
      "Android notification permission granted."
    } else {
      "Notification permission denied. You can still inspect watchlist matches inside Explorer."
    }
  }

  val matchesByWatchlistId = remember(watchlistMatches) {
    watchlistMatches.associateBy { it.watchlist.id }
  }
  val totalMatches = remember(watchlistMatches) {
    watchlistMatches.sumOf { it.matchingListings.size }
  }
  val enabledWatchlistPushCount = remember(watchlists) {
    watchlists.count { it.isActive && it.pushAlertsEnabled }
  }
  val activeAlertFeedsCount = remember(feedSubscriptions) {
    feedSubscriptions.count { it.autoSyncEnabled && it.alertsEnabled }
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Spacer(Modifier.height(4.dp))
      Text(
        text = stringResource(R.string.notification_prefs_title),
        style = MaterialTheme.typography.headlineMedium
      )
      Text(
        text = stringResource(R.string.notification_prefs_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    item {
      val statusColor = if (hasPermission) telemetry.positive else telemetry.warning
      HekimaCard(
        title = "Android Alert Delivery",
        subtitle = if (hasPermission) {
          stringResource(R.string.notification_permission_granted)
        } else {
          "Android 13+ requires notification permission for background deal alerts"
        },
        trailingContent = {
          Surface(
            color = statusColor.copy(alpha = 0.14f),
            shape = RoundedCornerShape(999.dp)
          ) {
            Text(
              text = if (hasPermission) "Permitted" else "Action Needed",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              color = statusColor,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
          }
        }
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              Button(
                onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                modifier = Modifier
                  .weight(1f)
                  .minimumInteractiveComponentSize()
                  .testTag("request_notification_permission_button")
              ) {
                Icon(Icons.Filled.NotificationsActive, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.notification_permission_required))
              }
            }
            OutlinedButton(
              onClick = {
                if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                  permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                  val posted = WatchlistNotificationHelper.notifyWatchlistMatches(
                    context = context,
                    matchCount = maxOf(1, totalMatches),
                    sourceName = "Hekima Watchlist Alert Check"
                  )
                  testAlertMessage = if (posted) {
                    "Test price-drop notification sent to your Android notification shade."
                  } else {
                    "Unable to post system notification on this device."
                  }
                }
              },
              modifier = Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("send_test_notification_button")
            ) {
              Icon(Icons.Filled.NotificationsActive, null, Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text(stringResource(R.string.notification_send_test))
            }
          }

          if (testAlertMessage != null) {
            Text(
              text = testAlertMessage!!,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary
            )
          }
        }
      }
    }

    item {
      HekimaCard(
        title = stringResource(R.string.notification_master_title),
        subtitle = if (preferences.pushAlertsEnabled) {
          "Active • ${preferences.frequency.label} • $enabledWatchlistPushCount watchlist(s) subscribed"
        } else {
          "Paused • Watchlist matches still highlight inside Explorer"
        },
        trailingContent = {
          Switch(
            checked = preferences.pushAlertsEnabled,
            onCheckedChange = onToggleMasterPush,
            modifier = Modifier.testTag("master_push_alerts_switch")
          )
        }
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = if (preferences.pushAlertsEnabled) {
                Icons.Filled.NotificationsActive
              } else {
                Icons.Filled.NotificationsOff
              },
              contentDescription = null,
              tint = if (preferences.pushAlertsEnabled) telemetry.positive else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(20.dp)
            )
            Text(
              text = if (preferences.pushAlertsEnabled) {
                "Background dealership feed syncs will notify you when prices drop within your active watchlists."
              } else {
                "System push notifications are globally paused. Turn this switch on to resume alerts."
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.Schedule,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = stringResource(R.string.notification_frequency_title),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold
            )
          }

          PriceDropAlertFrequency.entries.forEach { freq ->
            val selected = preferences.frequency == freq
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .clickable { onSelectFrequency(freq) }
                .padding(vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              RadioButton(
                selected = selected,
                onClick = { onSelectFrequency(freq) },
                modifier = Modifier.testTag("frequency_radio_${freq.key}")
              )
              Spacer(Modifier.width(8.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = freq.label,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                )
                Text(
                  text = freq.description,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }

          if (preferences.lastNotifiedEpochMs > 0L) {
            Text(
              text = "Last price-drop alert sent: ${KenyanMarketAnalyticsEngine.formatRelativeSyncTime(preferences.lastNotifiedEpochMs)}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }

    item {
      HekimaCard(
        title = stringResource(R.string.notification_watchlists_title),
        subtitle = if (isGuestMode) {
          "Preview watchlists shown • Sign in with Google to save custom watchlist push toggles"
        } else {
          "$enabledWatchlistPushCount of ${watchlists.size} watchlist(s) configured for push alerts"
        },
        trailingContent = {
          OutlinedButton(
            onClick = onOpenExplorerWatchlists,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("manage_watchlists_explorer_button")
          ) {
            Icon(Icons.Filled.Tune, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Edit Rules")
          }
        }
      ) {
        if (watchlists.isEmpty()) {
          EmptyAnalyticsState(
            title = "No Target Price Watchlists",
            message = "Create a target vehicle watchlist in Explorer to enable vehicle-specific price-drop notifications.",
            actionLabel = "Add Watchlist in Explorer",
            onAction = onOpenExplorerWatchlists
          )
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            watchlists.forEach { wl ->
              val match = matchesByWatchlistId[wl.id]
              val hitCount = match?.matchingListings?.size ?: 0
              val bestPrice = match?.bestPriceKes
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                  ) {
                    Text(
                      text = "${wl.make} ${wl.model} (${wl.minYear}–${wl.maxYear})",
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.SemiBold
                    )
                    Text(
                      text = "Budget ≤ ${KenyanMarketAnalyticsEngine.formatKes(wl.maxBudgetKes)} • ${wl.preferredImportStatus}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val statusLine = when {
                      !wl.isActive -> "Rule paused in Explorer"
                      hitCount > 0 && bestPrice != null ->
                        "$hitCount match(es) now • Best ${KenyanMarketAnalyticsEngine.formatCompactMillions(bestPrice)}"
                      else -> "Monitoring future feed drops"
                    }
                    Text(
                      text = statusLine,
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.SemiBold,
                      color = if (hitCount > 0 && wl.isActive) telemetry.positive else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  Spacer(Modifier.width(12.dp))
                  Column(horizontalAlignment = Alignment.End) {
                    Switch(
                      checked = wl.pushAlertsEnabled,
                      onCheckedChange = { enabled -> onToggleWatchlistPush(wl, enabled) },
                      modifier = Modifier.testTag("watchlist_alert_switch_${wl.id}")
                    )
                    Text(
                      text = if (wl.pushAlertsEnabled) "Push On" else "Muted",
                      style = MaterialTheme.typography.labelSmall,
                      color = if (wl.pushAlertsEnabled) telemetry.positive else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }
            }

            if (isGuestMode) {
              FilledTonalButton(
                onClick = onRequestSignIn,
                modifier = Modifier
                  .fillMaxWidth()
                  .minimumInteractiveComponentSize()
                  .testTag("notifications_guest_signin_button")
              ) {
                Text("Sign in with Google to Sync Watchlist Alerts")
              }
            }
          }
        }
      }
    }

    item {
      HekimaCard(
        title = "Background Feed Trigger Status",
        subtitle = "WorkManager checks subscribed HTTPS dealership feeds even when closed",
        trailingContent = {
          Icon(Icons.Filled.CloudSync, null, tint = MaterialTheme.colorScheme.primary)
        }
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = if (isGuestMode) {
              "Sign in and add HTTPS dealership feed URLs in Data Management to automate background price-drop checks."
            } else {
              "$activeAlertFeedsCount of ${feedSubscriptions.size} subscribed dealership feed(s) have auto-sync & price alerts enabled."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          OutlinedButton(
            onClick = onOpenDataFeeds,
            modifier = Modifier
              .fillMaxWidth()
              .minimumInteractiveComponentSize()
              .testTag("open_feed_schedules_button")
          ) {
            Icon(Icons.Filled.CloudSync, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Manage Auto-Sync Dealership Feeds")
          }
        }
      }
    }

    item { Spacer(Modifier.height(16.dp)) }
  }
}
