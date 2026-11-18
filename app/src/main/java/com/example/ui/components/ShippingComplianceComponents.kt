package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.analytics.KenyanMarketAnalyticsEngine
import com.example.domain.model.ExchangeRateSnapshot
import com.example.domain.model.ListingProvenance
import com.example.domain.model.MarketSyncStatus
import com.example.ui.theme.LocalTelemetryColors
import kotlinx.coroutines.delay
import java.util.Locale

enum class VisualSyncState {
  LIVE,
  SYNCING,
  ERROR,
  OFFLINE
}

fun resolveVisualSyncState(
  syncStatus: MarketSyncStatus,
  isRefreshingFx: Boolean
): VisualSyncState = when {
  syncStatus.isSyncingMarket || isRefreshingFx -> VisualSyncState.SYNCING
  !syncStatus.syncErrorMessage.isNullOrBlank() -> VisualSyncState.ERROR
  !syncStatus.isUsingFallbackCatalog -> VisualSyncState.LIVE
  else -> VisualSyncState.OFFLINE
}

@Composable
fun SyncStatusIndicatorPill(
  syncState: VisualSyncState,
  onDarkBackdrop: Boolean = false,
  modifier: Modifier = Modifier
) {
  val telemetry = LocalTelemetryColors.current
  val statusColor = when (syncState) {
    VisualSyncState.LIVE -> telemetry.positive
    VisualSyncState.SYNCING -> telemetry.accentCyan
    VisualSyncState.ERROR -> telemetry.negative
    VisualSyncState.OFFLINE -> telemetry.warning
  }
  val labelText = when (syncState) {
    VisualSyncState.LIVE -> stringResource(R.string.sync_status_live)
    VisualSyncState.SYNCING -> stringResource(R.string.sync_status_syncing)
    VisualSyncState.ERROR -> stringResource(R.string.sync_status_error)
    VisualSyncState.OFFLINE -> stringResource(R.string.sync_status_offline)
  }

  val shouldPulse = syncState == VisualSyncState.LIVE || syncState == VisualSyncState.SYNCING
  val infiniteTransition = rememberInfiniteTransition(label = "sync_pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = if (shouldPulse) 1.9f else 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = if (syncState == VisualSyncState.SYNCING) 750 else 1400),
      repeatMode = RepeatMode.Restart
    ),
    label = "pulse_scale"
  )
  val pulseAlpha by infiniteTransition.animateFloat(
    initialValue = if (shouldPulse) 0.55f else 0f,
    targetValue = 0f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = if (syncState == VisualSyncState.SYNCING) 750 else 1400),
      repeatMode = RepeatMode.Restart
    ),
    label = "pulse_alpha"
  )

  val containerColor = if (onDarkBackdrop) {
    Color(0xBB090F1C)
  } else {
    statusColor.copy(alpha = 0.14f)
  }
  val borderColor = statusColor.copy(alpha = if (onDarkBackdrop) 0.55f else 0.36f)
  val textColor = if (onDarkBackdrop) Color.White else statusColor

  Surface(
    color = containerColor,
    shape = RoundedCornerShape(999.dp),
    border = BorderStroke(1.dp, borderColor),
    modifier = modifier
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(12.dp)
      ) {
        if (shouldPulse) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .scale(pulseScale)
              .alpha(pulseAlpha)
              .clip(CircleShape)
              .background(statusColor)
          )
        }
        Box(
          modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(statusColor)
        )
      }
      Text(
        text = labelText,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = textColor
      )
    }
  }
}

@Composable
fun LastUpdatedTimestampChip(
  lastUpdatedEpochMs: Long,
  accentColor: Color,
  modifier: Modifier = Modifier
) {
  var nowEpochMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(lastUpdatedEpochMs) {
    nowEpochMs = System.currentTimeMillis()
    while (true) {
      delay(30_000L)
      nowEpochMs = System.currentTimeMillis()
    }
  }

  val labelPrefix = stringResource(R.string.last_updated_label)
  val timestampText = if (lastUpdatedEpochMs > 0L) {
    KenyanMarketAnalyticsEngine.formatLastUpdatedDisplay(
      epochMs = lastUpdatedEpochMs,
      nowEpochMs = nowEpochMs
    )
  } else {
    stringResource(R.string.last_updated_awaiting)
  }

  Surface(
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.22f)),
    modifier = modifier.testTag("last_updated_timestamp")
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Icon(
        imageVector = Icons.Filled.Schedule,
        contentDescription = labelPrefix,
        tint = accentColor,
        modifier = Modifier.size(15.dp)
      )
      Text(
        text = "$labelPrefix:",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = timestampText,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarketSyncStatusBar(
  syncStatus: MarketSyncStatus,
  exchangeRate: ExchangeRateSnapshot,
  isRefreshingFx: Boolean,
  onRefreshClick: () -> Unit,
  onManageSourcesClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val telemetry = LocalTelemetryColors.current
  val syncState = resolveVisualSyncState(syncStatus, isRefreshingFx)
  val isLive = !syncStatus.isUsingFallbackCatalog
  val isBusy = syncState == VisualSyncState.SYNCING
  val accentColor = when (syncState) {
    VisualSyncState.LIVE -> telemetry.positive
    VisualSyncState.SYNCING -> telemetry.accentCyan
    VisualSyncState.ERROR -> telemetry.negative
    VisualSyncState.OFFLINE -> telemetry.warning
  }

  val marketHeadline = when (syncState) {
    VisualSyncState.SYNCING -> "Syncing Live Market & FX…"
    VisualSyncState.ERROR -> "Live Sync Needs Attention"
    VisualSyncState.LIVE -> "Live Kenyan Market Active"
    VisualSyncState.OFFLINE -> "Offline Reference Benchmark"
  }

  val effectiveLastUpdatedMs = maxOf(
    syncStatus.lastMarketSyncEpochMs,
    if (!exchangeRate.isFallback) exchangeRate.fetchedAtEpochMs else 0L
  )

  val sourceSummary = if (isLive) {
    buildList {
      if (syncStatus.liveYardCount > 0) add("${syncStatus.liveYardCount} Live Yard")
      if (syncStatus.dealerFeedCount > 0) add("${syncStatus.dealerFeedCount} Dealer Feed")
      if (syncStatus.csvImportCount > 0) add("${syncStatus.csvImportCount} CSV/XLSX")
      if (syncStatus.offlineReferenceCount > 0) add("${syncStatus.offlineReferenceCount} Reference")
    }.joinToString(" • ")
  } else {
    "${syncStatus.offlineReferenceCount} Reference units"
  }

  val fxPillText = if (exchangeRate.isFallback) {
    "USD/KES ~${String.format(Locale.US, "%.2f", exchangeRate.usdToKes)} (Fallback)"
  } else {
    "USD/KES ${String.format(Locale.US, "%.2f", exchangeRate.usdToKes)} (${KenyanMarketAnalyticsEngine.formatRelativeSyncTime(exchangeRate.fetchedAtEpochMs)})"
  }

  Surface(
    color = accentColor.copy(alpha = 0.10f),
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f)),
    modifier = modifier
      .fillMaxWidth()
      .testTag("market_sync_status_bar")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = when (syncState) {
            VisualSyncState.SYNCING -> Icons.Filled.Sync
            VisualSyncState.ERROR -> Icons.Filled.ErrorOutline
            VisualSyncState.LIVE -> Icons.Filled.CloudDone
            VisualSyncState.OFFLINE -> Icons.Filled.CloudOff
          },
          contentDescription = marketHeadline,
          tint = accentColor,
          modifier = Modifier.size(20.dp)
        )
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text(
              text = marketHeadline,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurface
            )
            SyncStatusIndicatorPill(
              syncState = syncState,
              modifier = Modifier.testTag("sync_status_indicator_pill")
            )
          }
        }
        if (!isLive) {
          TextButton(
            onClick = onManageSourcesClick,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("sync_bar_sources_button")
          ) {
            Text("Sources")
          }
        }
        IconButton(
          onClick = onRefreshClick,
          enabled = !isBusy,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("sync_bar_refresh_button")
        ) {
          if (isBusy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
          } else {
            Icon(
              imageVector = Icons.Filled.Refresh,
              contentDescription = "Refresh live FX and market data",
              tint = accentColor
            )
          }
        }
      }

      LastUpdatedTimestampChip(
        lastUpdatedEpochMs = effectiveLastUpdatedMs,
        accentColor = accentColor,
        modifier = Modifier.fillMaxWidth()
      )

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
          shape = RoundedCornerShape(8.dp)
        ) {
          Text(
            text = sourceSummary,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
          shape = RoundedCornerShape(8.dp)
        ) {
          Text(
            text = fxPillText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }
      }

      if (!syncStatus.syncErrorMessage.isNullOrBlank()) {
        Text(
          text = syncStatus.syncErrorMessage,
          style = MaterialTheme.typography.bodySmall,
          color = telemetry.negative
        )
      }
    }
  }
}

@Composable
fun ProvenanceBadge(
  provenance: ListingProvenance,
  updatedAtEpochMs: Long = 0L,
  modifier: Modifier = Modifier
) {
  val telemetry = LocalTelemetryColors.current
  val badgeColor = when (provenance) {
    ListingProvenance.LIVE_YARD -> telemetry.positive
    ListingProvenance.DEALER_FEED -> telemetry.accentCyan
    ListingProvenance.CSV_IMPORT -> MaterialTheme.colorScheme.primary
    ListingProvenance.OFFLINE_REFERENCE -> MaterialTheme.colorScheme.onSurfaceVariant
  }
  val bgAlpha = if (provenance == ListingProvenance.OFFLINE_REFERENCE) 0.10f else 0.15f
  val labelText = if (updatedAtEpochMs > 0L && provenance != ListingProvenance.OFFLINE_REFERENCE) {
    "${provenance.badgeLabel} • ${KenyanMarketAnalyticsEngine.formatRelativeSyncTime(updatedAtEpochMs)}"
  } else {
    provenance.badgeLabel
  }

  Surface(
    color = badgeColor.copy(alpha = bgAlpha),
    shape = RoundedCornerShape(6.dp),
    modifier = modifier
  ) {
    Text(
      text = labelText,
      style = MaterialTheme.typography.labelSmall,
      color = badgeColor,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
    )
  }
}

@Composable
fun KraAdvisoryDisclaimerBanner(
  title: String = "Analytical Estimate & KRA Advisory Notice",
  detail: String = "Valuations, CRSP duty projections, 8-year import eligibility, and fuel running costs are independent estimates based on Kenyan market observations and published KRA schedules—not an official KRA, NTSA, or EPRA assessment. Always verify chassis JEVIC/QISJ logs and confirm final customs duty with a licensed clearing agent.",
  modifier: Modifier = Modifier
) {
  val telemetry = LocalTelemetryColors.current
  Surface(
    color = telemetry.warning.copy(alpha = 0.10f),
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, telemetry.warning.copy(alpha = 0.30f)),
    modifier = modifier
      .fillMaxWidth()
      .testTag("kra_advisory_disclaimer_banner")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.Top
    ) {
      Icon(
        imageVector = Icons.Filled.Info,
        contentDescription = "Advisory disclaimer",
        tint = telemetry.warning,
        modifier = Modifier.size(18.dp)
      )
      Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
          text = title,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = detail,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
fun PostLiveYardListingDialog(
  availableMakes: List<String>,
  modelsForMake: (String) -> List<String>,
  defaultSellerName: String,
  onDismiss: () -> Unit,
  onPublish: (
    make: String,
    model: String,
    year: Int,
    askingPriceKes: Long,
    mileageKm: Int,
    engineCc: Int,
    transmission: String,
    fuelType: String,
    condition: String,
    importStatus: String,
    location: String,
    sellerName: String
  ) -> Unit
) {
  var selectedMake by remember { mutableStateOf(availableMakes.firstOrNull() ?: "Toyota") }
  val makeModels = remember(selectedMake) { modelsForMake(selectedMake).ifEmpty { listOf("Axio") } }
  var selectedModel by remember(selectedMake) { mutableStateOf(makeModels.first()) }
  var selectedYear by remember { mutableStateOf("2019") }
  var priceText by remember { mutableStateOf("1850000") }
  var mileageText by remember { mutableStateOf("68000") }
  var engineText by remember { mutableStateOf("1500") }
  var transmission by remember { mutableStateOf("Automatic") }
  var fuelType by remember { mutableStateOf("Petrol") }
  var condition by remember { mutableStateOf("Good") }
  var importStatus by remember { mutableStateOf("Foreign Used") }
  var location by remember { mutableStateOf("Nairobi") }
  var sellerName by remember { mutableStateOf(defaultSellerName) }
  var validationError by remember { mutableStateOf<String?>(null) }

  val yearOptions = (2012..2025).reversed().map { it.toString() }
  val cityOptions = listOf("Nairobi", "Mombasa", "Kisumu", "Nakuru", "Eldoret", "Thika")

  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.widthIn(max = 560.dp),
    icon = { Icon(Icons.Filled.Storefront, contentDescription = null) },
    title = { Text("Publish Live Yard Listing") },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text(
          text = "Share real-time Kenyan showroom or yard inventory to the authenticated marketplace.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          DropdownSelectorField(
            label = "Make",
            options = availableMakes.ifEmpty { listOf("Toyota", "Mazda", "Subaru", "Honda", "Nissan") },
            selected = selectedMake,
            onSelect = { selectedMake = it },
            modifier = Modifier.weight(1f),
            testTag = "post_listing_make"
          )
          DropdownSelectorField(
            label = "Model",
            options = makeModels,
            selected = selectedModel,
            onSelect = { selectedModel = it },
            modifier = Modifier.weight(1f),
            testTag = "post_listing_model"
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          DropdownSelectorField(
            label = "Year",
            options = yearOptions,
            selected = selectedYear,
            onSelect = { selectedYear = it },
            modifier = Modifier.weight(1f)
          )
          DropdownSelectorField(
            label = "City / Yard",
            options = cityOptions,
            selected = location,
            onSelect = { location = it },
            modifier = Modifier.weight(1f)
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = priceText,
            onValueChange = { priceText = it.filter { ch -> ch.isDigit() } },
            label = { Text("Asking Price (KES)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier
              .weight(1f)
              .testTag("post_listing_price_input")
          )
          OutlinedTextField(
            value = mileageText,
            onValueChange = { mileageText = it.filter { ch -> ch.isDigit() } },
            label = { Text("Mileage (km)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier
              .weight(1f)
              .testTag("post_listing_mileage_input")
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = engineText,
            onValueChange = { engineText = it.filter { ch -> ch.isDigit() } },
            label = { Text("Engine (cc)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f)
          )
          DropdownSelectorField(
            label = "Origin",
            options = listOf("Foreign Used", "Locally Used"),
            selected = importStatus,
            onSelect = { importStatus = it },
            modifier = Modifier.weight(1f)
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          DropdownSelectorField(
            label = "Fuel",
            options = listOf("Petrol", "Hybrid", "Diesel"),
            selected = fuelType,
            onSelect = { fuelType = it },
            modifier = Modifier.weight(1f)
          )
          DropdownSelectorField(
            label = "Condition",
            options = listOf("Excellent", "Good", "Fair"),
            selected = condition,
            onSelect = { condition = it },
            modifier = Modifier.weight(1f)
          )
        }

        DropdownSelectorField(
          label = "Transmission",
          options = listOf("Automatic", "CVT", "Manual"),
          selected = transmission,
          onSelect = { transmission = it },
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = sellerName,
          onValueChange = { sellerName = it },
          label = { Text("Yard / Dealer Display Name") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("post_listing_seller_input")
        )

        if (validationError != null) {
          Text(
            text = validationError!!,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val price = priceText.toLongOrNull()
          val mileage = mileageText.toIntOrNull()
          val engine = engineText.toIntOrNull() ?: 1500
          val year = selectedYear.toIntOrNull() ?: 2019
          when {
            price == null || price !in 100_000L..150_000_000L -> {
              validationError = "Enter an asking price between KES 100,000 and KES 150,000,000."
            }
            mileage == null || mileage !in 0..1_500_000 -> {
              validationError = "Enter valid odometer mileage (0 – 1,500,000 km)."
            }
            else -> {
              onPublish(
                selectedMake,
                selectedModel,
                year,
                price,
                mileage,
                engine.coerceIn(600, 7500),
                transmission,
                fuelType,
                condition,
                importStatus,
                location,
                sellerName.trim()
              )
              onDismiss()
            }
          }
        },
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("confirm_publish_listing_button")
      ) {
        Text("Publish Live")
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
