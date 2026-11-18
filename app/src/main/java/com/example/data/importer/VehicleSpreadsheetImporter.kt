package com.example.data.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.domain.model.ImportResult
import com.example.domain.model.ImportedVehicleListing
import com.example.domain.model.VehicleListing
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

object VehicleSpreadsheetImporter {

  private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(12, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .followRedirects(true)
      .build()
  }

  private val makeAliases = mapOf(
    "toyta" to "Toyota",
    "toyo" to "Toyota",
    "toyota" to "Toyota",
    "mazd" to "Mazda",
    "mazda" to "Mazda",
    "subar" to "Subaru",
    "subaru" to "Subaru",
    "hond" to "Honda",
    "honda" to "Honda",
    "nissn" to "Nissan",
    "nissan" to "Nissan",
    "vw" to "Volkswagen",
    "volkswagen" to "Volkswagen",
    "volkswagon" to "Volkswagen",
    "benz" to "Mercedes-Benz",
    "merc" to "Mercedes-Benz",
    "mercedes" to "Mercedes-Benz",
    "mercedes benz" to "Mercedes-Benz",
    "mercedes-benz" to "Mercedes-Benz",
    "bmw" to "BMW",
    "mitsubishi" to "Mitsubishi",
    "suzuki" to "Suzuki",
    "isuzu" to "Isuzu",
    "ford" to "Ford",
    "audi" to "Audi",
    "lexus" to "Lexus",
    "hyundai" to "Hyundai",
    "kia" to "Kia",
    "landrover" to "Land Rover",
    "land rover" to "Land Rover",
    "range rover" to "Land Rover"
  )

  private val modelAliases = mapOf(
    "cx5" to "CX-5",
    "cx 5" to "CX-5",
    "cx3" to "CX-3",
    "cx 3" to "CX-3",
    "xtrail" to "X-Trail",
    "x trail" to "X-Trail",
    "crv" to "CR-V",
    "cr v" to "CR-V",
    "chr" to "C-HR",
    "c hr" to "C-HR",
    "prado" to "Land Cruiser Prado",
    "landcruiser prado" to "Land Cruiser Prado",
    "land cruiser v8" to "Land Cruiser V8",
    "v8" to "Land Cruiser V8",
    "lc200" to "Land Cruiser V8",
    "axio nze" to "Axio",
    "corolla axio" to "Axio",
    "corolla fielder" to "Fielder",
    "c 200" to "C200",
    "c200" to "C200",
    "e 250" to "E250",
    "e250" to "E250",
    "gle350" to "GLE 350",
    "rav 4" to "RAV4",
    "dmax" to "D-Max",
    "d max" to "D-Max",
    "d-max" to "D-Max",
    "kdh" to "Hiace",
    "rx450h" to "RX 450h",
    "rx 450h" to "RX 450h",
    "evoque" to "Range Rover Evoque",
    "rr sport" to "Range Rover Sport",
    "range rover sport" to "Range Rover Sport"
  )

  fun importFromUri(
    context: Context,
    uri: Uri,
    existingListings: List<VehicleListing>,
    userId: String
  ): ImportResult {
    val fileName = resolveDisplayName(context, uri)
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
      ?: throw IllegalStateException("Unable to open selected file.")

    val isXlsx = fileName.lowercase().endsWith(".xlsx")
    val rows = if (isXlsx) {
      parseXlsx(ByteArrayInputStream(bytes))
    } else {
      parseCsv(bytes.decodeToString())
    }

    return cleanAndNormalizeRows(
      rows = rows,
      sourceName = fileName.removeSuffix(".csv").removeSuffix(".xlsx").ifBlank { "Uploaded Dataset" }.take(80),
      sourceType = if (isXlsx) "xlsx" else "csv",
      existingListings = existingListings,
      userId = userId
    )
  }

  fun importCsvText(
    csvText: String,
    sourceName: String,
    existingListings: List<VehicleListing>,
    userId: String,
    sourceType: String = "csv"
  ): ImportResult {
    val rows = parseCsv(csvText)
    return cleanAndNormalizeRows(
      rows = rows,
      sourceName = sourceName.take(80),
      sourceType = sourceType,
      existingListings = existingListings,
      userId = userId
    )
  }

  fun importFromFeedUrl(
    url: String,
    existingListings: List<VehicleListing>,
    userId: String
  ): ImportResult {
    val trimmedUrl = url.trim()
    require(trimmedUrl.startsWith("https://", ignoreCase = true)) {
      "Feed URL must start with https://"
    }
    val parsedUri = runCatching { URI(trimmedUrl) }.getOrNull()
      ?: throw IllegalArgumentException("Invalid HTTPS feed URL.")
    val hostLabel = parsedUri.host?.take(48)?.ifBlank { "External Feed" } ?: "External Feed"
    val sourceName = "Feed: $hostLabel".take(80)

    val request = Request.Builder()
      .url(trimmedUrl)
      .header("Accept", "application/json, text/csv, text/plain, */*")
      .get()
      .build()

    val payload = httpClient.newCall(request).execute().use { response ->
      if (!response.isSuccessful) {
        throw IllegalStateException("Feed server returned HTTP ${response.code}")
      }
      val body = response.body?.string().orEmpty()
      if (body.isBlank()) throw IllegalStateException("Feed URL returned an empty response.")
      if (body.length > 4_000_000) throw IllegalStateException("Feed payload exceeds 4 MB limit.")
      body
    }

    val firstChar = payload.trimStart().firstOrNull()
    return if (firstChar == '{' || firstChar == '[') {
      importJsonText(
        jsonText = payload,
        sourceName = sourceName,
        existingListings = existingListings,
        userId = userId,
        sourceType = "feed"
      )
    } else {
      importCsvText(
        csvText = payload,
        sourceName = sourceName,
        existingListings = existingListings,
        userId = userId,
        sourceType = "feed"
      )
    }
  }

  fun importJsonText(
    jsonText: String,
    sourceName: String,
    existingListings: List<VehicleListing>,
    userId: String,
    sourceType: String = "feed"
  ): ImportResult {
    val trimmed = jsonText.trim()
    val jsonArray: JSONArray = when {
      trimmed.startsWith("[") -> JSONArray(trimmed)
      trimmed.startsWith("{") -> {
        val root = JSONObject(trimmed)
        val arrayKey = listOf("listings", "vehicles", "items", "data", "results")
          .firstOrNull { key -> root.optJSONArray(key) != null }
          ?: throw IllegalArgumentException("JSON object must contain a 'listings', 'vehicles', or 'data' array.")
        root.getJSONArray(arrayKey)
      }
      else -> throw IllegalArgumentException("Invalid JSON feed payload.")
    }

    if (jsonArray.length() == 0) {
      return ImportResult(
        sourceName = sourceName.take(80),
        sourceType = sourceType,
        totalRowsRead = 0,
        importedListings = emptyList(),
        duplicatesSkipped = 0,
        invalidRowsSkipped = 0,
        cleaningNotes = listOf("JSON feed contained 0 vehicle records.")
      )
    }

    val headerSet = linkedSetOf<String>()
    val objects = mutableListOf<JSONObject>()
    for (i in 0 until jsonArray.length()) {
      val obj = jsonArray.optJSONObject(i) ?: continue
      objects.add(obj)
      val keys = obj.keys()
      while (keys.hasNext()) {
        headerSet.add(keys.next())
      }
    }

    val headers = headerSet.toList()
    val rows = mutableListOf<List<String>>()
    rows.add(headers)
    for (obj in objects) {
      rows.add(headers.map { key ->
        if (obj.isNull(key)) "" else obj.opt(key)?.toString()?.trim().orEmpty()
      })
    }

    return cleanAndNormalizeRows(
      rows = rows,
      sourceName = sourceName.take(80),
      sourceType = sourceType,
      existingListings = existingListings,
      userId = userId
    )
  }

  fun cleanAndNormalizeRows(
    rows: List<List<String>>,
    sourceName: String,
    sourceType: String,
    existingListings: List<VehicleListing>,
    userId: String
  ): ImportResult {
    if (rows.size < 2) {
      return ImportResult(
        sourceName = sourceName,
        sourceType = sourceType,
        totalRowsRead = 0,
        importedListings = emptyList(),
        duplicatesSkipped = 0,
        invalidRowsSkipped = 0,
        cleaningNotes = listOf("File has no data rows beyond headers.")
      )
    }

    val headers = rows.first().map { it.trim().lowercase().replace(" ", "_") }
    val makeIdx = findCol(headers, "make", "brand", "manufacturer")
    val modelIdx = findCol(headers, "model", "vehicle_model")
    val yearIdx = findCol(headers, "year", "yom", "manufacturing_year")
    val priceIdx = findCol(headers, "price", "asking_price", "price_kes", "kes", "amount")
    val mileageIdx = findCol(headers, "mileage", "mileage_km", "odometer", "km")
    val engineIdx = findCol(headers, "engine_cc", "engine", "cc", "capacity")
    val transIdx = findCol(headers, "transmission", "gearbox")
    val fuelIdx = findCol(headers, "fuel", "fuel_type")
    val condIdx = findCol(headers, "condition", "grade")
    val importIdx = findCol(headers, "import_status", "status", "usage")
    val locIdx = findCol(headers, "location", "city", "county")

    if (makeIdx < 0 || modelIdx < 0 || priceIdx < 0) {
      return ImportResult(
        sourceName = sourceName,
        sourceType = sourceType,
        totalRowsRead = rows.size - 1,
        importedListings = emptyList(),
        duplicatesSkipped = 0,
        invalidRowsSkipped = rows.size - 1,
        cleaningNotes = listOf("Missing required columns: make/brand, model, and price.")
      )
    }

    val existingSignatures = existingListings.map {
      signature(it.make, it.model, it.year, it.mileageKm, it.askingPriceKes)
    }.toMutableSet()

    val sourceId = "src_${UUID.randomUUID().toString().take(8)}"
    val cleaned = mutableListOf<ImportedVehicleListing>()
    var duplicates = 0
    var invalid = 0
    var normalizedPricesCount = 0
    var normalizedNamesCount = 0

    for (row in rows.drop(1)) {
      if (row.all { it.isBlank() }) continue

      val rawMake = row.getOrNull(makeIdx)?.trim().orEmpty()
      val rawModel = row.getOrNull(modelIdx)?.trim().orEmpty()
      val rawPrice = row.getOrNull(priceIdx)?.trim().orEmpty()

      val normMake = normalizeMake(rawMake)
      val normModel = normalizeModel(rawModel)
      if (normMake.isBlank() || normModel.isBlank()) {
        invalid++
        continue
      }
      if (!normMake.equals(rawMake, ignoreCase = false) || !normModel.equals(rawModel, ignoreCase = false)) {
        normalizedNamesCount++
      }

      val parsedPrice = parseKesPrice(rawPrice)
      if (parsedPrice == null || parsedPrice !in 100_000L..150_000_000L) {
        invalid++
        continue
      }
      if (rawPrice.any { !it.isDigit() }) normalizedPricesCount++

      val year = row.getOrNull(yearIdx)?.trim()?.toIntOrNull()?.takeIf { it in 1995..2026 } ?: 2018
      val mileage = parseMileage(row.getOrNull(mileageIdx)) ?: ((2026 - year) * 11500).coerceIn(15000, 180000)
      val engineCc = parseEngineCc(row.getOrNull(engineIdx)) ?: 1500
      val transmission = normalizeTransmission(row.getOrNull(transIdx))
      val fuel = normalizeFuel(row.getOrNull(fuelIdx))
      val condition = normalizeCondition(row.getOrNull(condIdx))
      val importStatus = normalizeImportStatus(row.getOrNull(importIdx))
      val location = row.getOrNull(locIdx)?.trim()?.ifBlank { "Nairobi" }?.take(80) ?: "Nairobi"

      val sig = signature(normMake, normModel, year, mileage, parsedPrice)
      if (!existingSignatures.add(sig)) {
        duplicates++
        continue
      }

      cleaned.add(
        ImportedVehicleListing(
          id = UUID.randomUUID().toString(),
          userId = userId,
          sourceId = sourceId,
          sourceName = sourceName,
          make = normMake.take(60),
          model = normModel.take(80),
          year = year,
          askingPriceKes = parsedPrice,
          mileageKm = mileage.coerceIn(0, 1_500_000),
          engineCc = engineCc.coerceIn(600, 7500),
          transmission = transmission,
          fuelType = fuel,
          condition = condition,
          importStatus = importStatus,
          location = location,
          demandScore = 78
        )
      )
    }

    val notes = buildList {
      add("Cleaned ${cleaned.size} listings ready for analytics.")
      if (normalizedPricesCount > 0) add("Standardized $normalizedPricesCount currency strings into numeric KES.")
      if (normalizedNamesCount > 0) add("Normalized $normalizedNamesCount make/model aliases.")
      if (duplicates > 0) add("Skipped $duplicates duplicate listings.")
      if (invalid > 0) add("Excluded $invalid rows with invalid or missing core values.")
    }

    return ImportResult(
      sourceName = sourceName,
      sourceType = sourceType,
      totalRowsRead = rows.size - 1,
      importedListings = cleaned,
      duplicatesSkipped = duplicates,
      invalidRowsSkipped = invalid,
      cleaningNotes = notes
    )
  }

  fun parseCsv(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    val currentRow = mutableListOf<String>()
    val currentCell = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < text.length) {
      val ch = text[i]
      when {
        ch == '"' -> {
          if (inQuotes && i + 1 < text.length && text[i + 1] == '"') {
            currentCell.append('"')
            i++
          } else {
            inQuotes = !inQuotes
          }
        }
        ch == ',' && !inQuotes -> {
          currentRow.add(currentCell.toString().trim())
          currentCell.clear()
        }
        (ch == '\n' || ch == '\r') && !inQuotes -> {
          if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
          currentRow.add(currentCell.toString().trim())
          if (currentRow.any { it.isNotEmpty() }) {
            rows.add(currentRow.toList())
          }
          currentRow.clear()
          currentCell.clear()
        }
        else -> currentCell.append(ch)
      }
      i++
    }
    if (currentCell.isNotEmpty() || currentRow.isNotEmpty()) {
      currentRow.add(currentCell.toString().trim())
      if (currentRow.any { it.isNotEmpty() }) rows.add(currentRow.toList())
    }
    return rows
  }

  private fun parseXlsx(inputStream: InputStream): List<List<String>> {
    val sharedStrings = mutableListOf<String>()
    var sheetBytes: ByteArray? = null

    ZipInputStream(inputStream).use { zip ->
      var entry = zip.nextEntry
      while (entry != null) {
        when (entry.name) {
          "xl/sharedStrings.xml" -> {
            val bytes = zip.readBytes()
            sharedStrings.addAll(parseSharedStringsXml(bytes))
          }
          "xl/worksheets/sheet1.xml" -> {
            sheetBytes = zip.readBytes()
          }
        }
        entry = zip.nextEntry
      }
    }

    val targetSheet = sheetBytes ?: return emptyList()
    val rows = mutableListOf<List<String>>()
    var currentRow = mutableListOf<String>()
    var cellType: String? = null
    var inValue = false
    val valueText = StringBuilder()

    val parser = XmlPullParserFactory.newInstance().newPullParser()
    parser.setInput(ByteArrayInputStream(targetSheet), "UTF-8")
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
      when (event) {
        XmlPullParser.START_TAG -> when (parser.name) {
          "row" -> currentRow = mutableListOf()
          "c" -> cellType = parser.getAttributeValue(null, "t")
          "v", "t" -> {
            inValue = true
            valueText.clear()
          }
        }
        XmlPullParser.TEXT -> if (inValue) valueText.append(parser.text)
        XmlPullParser.END_TAG -> when (parser.name) {
          "v", "t" -> {
            inValue = false
            val raw = valueText.toString().trim()
            val resolved = if (cellType == "s") {
              raw.toIntOrNull()?.let { idx -> sharedStrings.getOrNull(idx) } ?: raw
            } else raw
            currentRow.add(resolved)
          }
          "row" -> if (currentRow.any { it.isNotBlank() }) rows.add(currentRow.toList())
        }
      }
      event = parser.next()
    }
    return rows
  }

  private fun parseSharedStringsXml(bytes: ByteArray): List<String> {
    val list = mutableListOf<String>()
    val parser = XmlPullParserFactory.newInstance().newPullParser()
    parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
    var event = parser.eventType
    var inText = false
    val sb = StringBuilder()
    while (event != XmlPullParser.END_DOCUMENT) {
      when (event) {
        XmlPullParser.START_TAG -> if (parser.name == "si") sb.clear() else if (parser.name == "t") inText = true
        XmlPullParser.TEXT -> if (inText) sb.append(parser.text)
        XmlPullParser.END_TAG -> if (parser.name == "t") inText = false else if (parser.name == "si") list.add(sb.toString())
      }
      event = parser.next()
    }
    return list
  }

  private fun findCol(headers: List<String>, vararg aliases: String): Int {
    for (alias in aliases) {
      val idx = headers.indexOfFirst { it == alias || it.contains(alias) }
      if (idx >= 0) return idx
    }
    return -1
  }

  fun normalizeMake(raw: String): String {
    val clean = raw.trim().lowercase()
    return makeAliases[clean] ?: raw.trim().replaceFirstChar { it.uppercase() }
  }

  fun normalizeModel(raw: String): String {
    val clean = raw.trim().lowercase()
    return modelAliases[clean] ?: raw.trim().split(" ")
      .filter { it.isNotBlank() }
      .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
  }

  fun parseKesPrice(raw: String): Long? {
    val clean = raw.uppercase()
      .replace("KES", "")
      .replace("KSH", "")
      .replace(",", "")
      .replace(" ", "")
      .trim()
    if (clean.isEmpty()) return null
    return when {
      clean.endsWith("M") -> clean.dropLast(1).toDoubleOrNull()?.let { (it * 1_000_000.0).toLong() }
      clean.endsWith("K") -> clean.dropLast(1).toDoubleOrNull()?.let { (it * 1_000.0).toLong() }
      else -> clean.toDoubleOrNull()?.toLong()
    }
  }

  private fun parseMileage(raw: String?): Int? {
    val clean = raw?.uppercase()?.replace("KM", "")?.replace(",", "")?.trim() ?: return null
    return clean.toDoubleOrNull()?.toInt()
  }

  private fun parseEngineCc(raw: String?): Int? {
    val clean = raw?.uppercase()?.replace("CC", "")?.replace(",", "")?.trim() ?: return null
    val num = clean.toDoubleOrNull() ?: return null
    return if (num < 10.0) (num * 1000).toInt() else num.toInt()
  }

  private fun normalizeTransmission(raw: String?): String = when {
    raw == null -> "Automatic"
    raw.contains("man", ignoreCase = true) -> "Manual"
    raw.contains("cvt", ignoreCase = true) -> "CVT"
    else -> "Automatic"
  }

  private fun normalizeFuel(raw: String?): String = when {
    raw == null -> "Petrol"
    raw.contains("hyb", ignoreCase = true) -> "Hybrid"
    raw.contains("dies", ignoreCase = true) -> "Diesel"
    else -> "Petrol"
  }

  private fun normalizeCondition(raw: String?): String = when {
    raw == null -> "Good"
    raw.contains("exc", ignoreCase = true) || raw.contains("mint", ignoreCase = true) -> "Excellent"
    raw.contains("fair", ignoreCase = true) -> "Fair"
    else -> "Good"
  }

  private fun normalizeImportStatus(raw: String?): String = when {
    raw == null -> "Foreign Used"
    raw.contains("loc", ignoreCase = true) || raw.contains("kenya", ignoreCase = true) -> "Locally Used"
    else -> "Foreign Used"
  }

  private fun signature(make: String, model: String, year: Int, mileageKm: Int, priceKes: Long): String {
    return "${make.lowercase()}|${model.lowercase()}|$year|${mileageKm / 1000}|${priceKes / 10000}"
  }

  private fun resolveDisplayName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
      val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
      if (idx >= 0 && cursor.moveToFirst()) {
        return cursor.getString(idx) ?: "import.csv"
      }
    }
    return uri.lastPathSegment ?: "import.csv"
  }
}
