package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.ExchangeRateSnapshot
import com.example.domain.model.NotificationPreferences
import com.example.domain.model.PriceDropAlertFrequency
import com.example.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.hekimaDataStore: DataStore<Preferences> by preferencesDataStore(name = "hekima_prefs")

class ThemePreferencesRepository(private val context: Context) {
  private val themeKey = stringPreferencesKey("theme_mode")
  private val usdToKesKey = doublePreferencesKey("fx_usd_to_kes")
  private val jpyToKesKey = doublePreferencesKey("fx_jpy_to_kes")
  private val fxTimestampKey = longPreferencesKey("fx_fetched_epoch_ms")
  private val fxSourceKey = stringPreferencesKey("fx_source_label")
  private val pushAlertsEnabledKey = booleanPreferencesKey("notif_push_alerts_enabled")
  private val alertFrequencyKey = stringPreferencesKey("notif_price_drop_frequency")
  private val lastNotifiedKey = longPreferencesKey("notif_last_sent_epoch_ms")

  val themeMode: Flow<ThemeMode> = context.hekimaDataStore.data.map { prefs ->
    ThemeMode.fromKey(prefs[themeKey])
  }

  val notificationPreferences: Flow<NotificationPreferences> = context.hekimaDataStore.data.map { prefs ->
    NotificationPreferences(
      pushAlertsEnabled = prefs[pushAlertsEnabledKey] ?: true,
      frequency = PriceDropAlertFrequency.fromKey(prefs[alertFrequencyKey]),
      lastNotifiedEpochMs = prefs[lastNotifiedKey] ?: 0L
    )
  }

  suspend fun getNotificationPreferencesOnce(): NotificationPreferences = notificationPreferences.first()

  suspend fun setPushAlertsEnabled(enabled: Boolean) {
    context.hekimaDataStore.edit { prefs ->
      prefs[pushAlertsEnabledKey] = enabled
    }
  }

  suspend fun setAlertFrequency(frequency: PriceDropAlertFrequency) {
    context.hekimaDataStore.edit { prefs ->
      prefs[alertFrequencyKey] = frequency.key
    }
  }

  suspend fun recordNotificationDispatched(epochMs: Long = System.currentTimeMillis()) {
    context.hekimaDataStore.edit { prefs ->
      prefs[lastNotifiedKey] = epochMs
    }
  }

  val cachedExchangeRate: Flow<ExchangeRateSnapshot?> = context.hekimaDataStore.data.map { prefs ->
    val usd = prefs[usdToKesKey]
    val jpy = prefs[jpyToKesKey]
    val ts = prefs[fxTimestampKey] ?: 0L
    if (usd != null && jpy != null && usd > 50.0 && jpy > 0.1) {
      ExchangeRateSnapshot(
        usdToKes = usd,
        jpyToKes = jpy,
        fetchedAtEpochMs = ts,
        sourceLabel = prefs[fxSourceKey] ?: "Cached Market FX",
        isFallback = false
      )
    } else {
      null
    }
  }

  suspend fun getCachedExchangeRateOnce(): ExchangeRateSnapshot? = cachedExchangeRate.first()

  suspend fun saveExchangeRate(snapshot: ExchangeRateSnapshot) {
    context.hekimaDataStore.edit { prefs ->
      prefs[usdToKesKey] = snapshot.usdToKes
      prefs[jpyToKesKey] = snapshot.jpyToKes
      prefs[fxTimestampKey] = snapshot.fetchedAtEpochMs
      prefs[fxSourceKey] = snapshot.sourceLabel
    }
  }

  suspend fun setThemeMode(mode: ThemeMode) {
    context.hekimaDataStore.edit { prefs ->
      prefs[themeKey] = mode.key
    }
  }

  suspend fun clearFxCache() {
    context.hekimaDataStore.edit { prefs ->
      prefs.remove(usdToKesKey)
      prefs.remove(jpyToKesKey)
      prefs.remove(fxTimestampKey)
      prefs.remove(fxSourceKey)
      prefs.remove(pushAlertsEnabledKey)
      prefs.remove(alertFrequencyKey)
      prefs.remove(lastNotifiedKey)
    }
  }
}
