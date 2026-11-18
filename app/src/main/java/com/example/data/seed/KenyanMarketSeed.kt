package com.example.data.seed

import com.example.domain.model.PriceObservation
import com.example.domain.model.VehicleListing
import com.example.domain.model.VehicleMake
import com.example.domain.model.VehicleModel
import com.example.domain.model.VehicleSegment
import kotlin.math.pow

object KenyanMarketSeed {

  val makes: List<VehicleMake> = listOf(
    VehicleMake("mk_toyota", "Toyota", "Japan", "Dominant"),
    VehicleMake("mk_mazda", "Mazda", "Japan", "High"),
    VehicleMake("mk_subaru", "Subaru", "Japan", "High"),
    VehicleMake("mk_honda", "Honda", "Japan", "High"),
    VehicleMake("mk_nissan", "Nissan", "Japan", "Medium-High"),
    VehicleMake("mk_suzuki", "Suzuki", "Japan", "Medium-High"),
    VehicleMake("mk_mitsubishi", "Mitsubishi", "Japan", "Medium"),
    VehicleMake("mk_isuzu", "Isuzu", "Japan", "Commercial"),
    VehicleMake("mk_vw", "Volkswagen", "Germany", "Medium"),
    VehicleMake("mk_mercedes", "Mercedes-Benz", "Germany", "Luxury"),
    VehicleMake("mk_bmw", "BMW", "Germany", "Luxury"),
    VehicleMake("mk_audi", "Audi", "Germany", "Luxury"),
    VehicleMake("mk_landrover", "Land Rover", "UK", "Luxury"),
    VehicleMake("mk_lexus", "Lexus", "Japan", "Luxury"),
    VehicleMake("mk_ford", "Ford", "USA", "Pickups"),
    VehicleMake("mk_hyundai", "Hyundai", "South Korea", "Medium"),
    VehicleMake("mk_kia", "Kia", "South Korea", "Medium")
  )

  private data class ModelTemplate(
    val id: String,
    val make: String,
    val model: String,
    val bodyType: String,
    val segment: VehicleSegment,
    val base2018Kes: Long,
    val engineCc: Int,
    val transmission: String,
    val fuelType: String,
    val demandScore: Int,
    val annualDepRate: Double
  )

  private val templates = listOf(
    // --- Everyday Cars & Sedans ---
    ModelTemplate("md_axio", "Toyota", "Axio", "Sedan", VehicleSegment.CARS, 1390000L, 1500, "Automatic", "Petrol", 95, 0.068),
    ModelTemplate("md_fielder", "Toyota", "Fielder", "Wagon", VehicleSegment.CARS, 1480000L, 1500, "Automatic", "Petrol", 96, 0.064),
    ModelTemplate("md_vitz", "Toyota", "Vitz", "Hatchback", VehicleSegment.CARS, 1120000L, 1300, "Automatic", "Petrol", 93, 0.062),
    ModelTemplate("md_premio", "Toyota", "Premio", "Sedan", VehicleSegment.CARS, 2050000L, 1800, "Automatic", "Petrol", 91, 0.065),
    ModelTemplate("md_allion", "Toyota", "Allion", "Sedan", VehicleSegment.CARS, 1980000L, 1500, "Automatic", "Petrol", 90, 0.066),
    ModelTemplate("md_belta", "Toyota", "Belta", "Sedan", VehicleSegment.CARS, 1080000L, 1300, "Automatic", "Petrol", 86, 0.072),
    ModelTemplate("md_passo", "Toyota", "Passo", "Hatchback", VehicleSegment.CARS, 950000L, 1000, "Automatic", "Petrol", 88, 0.071),
    ModelTemplate("md_demio", "Mazda", "Demio", "Hatchback", VehicleSegment.CARS, 1080000L, 1300, "Automatic", "Petrol", 89, 0.082),
    ModelTemplate("md_axela", "Mazda", "Axela", "Sedan", VehicleSegment.CARS, 1580000L, 1500, "Automatic", "Petrol", 84, 0.086),
    ModelTemplate("md_atenza", "Mazda", "Atenza", "Sedan", VehicleSegment.CARS, 2150000L, 2000, "Automatic", "Diesel", 80, 0.094),
    ModelTemplate("md_impreza", "Subaru", "Impreza", "Hatchback", VehicleSegment.CARS, 1560000L, 1600, "Automatic", "Petrol", 83, 0.089),
    ModelTemplate("md_legacy", "Subaru", "Legacy", "Wagon", VehicleSegment.CARS, 1920000L, 2000, "Automatic", "Petrol", 79, 0.092),
    ModelTemplate("md_fit", "Honda", "Fit", "Hatchback", VehicleSegment.CARS, 1140000L, 1300, "CVT", "Hybrid", 88, 0.074),
    ModelTemplate("md_grace", "Honda", "Grace", "Sedan", VehicleSegment.CARS, 1460000L, 1500, "Automatic", "Hybrid", 82, 0.079),
    ModelTemplate("md_note", "Nissan", "Note", "Hatchback", VehicleSegment.CARS, 960000L, 1200, "CVT", "Hybrid", 85, 0.092),
    ModelTemplate("md_tiida", "Nissan", "Tiida", "Hatchback", VehicleSegment.CARS, 980000L, 1500, "Automatic", "Petrol", 77, 0.095),
    ModelTemplate("md_sylphy", "Nissan", "Sylphy", "Sedan", VehicleSegment.CARS, 1340000L, 1800, "CVT", "Petrol", 76, 0.097),
    ModelTemplate("md_swift", "Suzuki", "Swift", "Hatchback", VehicleSegment.CARS, 1160000L, 1200, "Automatic", "Petrol", 84, 0.076),
    ModelTemplate("md_alto", "Suzuki", "Alto", "Hatchback", VehicleSegment.CARS, 760000L, 660, "Automatic", "Petrol", 81, 0.078),
    ModelTemplate("md_baleno", "Suzuki", "Baleno", "Hatchback", VehicleSegment.CARS, 1320000L, 1200, "Automatic", "Petrol", 79, 0.080),
    ModelTemplate("md_golf", "Volkswagen", "Golf", "Hatchback", VehicleSegment.CARS, 1680000L, 1400, "Automatic", "Petrol", 76, 0.105),
    ModelTemplate("md_polo", "Volkswagen", "Polo", "Hatchback", VehicleSegment.CARS, 1290000L, 1200, "Automatic", "Petrol", 74, 0.102),

    // --- Compact & Family SUVs / Crossovers ---
    ModelTemplate("md_rav4", "Toyota", "RAV4", "SUV", VehicleSegment.SUVS, 3150000L, 2000, "Automatic", "Hybrid", 90, 0.071),
    ModelTemplate("md_harrier", "Toyota", "Harrier", "SUV", VehicleSegment.SUVS, 3650000L, 2000, "Automatic", "Petrol", 93, 0.075),
    ModelTemplate("md_chr", "Toyota", "C-HR", "Crossover", VehicleSegment.SUVS, 2450000L, 1800, "CVT", "Hybrid", 84, 0.077),
    ModelTemplate("md_vanguard", "Toyota", "Vanguard", "SUV", VehicleSegment.SUVS, 2680000L, 2400, "Automatic", "Petrol", 87, 0.076),
    ModelTemplate("md_rush", "Toyota", "Rush", "SUV", VehicleSegment.SUVS, 2120000L, 1500, "Automatic", "Petrol", 82, 0.078),
    ModelTemplate("md_cx5", "Mazda", "CX-5", "SUV", VehicleSegment.SUVS, 2820000L, 2000, "Automatic", "Petrol", 89, 0.088),
    ModelTemplate("md_cx3", "Mazda", "CX-3", "Crossover", VehicleSegment.SUVS, 1980000L, 1500, "Automatic", "Diesel", 80, 0.087),
    ModelTemplate("md_forester", "Subaru", "Forester", "SUV", VehicleSegment.SUVS, 2780000L, 2000, "Automatic", "Petrol", 90, 0.084),
    ModelTemplate("md_xv", "Subaru", "XV", "Crossover", VehicleSegment.SUVS, 2040000L, 2000, "Automatic", "Petrol", 85, 0.085),
    ModelTemplate("md_outback", "Subaru", "Outback", "SUV", VehicleSegment.SUVS, 2950000L, 2500, "Automatic", "Petrol", 82, 0.086),
    ModelTemplate("md_vezel", "Honda", "Vezel", "Crossover", VehicleSegment.SUVS, 2240000L, 1500, "Automatic", "Hybrid", 87, 0.079),
    ModelTemplate("md_crv", "Honda", "CR-V", "SUV", VehicleSegment.SUVS, 2890000L, 2000, "Automatic", "Petrol", 83, 0.081),
    ModelTemplate("md_xtrail", "Nissan", "X-Trail", "SUV", VehicleSegment.SUVS, 2320000L, 2000, "CVT", "Petrol", 81, 0.096),
    ModelTemplate("md_juke", "Nissan", "Juke", "Crossover", VehicleSegment.SUVS, 1540000L, 1500, "CVT", "Petrol", 75, 0.094),
    ModelTemplate("md_qashqai", "Nissan", "Qashqai", "Crossover", VehicleSegment.SUVS, 1920000L, 1600, "CVT", "Petrol", 76, 0.095),
    ModelTemplate("md_outlander", "Mitsubishi", "Outlander", "SUV", VehicleSegment.SUVS, 2480000L, 2400, "CVT", "Hybrid", 77, 0.095),
    ModelTemplate("md_rvr", "Mitsubishi", "RVR", "Crossover", VehicleSegment.SUVS, 1790000L, 1800, "CVT", "Petrol", 78, 0.091),
    ModelTemplate("md_pajero", "Mitsubishi", "Pajero", "SUV", VehicleSegment.SUVS, 4250000L, 3200, "Automatic", "Diesel", 79, 0.083),
    ModelTemplate("md_escudo", "Suzuki", "Escudo", "SUV", VehicleSegment.SUVS, 1960000L, 1600, "Automatic", "Petrol", 79, 0.082),
    ModelTemplate("md_jimny", "Suzuki", "Jimny", "SUV", VehicleSegment.SUVS, 2650000L, 1500, "Automatic", "Petrol", 83, 0.069),
    ModelTemplate("md_tiguan", "Volkswagen", "Tiguan", "SUV", VehicleSegment.SUVS, 2790000L, 2000, "Automatic", "Petrol", 78, 0.108),
    ModelTemplate("md_tucson", "Hyundai", "Tucson", "SUV", VehicleSegment.SUVS, 2640000L, 2000, "Automatic", "Petrol", 75, 0.098),
    ModelTemplate("md_sportage", "Kia", "Sportage", "SUV", VehicleSegment.SUVS, 2580000L, 2000, "Automatic", "Diesel", 74, 0.099),

    // --- 7-Seater MPVs, Station Wagons & Work Vans ---
    ModelTemplate("md_noah", "Toyota", "Noah", "MPV", VehicleSegment.VANS_MPVS, 2320000L, 2000, "Automatic", "Petrol", 92, 0.067),
    ModelTemplate("md_voxy", "Toyota", "Voxy", "MPV", VehicleSegment.VANS_MPVS, 2380000L, 2000, "Automatic", "Petrol", 91, 0.068),
    ModelTemplate("md_wish", "Toyota", "Wish", "MPV", VehicleSegment.VANS_MPVS, 1650000L, 1800, "Automatic", "Petrol", 89, 0.069),
    ModelTemplate("md_sienta", "Toyota", "Sienta", "MPV", VehicleSegment.VANS_MPVS, 1490000L, 1500, "Automatic", "Hybrid", 87, 0.070),
    ModelTemplate("md_probox", "Toyota", "Probox", "Utility Wagon", VehicleSegment.VANS_MPVS, 1180000L, 1500, "Automatic", "Petrol", 97, 0.055),
    ModelTemplate("md_succeed", "Toyota", "Succeed", "Utility Wagon", VehicleSegment.VANS_MPVS, 1210000L, 1500, "Automatic", "Petrol", 94, 0.057),
    ModelTemplate("md_hiace", "Toyota", "Hiace", "Commercial Van", VehicleSegment.VANS_MPVS, 3850000L, 2800, "Manual", "Diesel", 95, 0.060),
    ModelTemplate("md_serena", "Nissan", "Serena", "MPV", VehicleSegment.VANS_MPVS, 1840000L, 2000, "CVT", "Hybrid", 82, 0.088),
    ModelTemplate("md_nv200", "Nissan", "NV200", "Van", VehicleSegment.VANS_MPVS, 1380000L, 1600, "Automatic", "Petrol", 84, 0.079),
    ModelTemplate("md_freed", "Honda", "Freed", "MPV", VehicleSegment.VANS_MPVS, 1480000L, 1500, "Automatic", "Hybrid", 81, 0.078),

    // --- Pickups & Commercial Utility ---
    ModelTemplate("md_hilux", "Toyota", "Hilux", "Pickup", VehicleSegment.PICKUPS, 4650000L, 2800, "Automatic", "Diesel", 95, 0.054),
    ModelTemplate("md_dmax", "Isuzu", "D-Max", "Pickup", VehicleSegment.PICKUPS, 4150000L, 2500, "Manual", "Diesel", 92, 0.058),
    ModelTemplate("md_ranger", "Ford", "Ranger", "Pickup", VehicleSegment.PICKUPS, 3980000L, 2200, "Automatic", "Diesel", 83, 0.076),
    ModelTemplate("md_navara", "Nissan", "Navara", "Pickup", VehicleSegment.PICKUPS, 3620000L, 2500, "Automatic", "Diesel", 79, 0.081),
    ModelTemplate("md_l200", "Mitsubishi", "L200", "Pickup", VehicleSegment.PICKUPS, 3380000L, 2400, "Manual", "Diesel", 80, 0.077),

    // --- Executive & Luxury ---
    ModelTemplate("md_prado", "Toyota", "Land Cruiser Prado", "Luxury SUV", VehicleSegment.LUXURY, 7400000L, 2800, "Automatic", "Diesel", 94, 0.058),
    ModelTemplate("md_lc200", "Toyota", "Land Cruiser V8", "Luxury SUV", VehicleSegment.LUXURY, 11800000L, 4500, "Automatic", "Diesel", 91, 0.056),
    ModelTemplate("md_rx450h", "Lexus", "RX 450h", "Luxury SUV", VehicleSegment.LUXURY, 6450000L, 3500, "Automatic", "Hybrid", 76, 0.089),
    ModelTemplate("md_c200", "Mercedes-Benz", "C200", "Executive Sedan", VehicleSegment.LUXURY, 3580000L, 2000, "Automatic", "Petrol", 75, 0.118),
    ModelTemplate("md_e250", "Mercedes-Benz", "E250", "Executive Sedan", VehicleSegment.LUXURY, 4450000L, 2000, "Automatic", "Petrol", 72, 0.120),
    ModelTemplate("md_gle", "Mercedes-Benz", "GLE 350", "Luxury SUV", VehicleSegment.LUXURY, 7900000L, 3000, "Automatic", "Diesel", 70, 0.125),
    ModelTemplate("md_x3", "BMW", "X3", "Luxury SUV", VehicleSegment.LUXURY, 3850000L, 2000, "Automatic", "Petrol", 69, 0.128),
    ModelTemplate("md_x5", "BMW", "X5", "Luxury SUV", VehicleSegment.LUXURY, 6750000L, 3000, "Automatic", "Diesel", 68, 0.130),
    ModelTemplate("md_320i", "BMW", "320i", "Executive Sedan", VehicleSegment.LUXURY, 2950000L, 2000, "Automatic", "Petrol", 66, 0.132),
    ModelTemplate("md_q5", "Audi", "Q5", "Luxury SUV", VehicleSegment.LUXURY, 3920000L, 2000, "Automatic", "Petrol", 70, 0.122),
    ModelTemplate("md_touareg", "Volkswagen", "Touareg", "Luxury SUV", VehicleSegment.LUXURY, 4850000L, 3000, "Automatic", "Diesel", 67, 0.124),
    ModelTemplate("md_discovery", "Land Rover", "Discovery", "Luxury SUV", VehicleSegment.LUXURY, 5650000L, 3000, "Automatic", "Diesel", 68, 0.126),
    ModelTemplate("md_rrsport", "Land Rover", "Range Rover Sport", "Luxury SUV", VehicleSegment.LUXURY, 8950000L, 3000, "Automatic", "Diesel", 71, 0.129),
    ModelTemplate("md_evoque", "Land Rover", "Range Rover Evoque", "Luxury SUV", VehicleSegment.LUXURY, 4350000L, 2000, "Automatic", "Petrol", 72, 0.119)
  )

  val models: List<VehicleModel> = templates.map { tpl ->
    VehicleModel(
      id = tpl.id,
      make = tpl.make,
      name = tpl.model,
      bodyType = tpl.bodyType,
      typicalEngineCc = tpl.engineCc,
      annualDepreciationRate = tpl.annualDepRate,
      segment = tpl.segment
    )
  }

  private val locations = listOf("Nairobi", "Mombasa", "Kisumu", "Nakuru", "Eldoret", "Thika", "Nyeri")
  private val sources = listOf(
    "seed_kcb" to "Kenyan Car Bazaar (Catalog)",
    "seed_cheki" to "AutoCheki Kenya Benchmark",
    "seed_yards" to "Nairobi & Mombasa Yards"
  )

  val listings: List<VehicleListing> by lazy {
    val result = mutableListOf<VehicleListing>()
    val years = listOf(2014, 2016, 2018, 2020, 2022)
    var index = 1

    for (tpl in templates) {
      for ((yIdx, year) in years.withIndex()) {
        val ageFrom2018 = 2018 - year
        val yearFactor = (1.0 - tpl.annualDepRate).pow(ageFrom2018.toDouble())
        val isLocal = (index % 3 == 0) && year <= 2018
        val importStatus = if (isLocal) "Locally Used" else "Foreign Used"
        val statusFactor = if (isLocal) 0.89 else 1.0
        val condition = when {
          year >= 2021 -> "Excellent"
          index % 5 == 0 -> "Fair"
          else -> "Good"
        }
        val condFactor = when (condition) {
          "Excellent" -> 1.06
          "Fair" -> 0.90
          else -> 1.0
        }

        // Introduce a few realistic suspect-low odometer units for buyer audit demonstration
        val suspectLowMileageCase =
          (tpl.model == "Harrier" && year == 2016) ||
            (tpl.model == "CX-5" && year == 2016) ||
            (tpl.model == "Demio" && year == 2016) ||
            (tpl.model == "Note" && year == 2018)

        val baseMileage = if (suspectLowMileageCase) {
          24000 + (index % 3) * 3200
        } else {
          ((2026 - year) * 11200 + (index % 4) * 5800 + if (isLocal) 24000 else 0)
            .coerceIn(18000, 210000)
        }
        val mileageFactor = 1.0 - ((baseMileage - 75000).coerceAtLeast(-40000) / 100000.0) * 0.065

        val anomalyFactor = when {
          tpl.model == "Axio" && year == 2018 -> 0.84 // Undervalued deal matching target watchlists
          tpl.model == "Fielder" && year == 2018 -> 0.87
          tpl.model == "C200" && year == 2018 -> 1.22 // Overpriced outlier
          else -> 0.97 + ((index * 17) % 9) * 0.008
        }

        val rawPrice = tpl.base2018Kes * yearFactor * statusFactor * condFactor * mileageFactor * anomalyFactor
        val roundedPrice = ((rawPrice / 10000.0).toLong() * 10000L).coerceAtLeast(480000L)
        val sourcePair = sources[index % sources.size]
        val transmission = if (tpl.model == "Vitz" && index % 6 == 0) "Manual" else tpl.transmission
        val fuel = if (tpl.model == "CX-5" && yIdx % 2 == 0) "Diesel" else tpl.fuelType

        result.add(
          VehicleListing(
            id = "seed_${tpl.make.lowercase().replace(" ", "_")}_${tpl.model.lowercase().replace(" ", "_")}_${year}_$index",
            make = tpl.make,
            model = tpl.model,
            year = year,
            askingPriceKes = roundedPrice,
            mileageKm = baseMileage,
            engineCc = tpl.engineCc,
            transmission = transmission,
            fuelType = fuel,
            condition = condition,
            importStatus = importStatus,
            location = locations[index % locations.size],
            demandScore = (tpl.demandScore + (if (importStatus == "Foreign Used") 3 else -4)).coerceIn(45, 99),
            sourceId = sourcePair.first,
            sourceName = sourcePair.second,
            isImported = false,
            segment = tpl.segment.label
          )
        )
        index++
      }
    }
    result
  }

  val historicalObservations: List<PriceObservation> by lazy {
    val obs = mutableListOf<PriceObservation>()
    val months = listOf("2025-Q2", "2025-Q4", "2026-Q2", "2026-Q4")
    val anchorModels = listOf("Axio", "CX-5", "Forester", "Harrier", "Fielder", "Vezel", "Probox", "Hilux")
    templates.filter { it.model in anchorModels }.forEach { tpl ->
      months.forEachIndexed { mIdx, quarter ->
        val drift = 1.0 - (mIdx * 0.018)
        obs.add(
          PriceObservation(
            modelKey = "${tpl.make}_${tpl.model}_2018",
            year = 2018,
            averagePriceKes = ((tpl.base2018Kes * drift) / 10000.0).toLong() * 10000L,
            sampleSize = 12 + mIdx * 3,
            observedMonth = quarter
          )
        )
      }
    }
    obs
  }

  fun modelRateFor(make: String, model: String): Double {
    return templates.firstOrNull {
      it.make.equals(make, ignoreCase = true) && it.model.equals(model, ignoreCase = true)
    }?.annualDepRate ?: 0.085
  }

  fun segmentFor(make: String, model: String): VehicleSegment {
    val match = templates.firstOrNull {
      it.make.equals(make, ignoreCase = true) && it.model.equals(model, ignoreCase = true)
    }
    if (match != null) return match.segment
    val mLower = make.lowercase()
    val mdLower = model.lowercase()
    return when {
      mLower in listOf("mercedes-benz", "bmw", "audi", "land rover", "lexus") -> VehicleSegment.LUXURY
      mdLower.contains("hilux") || mdLower.contains("d-max") || mdLower.contains("ranger") ||
        mdLower.contains("navara") || mdLower.contains("l200") -> VehicleSegment.PICKUPS
      mdLower.contains("noah") || mdLower.contains("voxy") || mdLower.contains("serena") ||
        mdLower.contains("hiace") || mdLower.contains("probox") || mdLower.contains("succeed") ||
        mdLower.contains("wish") || mdLower.contains("sienta") -> VehicleSegment.VANS_MPVS
      mdLower.contains("cx-") || mdLower.contains("rav4") || mdLower.contains("harrier") ||
        mdLower.contains("forester") || mdLower.contains("x-trail") || mdLower.contains("cr-v") ||
        mdLower.contains("prado") || mdLower.contains("tiguan") -> VehicleSegment.SUVS
      else -> VehicleSegment.CARS
    }
  }
}
