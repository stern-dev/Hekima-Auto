package com.example.domain.analytics

import com.example.domain.model.AuctionSheetAnalysis
import com.example.domain.model.InspectionCheckItem
import com.example.domain.model.InspectionStatus
import com.example.domain.model.VehicleListing
import com.example.domain.model.YardInspectionDraft
import com.example.domain.model.YardInspectionReport
import java.util.Locale

object YardInspectionEngine {

  fun defaultChecklist(): List<InspectionCheckItem> = listOf(
    InspectionCheckItem(
      id = "eng_cold_start",
      category = "Engine & Transmission",
      title = "Cold start, idle stability & CVT shift",
      guidance = "Check for white/blue exhaust smoke, timing chain rattle, or CVT judder on takeoff.",
      weight = 14,
      defaultRepairKes = 85_000L
    ),
    InspectionCheckItem(
      id = "eng_fluids_leaks",
      category = "Engine & Transmission",
      title = "Engine oil cap, coolant & sump leaks",
      guidance = "Ensure no milky emulsion under oil cap and no fresh oil/ATF leaks beneath sump.",
      weight = 10,
      defaultRepairKes = 35_000L
    ),
    InspectionCheckItem(
      id = "rust_undercarriage",
      category = "Undercarriage & Rust",
      title = "Subframe, sills & suspension corrosion",
      guidance = "Inspect rear axle beam, exhaust mounts, and wheel arches for deep flaking Japanese snow-salt rust.",
      weight = 12,
      defaultRepairKes = 55_000L
    ),
    InspectionCheckItem(
      id = "rust_suspension",
      category = "Undercarriage & Rust",
      title = "Lower arms, bushes & shock absorbers",
      guidance = "Look for torn CV boots, leaking struts, and worn lower arm bushings for Kenyan roads.",
      weight = 8,
      defaultRepairKes = 42_000L
    ),
    InspectionCheckItem(
      id = "vin_chassis_match",
      category = "Chassis & VIN Verification",
      title = "Chassis & engine plate match logbook/auction",
      guidance = "Verify firewall stamp matches NTSA logbook, customs C17B/IDF, and auction sheet frame ID.",
      weight = 12,
      defaultRepairKes = 0L
    ),
    InspectionCheckItem(
      id = "vin_obd_scan",
      category = "Chassis & VIN Verification",
      title = "OBD-II ECU & SRS airbag scan",
      guidance = "Confirm zero cleared ABS, hybrid battery, or airbag fault codes; check seat belt year tags.",
      weight = 10,
      defaultRepairKes = 60_000L
    ),
    InspectionCheckItem(
      id = "docs_jevic_qisj",
      category = "JEVIC/QISJ & NTSA TIMS Docs",
      title = "JEVIC/QISJ certificate & odometer audit",
      guidance = "Cross-check inspection mileage certificate and Japanese export cert against cluster reading.",
      weight = 10,
      defaultRepairKes = 0L
    ),
    InspectionCheckItem(
      id = "docs_tims_kra",
      category = "JEVIC/QISJ & NTSA TIMS Docs",
      title = "NTSA TIMS ownership & KRA duty clearance",
      guidance = "Verify active TIMS transfer PIN, zero caveat/chattel mortgage, and authentic KRA duty entry.",
      weight = 8,
      defaultRepairKes = 0L
    ),
    InspectionCheckItem(
      id = "body_panel_gaps",
      category = "Body & Repaint Audit",
      title = "Panel gaps, core support & weld seams",
      guidance = "Inspect radiator core support bolts and pillar spot welds for undisclosed R/RA collision repairs.",
      weight = 8,
      defaultRepairKes = 48_000L
    ),
    InspectionCheckItem(
      id = "body_tyres_glass",
      category = "Body & Repaint Audit",
      title = "Tyre tread age, glass stamps & lights",
      guidance = "Check 4-digit tyre manufacture week/year and matching Toyota/OEM glass stamps.",
      weight = 4,
      defaultRepairKes = 38_000L
    ),
    InspectionCheckItem(
      id = "road_brakes_steering",
      category = "Road Test",
      title = "Straight tracking, steering rack & ABS braking",
      guidance = "Confirm steering returns smoothly, zero pulling under hard braking, and quiet wheel bearings.",
      weight = 4,
      defaultRepairKes = 30_000L
    )
  )

  fun createDraftFromListing(listing: VehicleListing): YardInspectionDraft {
    val odoReport = KenyanMarketAnalyticsEngine.evaluateOdometerSanity(
      year = listing.year,
      mileageKm = listing.mileageKm,
      importStatus = listing.importStatus
    )
    val baseItems = defaultChecklist().map { item ->
      if (item.id == "docs_jevic_qisj" && odoReport.status.name != "PLAUSIBLE") {
        item.copy(status = InspectionStatus.WATCH)
      } else {
        item
      }
    }
    return YardInspectionDraft(
      listingId = listing.id,
      vehicleTitle = listing.fullName,
      location = listing.location,
      askingPriceKes = listing.askingPriceKes,
      items = baseItems,
      notes = if (odoReport.status.name != "PLAUSIBLE") odoReport.headline else ""
    )
  }

  fun createDraftFromAiAnalysis(
    analysis: AuctionSheetAnalysis,
    fallbackPriceKes: Long = 1_500_000L
  ): YardInspectionDraft {
    val hasMileageWarning = analysis.mileageVerdict.contains("Suspect", ignoreCase = true) ||
      analysis.mileageVerdict.contains("Verify", ignoreCase = true)
    val hasAccidentGrade = analysis.auctionGrade.uppercase(Locale.US).startsWith("R") ||
      analysis.repairRemarks.any { it.contains("XX", ignoreCase = true) || it.contains("repair", ignoreCase = true) }

    val items = defaultChecklist().map { item ->
      when {
        item.id == "docs_jevic_qisj" && hasMileageWarning -> item.copy(status = InspectionStatus.WATCH)
        item.id == "body_panel_gaps" && hasAccidentGrade -> item.copy(status = InspectionStatus.WATCH)
        else -> item
      }
    }
    val offerRef = analysis.suggestedMaxOfferKes.takeIf { it >= 100_000L } ?: fallbackPriceKes
    val aiNotes = buildString {
      append("AI Grade ${analysis.auctionGrade}/${analysis.interiorGrade}")
      if (analysis.chassisCode.isNotBlank()) append(" • Chassis ${analysis.chassisCode}")
      if (analysis.redFlags.isNotEmpty()) append("\nRed Flags: ${analysis.redFlags.joinToString("; ")}")
    }
    return YardInspectionDraft(
      listingId = "ai_${analysis.make.lowercase(Locale.US)}_${analysis.year}",
      vehicleTitle = analysis.vehicleTitle.ifBlank { "${analysis.make} ${analysis.model} ${analysis.year}".trim() },
      location = "Mombasa / Yard Inspection",
      askingPriceKes = offerRef,
      items = items,
      notes = aiNotes.take(900)
    )
  }

  fun toReport(userId: String, draft: YardInspectionDraft): YardInspectionReport {
    val summary = draft.items
      .groupBy { it.category }
      .entries
      .take(12)
      .map { (category, group) ->
        val issueCount = group.count { it.status == InspectionStatus.ISSUE }
        val watchCount = group.count { it.status == InspectionStatus.WATCH }
        when {
          issueCount > 0 -> "$category: $issueCount Issue(s)"
          watchCount > 0 -> "$category: $watchCount Watch"
          else -> "$category: Pass"
        }
      }
    return YardInspectionReport(
      userId = userId,
      listingId = draft.listingId,
      vehicleTitle = draft.vehicleTitle,
      location = draft.location,
      askingPriceKes = draft.askingPriceKes,
      overallScore = draft.overallScore,
      verdictTier = draft.verdictTier,
      estimatedRepairKes = draft.estimatedRepairKes,
      recommendedOfferKes = draft.recommendedOfferKes,
      checklistSummary = summary,
      notes = draft.notes.ifBlank { null }
    )
  }
}
