package dte.masteriot.daily_carbon_tracker.data.repository

import androidx.annotation.StringRes
import dte.masteriot.daily_carbon_tracker.R
import dte.masteriot.daily_carbon_tracker.data.model.ActivityCategory
import dte.masteriot.daily_carbon_tracker.data.model.EmissionFactor
import dte.masteriot.daily_carbon_tracker.data.model.QuantityUnit

/**
 * Catalogue des activités proposées dans le Journal, avec :
 * - leur identifiant dans l'API Impact CO2 (id de transport ou slug de repas) ;
 * - un facteur de secours intégré, repris des données ADEME d'Impact CO2
 *   (fichiers deplacement.ts et repas.ts du dépôt incubateur-ademe/impactco2),
 *   utilisé tant que l'API n'a pas répondu ou si le réseau est coupé.
 *
 * Transports : kg CO2e par km et par personne, hors construction du véhicule
 * (même calcul que l'API par défaut). Avion : traînées comprises.
 */
object FactorCatalog {

    const val FLIGHT_ID = "flight"

    data class Spec(
        val id: String,
        val category: ActivityCategory,
        val unit: QuantityUnit,
        @StringRes val labelRes: Int,
        val fallbackKgPerUnit: Double,
        val apiTransportId: Int? = null,
        val apiMealSlug: String? = null,
    )

    /** L'avion a un facteur différent selon la distance (court, moyen, long courrier). */
    data class FlightBand(
        val id: String,
        val maxKm: Double,
        val probeKm: Int,
        val fallbackKgPerKm: Double,
    )

    val transportSpecs = listOf(
        Spec("car_thermal", ActivityCategory.TRANSPORT, QuantityUnit.KM, R.string.factor_car_thermal, 0.17689, apiTransportId = 4),
        Spec("car_electric", ActivityCategory.TRANSPORT, QuantityUnit.KM, R.string.factor_car_electric, 0.01934, apiTransportId = 5),
        Spec("bus", ActivityCategory.TRANSPORT, QuantityUnit.KM, R.string.factor_bus, 0.1135, apiTransportId = 9),
        Spec("tram", ActivityCategory.TRANSPORT, QuantityUnit.KM, R.string.factor_tram, 0.0038, apiTransportId = 10),
        Spec("metro", ActivityCategory.TRANSPORT, QuantityUnit.KM, R.string.factor_metro, 0.0042, apiTransportId = 11),
        Spec("ter", ActivityCategory.TRANSPORT, QuantityUnit.KM, R.string.factor_ter, 0.0229, apiTransportId = 15),
        Spec("tgv", ActivityCategory.TRANSPORT, QuantityUnit.KM, R.string.factor_tgv, 0.0023, apiTransportId = 2),
        Spec("ebike", ActivityCategory.TRANSPORT, QuantityUnit.KM, R.string.factor_ebike, 0.00223, apiTransportId = 8),
        Spec("bike", ActivityCategory.TRANSPORT, QuantityUnit.KM, R.string.factor_bike, 0.0, apiTransportId = 7),
        Spec("walk", ActivityCategory.TRANSPORT, QuantityUnit.KM, R.string.factor_walk, 0.0, apiTransportId = 30),
    )

    const val FLIGHT_API_ID = 1

    val flightBands = listOf(
        FlightBand("flight_short", maxKm = 1000.0, probeKm = 500, fallbackKgPerKm = 0.2242),
        FlightBand("flight_medium", maxKm = 2000.0, probeKm = 1500, fallbackKgPerKm = 0.1843),
        FlightBand("flight_medium_long", maxKm = 5000.0, probeKm = 3000, fallbackKgPerKm = 0.1665),
        FlightBand("flight_long", maxKm = Double.MAX_VALUE, probeKm = 6000, fallbackKgPerKm = 0.1776),
    )

    val flightSpec = Spec(
        FLIGHT_ID, ActivityCategory.FLIGHT, QuantityUnit.KM, R.string.factor_flight,
        flightBands.first().fallbackKgPerKm, apiTransportId = FLIGHT_API_ID,
    )

    val mealSpecs = listOf(
        Spec("meal_beef", ActivityCategory.MEAL, QuantityUnit.MEAL, R.string.meal_beef, 4.97, apiMealSlug = "repasavecduboeuf"),
        Spec("meal_pork", ActivityCategory.MEAL, QuantityUnit.MEAL, R.string.meal_pork, 1.65, apiMealSlug = "repasavecduporc"),
        Spec("meal_chicken", ActivityCategory.MEAL, QuantityUnit.MEAL, R.string.meal_chicken, 1.46, apiMealSlug = "repasavecdupoulet"),
        Spec("meal_white_fish", ActivityCategory.MEAL, QuantityUnit.MEAL, R.string.meal_white_fish, 2.43, apiMealSlug = "repasavecdupoissonblanc"),
        Spec("meal_oily_fish", ActivityCategory.MEAL, QuantityUnit.MEAL, R.string.meal_oily_fish, 1.46, apiMealSlug = "repasavecdupoissongras"),
        Spec("meal_vegetarian", ActivityCategory.MEAL, QuantityUnit.MEAL, R.string.meal_vegetarian, 0.85, apiMealSlug = "repasvegetarien"),
        Spec("meal_vegan", ActivityCategory.MEAL, QuantityUnit.MEAL, R.string.meal_vegan, 0.54, apiMealSlug = "repasvegetalien"),
    )

    /** Catégories proposées dans le formulaire du Journal (l'énergie vient de MQTT). */
    val journalCategories = listOf(ActivityCategory.TRANSPORT, ActivityCategory.FLIGHT, ActivityCategory.MEAL)

    val selectableSpecs: List<Spec> = transportSpecs + flightSpec + mealSpecs

    /** Valeurs de secours : id → kg CO2e par unité (inclut les tranches de l'avion). */
    val fallbackValues: Map<String, Double> = buildMap {
        selectableSpecs.forEach { put(it.id, it.fallbackKgPerUnit) }
        flightBands.forEach { put(it.id, it.fallbackKgPerKm) }
    }

    fun specsFor(category: ActivityCategory): List<Spec> = selectableSpecs.filter { it.category == category }

    fun spec(id: String): Spec? = selectableSpecs.firstOrNull { it.id == id }

    fun flightBandFor(km: Double): FlightBand = flightBands.first { km <= it.maxKm }

    /** Facteur (kg CO2e par unité) à appliquer pour cette activité et cette quantité. */
    fun factorFor(specId: String, quantity: Double, values: Map<String, Double>): Double {
        val key = if (specId == FLIGHT_ID) flightBandFor(quantity).id else specId
        return values[key] ?: fallbackValues[key] ?: 0.0
    }

    fun co2eFor(specId: String, quantity: Double, values: Map<String, Double>): Double =
        factorFor(specId, quantity, values) * quantity

    fun Spec.toEmissionFactor(values: Map<String, Double>) = EmissionFactor(
        id = id,
        labelRes = labelRes,
        category = category,
        kgCo2ePerUnit = values[id] ?: fallbackKgPerUnit,
        unit = unit,
    )
}
