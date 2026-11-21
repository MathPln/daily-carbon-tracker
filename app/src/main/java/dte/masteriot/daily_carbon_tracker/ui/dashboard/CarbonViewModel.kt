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
import java.util.Calendar
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
    val yearUsedKg: Double = 0.0,
    val annualBudgetKg: Double = Constants.DAILY_CARBON_BUDGET_KG * 365,
    val predictedAnnualKg: Double = 0.0,
) {
    val progress: Float get() = if (budgetKg > 0) (totalKg / budgetKg).toFloat() else 0f
    val yearProgress: Float get() = if (annualBudgetKg > 0) (yearUsedKg / annualBudgetKg).toFloat() else 0f
    val predictedYearProgress: Float get() = if (annualBudgetKg > 0) (predictedAnnualKg / annualBudgetKg).toFloat() else 0f

    val status: BudgetStatus
        get() = when {
            progress > 1f -> BudgetStatus.OVER
            progress >= WARNING_THRESHOLD -> BudgetStatus.WARNING
            else -> BudgetStatus.WITHIN
        }

    val yearStatus: BudgetStatus
        get() = when {
            predictedYearProgress > 1f -> BudgetStatus.OVER
            predictedYearProgress >= WARNING_THRESHOLD -> BudgetStatus.WARNING
            else -> BudgetStatus.WITHIN
        }

    companion object {
        const val WARNING_THRESHOLD = 0.8f

        fun fromEntries(
            entries: List<ActivityEntry>,
            extraEnergyKg: Double = 0.0,
            ecoModeActive: Boolean? = null,
            nowMillis: Long = System.currentTimeMillis(),
            annualBudgetKg: Double? = null,
        ): DashboardUiState {
            val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
            val currentYear = cal.get(Calendar.YEAR)
            val elapsedDays = cal.get(Calendar.DAY_OF_YEAR).coerceAtLeast(1)
            val daysInYear = cal.getActualMaximum(Calendar.DAY_OF_YEAR).coerceAtLeast(365)

            // Annual target drives the daily target so both stay consistent
            val resolvedAnnualBudget = annualBudgetKg?.takeIf { it > 0.0 }
                ?: (Constants.DAILY_CARBON_BUDGET_KG * daysInYear)
            val dailyBudget = resolvedAnnualBudget / daysInYear

            val todayEntries = entries.filter { isToday(it.timestampMillis, nowMillis) }
            val transport = todayEntries
                .filter { it.category == ActivityCategory.TRANSPORT || it.category == ActivityCategory.FLIGHT }
                .sumOf { it.co2eKg }
            val meals = todayEntries.filter { it.category == ActivityCategory.MEAL }.sumOf { it.co2eKg }
            val energy = todayEntries.filter { it.category == ActivityCategory.ENERGY }.sumOf { it.co2eKg } + extraEnergyKg
            val total = transport + meals + energy
            val autoEco = ecoModeActive ?: (total >= dailyBudget * WARNING_THRESHOLD)

            val entryCal = Calendar.getInstance()
            val yearUsed = entries.filter { entry ->
                entryCal.timeInMillis = entry.timestampMillis
                entryCal.get(Calendar.YEAR) == currentYear
            }.sumOf { it.co2eKg }

            val dailyAverage = yearUsed / elapsedDays
            val predictedAnnual = dailyAverage * daysInYear

            return DashboardUiState(
                totalKg = total,
                budgetKg = dailyBudget,
                breakdown = listOf(
                    CategoryShare(ActivityCategory.TRANSPORT, transport),
                    CategoryShare(ActivityCategory.MEAL, meals),
                    CategoryShare(ActivityCategory.ENERGY, energy),
                ),
                ecoModeActive = autoEco,
                yearUsedKg = yearUsed,
                annualBudgetKg = resolvedAnnualBudget,
                predictedAnnualKg = predictedAnnual,
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
    private val annualBudgetFlow: StateFlow<Double>? = null,
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
        repository.entries,
        dayTicker,
        mqttRepository?.todayEnergyCo2eKg ?: flowOf(0.0),
        annualBudgetFlow ?: flowOf(null),
    ) { allEntries, now, mqttEnergyKg, configuredAnnualBudget ->
        DashboardUiState.fromEntries(
            entries = allEntries,
            extraEnergyKg = mqttEnergyKg,
            nowMillis = now,
            annualBudgetKg = configuredAnnualBudget,
        )
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
                CarbonViewModel(
                    repository = app.carbonRepository,
                    motionRepository = app.motionRepository,
                    mqttRepository = app.mqttRepository,
                    annualBudgetFlow = app.annualBudgetKg,
                )
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
            yearUsedKg = 420.0,
            annualBudgetKg = 2007.5,
            predictedAnnualKg = 472.5,
        )
    }
}