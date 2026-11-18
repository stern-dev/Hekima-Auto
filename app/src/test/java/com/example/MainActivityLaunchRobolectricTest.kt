package com.example

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.seed.KenyanMarketSeed
import com.example.domain.analytics.KenyanMarketAnalyticsEngine
import com.example.domain.model.FilterState
import com.example.domain.model.ThemeMode
import com.example.ui.navigation.HekimaDestination
import com.example.ui.screens.OverviewScreen
import com.example.ui.theme.HekimaAutoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp")
class MainActivityLaunchRobolectricTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun mainActivity_opensDirectlyIntoGuestPreviewOverview() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Kenyan Market Pulse").assertIsDisplayed()
    composeTestRule.onNodeWithText("Vehicles Analyzed").assertIsDisplayed()
    composeTestRule.onNodeWithTag("topbar_sign_in_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("overview_sign_in_button").assertIsDisplayed()
  }

  @Test
  fun guestPreview_cloudGatedAction_opensAndDismissesGoogleSignInModal() {
    composeTestRule.waitForIdle()
    // Navigate to Pricing via bottom nav and tap Save Valuation (a cloud-gated action)
    composeTestRule.onNodeWithTag("bottom_nav_pricing").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("save_analysis_button")
      .performScrollTo()
      .performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("Sign In to Save Valuation Reports").assertIsDisplayed()
    composeTestRule.onNodeWithTag("modal_google_sign_in_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("dismiss_auth_modal_button")
      .assertIsDisplayed()
      .performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("modal_google_sign_in_button").assertDoesNotExist()
  }

  @Test
  fun navHost_overviewStartDestination_rendersInsideThemeAndSurface() {
    val summary = KenyanMarketAnalyticsEngine.computeDashboardSummary(KenyanMarketSeed.listings)
    composeTestRule.activity.runOnUiThread {
      composeTestRule.activity.setContent {
        HekimaAutoTheme(themeMode = ThemeMode.DARK) {
          Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
          ) {
            val navController = rememberNavController()
            NavHost(
              navController = navController,
              startDestination = HekimaDestination.OVERVIEW.route
            ) {
              composable(HekimaDestination.OVERVIEW.route) {
                OverviewScreen(
                  summary = summary,
                  filterState = FilterState(),
                  availableMakes = listOf("Toyota", "Mazda", "Subaru"),
                  onUpdateFilter = {},
                  onResetFilters = {},
                  onNavigateModule = {},
                  isGuestMode = true,
                  onRequestSignIn = {}
                )
              }
            }
          }
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Kenyan Market Pulse").assertIsDisplayed()
    composeTestRule.onNodeWithText("Vehicles Analyzed").assertIsDisplayed()
  }

  @Test
  fun pricingScreen_rendersKraDutyEstimatorAndTcoCards() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("bottom_nav_pricing").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("pricing_lazy_column")
      .performScrollToNode(hasTestTag("kra_duty_card"))
    composeTestRule.onNodeWithTag("kra_duty_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("import_age_rule_banner").assertIsDisplayed()

    composeTestRule.onNodeWithTag("pricing_lazy_column")
      .performScrollToNode(hasTestTag("tco_card"))
    composeTestRule.onNodeWithTag("tco_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pricing_lazy_column")
      .performScrollToNode(hasText("Annual Fuel Trim Comparison (EPRA Benchmarks)"))
    composeTestRule.onNodeWithText("Annual Fuel Trim Comparison (EPRA Benchmarks)").assertIsDisplayed()
  }

  @Test
  fun landedCostScreen_calculatesTaxesAndSupportsUsdCifOverride() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("chip_landed_cost")
      .performScrollTo()
      .performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("Mombasa Landed Cost").assertIsDisplayed()
    composeTestRule.onNodeWithTag("landed_age_rule_banner").assertIsDisplayed()

    composeTestRule.onNodeWithTag("landed_cost_lazy_column")
      .performScrollToNode(hasTestTag("cif_currency_usd"))
    composeTestRule.onNodeWithTag("cif_currency_usd").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("usd_rate_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("custom_cif_input").performTextInput("12000")
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("landed_cost_lazy_column")
      .performScrollToNode(hasTestTag("landed_cost_summary_card"))
    composeTestRule.onNodeWithTag("total_landed_cost_value").assertIsDisplayed()

    composeTestRule.onNodeWithTag("landed_cost_lazy_column")
      .performScrollToNode(hasTestTag("kra_tax_breakdown_card"))
    composeTestRule.onNodeWithTag("kra_tax_breakdown_card").assertIsDisplayed()

    composeTestRule.onNodeWithTag("landed_cost_lazy_column")
      .performScrollToNode(hasTestTag("local_vs_import_card"))
    composeTestRule.onNodeWithTag("local_vs_import_card").assertIsDisplayed()
  }

  @Test
  fun pricingScreen_showsBriefExportButtonsAndOdometerSanityWarning() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("bottom_nav_pricing").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("pricing_lazy_column")
      .performScrollToNode(hasTestTag("export_pricing_pdf_button"))
    composeTestRule.onNodeWithTag("export_pricing_pdf_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("share_pricing_text_button").assertIsDisplayed()
  }

  @Test
  fun explorerAndDataManagement_renderWatchlistsAndFeedUrlWithGuestGate() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("bottom_nav_explorer").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("watchlist_section_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("add_watchlist_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("modal_google_sign_in_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("dismiss_auth_modal_button").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("bottom_nav_overview").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("chip_data")
      .performScrollTo()
      .performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("data_management_lazy_column")
      .performScrollToNode(hasTestTag("feed_url_input"))
    composeTestRule.onNodeWithTag("feed_url_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("fetch_feed_url_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("modal_google_sign_in_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("dismiss_auth_modal_button").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("data_management_lazy_column")
      .performScrollToNode(hasTestTag("save_feed_subscription_button"))
    composeTestRule.onNodeWithTag("save_feed_subscription_button")
      .assertIsDisplayed()
      .performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("modal_google_sign_in_button").assertIsDisplayed()
  }

  @Test
  fun aiAuctionAnalyzerScreen_populatesSampleTextAndEnablesAnalyzeButton() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("chip_ai_analyzer").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("AI Auction & Listing Analyzer").assertIsDisplayed()
    composeTestRule.onNodeWithTag("sample_auction_clean").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("analyze_auction_button")
      .assertIsDisplayed()
      .assertIsEnabled()
  }

  @Test
  fun yardInspectionScreen_updatesScoreOnIssueAndGatesCloudSave() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("chip_yard_inspection").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("On-Site Yard Inspection").assertIsDisplayed()
    composeTestRule.onNodeWithTag("yard_overall_score_value").assertIsDisplayed()

    composeTestRule.onNodeWithTag("yard_inspection_lazy_column")
      .performScrollToNode(hasTestTag("inspection_item_eng_cold_start_issue"))
    composeTestRule.onNodeWithTag("inspection_item_eng_cold_start_issue").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("yard_inspection_lazy_column")
      .performScrollToNode(hasTestTag("save_inspection_button"))
    composeTestRule.onNodeWithTag("save_inspection_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("modal_google_sign_in_button").assertIsDisplayed()
  }

  @Test
  fun landedCostScreen_displaysLiveFxBannerAndRefreshButton() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("chip_landed_cost")
      .performScrollTo()
      .performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("landed_cost_lazy_column")
      .performScrollToNode(hasTestTag("live_fx_banner"))
    composeTestRule.onNodeWithTag("live_fx_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("refresh_fx_button").assertIsDisplayed()
  }
}
