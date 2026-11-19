package dte.masteriot.daily_carbon_tracker.ui.dashboard

import androidx.lifecycle.ViewModel
import dte.masteriot.daily_carbon_tracker.data.model.ActivityCategory
import dte.masteriot.daily_carbon_tracker.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Statut du budget, toujours affiché avec icône + libellé + couleur. */
enum class BudgetStatus { WITHIN, WARNING, OVER }

/** Part d'une catégorie (Transport, Repas, Énergie) dans le total du jour. */
data class CategoryShare(val category: ActivityCategory, val co2eKg: Double)

/** État immuable affiché par le Dashboard (source unique de vérité exposée en StateFlow). */
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
    }
}

/**
 * ViewModel du Dashboard. Étape 2 : données de test.
 * Étape 3 : il sera alimenté par le CarbonRepository (activités du Journal + facteurs ADEME).
 */
class CarbonViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(sampleState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    companion object {
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
