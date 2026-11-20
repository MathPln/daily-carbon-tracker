package dte.masteriot.daily_carbon_tracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dte.masteriot.daily_carbon_tracker.CarbonApplication
import dte.masteriot.daily_carbon_tracker.data.model.ActivityCategory
import dte.masteriot.daily_carbon_tracker.data.model.ActivityEntry
import dte.masteriot.daily_carbon_tracker.data.repository.CarbonRepository
import dte.masteriot.daily_carbon_tracker.data.repository.FactorCatalog
import dte.masteriot.daily_carbon_tracker.data.repository.FactorSource
import dte.masteriot.daily_carbon_tracker.data.repository.MotionRepository
import dte.masteriot.daily_carbon_tracker.data.repository.MqttRepository
import dte.masteriot.daily_carbon_tracker.data.sensor.DetectedMotion
import dte.masteriot.daily_carbon_tracker.utils.Constants
import dte.masteriot.daily_carbon_tracker.utils.isToday
import dte.masteriot.daily_carbon_tracker.utils.millisUntilNextMidnight
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class BudgetStatus { WITHIN, WARNING, OVER }

data class CategoryShare(val category: ActivityCategory, val co2eKg: Double)

data class DashboardUiState(
    val totalKg: Double,
    val budgetKg: Double = Constants.DAILY_CARBON_BUDGET_KG,
    val breakdown: List<CategoryShare>,
    val ecoModeActive: Boolean,
) {
    val progress: Float get() = if (budgetKg > 0) (totalKg / budgetKg).toFloat() else 0f

    val status: BudgetStatus
        get() = when {
            progress > 1f -> BudgetStatus.OVER
            progress >= WARNING_THRESHOLD -> BudgetStatus.WARNING
            else -> BudgetStatus.WITHIN
        }

    companion object {
        const val WARNING_THRESHOLD = 0.8f

        fun fromEntries(
            entries: List<ActivityEntry>,
            extraEnergyKg: Double = 0.0,
            ecoModeActive: Boolean? = null,
        ): DashboardUiState {
            val transport = entries
                .filter { it.category == ActivityCategory.TRANSPORT || it.category == ActivityCategory.FLIGHT }
                .sumOf { it.co2eKg }
            val meals = entries.filter { it.category == ActivityCategory.MEAL }.sumOf { it.co2eKg }
            val energy = entries.filter { it.category == ActivityCategory.ENERGY }.sumOf { it.co2eKg } + extraEnergyKg
            val total = transport + meals + energy
            val autoEco = ecoModeActive ?: (total >= Constants.DAILY_CARBON_BUDGET_KG * WARNING_THRESHOLD)
            return DashboardUiState(
                totalKg = total,
                breakdown = listOf(
                    CategoryShare(ActivityCategory.TRANSPORT, transport),
                    CategoryShare(ActivityCategory.MEAL, meals),
                    CategoryShare(ActivityCategory.ENERGY, energy),
                ),
                ecoModeActive = autoEco,
            )
        }
    }
}

data class LogbookUiState(
    val todayEntries: List<ActivityEntry> = emptyList(),
    val todayTotalKg: Double = 0.0,
    val factorValues: Map<String, Double> = FactorCatalog.fallbackValues,
    val factorSource: FactorSource = FactorSource.LOADING,
    val suggestedMotion: DetectedMotion? = null,
)

class CarbonViewModel(
    private val repository: CarbonRepository,
    private val motionRepository: MotionRepository? = null,
    private val mqttRepository: MqttRepository? = null,
) : ViewModel() {

    private val dayTicker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(millisUntilNextMidnight() + MIDNIGHT_MARGIN_MS)
        }
    }

    private val todayEntries = combine(repository.entries, dayTicker) { entries, now ->
        entries.filter { isToday(it.timestampMillis, now) }
    }

    val dashboardState: StateFlow<DashboardUiState> = combine(
        todayEntries,
        mqttRepository?.todayEnergyCo2eKg ?: flowOf(0.0),
    ) { entries, mqttEnergyKg ->
        DashboardUiState.fromEntries(entries, extraEnergyKg = mqttEnergyKg)
    }.onEach { state ->
        mqttRepository?.onBudgetEvaluated(state.status, state.totalKg, state.budgetKg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState.fromEntries(emptyList()))

    val logbookState: StateFlow<LogbookUiState> = combine(
        todayEntries,
        repository.factorValues,
        repository.factorSource,
        motionRepository?.suggestedMotion ?: flowOf(null),
    ) { entries, values, source, motion ->
        LogbookUiState(
            todayEntries = entries,
            todayTotalKg = entries.sumOf { it.co2eKg },
            factorValues = values,
            factorSource = source,
            suggestedMotion = motion,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogbookUiState())

    init {
        viewModelScope.launch { repository.ensureFactorsLoaded() }
    }

    fun addActivity(factorId: String, quantity: Double) {
        repository.addActivity(factorId, quantity)
    }

    fun removeActivity(id: Long) {
        repository.removeActivity(id)
    }

    fun retryFactors() {
        viewModelScope.launch { repository.refreshFactors() }
    }

    fun dismissMotionSuggestion() {
        motionRepository?.clearSuggestion()
    }

    companion object {
        private const val MIDNIGHT_MARGIN_MS = 1_000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CarbonApplication
                CarbonViewModel(app.carbonRepository, app.motionRepository, app.mqttRepository)
            }
        }

        fun sampleState() = DashboardUiState(
            totalKg = 3.5,
            breakdown = listOf(
                CategoryShare(ActivityCategory.TRANSPORT, 1.8),
                CategoryShare(ActivityCategory.MEAL, 1.1),
                CategoryShare(ActivityCategory.ENERGY, 0.6),
            ),
            ecoModeActive = false,
        )
    }
}