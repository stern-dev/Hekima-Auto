package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.FilterListOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.FilterState
import com.example.domain.model.ListingProvenance
import com.example.domain.model.VehicleSegment
import com.example.ui.theme.LocalTelemetryColors

@Composable
fun HekimaCard(
  modifier: Modifier = Modifier,
  title: String? = null,
  subtitle: String? = null,
  trailingContent: (@Composable () -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit
) {
  val border = LocalTelemetryColors.current.cardBorder
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, border),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      if (title != null) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = title,
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
              Spacer(Modifier.height(2.dp))
              Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
          if (trailingContent != null) {
            Spacer(Modifier.width(8.dp))
            trailingContent()
          }
        }
        Spacer(Modifier.height(12.dp))
      }
      content()
    }
  }
}

@Composable
fun KpiMetricTile(
  label: String,
  value: String,
  subValue: String,
  icon: ImageVector,
  accentColor: Color = MaterialTheme.colorScheme.primary,
  modifier: Modifier = Modifier
) {
  val border = LocalTelemetryColors.current.cardBorder
  Card(
    modifier = modifier,
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, border)
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = label,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f)
        )
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = accentColor.copy(alpha = 0.14f)
        ) {
          Icon(
            imageVector = icon,
            contentDescription = label,
            tint = accentColor,
            modifier = Modifier
              .padding(6.dp)
              .size(16.dp)
          )
        }
      }
      Spacer(Modifier.height(8.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Spacer(Modifier.height(2.dp))
      Text(
        text = subValue,
        style = MaterialTheme.typography.labelSmall,
        color = accentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownSelectorField(
  label: String,
  options: List<String>,
  selected: String,
  onSelect: (String) -> Unit,
  modifier: Modifier = Modifier,
  testTag: String = ""
) {
  var expanded by remember { mutableStateOf(false) }

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = !expanded },
    modifier = modifier
  ) {
    OutlinedTextField(
      value = selected,
      onValueChange = {},
      readOnly = true,
      label = { Text(label) },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      modifier = Modifier
        .fillMaxWidth()
        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        .let { if (testTag.isNotEmpty()) it.testTag(testTag) else it },
      singleLine = true
    )
    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false }
    ) {
      options.forEach { option ->
        DropdownMenuItem(
          text = { Text(option) },
          onClick = {
            onSelect(option)
            expanded = false
          }
        )
      }
    }
  }
}

@Composable
fun FilterStripBar(
  filterState: FilterState,
  availableMakes: List<String>,
  onUpdateFilter: ((FilterState) -> FilterState) -> Unit,
  onReset: () -> Unit,
  modifier: Modifier = Modifier
) {
  val statusOptions = listOf("All", "Foreign Used", "Locally Used")
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      VehicleSegment.entries.forEach { segment ->
        FilterChip(
          selected = filterState.selectedSegment == segment,
          onClick = { onUpdateFilter { it.copy(selectedSegment = segment) } },
          label = { Text(segment.label) },
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("segment_chip_${segment.name.lowercase()}")
        )
      }
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      (listOf("All") + availableMakes).forEach { make ->
        FilterChip(
          selected = filterState.selectedMake == make,
          onClick = { onUpdateFilter { it.copy(selectedMake = make) } },
          label = { Text(make) },
          modifier = Modifier.minimumInteractiveComponentSize()
        )
      }
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      statusOptions.forEach { status ->
        FilterChip(
          selected = filterState.importStatus == status,
          onClick = { onUpdateFilter { it.copy(importStatus = status) } },
          label = { Text(if (status == "All") "All Origins" else status) },
          modifier = Modifier.minimumInteractiveComponentSize()
        )
      }
      FilterChip(
        selected = filterState.onlyWatchlistMatches,
        onClick = { onUpdateFilter { it.copy(onlyWatchlistMatches = !it.onlyWatchlistMatches) } },
        label = { Text("Watchlist Matches") },
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("filter_watchlist_matches_chip")
      )
      FilterChip(
        selected = filterState.onlyOdometerFlagged,
        onClick = { onUpdateFilter { it.copy(onlyOdometerFlagged = !it.onlyOdometerFlagged) } },
        label = { Text("Odometer Flags") },
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("filter_odometer_flags_chip")
      )
      ListingProvenance.entries.forEach { provenance ->
        FilterChip(
          selected = filterState.provenanceFilter == provenance,
          onClick = {
            onUpdateFilter {
              it.copy(provenanceFilter = if (it.provenanceFilter == provenance) null else provenance)
            }
          },
          label = { Text(provenance.filterLabel) },
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("filter_provenance_${provenance.name.lowercase()}")
        )
      }
      if (filterState != FilterState()) {
        OutlinedButton(
          onClick = onReset,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("reset_filters_button")
        ) {
          Icon(
            imageVector = Icons.Outlined.FilterListOff,
            contentDescription = "Reset filters",
            modifier = Modifier.size(16.dp)
          )
          Spacer(Modifier.width(6.dp))
          Text("Reset")
        }
      }
    }
  }
}

@Composable
fun EmptyAnalyticsState(
  title: String,
  message: String,
  actionLabel: String,
  onAction: () -> Unit,
  modifier: Modifier = Modifier
) {
  HekimaCard(modifier = modifier) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Icon(
        imageVector = Icons.Outlined.Analytics,
        contentDescription = title,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(44.dp)
      )
      Spacer(Modifier.height(12.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium
      )
      Spacer(Modifier.height(6.dp))
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(Modifier.height(16.dp))
      Button(
        onClick = onAction,
        modifier = Modifier.minimumInteractiveComponentSize()
      ) {
        Text(actionLabel)
      }
    }
  }
}
