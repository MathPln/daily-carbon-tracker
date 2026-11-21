package dte.masteriot.daily_carbon_tracker.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dte.masteriot.daily_carbon_tracker.CarbonApplication
import dte.masteriot.daily_carbon_tracker.R
import dte.masteriot.daily_carbon_tracker.ui.dashboard.DashboardScreen
import dte.masteriot.daily_carbon_tracker.ui.energy.EnergyScreen
import dte.masteriot.daily_carbon_tracker.ui.logbook.LogbookScreen
import dte.masteriot.daily_carbon_tracker.ui.map.MapRouteScreen
import dte.masteriot.daily_carbon_tracker.utils.formatKg
import java.util.Calendar
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarbonApp(
    highContrast: Boolean,
    onHighContrastChange: (Boolean) -> Unit,
) {
    val app = LocalContext.current.applicationContext as CarbonApplication
    val mqtt = app.mqttRepository
    val brokerConfig by mqtt.brokerConfig.collectAsStateWithLifecycle()
    val fontScale by app.fontScale.collectAsStateWithLifecycle()
    val annualBudgetKg by app.annualBudgetKg.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = TopLevelDestination.entries
        .firstOrNull { it.route == backStackEntry?.destination?.route }
        ?: TopLevelDestination.DASHBOARD
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }

    val navigateTo: (TopLevelDestination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(currentDestination.labelRes),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                actions = {
                    IconToggleButton(checked = highContrast, onCheckedChange = onHighContrastChange) {
                        Icon(
                            imageVector = Icons.Filled.Contrast,
                            contentDescription = stringResource(R.string.action_high_contrast),
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.action_broker_settings),
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = destination == currentDestination
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigateTo(destination) },
                        icon = {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.DASHBOARD.route,
            modifier = Modifier.padding(innerPadding).imePadding(),
        ) {
            composable(TopLevelDestination.DASHBOARD.route) {
                DashboardScreen(onAddActivity = { navigateTo(TopLevelDestination.LOGBOOK) })
            }
            composable(TopLevelDestination.LOGBOOK.route) { LogbookScreen() }
            composable(TopLevelDestination.ENERGY.route) { EnergyScreen() }
            composable(TopLevelDestination.MAP.route) { MapRouteScreen() }
        }
    }

    if (showSettingsDialog) {
        var hostInput by rememberSaveable(brokerConfig.host) { mutableStateOf(brokerConfig.host) }
        var portInput by rememberSaveable(brokerConfig.port) { mutableStateOf(brokerConfig.port.toString()) }
        var selectedScale by rememberSaveable(fontScale) { mutableStateOf(fontScale) }
        var annualBudgetInput by rememberSaveable(annualBudgetKg) {
            mutableStateOf(formatKg(annualBudgetKg).replace(',', '.'))
        }

        val scaleOptions = listOf(
            "Small" to 0.9f,
            "Normal" to 1.0f,
            "Large" to 1.2f,
        )
        val daysInYear = Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_YEAR).coerceAtLeast(365)
        val parsedAnnual = annualBudgetInput.replace(',', '.').toDoubleOrNull()
        val previewDaily = if (parsedAnnual != null && parsedAnnual > 0.0) parsedAnnual / daysInYear else null

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Settings") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Font size", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        scaleOptions.forEach { (label, scaleValue) ->
                            FilterChip(
                                selected = abs(selectedScale - scaleValue) < 0.01f,
                                onClick = { selectedScale = scaleValue },
                                label = { Text(label) },
                            )
                        }
                    }

                    HorizontalDivider()

                    Text("Annual carbon target", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = annualBudgetInput,
                        onValueChange = { annualBudgetInput = it },
                        label = { Text("Annual target (kg CO2e)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (previewDaily != null) {
                        Text(
                            text = "Daily target: ${formatKg(previewDaily)} kg CO2e/day",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    HorizontalDivider()

                    Text(stringResource(R.string.broker_dialog_title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.broker_dialog_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = hostInput,
                        onValueChange = { hostInput = it },
                        label = { Text(stringResource(R.string.broker_host_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = portInput,
                        onValueChange = { portInput = it },
                        label = { Text(stringResource(R.string.broker_port_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetAnnual = parsedAnnual?.takeIf { it > 0.0 } ?: annualBudgetKg
                        app.updatePreferences(selectedScale, targetAnnual)
                        mqtt.updateBrokerConfig(hostInput, portInput.toIntOrNull() ?: 1883, reconnectNow = true)
                        showSettingsDialog = false
                    }
                ) {
                    Text(stringResource(R.string.mqtt_connect))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}