package dte.masteriot.daily_carbon_tracker.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dte.masteriot.daily_carbon_tracker.CarbonApplication
import dte.masteriot.daily_carbon_tracker.data.model.RouteOption
import dte.masteriot.daily_carbon_tracker.data.model.TransportMode
import dte.masteriot.daily_carbon_tracker.data.repository.CarbonRepository
import dte.masteriot.daily_carbon_tracker.data.repository.FactorCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

enum class TransitChoice(val label: String, val emissionFactorId: String) {
    METRO("Metro", "bus"),
    BUS("Bus", "bus"),
    CERCANIAS("Cercanias", "bus"),
    COMBO("Combo", "bus"),
}

data class RouteSegment(
    val points: List<GeoPoint>,
    val isDashed: Boolean,
)

data class TransitBreakdown(
    val choice: TransitChoice,
    val walkKm: Double,
    val walkMinutes: Int,
    val walkCo2eKg: Double,
    val transitKm: Double,
    val transitMinutes: Int,
    val transitCo2eKg: Double,
) {
    val totalKm: Double get() = walkKm + transitKm
    val totalMinutes: Int get() = walkMinutes + transitMinutes
    val totalCo2eKg: Double get() = walkCo2eKg + transitCo2eKg
}

data class MapUiState(
    val startPoint: GeoPoint? = GeoPoint(40.3897, -3.6279),
    val endPoint: GeoPoint? = GeoPoint(40.4168, -3.7038),
    val routeOptions: List<RouteOption> = emptyList(),
    val selectedMode: TransportMode = TransportMode.PUBLIC_TRANSPORT,
    val transitChoice: TransitChoice = TransitChoice.METRO,
    val customWalkMinutes: Int = 10,
    val customTotalMinutes: Int = 25,
    val transitBreakdown: TransitBreakdown? = null,
    val activeSegments: List<RouteSegment> = emptyList(),
    val isLoadingRoute: Boolean = false,
)

private data class RoadRouteData(
    val roadPoints: List<GeoPoint>,
    val roadDistanceKm: Double,
    val carMinutes: Int,
)

private data class RouteBundle(
    val roadRoute: RoadRouteData? = null,
    val factors: Map<String, Double> = emptyMap(),
)

private data class TransitConfig(
    val choice: TransitChoice = TransitChoice.METRO,
    val walkMinutes: Int = 10,
    val totalMinutes: Int = 25,
)

private data class DerivedMapData(
    val options: List<RouteOption> = emptyList(),
    val segments: List<RouteSegment> = emptyList(),
    val breakdown: TransitBreakdown? = null,
)

class MapViewModel(
    private val carbonRepository: CarbonRepository,
) : ViewModel() {

    private val startPointFlow = MutableStateFlow<GeoPoint?>(GeoPoint(40.3897, -3.6279))
    private val endPointFlow = MutableStateFlow<GeoPoint?>(GeoPoint(40.4168, -3.7038))
    private val selectedModeFlow = MutableStateFlow(TransportMode.PUBLIC_TRANSPORT)
    private val transitConfigFlow = MutableStateFlow(TransitConfig())
    private val routeBundleFlow = MutableStateFlow(RouteBundle(factors = carbonRepository.factorValues.value))
    private val loadingFlow = MutableStateFlow(false)

    private var routingJob: Job? = null

    val uiState: StateFlow<MapUiState> = combine(
        startPointFlow,
        endPointFlow,
        selectedModeFlow,
        transitConfigFlow,
        combine(routeBundleFlow, loadingFlow) { bundle, loading -> bundle to loading },
    ) { start, end, mode, transitCfg, bundleAndLoading ->
        val (bundle, loading) = bundleAndLoading
        val derived = if (start != null && end != null) {
            buildDerivedData(start, end, mode, transitCfg, bundle)
        } else {
            DerivedMapData()
        }
        MapUiState(
            startPoint = start,
            endPoint = end,
            routeOptions = derived.options,
            selectedMode = mode,
            transitChoice = transitCfg.choice,
            customWalkMinutes = transitCfg.walkMinutes,
            customTotalMinutes = transitCfg.totalMinutes,
            transitBreakdown = derived.breakdown,
            activeSegments = derived.segments,
            isLoadingRoute = loading,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    init {
        refreshRoutes()
    }

    fun onMapTap(point: GeoPoint) {
        when {
            startPointFlow.value == null -> {
                startPointFlow.value = point
                refreshRoutes()
            }
            endPointFlow.value == null -> {
                endPointFlow.value = point
                refreshRoutes()
            }
            else -> {
                startPointFlow.value = point
                endPointFlow.value = null
                routeBundleFlow.value = RouteBundle(factors = carbonRepository.factorValues.value)
            }
        }
    }

    fun setStartPoint(point: GeoPoint) {
        startPointFlow.value = point
        refreshRoutes()
    }

    fun setRoutePoints(start: GeoPoint?, end: GeoPoint?) {
        start?.let { startPointFlow.value = it }
        end?.let { endPointFlow.value = it }
        refreshRoutes()
    }

    fun selectMode(mode: TransportMode) {
        selectedModeFlow.value = mode
    }

    // Re-seed default walking and total duration estimates when transit type changes
    fun selectTransitChoice(choice: TransitChoice) {
        val corridorKm = routeBundleFlow.value.roadRoute?.roadDistanceKm ?: 5.0
        val estimated = estimateTransitDurations(corridorKm, choice)
        transitConfigFlow.value = TransitConfig(
            choice = choice,
            walkMinutes = estimated.first,
            totalMinutes = estimated.second,
        )
    }

    // Allow manual override of walking and total transit minutes in UI fields
    fun updateTransitDurations(walkMinutesText: String, totalMinutesText: String) {
        val walkMin = walkMinutesText.trim().toIntOrNull()?.coerceIn(0, 180)
            ?: transitConfigFlow.value.walkMinutes
        val totalMin = totalMinutesText.trim().toIntOrNull()?.coerceIn(1, 300)
            ?: transitConfigFlow.value.totalMinutes
        transitConfigFlow.value = transitConfigFlow.value.copy(
            walkMinutes = walkMin,
            totalMinutes = maxOf(totalMin, walkMin + 1),
        )
    }

    // Log walking and transit distances separately when multimodal transit is selected
    fun logSelectedRoute() {
        val current = uiState.value
        if (current.selectedMode == TransportMode.PUBLIC_TRANSPORT && current.transitBreakdown != null) {
            val b = current.transitBreakdown
            if (b.walkKm > 0.0) carbonRepository.addActivity("walk", b.walkKm)
            if (b.transitKm > 0.0) carbonRepository.addActivity(b.choice.emissionFactorId, b.transitKm)
            return
        }
        val option = current.routeOptions.firstOrNull { it.mode == current.selectedMode } ?: return
        val factorId = when (option.mode) {
            TransportMode.WALK -> "walk"
            TransportMode.BIKE -> "bike"
            TransportMode.PUBLIC_TRANSPORT -> "bus"
            TransportMode.CAR -> "car_thermal"
        }
        carbonRepository.addActivity(factorId, option.distanceKm)
    }

    private fun refreshRoutes() {
        val start = startPointFlow.value
        val end = endPointFlow.value
        if (start == null || end == null) {
            routeBundleFlow.value = RouteBundle(factors = carbonRepository.factorValues.value)
            return
        }

        routingJob?.cancel()
        routingJob = viewModelScope.launch {
            loadingFlow.value = true
            val factors = carbonRepository.factorValues.value
            val fetchedRoad = withContext(Dispatchers.IO) {
                fetchRoadRoute(start, end)
            }
            val corridorKm = fetchedRoad?.roadDistanceKm
                ?: ((start.distanceToAsDouble(end) / 1000.0) * 1.25).coerceAtLeast(0.5)
            val estimated = estimateTransitDurations(corridorKm, transitConfigFlow.value.choice)
            transitConfigFlow.value = transitConfigFlow.value.copy(
                walkMinutes = estimated.first,
                totalMinutes = estimated.second,
            )
            routeBundleFlow.value = RouteBundle(
                roadRoute = fetchedRoad,
                factors = factors,
            )
            loadingFlow.value = false
        }
    }

    // Default duration estimate (walk minutes to total minutes) by transit line type
    private fun estimateTransitDurations(corridorKm: Double, choice: TransitChoice): Pair<Int, Int> {
        val km = corridorKm.coerceAtLeast(0.5)
        return when (choice) {
            TransitChoice.METRO -> {
                val walkMin = ((km * 0.14 / 4.8) * 60.0).roundToInt().coerceIn(5, 25)
                val rideMin = ((km * 0.86 / 26.0) * 60.0).roundToInt().coerceAtLeast(5) + 5
                walkMin to (walkMin + rideMin)
            }
            TransitChoice.BUS -> {
                val walkMin = ((km * 0.10 / 4.8) * 60.0).roundToInt().coerceIn(4, 20)
                val rideMin = ((km * 0.90 / 17.0) * 60.0).roundToInt().coerceAtLeast(6) + 5
                walkMin to (walkMin + rideMin)
            }
            TransitChoice.CERCANIAS -> {
                val walkMin = ((km * 0.16 / 4.8) * 60.0).roundToInt().coerceIn(6, 30)
                val rideMin = ((km * 0.84 / 40.0) * 60.0).roundToInt().coerceAtLeast(7) + 6
                walkMin to (walkMin + rideMin)
            }
            TransitChoice.COMBO -> {
                val walkMin = ((km * 0.18 / 4.8) * 60.0).roundToInt().coerceIn(8, 35)
                val rideMin = ((km * 0.82 / 20.0) * 60.0).roundToInt().coerceAtLeast(8) + 8
                walkMin to (walkMin + rideMin)
            }
        }
    }

    private fun buildDerivedData(
        start: GeoPoint,
        end: GeoPoint,
        selectedMode: TransportMode,
        transitCfg: TransitConfig,
        bundle: RouteBundle,
    ): DerivedMapData {
        val fallbackKm = ((start.distanceToAsDouble(end) / 1000.0) * 1.25).coerceAtLeast(0.2)
        val roadPoints = bundle.roadRoute?.roadPoints?.takeIf { it.size >= 2 } ?: listOf(start, end)
        val roadKm = bundle.roadRoute?.roadDistanceKm ?: fallbackKm
        val carMinutes = bundle.roadRoute?.carMinutes
            ?: ((roadKm / 30.0) * 60.0).roundToInt().coerceAtLeast(1)

        // Full-length paths from start to end with a slight lateral offset so walk/bike differ from car
        val walkKm = (roadKm * 0.92).coerceAtLeast(0.2)
        val walkMinutes = ((walkKm / 4.8) * 60.0).roundToInt().coerceAtLeast(1)
        val walkPoints = offsetRoutePoints(roadPoints, start, end, latOffset = 0.0012, lonOffset = -0.0010)

        val bikeKm = (roadKm * 0.97).coerceAtLeast(0.2)
        val bikeMinutes = ((bikeKm / 14.5) * 60.0).roundToInt().coerceAtLeast(1)
        val bikePoints = offsetRoutePoints(roadPoints, start, end, latOffset = -0.0010, lonOffset = 0.0012)

        // Multimodal transit: walking distance from walkMinutes at 4.8 km/h + remaining corridor distance
        val transitWalkKm = ((transitCfg.walkMinutes / 60.0) * 4.8).coerceIn(0.0, roadKm * 0.9)
        val transitRideKm = (roadKm - transitWalkKm).coerceAtLeast(0.2)
        val transitRideMinutes = (transitCfg.totalMinutes - transitCfg.walkMinutes).coerceAtLeast(1)

        val walkCo2eKg = FactorCatalog.co2eFor("walk", transitWalkKm, bundle.factors)
        val baseTransitCo2eKg = FactorCatalog.co2eFor("bus", transitRideKm, bundle.factors)
        val transitCo2eKg = when (transitCfg.choice) {
            TransitChoice.METRO -> baseTransitCo2eKg * 0.35
            TransitChoice.BUS -> baseTransitCo2eKg
            TransitChoice.CERCANIAS -> baseTransitCo2eKg * 0.45
            TransitChoice.COMBO -> baseTransitCo2eKg * 0.50
        }

        val breakdown = TransitBreakdown(
            choice = transitCfg.choice,
            walkKm = transitWalkKm,
            walkMinutes = transitCfg.walkMinutes,
            walkCo2eKg = walkCo2eKg,
            transitKm = transitRideKm,
            transitMinutes = transitRideMinutes,
            transitCo2eKg = transitCo2eKg,
        )

        val walkOption = RouteOption(
            mode = TransportMode.WALK,
            distanceKm = walkKm,
            durationMinutes = walkMinutes,
            co2eKg = FactorCatalog.co2eFor("walk", walkKm, bundle.factors),
        )
        val bikeOption = RouteOption(
            mode = TransportMode.BIKE,
            distanceKm = bikeKm,
            durationMinutes = bikeMinutes,
            co2eKg = FactorCatalog.co2eFor("bike", bikeKm, bundle.factors),
        )
        val transitOption = RouteOption(
            mode = TransportMode.PUBLIC_TRANSPORT,
            distanceKm = breakdown.totalKm,
            durationMinutes = transitCfg.totalMinutes,
            co2eKg = breakdown.totalCo2eKg,
        )
        val carOption = RouteOption(
            mode = TransportMode.CAR,
            distanceKm = roadKm,
            durationMinutes = carMinutes,
            co2eKg = FactorCatalog.co2eFor("car_thermal", roadKm, bundle.factors),
        )

        // Show distinct full-length paths per mode; public transit renders a simple dashed line
        val segments = when (selectedMode) {
            TransportMode.WALK -> listOf(RouteSegment(walkPoints, isDashed = false))
            TransportMode.BIKE -> listOf(RouteSegment(bikePoints, isDashed = false))
            TransportMode.PUBLIC_TRANSPORT -> listOf(RouteSegment(listOf(start, end), isDashed = true))
            TransportMode.CAR -> listOf(RouteSegment(roadPoints, isDashed = false))
        }

        return DerivedMapData(
            options = listOf(walkOption, bikeOption, transitOption, carOption),
            segments = segments,
            breakdown = breakdown,
        )
    }

    // Keep start and end anchored while slightly shifting intermediate points for distinct mode polylines
    private fun offsetRoutePoints(
        points: List<GeoPoint>,
        start: GeoPoint,
        end: GeoPoint,
        latOffset: Double,
        lonOffset: Double,
    ): List<GeoPoint> {
        if (points.size <= 2) return listOf(start, end)
        return points.mapIndexed { index, pt ->
            if (index == 0) {
                start
            } else if (index == points.lastIndex) {
                end
            } else {
                val weight = sin((index.toDouble() / points.lastIndex) * PI)
                GeoPoint(pt.latitude + latOffset * weight, pt.longitude + lonOffset * weight)
            }
        }
    }

    // Query OSRM car profile for real street geometry, distance, and driving duration
    private fun fetchRoadRoute(start: GeoPoint, end: GeoPoint): RoadRouteData? {
        return try {
            val urlStr = String.format(
                Locale.US,
                "https://router.project-osrm.org/route/v1/car/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson",
                start.longitude,
                start.latitude,
                end.longitude,
                end.latitude,
            )
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 5_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "DailyCarbonTracker/1.0")
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { reader ->
                val json = JSONObject(reader.readText())
                val routes = json.optJSONArray("routes") ?: return null
                if (routes.length() == 0) return null
                val route = routes.getJSONObject(0)
                val distanceKm = (route.optDouble("distance", 0.0) / 1000.0).coerceAtLeast(0.2)
                val durationMinutes = (route.optDouble("duration", 0.0) / 60.0).roundToInt().coerceAtLeast(1)
                val coords = route.optJSONObject("geometry")?.optJSONArray("coordinates")
                val points = buildList {
                    if (coords != null) {
                        for (i in 0 until coords.length()) {
                            val pair = coords.getJSONArray(i)
                            if (pair.length() >= 2) {
                                add(GeoPoint(pair.getDouble(1), pair.getDouble(0)))
                            }
                        }
                    }
                }
                if (points.size < 2) return null
                RoadRouteData(points, distanceKm, durationMinutes)
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CarbonApplication
                MapViewModel(app.carbonRepository)
            }
        }
    }
}