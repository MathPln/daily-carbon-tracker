package dte.masteriot.daily_carbon_tracker.ui.energy

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dte.masteriot.daily_carbon_tracker.R
import dte.masteriot.daily_carbon_tracker.ui.common.PlaceholderContent

/** Écran Énergie — provisoire, développé à l'étape 5 (MQTT). */
@Composable
fun EnergyScreen(modifier: Modifier = Modifier) {
    PlaceholderContent(
        icon = Icons.Filled.Bolt,
        title = stringResource(R.string.tab_energy),
        message = stringResource(R.string.placeholder_energy),
        modifier = modifier,
    )
}
