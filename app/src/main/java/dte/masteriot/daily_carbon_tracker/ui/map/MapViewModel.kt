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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

data class MapUiState(
    val startPoint: GeoPoint? = GeoPoint(40.3897, -3.6279),
    val endPoint: GeoPoint? = GeoPoint(40.4168, -3.7038),
    val routePoints: List<GeoPoint> = emptyList(),
    val routeOptions: List<RouteOption> = emptyList(),
    val selectedMode: TransportMode = TransportMode.PUBLIC_TRANSPORT,
    val isLoading: Boolean = false,
)

class MapViewModel(
    private val carbonRepository: CarbonRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        calculateRoutes()
    }

    fun onMapTap(point: GeoPoint) {
        val current = _uiState.value
        when {
            current.startPoint == null -> setStartPoint(point)
            current.endPoint == null -> setRoutePoints(current.startPoint, point)
            else -> _uiState.update {
                it.copy(startPoint = point, endPoint = null, routePoints = emptyList(), routeOptions = emptyList())
            }
        }
    }

    fun setStartPoint(point: GeoPoint) {
        _uiState.update { it.copy(startPoint = point) }
        calculateRoutes()
    }

    fun setRoutePoints(start: GeoPoint?, end: GeoPoint?) {
        _uiState.update {
            it.copy(startPoint = start ?: it.startPoint, endPoint = end ?: it.endPoint)
        }
        calculateRoutes()
    }

    fun selectMode(mode: TransportMode) {
        _uiState.update { it.copy(selectedMode = mode) }
    }

    fun logSelectedRoute() {
        val state = _uiState.value
        val option = state.routeOptions.firstOrNull { it.mode == state.selectedMode } ?: return
        val factorId = when (option.mode) {
            TransportMode.WALK -> "walk"
            TransportMode.BIKE -> "bike"
            TransportMode.PUBLIC_TRANSPORT -> "bus"
            TransportMode.CAR -> "car_thermal"
        }
        carbonRepository.addActivity(factorId, option.distanceKm)
    }

    private fun calculateRoutes() {
        val start = _uiState.value.startPoint ?: return
        val end = _uiState.value.endPoint ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val osrmResult = withContext(Dispatchers.IO) { fetchOsrmRoute(start, end) }

            // Fallback to straight-line distance * 1.25 if offline or OSRM fails
            val distanceKm = osrmResult?.first ?: ((start.distanceToAsDouble(end) / 1000.0) * 1.25).coerceAtLeast(0.2)
            val points = osrmResult?.second ?: listOf(start, end)
            val factors = carbonRepository.factorValues.value

            val options = listOf(
                RouteOption(TransportMode.WALK, distanceKm, ((distanceKm / 4.8) * 60).roundToInt().coerceAtLeast(1), FactorCatalog.co2eFor("walk", distanceKm, factors)),
                RouteOption(TransportMode.BIKE, distanceKm, ((distanceKm / 15.0) * 60).roundToInt().coerceAtLeast(1), FactorCatalog.co2eFor("bike", distanceKm, factors)),
                RouteOption(TransportMode.PUBLIC_TRANSPORT, distanceKm, ((distanceKm / 22.0) * 60).roundToInt().coerceAtLeast(1), FactorCatalog.co2eFor("bus", distanceKm, factors)),
                RouteOption(TransportMode.CAR, distanceKm, ((distanceKm / 30.0) * 60).roundToInt().coerceAtLeast(1), FactorCatalog.co2eFor("car_thermal", distanceKm, factors)),
            )

            _uiState.update {
                it.copy(routePoints = points, routeOptions = options, isLoading = false)
            }
        }
    }

    private fun fetchOsrmRoute(start: GeoPoint, end: GeoPoint): Pair<Double, List<GeoPoint>>? {
        return try {
            val urlStr = String.format(
                Locale.US,
                "https://router.project-osrm.org/route/v1/car/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson",
                start.longitude, start.latitude, end.longitude, end.latitude,
            )
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 5_000
            }
            if (conn.responseCode !in 200..299) return null

            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val route = json.optJSONArray("routes")?.optJSONObject(0) ?: return null
            val distanceKm = (route.optDouble("distance", 0.0) / 1000.0).coerceAtLeast(0.2)
            val coords = route.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return null

            val points = (0 until coords.length()).mapNotNull { i ->
                val pair = coords.optJSONArray(i)
                if (pair != null && pair.length() >= 2) GeoPoint(pair.getDouble(1), pair.getDouble(0)) else null
            }
            if (points.size >= 2) distanceKm to points else null
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