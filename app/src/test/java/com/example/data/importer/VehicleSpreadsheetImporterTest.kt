package com.example.data.importer

import com.example.data.seed.KenyanMarketSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleSpreadsheetImporterTest {

  @Test
  fun importCsvText_normalizesAliasesPricesAndDeduplicates() {
    val csv = """
      make,model,year,price,mileage,engine_cc,transmission,fuel,condition,import_status,location
      Toyta,Axio NZE,2018,KSh 1.42M,72000,1500,Automatic,Petrol,Good,Foreign Used,Nairobi
      Toyta,Axio NZE,2018,KSh 1.42M,72000,1500,Automatic,Petrol,Good,Foreign Used,Nairobi
      VW,CX5,2019,KES 2,950,000,58000,2000,Automatic,Petrol,Excellent,Foreign Used,Mombasa
      InvalidMake,,2018,bad_price,50000,1500,Automatic,Petrol,Good,Foreign Used,Kisumu
    """.trimIndent()

    // Use quoted CSV for KES 2,950,000
    val cleanCsv = """
      make,model,year,price,mileage,engine_cc,transmission,fuel,condition,import_status,location
      Toyta,Axio NZE,2018,KSh 1.42M,72000,1500,Automatic,Petrol,Good,Foreign Used,Nairobi
      Toyta,Axio NZE,2018,KSh 1.42M,72000,1500,Automatic,Petrol,Good,Foreign Used,Nairobi
      VW,Golf,2019,"KES 2,150,000",58000,1400,Automatic,Petrol,Excellent,Foreign Used,Mombasa
      ,MissingModel,2018,bad_price,50000,1500,Automatic,Petrol,Good,Foreign Used,Kisumu
    """.trimIndent()

    val result = VehicleSpreadsheetImporter.importCsvText(
      csvText = cleanCsv,
      sourceName = "Test CSV",
      existingListings = KenyanMarketSeed.listings,
      userId = "user_1"
    )

    assertEquals(2, result.importedListings.size)
    assertEquals(1, result.duplicatesSkipped)
    assertEquals(1, result.invalidRowsSkipped)
    assertEquals("Toyota", result.importedListings[0].make)
    assertEquals("Axio", result.importedListings[0].model)
    assertEquals(1_420_000L, result.importedListings[0].askingPriceKes)
    assertEquals("Volkswagen", result.importedListings[1].make)
    assertEquals(2_150_000L, result.importedListings[1].askingPriceKes)
  }

  @Test
  fun importCsvText_handlesEmptyInputSafely() {
    val result = VehicleSpreadsheetImporter.importCsvText(
      csvText = "make,model,price\n",
      sourceName = "Empty",
      existingListings = emptyList(),
      userId = "user_1"
    )
    assertTrue(result.importedListings.isEmpty())
    assertEquals(0, result.totalRowsRead)
  }
}
