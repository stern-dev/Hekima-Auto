package com.example.data.fx

import com.example.data.preferences.ThemePreferencesRepository
import com.example.domain.model.ExchangeRateSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ExchangeRateRepository(
  private val prefsRepo: ThemePreferencesRepository,
  private val httpClient: OkHttpClient = defaultClient()
) {
  suspend fun loadInitialRate(): ExchangeRateSnapshot {
    return prefsRepo.getCachedExchangeRateOnce() ?: ExchangeRateSnapshot()
  }

  suspend fun refreshIfStale(
    ttlMs: Long = DEFAULT_FX_TTL_MS,
    endpointUrl: String = LIVE_FX_ENDPOINT
  ): ExchangeRateSnapshot {
    val current = loadInitialRate()
    return if (current.isStale(System.currentTimeMillis(), ttlMs)) {
      fetchLatestRates(endpointUrl)
    } else {
      current
    }
  }

  suspend fun fetchLatestRates(
    endpointUrl: String = LIVE_FX_ENDPOINT
  ): ExchangeRateSnapshot = withContext(Dispatchers.IO) {
    val cached = prefsRepo.getCachedExchangeRateOnce()
    try {
      val request = Request.Builder()
        .url(endpointUrl)
        .header("Accept", "application/json")
        .build()

      httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          throw IllegalStateException("FX server responded HTTP ${response.code}")
        }
        val body = response.body?.string().orEmpty()
        val parsed = parseOpenErApiJson(body, System.currentTimeMillis())
        prefsRepo.saveExchangeRate(parsed)
        parsed
      }
    } catch (_: Exception) {
      (cached ?: ExchangeRateSnapshot()).copy(
        isFallback = true,
        sourceLabel = if (cached != null) "Offline Cached Rate" else "Offline Benchmark (129 KES/USD)"
      )
    }
  }

  companion object {
    const val LIVE_FX_ENDPOINT = "https://open.er-api.com/v6/latest/USD"
    const val DEFAULT_FX_TTL_MS = 6L * 60L * 60L * 1000L

    private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(10, TimeUnit.SECONDS)
      .build()

    fun parseOpenErApiJson(jsonText: String, nowEpochMs: Long = System.currentTimeMillis()): ExchangeRateSnapshot {
      val root = JSONObject(jsonText)
      val result = root.optString("result", "success")
      require(result.equals("success", ignoreCase = true) || root.has("rates")) {
        "Invalid FX payload"
      }
      val rates = root.getJSONObject("rates")
      val usdToKes = rates.getDouble("KES")
      val usdToJpy = rates.optDouble("JPY", 150.0).coerceAtLeast(1.0)
      require(usdToKes in 50.0..500.0) { "Out-of-range USD/KES quote: $usdToKes" }
      val jpyToKes = usdToKes / usdToJpy
      return ExchangeRateSnapshot(
        usdToKes = usdToKes,
        jpyToKes = jpyToKes,
        fetchedAtEpochMs = nowEpochMs,
        sourceLabel = "Live Market FX (open.er-api.com)",
        isFallback = false
      )
    }
  }
}
