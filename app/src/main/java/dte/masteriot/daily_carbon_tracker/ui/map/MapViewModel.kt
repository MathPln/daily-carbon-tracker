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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.osmdroid.util.GeoPoint
import kotlin.math.roundToInt

data class MapUiState(
    val startPoint: GeoPoint? = GeoPoint(40.3897, -3.6279),
    val endPoint: GeoPoint? = GeoPoint(40.4168, -3.7038),
    val routeOptions: List<RouteOption> = emptyList(),
    val selectedMode: TransportMode = TransportMode.PUBLIC_TRANSPORT,
)

class MapViewModel(
    private val carbonRepository: CarbonRepository,
) : ViewModel() {

    private val startPoint = MutableStateFlow<GeoPoint?>(GeoPoint(40.3897, -3.6279))
    private val endPoint = MutableStateFlow<GeoPoint?>(GeoPoint(40.4168, -3.7038))
    private val selectedMode = MutableStateFlow(TransportMode.PUBLIC_TRANSPORT)

    val uiState: StateFlow<MapUiState> = combine(
        startPoint,
        endPoint,
        selectedMode,
        carbonRepository.factorValues,
    ) { start, end, mode, factors ->
        val options = if (start != null && end != null) computeOptions(start, end, factors) else emptyList()
        MapUiState(start, end, options, mode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    fun onMapTap(point: GeoPoint) {
        when {
            startPoint.value == null -> startPoint.value = point
            endPoint.value == null -> endPoint.value = point
            else -> {
                startPoint.value = point
                endPoint.value = null
            }
        }
    }

    fun setStartPoint(point: GeoPoint) {
        startPoint.value = point
    }

    fun selectMode(mode: TransportMode) {
        selectedMode.value = mode
    }

    fun logSelectedRoute() {
        val option = uiState.value.routeOptions.firstOrNull { it.mode == selectedMode.value } ?: return
        val factorId = when (option.mode) {
            TransportMode.WALK -> "walk"
            TransportMode.BIKE -> "bike"
            TransportMode.PUBLIC_TRANSPORT -> "bus"
            TransportMode.CAR -> "car_thermal"
        }
        carbonRepository.addActivity(factorId, option.distanceKm)
    }

    private fun computeOptions(start: GeoPoint, end: GeoPoint, factors: Map<String, Double>): List<RouteOption> {
        val km = ((start.distanceToAsDouble(end) / 1000.0) * 1.25).coerceAtLeast(0.1)
        val specs = listOf(
            Triple(TransportMode.WALK, "walk" to 5.0, "walk"),
            Triple(TransportMode.BIKE, "bike" to 15.0, "bike"),
            Triple(TransportMode.PUBLIC_TRANSPORT, "bus" to 25.0, "bus"),
            Triple(TransportMode.CAR, "car_thermal" to 35.0, "car_thermal"),
        )
        return specs.map { (mode, speedPair, factorId) ->
            val minutes = ((km / speedPair.second) * 60.0).roundToInt().coerceAtLeast(1)
            RouteOption(mode, km, minutes, FactorCatalog.co2eFor(factorId, km, factors))
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