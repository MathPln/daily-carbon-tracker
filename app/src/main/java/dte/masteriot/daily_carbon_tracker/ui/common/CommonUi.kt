package dte.masteriot.daily_carbon_tracker.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dte.masteriot.daily_carbon_tracker.R
import dte.masteriot.daily_carbon_tracker.data.model.ActivityCategory
import dte.masteriot.daily_carbon_tracker.ui.theme.LocalHighContrast

/** Carte de base : en haut contraste, elle reçoit une bordure nette au lieu d'une simple ombre. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = if (LocalHighContrast.current) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface)
    } else {
        null
    }
    Card(modifier = modifier, border = border, content = content)
}

@StringRes
fun ActivityCategory.labelRes(): Int = when (this) {
    ActivityCategory.TRANSPORT -> R.string.category_transport
    ActivityCategory.FLIGHT -> R.string.category_flight
    ActivityCategory.MEAL -> R.string.category_meal
    ActivityCategory.ENERGY -> R.string.category_energy
}

fun ActivityCategory.icon(): ImageVector = when (this) {
    ActivityCategory.TRANSPORT -> Icons.Filled.DirectionsCar
    ActivityCategory.FLIGHT -> Icons.Filled.Flight
    ActivityCategory.MEAL -> Icons.Filled.Restaurant
    ActivityCategory.ENERGY -> Icons.Filled.Bolt
}

/** Contenu provisoire des écrans pas encore développés. */
@Composable
fun PlaceholderContent(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}
