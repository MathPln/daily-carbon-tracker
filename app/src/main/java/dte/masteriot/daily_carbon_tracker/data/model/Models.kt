package dte.masteriot.daily_carbon_tracker.data.model

import androidx.annotation.StringRes

/** Catégories d'activités saisissables dans le Journal. */
enum class ActivityCategory { TRANSPORT, FLIGHT, MEAL, ENERGY }

/** Modes de transport (Journal, autosuggestion par accéléromètre, comparatif d'itinéraires). */
enum class TransportMode { WALK, BIKE, PUBLIC_TRANSPORT, CAR }

/** Unité de la quantité saisie : des kilomètres ou un nombre de repas. */
enum class QuantityUnit { KM, MEAL }

/** Facteur d'émission ADEME : kg CO2e par unité (par km ou par repas). */
data class EmissionFactor(
    val id: String,
    @StringRes val labelRes: Int,
    val category: ActivityCategory,
    val kgCo2ePerUnit: Double,
    val unit: QuantityUnit,
)

/** Une activité enregistrée dans le Journal. */
data class ActivityEntry(
    val id: Long,
    val category: ActivityCategory,
    val factorId: String,
    @StringRes val labelRes: Int,
    val quantity: Double,
    val unit: QuantityUnit,
    val co2eKg: Double,
    val timestampMillis: Long,
)

/** Relevé reçu sur le topic MQTT home/energy : {"electricity_kwh": 2.45, "gas_m3": 0.60}. */
data class EnergyReading(
    val electricityKwh: Double,
    val gasM3: Double,
    val receivedAtMillis: Long,
)

/** Une alternative d'itinéraire présentée dans le comparatif de l'écran Carte. */
data class RouteOption(
    val mode: TransportMode,
    val distanceKm: Double,
    val durationMinutes: Int,
    val co2eKg: Double,
)
