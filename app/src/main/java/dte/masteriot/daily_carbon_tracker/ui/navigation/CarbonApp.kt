package dte.masteriot.daily_carbon_tracker.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dte.masteriot.daily_carbon_tracker.R
import dte.masteriot.daily_carbon_tracker.ui.dashboard.DashboardScreen
import dte.masteriot.daily_carbon_tracker.ui.energy.EnergyScreen
import dte.masteriot.daily_carbon_tracker.ui.logbook.LogbookScreen
import dte.masteriot.daily_carbon_tracker.ui.map.MapRouteScreen
import dte.masteriot.daily_carbon_tracker.utils.Constants

/**
 * Racine de l'interface : un Scaffold unique avec une barre supérieure légère
 * (titre + haut contraste + réglages du broker) et une barre de navigation à 4 onglets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarbonApp(
    highContrast: Boolean,
    onHighContrastChange: (Boolean) -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = TopLevelDestination.entries
        .firstOrNull { it.route == backStackEntry?.destination?.route }
        ?: TopLevelDestination.DASHBOARD
    var showBrokerDialog by rememberSaveable { mutableStateOf(false) }

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
                    // Bouton bascule : TalkBack annonce « activé / désactivé »
                    IconToggleButton(checked = highContrast, onCheckedChange = onHighContrastChange) {
                        Icon(
                            imageVector = Icons.Filled.Contrast,
                            contentDescription = stringResource(R.string.action_high_contrast),
                        )
                    }
                    IconButton(onClick = { showBrokerDialog = true }) {
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
                                contentDescription = null, // le libellé texte est lu à la place
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
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.DASHBOARD.route) {
                DashboardScreen(onAddActivity = { navigateTo(TopLevelDestination.LOGBOOK) })
            }
            composable(TopLevelDestination.LOGBOOK.route) { LogbookScreen() }
            composable(TopLevelDestination.ENERGY.route) { EnergyScreen() }
            composable(TopLevelDestination.MAP.route) { MapRouteScreen() }
        }
    }

    if (showBrokerDialog) {
        AlertDialog(
            onDismissRequest = { showBrokerDialog = false },
            title = { Text(stringResource(R.string.broker_dialog_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.broker_dialog_message,
                        Constants.DEFAULT_BROKER_HOST,
                        Constants.DEFAULT_BROKER_PORT,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { showBrokerDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}
