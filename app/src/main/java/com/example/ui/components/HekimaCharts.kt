package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.analytics.KenyanMarketAnalyticsEngine
import com.example.domain.model.BrandPriceStat
import com.example.domain.model.BrandShareSlice
import com.example.domain.model.ComparisonMetric
import com.example.domain.model.HistogramBucket
import com.example.domain.model.ModelDepreciationSeries
import com.example.domain.model.ScatterPoint
import com.example.ui.theme.LocalTelemetryColors
import kotlin.math.hypot
import kotlin.math.max

@Composable
fun BrandPriceBarChart(
  stats: List<BrandPriceStat>,
  modifier: Modifier = Modifier
) {
  val palette = LocalTelemetryColors.current.chartPalette
  val items = stats.take(8)
  var selectedIdx by remember(items) { mutableIntStateOf(0) }

  if (items.isEmpty()) {
    EmptyChartPlaceholder("No brand pricing data for current filters.", modifier)
    return
  }

  val maxAvg = items.maxOf { it.averagePriceKes }.coerceAtLeast(1L).toFloat()
  val selected = items.getOrNull(selectedIdx) ?: items.first()

  Column(modifier = modifier.fillMaxWidth()) {
    // Interactive selected readout
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
        .padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text(
          text = selected.brand,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = "${selected.listingCount} listings • ${String.format("%.1f", selected.avgRetentionPct)}% 5y retention",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = KenyanMarketAnalyticsEngine.formatKes(selected.averagePriceKes),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary
        )
        Text(
          text = "Med: ${KenyanMarketAnalyticsEngine.formatCompactMillions(selected.medianPriceKes)}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    Spacer(Modifier.height(12.dp))

    items.forEachIndexed { idx, stat ->
      val ratio = (stat.averagePriceKes / maxAvg).coerceIn(0.06f, 1f)
      val barColor = palette[idx % palette.size]
      val isSelected = idx == selectedIdx
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .clickable { selectedIdx = idx }
          .background(
            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            else Color.Transparent
          )
          .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = stat.brand,
          style = MaterialTheme.typography.bodySmall,
          fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.width(88.dp)
        )
        Box(
          modifier = Modifier
            .weight(1f)
            .height(16.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth(ratio)
              .height(16.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(barColor)
          )
        }
        Spacer(Modifier.width(8.dp))
        Text(
          text = KenyanMarketAnalyticsEngine.formatCompactMillions(stat.averagePriceKes),
          style = MaterialTheme.typography.labelMedium,
          color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.width(48.dp)
        )
      }
    }
  }
}

@Composable
fun ScatterPlotChart(
  points: List<ScatterPoint>,
  xAxisUnit: String,
  yAxisLabel: String = "KES (M)",
  showTrendline: Boolean = true,
  modifier: Modifier = Modifier
) {
  if (points.size < 2) {
    EmptyChartPlaceholder("At least 2 data points needed for scatter analysis.", modifier)
    return
  }

  val primary = MaterialTheme.colorScheme.primary
  val cyan = LocalTelemetryColors.current.accentCyan
  val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
  var selectedPoint by remember(points) { mutableStateOf<ScatterPoint?>(points.firstOrNull()) }

  val minX = points.minOf { it.x }
  val maxX = max(points.maxOf { it.x }, minX + 1f)
  val minY = (points.minOf { it.y } * 0.85f).coerceAtLeast(0f)
  val maxY = max(points.maxOf { it.y } * 1.08f, minY + 0.5f)

  // Compute linear regression trendline y = m*x + b
  val meanX = points.map { it.x.toDouble() }.average()
  val meanY = points.map { it.y.toDouble() }.average()
  val num = points.sumOf { (it.x - meanX) * (it.y - meanY) }
  val den = points.sumOf { (it.x - meanX) * (it.x - meanX) }.coerceAtLeast(1e-6)
  val slope = num / den
  val intercept = meanY - slope * meanX

  Column(modifier = modifier.fillMaxWidth()) {
    selectedPoint?.let { pt ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
          .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = pt.label,
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f)
        )
        Text(
          text = "${pt.x.toInt()} $xAxisUnit • ${KenyanMarketAnalyticsEngine.formatCompactMillions(pt.priceKes)}",
          style = MaterialTheme.typography.labelMedium,
          color = primary
        )
      }
      Spacer(Modifier.height(8.dp))
    }

    Canvas(
      modifier = Modifier
        .fillMaxWidth()
        .height(176.dp)
        .pointerInput(points) {
          detectTapGestures { tap ->
            val w = size.width.toFloat()
            val h = size.height.toFloat()
            val pad = 24f
            val nearest = points.minByOrNull { pt ->
              val px = pad + ((pt.x - minX) / (maxX - minX)) * (w - pad * 2)
              val py = h - pad - ((pt.y - minY) / (maxY - minY)) * (h - pad * 2)
              hypot(px - tap.x, py - tap.y)
            }
            if (nearest != null) selectedPoint = nearest
          }
        }
    ) {
      val pad = 24f
      val chartW = size.width - pad * 2
      val chartH = size.height - pad * 2

      // Horizontal grid lines
      for (step in 0..3) {
        val y = pad + (chartH / 3f) * step
        drawLine(
          color = gridColor,
          start = Offset(pad, y),
          end = Offset(size.width - pad, y),
          strokeWidth = 1.5f
        )
      }

      if (showTrendline) {
        val yStart = (slope * minX + intercept).toFloat().coerceIn(minY, maxY)
        val yEnd = (slope * maxX + intercept).toFloat().coerceIn(minY, maxY)
        val sy1 = size.height - pad - ((yStart - minY) / (maxY - minY)) * chartH
        val sy2 = size.height - pad - ((yEnd - minY) / (maxY - minY)) * chartH
        drawLine(
          color = cyan,
          start = Offset(pad, sy1),
          end = Offset(size.width - pad, sy2),
          strokeWidth = 4f,
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
        )
      }

      points.forEach { pt ->
        val px = pad + ((pt.x - minX) / (maxX - minX)) * chartW
        val py = size.height - pad - ((pt.y - minY) / (maxY - minY)) * chartH
        val isSel = pt == selectedPoint
        drawCircle(
          color = if (isSel) cyan else primary.copy(alpha = 0.72f),
          radius = if (isSel) 11f else 7f,
          center = Offset(px, py)
        )
        if (isSel) {
          drawCircle(
            color = Color.White,
            radius = 13f,
            center = Offset(px, py),
            style = Stroke(width = 2.5f)
          )
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = "${minX.toInt()} $xAxisUnit",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Text(
        text = "$yAxisLabel (tap dot to inspect)",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Text(
        text = "${maxX.toInt()} $xAxisUnit",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DepreciationCurveChart(
  seriesList: List<ModelDepreciationSeries>,
  modifier: Modifier = Modifier
) {
  if (seriesList.isEmpty() || seriesList.all { it.points.isEmpty() }) {
    EmptyChartPlaceholder("Select vehicles to plot depreciation and 5-year projections.", modifier)
    return
  }

  val palette = LocalTelemetryColors.current.chartPalette
  val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
  val allPoints = seriesList.flatMap { it.points }
  val maxVal = allPoints.maxOf { it.valueKes }.coerceAtLeast(1_000_000L).toFloat() * 1.08f
  val minVal = (allPoints.minOf { it.valueKes }.toFloat() * 0.8f).coerceAtLeast(0f)
  val xLabels = seriesList.maxByOrNull { it.points.size }?.points?.map { it.label }.orEmpty()
  val maxCount = (xLabels.size - 1).coerceAtLeast(1)

  Column(modifier = modifier.fillMaxWidth()) {
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      seriesList.forEachIndexed { idx, s ->
        val color = palette[idx % palette.size]
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(10.dp)
              .clip(CircleShape)
              .background(color)
          )
          Spacer(Modifier.width(5.dp))
          Text(
            text = s.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
          )
        }
      }
    }

    Spacer(Modifier.height(6.dp))
    Text(
      text = "Solid line = Observed market baseline  •  Dashed = 5-year modeled projection",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))

    Canvas(
      modifier = Modifier
        .fillMaxWidth()
        .height(190.dp)
    ) {
      val pad = 26f
      val chartW = size.width - pad * 2
      val chartH = size.height - pad * 2

      for (step in 0..3) {
        val y = pad + (chartH / 3f) * step
        drawLine(gridColor, Offset(pad, y), Offset(size.width - pad, y), strokeWidth = 1.5f)
      }

      seriesList.forEachIndexed { sIdx, series ->
        val color = palette[sIdx % palette.size]
        val coords = series.points.mapIndexed { pIdx, pt ->
          val x = pad + (pIdx.toFloat() / maxCount) * chartW
          val y = size.height - pad - ((pt.valueKes - minVal) / (maxVal - minVal).coerceAtLeast(1f)) * chartH
          Triple(Offset(x, y), pt.isProjected, pt)
        }

        // Draw solid observed segments vs dashed projected segments
        for (i in 0 until coords.size - 1) {
          val (start, _, _) = coords[i]
          val (end, endProj, _) = coords[i + 1]
          drawLine(
            color = color,
            start = start,
            end = end,
            strokeWidth = 5f,
            cap = StrokeCap.Round,
            pathEffect = if (endProj) PathEffect.dashPathEffect(floatArrayOf(12f, 9f)) else null
          )
        }

        coords.forEach { (ptOffset, isProj, _) ->
          if (isProj) {
            drawCircle(color = Color.White, radius = 6f, center = ptOffset)
            drawCircle(color = color, radius = 6f, center = ptOffset, style = Stroke(width = 3f))
          } else {
            drawCircle(color = color, radius = 7.5f, center = ptOffset)
          }
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      if (xLabels.isNotEmpty()) {
        Text(
          text = xLabels.first(),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (xLabels.size > 2) {
          Text(
            text = xLabels[xLabels.size / 2],
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Text(
          text = xLabels.last(),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
fun PriceDistributionChart(
  buckets: List<HistogramBucket>,
  modifier: Modifier = Modifier
) {
  if (buckets.isEmpty() || buckets.all { it.count == 0 }) {
    EmptyChartPlaceholder("No listings fall within the active price filter.", modifier)
    return
  }

  val maxCount = buckets.maxOf { it.count }.coerceAtLeast(1)
  val barColor = MaterialTheme.colorScheme.primary
  val accentColor = LocalTelemetryColors.current.accentCyan

  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(150.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.Bottom
    ) {
      buckets.forEachIndexed { idx, bucket ->
        val ratio = (bucket.count.toFloat() / maxCount).coerceAtLeast(0.05f)
        Column(
          modifier = Modifier.weight(1f),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Bottom
        ) {
          Text(
            text = "${bucket.count}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
          )
          Spacer(Modifier.height(4.dp))
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height((110 * ratio).dp)
              .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
              .background(if (idx % 2 == 0) barColor else accentColor)
          )
        }
      }
    }
    Spacer(Modifier.height(6.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      buckets.forEach { bucket ->
        Text(
          text = bucket.label,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          modifier = Modifier.weight(1f)
        )
      }
    }
  }
}

@Composable
fun MarketShareDonutChart(
  slices: List<BrandShareSlice>,
  totalVehicles: Int,
  modifier: Modifier = Modifier
) {
  if (slices.isEmpty()) {
    EmptyChartPlaceholder("No brand share data available.", modifier)
    return
  }

  val palette = LocalTelemetryColors.current.chartPalette
  val totalShown = slices.sumOf { it.count }.coerceAtLeast(1)

  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Box(
      modifier = Modifier.size(140.dp),
      contentAlignment = Alignment.Center
    ) {
      Canvas(modifier = Modifier.size(134.dp)) {
        var startAngle = -90f
        val strokeWidth = 24.dp.toPx()
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
        slices.forEachIndexed { idx, slice ->
          val sweep = (slice.count.toFloat() / totalShown) * 360f
          drawArc(
            color = palette[idx % palette.size],
            startAngle = startAngle,
            sweepAngle = (sweep - 2f).coerceAtLeast(1f),
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
          )
          startAngle += sweep
        }
      }
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "$totalVehicles",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "Vehicles",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      slices.forEachIndexed { idx, slice ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(palette[idx % palette.size])
            )
            Spacer(Modifier.width(6.dp))
            Text(
              text = slice.brand,
              style = MaterialTheme.typography.bodySmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          Text(
            text = "${String.format("%.0f", slice.percentage)}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}

@Composable
fun ComparisonMetricBars(
  metrics: List<ComparisonMetric>,
  modifier: Modifier = Modifier
) {
  if (metrics.isEmpty()) return
  val palette = LocalTelemetryColors.current.chartPalette
  val maxPrice = metrics.maxOf { it.vehicle.askingPriceKes }.coerceAtLeast(1L).toFloat()

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    metrics.forEachIndexed { idx, m ->
      val color = palette[idx % palette.size]
      val askRatio = (m.vehicle.askingPriceKes / maxPrice).coerceIn(0.08f, 1f)
      val fiveYrRatio = (m.fiveYearResaleKes / maxPrice).coerceIn(0.05f, 1f)

      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = m.vehicle.fullName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = "${String.format("%.1f", m.valueRetentionPct)}% 5y ret.",
            style = MaterialTheme.typography.labelMedium,
            color = color
          )
        }
        // Current Ask Bar
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("Now", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(36.dp))
          Box(
            modifier = Modifier
              .weight(1f)
              .height(10.dp)
              .clip(RoundedCornerShape(5.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth(askRatio)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(color)
            )
          }
          Spacer(Modifier.width(8.dp))
          Text(
            text = KenyanMarketAnalyticsEngine.formatCompactMillions(m.vehicle.askingPriceKes),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(46.dp)
          )
        }
        // 5-Year Projected Resale Bar
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("5 Yr", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(36.dp))
          Box(
            modifier = Modifier
              .weight(1f)
              .height(10.dp)
              .clip(RoundedCornerShape(5.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth(fiveYrRatio)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(color.copy(alpha = 0.55f))
            )
          }
          Spacer(Modifier.width(8.dp))
          Text(
            text = KenyanMarketAnalyticsEngine.formatCompactMillions(m.fiveYearResaleKes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(46.dp)
          )
        }
      }
    }
  }
}

@Composable
fun EmptyChartPlaceholder(message: String, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(120.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
      .padding(16.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = message,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}
