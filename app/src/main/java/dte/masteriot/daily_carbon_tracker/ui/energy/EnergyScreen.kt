package dte.masteriot.daily_carbon_tracker.ui.energy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dte.masteriot.daily_carbon_tracker.CarbonApplication
import dte.masteriot.daily_carbon_tracker.R
import dte.masteriot.daily_carbon_tracker.data.model.EnergyReading
import dte.masteriot.daily_carbon_tracker.data.repository.MqttRepository.Companion.toCo2eKg
import dte.masteriot.daily_carbon_tracker.data.repository.MqttStatus
import dte.masteriot.daily_carbon_tracker.ui.common.AppCard
import dte.masteriot.daily_carbon_tracker.ui.dashboard.BudgetStatus
import dte.masteriot.daily_carbon_tracker.ui.dashboard.CarbonViewModel
import dte.masteriot.daily_carbon_tracker.utils.formatCo2e
import dte.masteriot.daily_carbon_tracker.utils.formatNumber
import dte.masteriot.daily_carbon_tracker.utils.formatTime

@Composable
fun EnergyScreen(
    modifier: Modifier = Modifier,
    carbonViewModel: CarbonViewModel = viewModel(factory = CarbonViewModel.Factory),
) {
    val app = LocalContext.current.applicationContext as CarbonApplication
    val mqtt = app.mqttRepository

    val status by mqtt.status.collectAsStateWithLifecycle()
    val brokerConfig by mqtt.brokerConfig.collectAsStateWithLifecycle()
    val lastError by mqtt.lastError.collectAsStateWithLifecycle()
    val readings by mqtt.readings.collectAsStateWithLifecycle()
    val sentAlerts by mqtt.sentAlerts.collectAsStateWithLifecycle()
    val dashboardState by carbonViewModel.dashboardState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Carte de connexion au broker MQTT
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (statusIcon, statusText, statusColor) = when (status) {
                            MqttStatus.CONNECTED -> Triple(
                                Icons.Filled.CloudDone,
                                stringResource(R.string.mqtt_status_connected),
                                MaterialTheme.colorScheme.primary,
                            )
                            MqttStatus.CONNECTING -> Triple(
                                Icons.Filled.Sync,
                                stringResource(R.string.mqtt_status_connecting),
                                MaterialTheme.colorScheme.tertiary,
                            )
                            MqttStatus.DISCONNECTED -> Triple(
                                Icons.Filled.CloudOff,
                                stringResource(R.string.mqtt_status_disconnected),
                                MaterialTheme.colorScheme.error,
                            )
                        }
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.titleMedium,
                                color = statusColor,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(
                                text = "tcp://${brokerConfig.host}:${brokerConfig.port}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    lastError?.let { errorMsg ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = errorMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (status == MqttStatus.CONNECTED) {
                            OutlinedButton(onClick = { mqtt.disconnect() }) {
                                Text(stringResource(R.string.mqtt_disconnect))
                            }
                        } else {
                            Button(
                                onClick = { mqtt.connect() },
                                enabled = status != MqttStatus.CONNECTING,
                            ) {
                                Text(stringResource(R.string.mqtt_connect))
                            }
                        }
                    }
                }
            }
        }

        // 2. Carte Mode Éco & Alertes MQTT (carbon/alerts)
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Eco,
                            contentDescription = null,
                            tint = if (dashboardState.ecoModeActive) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(
                                if (dashboardState.ecoModeActive) R.string.eco_mode_on else R.string.eco_mode_off
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(
                            R.string.energy_budget_summary,
                            formatCo2e(dashboardState.totalKg),
                            formatCo2e(dashboardState.budgetKg),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = {
                            val alertStatus = if (dashboardState.status == BudgetStatus.WITHIN) {
                                BudgetStatus.WARNING
                            } else {
                                dashboardState.status
                            }
                            mqtt.publishBudgetAlert(
                                status = alertStatus,
                                totalKg = dashboardState.totalKg,
                                budgetKg = dashboardState.budgetKg,
                            )
                        },
                        enabled = status == MqttStatus.CONNECTED,
                    ) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.mqtt_publish_alert))
                    }
                }
            }
        }

        // 3. Liste des relevés du compteur intelligent (home/energy)
        item {
            Text(
                text = stringResource(R.string.energy_readings_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
        }

        if (readings.isEmpty()) {
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.energy_readings_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            items(readings) { reading ->
                EnergyReadingCard(reading)
            }
        }

        // 4. Historique des alertes publiées
        if (sentAlerts.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.mqtt_alerts_sent_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
            items(sentAlerts) { alert ->
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = alert,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EnergyReadingCard(reading: EnergyReading) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.energy_reading_values,
                        formatNumber(reading.electricityKwh),
                        formatNumber(reading.gasM3),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "${formatTime(reading.receivedAtMillis)} · ${formatCo2e(reading.toCo2eKg())}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}