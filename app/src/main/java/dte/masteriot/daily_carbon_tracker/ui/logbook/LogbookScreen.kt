package dte.masteriot.daily_carbon_tracker.ui.logbook

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dte.masteriot.daily_carbon_tracker.R
import dte.masteriot.daily_carbon_tracker.ui.common.PlaceholderContent

/** Écran Journal — provisoire, développé à l'étape 3 (ADEME) et 4 (accéléromètre). */
@Composable
fun LogbookScreen(modifier: Modifier = Modifier) {
    PlaceholderContent(
        icon = Icons.Filled.Book,
        title = stringResource(R.string.tab_logbook),
        message = stringResource(R.string.placeholder_logbook),
        modifier = modifier,
    )
}
