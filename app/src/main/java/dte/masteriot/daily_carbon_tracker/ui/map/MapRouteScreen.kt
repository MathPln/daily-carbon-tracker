package dte.masteriot.daily_carbon_tracker.ui.map

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dte.masteriot.daily_carbon_tracker.R
import dte.masteriot.daily_carbon_tracker.ui.common.PlaceholderContent

/** Écran Carte — provisoire, développé à l'étape 6 (osmdroid). */
@Composable
fun MapRouteScreen(modifier: Modifier = Modifier) {
    PlaceholderContent(
        icon = Icons.Filled.Map,
        title = stringResource(R.string.tab_map),
        message = stringResource(R.string.placeholder_map),
        modifier = modifier,
    )
}
