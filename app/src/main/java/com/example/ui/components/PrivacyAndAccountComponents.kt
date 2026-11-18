package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AccountDeletionState

@Composable
fun PrivacyPolicyDialog(
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.widthIn(max = 580.dp),
    icon = { Icon(Icons.Filled.Policy, contentDescription = null) },
    title = { Text("Privacy Policy & Data Handling") },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        PolicySection(
          heading = "1. Account & Authentication Data",
          body = "Hekima Auto uses Google Sign-In via Android Credential Manager and Firebase Authentication. When signed in, your Firebase user ID, display name, and email address are stored in your private profile document to isolate your garage bookmarks, watchlists, and yard inspections."
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        PolicySection(
          heading = "2. Cloud & Shared Market Data",
          body = "Saved vehicles, comparisons, valuation reports, imported CSV/XLSX datasets, feed subscriptions, and yard inspection scorecards are private to your account. If you choose to publish a unit to the Live Yard Marketplace, its vehicle specifications, KES price, city, and seller display name become visible to other signed-in Hekima Auto users."
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        PolicySection(
          heading = "3. External Network & AI Services",
          body = "• Exchange Rates: Fetches public USD/KES and JPY/KES currency quotes from open.er-api.com and caches them locally via Jetpack DataStore.\n• Dealership Feeds: Fetches only HTTPS CSV/JSON URLs you explicitly configure.\n• AI Auction Sheet Analyzer: When submitted by you, listing text or photos selected via the Android Photo Picker are processed through Firebase AI Logic."
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        PolicySection(
          heading = "4. Device Permissions & Tracking",
          body = "Hekima Auto uses zero-permission Android Photo Picker and Storage Access Framework for files—it never requests broad external media storage permissions. Optional notification permission (API 33+) is used solely for watchlist price alerts you enable. No third-party advertising SDKs or behavioral trackers are included."
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        PolicySection(
          heading = "5. Account & Cloud Data Deletion",
          body = "You may permanently delete your Firebase Authentication account and all cloud records (saved garage, watchlists, inspections, imported sheets, feed subscriptions, and live yard posts) at any time from Settings → Delete Account & Cloud Data."
        )
      }
    },
    confirmButton = {
      Button(
        onClick = onDismiss,
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("close_privacy_policy_button")
      ) {
        Text("Close")
      }
    }
  )
}

@Composable
private fun PolicySection(heading: String, body: String) {
  Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
    Text(
      text = heading,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface
    )
    Text(
      text = body,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
fun DeleteAccountConfirmDialog(
  onDismiss: () -> Unit,
  onConfirmDelete: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.widthIn(max = 520.dp),
    icon = {
      Icon(
        imageVector = Icons.Filled.DeleteForever,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error
      )
    },
    title = { Text("Delete Account & All Cloud Data?") },
    text = {
      Text(
        text = "This permanently deletes your Hekima Auto profile, saved garage vehicles, comparisons, valuation reports, watchlists, yard inspection scorecards, imported datasets, feed subscriptions, and live yard listings. This action cannot be undone.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    },
    confirmButton = {
      Button(
        onClick = onConfirmDelete,
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.error,
          contentColor = MaterialTheme.colorScheme.onError
        ),
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("confirm_delete_account_button")
      ) {
        Text("Permanently Delete")
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.minimumInteractiveComponentSize()
      ) {
        Text("Cancel")
      }
    }
  )
}

@Composable
fun AccountDeletionStatusDialog(
  state: AccountDeletionState,
  onReauthenticateAndRetry: () -> Unit,
  onDismiss: () -> Unit
) {
  when (state) {
    AccountDeletionState.Idle, AccountDeletionState.Completed -> Unit
    AccountDeletionState.Deleting -> {
      AlertDialog(
        onDismissRequest = {},
        title = { Text("Deleting Account & Cloud Data") },
        text = {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
            Spacer(Modifier.width(14.dp))
            Text(
              text = "Erasing Firestore collections, live yard posts, and Firebase credentials...",
              style = MaterialTheme.typography.bodyMedium
            )
          }
        },
        confirmButton = {}
      )
    }
    AccountDeletionState.RequiresRecentLogin -> {
      AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.VerifiedUser, contentDescription = null) },
        title = { Text("Confirm Google Identity") },
        text = {
          Text(
            text = "For security, Firebase requires a fresh Google Sign-In before permanently removing your account. Your cloud records have been queued for cleanup—verify with Google to finish deleting your account.",
            style = MaterialTheme.typography.bodyMedium
          )
        },
        confirmButton = {
          Button(
            onClick = onReauthenticateAndRetry,
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError
            ),
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("reauth_delete_account_button")
          ) {
            Text("Verify & Delete")
          }
        },
        dismissButton = {
          TextButton(
            onClick = onDismiss,
            modifier = Modifier.minimumInteractiveComponentSize()
          ) {
            Text("Cancel")
          }
        }
      )
    }
    is AccountDeletionState.Error -> {
      AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Account Deletion Notice") },
        text = {
          Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
          )
        },
        confirmButton = {
          Button(
            onClick = onDismiss,
            modifier = Modifier.minimumInteractiveComponentSize()
          ) {
            Text("OK")
          }
        }
      )
    }
  }
}
