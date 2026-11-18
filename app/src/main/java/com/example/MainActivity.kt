package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.preferences.ThemePreferencesRepository
import com.example.domain.model.ThemeMode
import com.example.firebase.configureAppCheck
import com.example.ui.navigation.HekimaRootNavigation
import com.example.ui.theme.HekimaAutoTheme

class MainActivity : ComponentActivity() {

  companion object {
    const val EXTRA_OPEN_WATCHLIST_MATCHES = "extra_open_watchlist_matches"
  }

  private val openWatchlistMatchesState = mutableStateOf(false)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configureAppCheck(this, intent)
    openWatchlistMatchesState.value = intent?.getBooleanExtra(EXTRA_OPEN_WATCHLIST_MATCHES, false) == true
    enableEdgeToEdge()
    setContent {
      val themePrefsRepo = remember { ThemePreferencesRepository(applicationContext) }
      val themeMode by themePrefsRepo.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
      val openWatchlists by openWatchlistMatchesState
      HekimaAutoTheme(themeMode = themeMode) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          HekimaRootNavigation(
            themePrefsRepo = themePrefsRepo,
            currentThemeMode = themeMode,
            openWatchlistMatches = openWatchlists,
            onConsumeWatchlistDeepLink = { openWatchlistMatchesState.value = false }
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (intent.getBooleanExtra(EXTRA_OPEN_WATCHLIST_MATCHES, false)) {
      openWatchlistMatchesState.value = true
    }
  }
}
