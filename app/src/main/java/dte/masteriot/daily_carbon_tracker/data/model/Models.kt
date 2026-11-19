package dte.masteriot.daily_carbon_tracker.data.model

/** Catégories d'activités saisissables dans le Journal. */
enum class ActivityCategory { TRANSPORT, FLIGHT, MEAL, ENERGY }

/** Modes de transport (Journal, autosuggestion par accéléromètre, comparatif d'itinéraires). */
enum class TransportMode { WALK, BIKE, PUBLIC_TRANSPORT, CAR }

/** Facteur d'émission ADEME : kg CO2e par unité (km, repas, kWh, m³…). */
data class EmissionFactor(
    val id: String,
    val label: String,
    val category: ActivityCategory,
    val kgCo2ePerUnit: Double,
    val unit: String,
)

/** Une activité enregistrée dans le Journal du jour. */
data class ActivityEntry(
    val id: Long,
    val category: ActivityCategory,
    val label: String,
    val quantity: Double,
    val unit: String,
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
