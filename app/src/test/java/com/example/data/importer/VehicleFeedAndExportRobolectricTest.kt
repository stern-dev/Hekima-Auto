package com.example.data.importer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.export.NegotiationBriefExporter
import com.example.data.seed.KenyanMarketSeed
import com.example.domain.analytics.KenyanMarketAnalyticsEngine
import com.example.domain.model.VehicleSpecification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VehicleFeedAndExportRobolectricTest {

  @Test
  fun importJsonText_supportsArrayAndWrappedListingsWithKenyanAliases() {
    val wrappedJson = """
      {
        "listings": [
          {
            "make": "Isuzu",
            "model": "DMax",
            "year": 2019,
            "price": "KES 3,450,000",
            "mileage": 92000,
            "engine_cc": 2500,
            "transmission": "Automatic",
            "fuel": "Diesel",
            "condition": "Good",
            "import_status": "Locally Used",
            "location": "Nakuru"
          },
          {
            "make": "Toyota",
            "model": "KDH",
            "year": 2018,
            "price": "KSh 3.1M",
            "mileage": 115000,
            "engine_cc": 2800,
            "transmission": "Manual",
            "fuel": "Diesel",
            "condition": "Good",
            "import_status": "Foreign Used",
            "location": "Eldoret"
          }
        ]
      }
    """.trimIndent()

    val res = VehicleSpreadsheetImporter.importJsonText(
      jsonText = wrappedJson,
      sourceName = "Feed: yards.example.co.ke",
      existingListings = KenyanMarketSeed.listings,
      userId = "user_1"
    )

    assertEquals("feed", res.sourceType)
    assertEquals(2, res.importedListings.size)
    assertEquals("D-Max", res.importedListings[0].model)
    assertEquals(3_450_000L, res.importedListings[0].askingPriceKes)
    assertEquals("Hiace", res.importedListings[1].model)
    assertEquals(3_100_000L, res.importedListings[1].askingPriceKes)
  }

  @Test(expected = IllegalArgumentException::class)
  fun importFromFeedUrl_rejectsInsecureHttpUrls() {
    VehicleSpreadsheetImporter.importFromFeedUrl(
      url = "http://insecure.example.co.ke/cars.csv",
      existingListings = emptyList(),
      userId = "user_1"
    )
  }

  @Test
  fun negotiationBriefExporter_buildsTextAndExportsValidPdfFiles() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val spec = VehicleSpecification(
      make = "Mazda",
      model = "CX-5",
      year = 2016,
      mileageKm = 24_000,
      engineCc = 2000,
      fuelType = "Petrol",
      importStatus = "Foreign Used"
    )
    val estimate = KenyanMarketAnalyticsEngine.estimatePrice(KenyanMarketSeed.listings, spec)
    val pricingText = NegotiationBriefExporter.buildPricingBriefText(estimate)
    assertTrue(pricingText.contains("HEKIMA AUTO"))
    assertTrue(pricingText.contains("Fair Market Value"))
    assertTrue(pricingText.contains("JEVIC/QISJ"))

    val pdfUri = NegotiationBriefExporter.exportPricingBriefPdf(context, estimate)
    assertEquals("content", pdfUri.scheme)
    val briefFile = File(File(context.cacheDir, "briefs"), "hekima_brief_mazda_cx_5_2016.pdf")
    assertTrue(briefFile.exists() && briefFile.length() > 100L)

    val compareUnits = KenyanMarketSeed.listings.take(2)
    val verdict = KenyanMarketAnalyticsEngine.compareVehicles(compareUnits)
    val compareText = NegotiationBriefExporter.buildComparisonBriefText(verdict)
    assertTrue(compareText.contains("MULTI-VEHICLE COMPARISON BRIEF"))

    val comparePdfUri = NegotiationBriefExporter.exportComparisonBriefPdf(context, verdict)
    assertEquals("content", comparePdfUri.scheme)
    val compareFile = File(File(context.cacheDir, "briefs"), "hekima_comparison_brief.pdf")
    assertTrue(compareFile.exists() && compareFile.length() > 100L)
  }
}
