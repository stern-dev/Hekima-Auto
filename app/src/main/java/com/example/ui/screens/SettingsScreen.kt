package com.example.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.auth.signOutUser
import com.example.domain.analytics.KenyanMarketAnalyticsEngine
import com.example.domain.model.AccountDeletionState
import com.example.domain.model.ExchangeRateSnapshot
import com.example.domain.model.MarketSyncStatus
import com.example.domain.model.NotificationPreferences
import com.example.domain.model.ThemeMode
import com.example.domain.model.UserProfile
import com.example.ui.components.AccountDeletionStatusDialog
import com.example.ui.components.DeleteAccountConfirmDialog
import com.example.ui.components.HekimaCard
import com.example.ui.components.KraAdvisoryDisclaimerBanner
import com.example.ui.components.PrivacyPolicyDialog
import java.util.Locale

@Composable
fun SettingsScreen(
  userProfile: UserProfile?,
  userEmail: String?,
  userDisplayName: String?,
  themeMode: ThemeMode,
  totalActiveListings: Int,
  savedVehiclesCount: Int,
  marketSyncStatus: MarketSyncStatus = MarketSyncStatus(),
  exchangeRate: ExchangeRateSnapshot = ExchangeRateSnapshot(),
  notificationPreferences: NotificationPreferences = NotificationPreferences(),
  activeWatchlistAlertCount: Int = 0,
  accountDeletionState: AccountDeletionState = AccountDeletionState.Idle,
  onSelectTheme: (ThemeMode) -> Unit,
  onOpenNotificationPreferences: () -> Unit = {},
  onRefreshLiveData: () -> Unit = {},
  onDeleteAccount: (android.content.Context, CredentialManager) -> Unit = { _, _ -> },
  onReauthenticateAndRetryDelete: (android.content.Context, CredentialManager) -> Unit = { _, _ -> },
  onDismissAccountDeletionState: () -> Unit = {},
  isGuestMode: Boolean = false,
  onRequestSignIn: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
  var showDeleteConfirmDialog by remember { mutableStateOf(false) }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Spacer(Modifier.height(4.dp))
      Text("Settings", style = MaterialTheme.typography.headlineMedium)
      Text(
        text = "Manage appearance, cloud sync account, privacy controls, and market diagnostics.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    item {
      HekimaCard(
        title = "Google Account & Cloud Sync",
        subtitle = "Saved vehicles, comparisons, valuations, inspections, and feeds sync securely"
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Filled.AccountCircle,
              contentDescription = "Account avatar",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
              Text(
                text = if (isGuestMode) {
                  "Guest Preview Mode"
                } else {
                  userProfile?.displayName?.ifBlank { userDisplayName ?: "Hekima Analyst" }
                    ?: userDisplayName ?: "Hekima Analyst"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
              )
              Text(
                text = if (isGuestMode) {
                  "Sign in to sync saved vehicles, live yard posts, and CSV feeds"
                } else {
                  userEmail ?: userProfile?.email ?: "Signed in with Google"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          if (isGuestMode) {
            Button(
              onClick = onRequestSignIn,
              modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("settings_sign_in_button")
            ) {
              Icon(Icons.AutoMirrored.Filled.Login, null, Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text("Sign in with Google")
            }
          } else {
            OutlinedButton(
              onClick = { signOutUser(credentialManager, scope) },
              modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("sign_out_button")
            ) {
              Icon(Icons.AutoMirrored.Filled.Logout, null, Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text("Sign Out")
            }

            Button(
              onClick = { showDeleteConfirmDialog = true },
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
              ),
              modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("delete_account_button")
            ) {
              Icon(Icons.Filled.DeleteForever, null, Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text("Delete Account & Cloud Data")
            }
          }
        }
      }
    }

    item {
      HekimaCard(
        title = "Notification Preferences & Watchlist Alerts",
        subtitle = if (notificationPreferences.pushAlertsEnabled) {
          "${notificationPreferences.frequency.label} • $activeWatchlistAlertCount watchlist(s) subscribed"
        } else {
          "Push notifications paused • In-app Explorer highlights still active"
        },
        trailingContent = {
          Icon(Icons.Filled.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
        }
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Choose how often you receive price-drop updates and toggle Android push alerts individually per vehicle watchlist.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          OutlinedButton(
            onClick = onOpenNotificationPreferences,
            modifier = Modifier
              .fillMaxWidth()
              .minimumInteractiveComponentSize()
              .testTag("open_notification_preferences_button")
          ) {
            Icon(Icons.Filled.NotificationsActive, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Manage Alert Preferences")
          }
        }
      }
    }

    item {
      HekimaCard(
        title = "Privacy Policy & Data Safety",
        subtitle = "Transparent disclosure of cloud storage, live FX & feed requests, and deletion rights",
        trailingContent = {
          Icon(Icons.Filled.Policy, null, tint = MaterialTheme.colorScheme.primary)
        }
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Hekima Auto collects zero advertising telemetry and requests zero broad storage permissions. All user-uploaded CSVs, watchlists, and inspections remain isolated to your authenticated Google UID.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          OutlinedButton(
            onClick = { showPrivacyPolicyDialog = true },
            modifier = Modifier
              .fillMaxWidth()
              .minimumInteractiveComponentSize()
              .testTag("view_privacy_policy_button")
          ) {
            Icon(Icons.Filled.Policy, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("View Privacy Policy & Data Handling")
          }
        }
      }
    }

    item {
      HekimaCard(
        title = "Appearance & Theme",
        subtitle = "Follows Android system theme by default; override anytime",
        trailingContent = {
          Icon(Icons.Filled.Palette, null, tint = MaterialTheme.colorScheme.primary)
        }
      ) {
        Column {
          ThemeMode.entries.forEach { mode ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              RadioButton(
                selected = themeMode == mode,
                onClick = { onSelectTheme(mode) },
                modifier = Modifier.testTag("theme_radio_${mode.key}")
              )
              Spacer(Modifier.width(8.dp))
              Column {
                Text(mode.label, style = MaterialTheme.typography.titleSmall)
                val desc = when (mode) {
                  ThemeMode.SYSTEM -> "Automatically adapts to Android dark/light setting"
                  ThemeMode.DARK -> "Deep obsidian navy & charcoal fintech interface"
                  ThemeMode.LIGHT -> "Clean analytical slate & white daytime interface"
                }
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
      }
    }

    item {
      HekimaCard(
        title = "Platform & Realtime Diagnostics",
        subtitle = "Hekima Auto Intelligence • Kenyan Market Engine",
        trailingContent = {
          OutlinedButton(
            onClick = onRefreshLiveData,
            modifier = Modifier.minimumInteractiveComponentSize()
          ) {
            Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Sync")
          }
        }
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          DiagRow("Currency Standardization", "Kenyan Shilling (KES)")
          DiagRow(
            label = "Market Mode",
            value = if (marketSyncStatus.isUsingFallbackCatalog) "Offline Reference Fallback" else "Live Market Active"
          )
          DiagRow("Active Listings", "$totalActiveListings (${marketSyncStatus.liveYardCount} yard • ${marketSyncStatus.dealerFeedCount} feed • ${marketSyncStatus.csvImportCount} CSV)")
          DiagRow(
            label = "Last Market Sync",
            value = KenyanMarketAnalyticsEngine.formatRelativeSyncTime(marketSyncStatus.lastMarketSyncEpochMs)
          )
          DiagRow(
            label = "USD/KES FX Rate",
            value = "${String.format(Locale.US, "%.2f", exchangeRate.usdToKes)} KES (${if (exchangeRate.isFallback) "Offline" else KenyanMarketAnalyticsEngine.formatRelativeSyncTime(exchangeRate.fetchedAtEpochMs)})"
          )
          DiagRow("Cloud Saved Vehicles", "$savedVehiclesCount bookmarked")
          DiagRow("Pricing & Duty Engine", "Log-OLS + KNN / EAC CET & KRA CRSP")
        }
      }
    }

    item {
      KraAdvisoryDisclaimerBanner()
    }

    item { Spacer(Modifier.height(16.dp)) }
  }

  if (showPrivacyPolicyDialog) {
    PrivacyPolicyDialog(onDismiss = { showPrivacyPolicyDialog = false })
  }

  if (showDeleteConfirmDialog) {
    DeleteAccountConfirmDialog(
      onDismiss = { showDeleteConfirmDialog = false },
      onConfirmDelete = {
        showDeleteConfirmDialog = false
        onDeleteAccount(context, credentialManager)
      }
    )
  }

  AccountDeletionStatusDialog(
    state = accountDeletionState,
    onReauthenticateAndRetry = {
      onReauthenticateAndRetryDelete(context, credentialManager)
    },
    onDismiss = onDismissAccountDeletionState
  )
}

@Composable
private fun DiagRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
  }
}
