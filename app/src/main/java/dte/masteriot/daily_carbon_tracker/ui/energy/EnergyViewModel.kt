package dte.masteriot.daily_carbon_tracker.ui.energy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dte.masteriot.daily_carbon_tracker.CarbonApplication
import dte.masteriot.daily_carbon_tracker.data.model.EnergyReading
import dte.masteriot.daily_carbon_tracker.data.repository.BrokerConfig
import dte.masteriot.daily_carbon_tracker.data.repository.MqttRepository
import dte.masteriot.daily_carbon_tracker.data.repository.MqttStatus
import dte.masteriot.daily_carbon_tracker.ui.dashboard.BudgetStatus
import kotlinx.coroutines.flow.StateFlow

class EnergyViewModel(
    private val mqttRepository: MqttRepository,
) : ViewModel() {

    val status: StateFlow<MqttStatus> = mqttRepository.status
    val brokerConfig: StateFlow<BrokerConfig> = mqttRepository.brokerConfig
    val lastError: StateFlow<String?> = mqttRepository.lastError
    val readings: StateFlow<List<EnergyReading>> = mqttRepository.readings
    val sentAlerts: StateFlow<List<String>> = mqttRepository.sentAlerts

    fun connect() {
        mqttRepository.connect()
    }

    fun disconnect() {
        mqttRepository.disconnect()
    }

    fun publishManualAlert(currentStatus: BudgetStatus, totalKg: Double, budgetKg: Double) {
        val alertStatus = if (currentStatus == BudgetStatus.WITHIN) {
            BudgetStatus.WARNING
        } else {
            currentStatus
        }
        mqttRepository.publishBudgetAlert(
            status = alertStatus,
            totalKg = totalKg,
            budgetKg = budgetKg,
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CarbonApplication
                EnergyViewModel(app.mqttRepository)
            }
        }
    }
}