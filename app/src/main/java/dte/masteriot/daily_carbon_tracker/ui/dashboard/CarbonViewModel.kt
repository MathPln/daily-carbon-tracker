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
import dte.masteriot.daily_carbon_tracker.utils.Constants
import dte.masteriot.daily_carbon_tracker.utils.isToday
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Statut du budget, toujours affiché avec icône + libellé + couleur. */
enum class BudgetStatus { WITHIN, WARNING, OVER }

/** Part d'une catégorie (Transport, Repas, Énergie) dans le total du jour. */
data class CategoryShare(val category: ActivityCategory, val co2eKg: Double)

/** État immuable affiché par le Dashboard. */
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

        /** Agrège les activités du jour en 3 catégories ; les vols comptent dans Transport. */
        fun fromEntries(entries: List<ActivityEntry>, ecoModeActive: Boolean = false): DashboardUiState {
            val transport = entries
                .filter { it.category == ActivityCategory.TRANSPORT || it.category == ActivityCategory.FLIGHT }
                .sumOf { it.co2eKg }
            val meals = entries.filter { it.category == ActivityCategory.MEAL }.sumOf { it.co2eKg }
            val energy = entries.filter { it.category == ActivityCategory.ENERGY }.sumOf { it.co2eKg }
            return DashboardUiState(
                totalKg = transport + meals + energy,
                breakdown = listOf(
                    CategoryShare(ActivityCategory.TRANSPORT, transport),
                    CategoryShare(ActivityCategory.MEAL, meals),
                    CategoryShare(ActivityCategory.ENERGY, energy),
                ),
                ecoModeActive = ecoModeActive,
            )
        }
    }
}

/** État immuable affiché par l'écran Journal. */
data class LogbookUiState(
    val todayEntries: List<ActivityEntry> = emptyList(),
    val todayTotalKg: Double = 0.0,
    /** id de facteur → kg CO2e par unité (ADEME, ou secours si hors ligne). */
    val factorValues: Map<String, Double> = FactorCatalog.fallbackValues,
    val factorSource: FactorSource = FactorSource.LOADING,
)

/**
 * ViewModel du Dashboard et du Journal. Il ne fait qu'exposer en StateFlow
 * les données du CarbonRepository (source unique de vérité) et lui transmettre
 * les actions de l'utilisateur.
 */
class CarbonViewModel(private val repository: CarbonRepository) : ViewModel() {

    private val todayEntries = repository.entries.map { entries -> entries.filter { isToday(it.timestampMillis) } }

    val dashboardState: StateFlow<DashboardUiState> = todayEntries
        .map { DashboardUiState.fromEntries(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState.fromEntries(emptyList()))

    val logbookState: StateFlow<LogbookUiState> = combine(
        todayEntries,
        repository.factorValues,
        repository.factorSource,
    ) { entries, values, source ->
        LogbookUiState(
            todayEntries = entries,
            todayTotalKg = entries.sumOf { it.co2eKg },
            factorValues = values,
            factorSource = source,
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

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CarbonApplication
                CarbonViewModel(app.carbonRepository)
            }
        }

        /** Données d'exemple pour les aperçus Android Studio. */
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
