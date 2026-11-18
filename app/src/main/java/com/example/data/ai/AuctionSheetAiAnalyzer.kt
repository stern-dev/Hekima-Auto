package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.domain.model.AuctionSheetAnalysis
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

class AuctionSheetAiAnalyzer {

  private val auctionSchema = Schema.obj(
    properties = mapOf(
      "vehicleTitle" to Schema.string(description = "Vehicle make, model and year"),
      "make" to Schema.string(description = "Detected brand make"),
      "model" to Schema.string(description = "Detected model name"),
      "year" to Schema.integer(description = "Manufacturing year (e.g. 2019)"),
      "chassisCode" to Schema.string(description = "Frame or chassis code (e.g. NZE161-7123456) or Unknown"),
      "auctionGrade" to Schema.string(description = "Auction exterior grade (e.g. 4.5, 4, 3.5, R, RA) or Unstated"),
      "interiorGrade" to Schema.string(description = "Auction interior grade (e.g. A, B, C) or Unstated"),
      "reportedMileageKm" to Schema.integer(description = "Reported odometer reading in km"),
      "mileageVerdict" to Schema.enumeration(
        values = listOf("Plausible", "Verify Auction Docs", "Suspect Rollback"),
        description = "Odometer sanity classification against expected annual km"
      ),
      "ageRuleNote" to Schema.string(description = "Kenya 8-year import age rule assessment"),
      "repairRemarks" to Schema.array(
        items = Schema.string(),
        description = "Decoded auction inspector marks (e.g. XX replaced panel, A3 dent, W2 repaint, S2 rust)"
      ),
      "redFlags" to Schema.array(
        items = Schema.string(),
        description = "Key red flags for Kenyan buyer attention"
      ),
      "yardVerificationChecks" to Schema.array(
        items = Schema.string(),
        description = "Concrete checks buyer should verify in person at Mombasa or Nairobi yard"
      ),
      "recommendedAction" to Schema.string(description = "Concise buyer recommendation and negotiation posture"),
      "suggestedMaxOfferKes" to Schema.integer(description = "Suggested ceiling offer in KES")
    ),
    optionalProperties = listOf("chassisCode", "interiorGrade")
  )

  private val generativeModel by lazy {
    Firebase.ai.generativeModel(
      modelName = MODEL_NAME,
      generationConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = auctionSchema
      }
    )
  }

  suspend fun analyze(
    listingText: String,
    imageBitmap: Bitmap?
  ): AuctionSheetAnalysis {
    val cleanText = listingText.trim()
    require(cleanText.isNotBlank() || imageBitmap != null) {
      "Attach an auction sheet / yard photo or paste listing details before analyzing."
    }

    val prompt = buildString {
      appendLine("You are Hekima Auto's Kenyan automotive import & auction sheet expert.")
      appendLine("Analyze the provided Japanese auction sheet image and/or Kenyan dealership listing details.")
      appendLine("Decode auction grades (S, 5, 4.5, 4, 3.5, R, RA), inspector map codes (A1-A3 scratch, U1-U3 dent, W1-W3 wave/repaint, X/XX replaced panel, S1-S2 rust, C corrosion), chassis prefix, and mileage plausibility.")
      appendLine("Apply Kenya's 8-year import age rule (in 2026, vehicles manufactured 2019+ are import-eligible; 2018 or older cannot be newly imported).")
      appendLine("Estimate a realistic Kenyan shilling (KES) negotiation ceiling and list physical yard verification steps.")
      if (cleanText.isNotBlank()) {
        appendLine()
        appendLine("Listing / Buyer Notes:")
        appendLine(cleanText.take(3500))
      }
    }

    return try {
      val response = withTimeout(AI_TIMEOUT_MS) {
        generativeModel.generateContent(
          content {
            if (imageBitmap != null) {
              image(imageBitmap)
            }
            text(prompt)
          }
        )
      }
      val rawJson = response.text
      if (rawJson.isNullOrBlank()) {
        throw IllegalStateException("AI analyzer returned an empty response.")
      }
      parseAnalysisJson(rawJson)
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Auction analysis timed out", e)
      throw IllegalStateException("AI analysis timed out after 60 seconds. Check your connection and retry.", e)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Firebase AI auction analysis failed", e)
      throw IllegalStateException(e.localizedMessage ?: "Firebase AI analysis failed.", e)
    }
  }

  companion object {
    private const val TAG = "AuctionSheetAi"
    const val MODEL_NAME = "gemini-3.8-flash"
    const val AI_TIMEOUT_MS = 60_000L

    fun parseAnalysisJson(jsonString: String): AuctionSheetAnalysis {
      val json = JSONObject(jsonString)
      fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length())
          .mapNotNull { optString(it).trim().takeIf { s -> s.isNotEmpty() } }
      }
      return AuctionSheetAnalysis(
        vehicleTitle = json.optString("vehicleTitle", "Analyzed Vehicle").ifBlank { "Analyzed Vehicle" },
        make = json.optString("make", "Toyota").ifBlank { "Toyota" },
        model = json.optString("model", "Vehicle").ifBlank { "Vehicle" },
        year = json.optInt("year", 2019).coerceIn(1990, 2030),
        chassisCode = json.optString("chassisCode", "Unstated").ifBlank { "Unstated" },
        auctionGrade = json.optString("auctionGrade", "Unstated").ifBlank { "Unstated" },
        interiorGrade = json.optString("interiorGrade", "Unstated").ifBlank { "Unstated" },
        reportedMileageKm = json.optInt("reportedMileageKm", 0).coerceAtLeast(0),
        mileageVerdict = json.optString("mileageVerdict", "Verify Auction Docs").ifBlank { "Verify Auction Docs" },
        ageRuleNote = json.optString("ageRuleNote", "Verify year against KEBS/KRA 8-year import cap."),
        repairRemarks = json.optJSONArray("repairRemarks").toStringList(),
        redFlags = json.optJSONArray("redFlags").toStringList(),
        yardVerificationChecks = json.optJSONArray("yardVerificationChecks").toStringList(),
        recommendedAction = json.optString("recommendedAction", "Inspect chassis and JEVIC records prior to deposit."),
        suggestedMaxOfferKes = json.optLong("suggestedMaxOfferKes", 1_450_000L).coerceAtLeast(100_000L)
      )
    }

    suspend fun loadScaledBitmap(context: Context, uri: Uri, maxEdgePx: Int = 1400): Bitmap? =
      withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
          BitmapFactory.decodeStream(stream, null, bounds)
        }
        val maxDim = max(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (maxDim / sampleSize > maxEdgePx) {
          sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        context.contentResolver.openInputStream(uri)?.use { stream ->
          BitmapFactory.decodeStream(stream, null, decodeOpts)
        }
      }
  }
}
