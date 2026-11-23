package dte.masteriot.daily_carbon_tracker.ui.dashboard

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dte.masteriot.daily_carbon_tracker.R
import dte.masteriot.daily_carbon_tracker.ui.common.AppCard
import dte.masteriot.daily_carbon_tracker.ui.common.icon
import dte.masteriot.daily_carbon_tracker.ui.common.labelRes
import dte.masteriot.daily_carbon_tracker.ui.theme.DailyCarbonTheme
import dte.masteriot.daily_carbon_tracker.ui.theme.LocalStatusColors
import dte.masteriot.daily_carbon_tracker.utils.formatCo2e
import dte.masteriot.daily_carbon_tracker.utils.formatKg
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    onAddActivity: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CarbonViewModel = viewModel(factory = CarbonViewModel.Factory),
) {
    val state by viewModel.dashboardState.collectAsStateWithLifecycle()
    DashboardContent(state = state, onAddActivity = onAddActivity, modifier = modifier)
}

/**
 * Dashboard adaptatif :
 * - portrait : empilement vertical resserré ;
 * - paysage : jauge à gauche, répartition et actions à droite.
 * Le défilement n'est qu'un filet de sécurité pour les très grandes polices système
 * (à taille normale, tout tient à l'écran sans défiler).
 */
@Composable
fun DashboardContent(
    state: DashboardUiState,
    onAddActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BudgetGaugeCard(
                state = state,
                fillHeight = true,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnnualBudgetCard(state)
                BreakdownCard(state)
                EcoModeCard(state.ecoModeActive)
                AddActivityButton(onAddActivity)
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BudgetGaugeCard(state = state, fillHeight = false, modifier = Modifier.fillMaxWidth())
            AnnualBudgetCard(state)
            BreakdownCard(state)
            EcoModeCard(state.ecoModeActive)
            AddActivityButton(onAddActivity)
        }
    }
}

// ---------------------------------------------------------------------------
// Jauge du budget quotidien & annuel
// ---------------------------------------------------------------------------

private data class StatusVisual(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val color: Color,
)

@Composable
private fun BudgetStatus.visual(): StatusVisual {
    val colors = LocalStatusColors.current
    return when (this) {
        BudgetStatus.WITHIN -> StatusVisual(R.string.status_within, Icons.Filled.CheckCircle, colors.ok)
        BudgetStatus.WARNING -> StatusVisual(R.string.status_warning, Icons.Filled.Warning, colors.warning)
        BudgetStatus.OVER -> StatusVisual(R.string.status_over, Icons.Filled.Error, colors.over)
    }
}

@Composable
private fun AnnualBudgetCard(state: DashboardUiState) {
    val yearVisual = state.yearStatus.visual()
    val usedPct = (state.yearProgress * 100).roundToInt()
    val predictedPct = (state.predictedYearProgress * 100).roundToInt()

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Annual budget",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Used: ${formatKg(state.yearUsedKg)} / ${formatKg(state.annualBudgetKg)} kg ($usedPct%)",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            // Horizontal gauge showing current annual usage against annual budget
            LinearProgressIndicator(
                progress = { state.yearProgress.coerceIn(0f, 1f) },
                color = yearVisual.color,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
            )
            Text(
                text = "Predicted end of year: ${formatKg(state.predictedAnnualKg)} kg ($predictedPct% at current pace)",
                style = MaterialTheme.typography.bodyMedium,
                color = yearVisual.color,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun BudgetGaugeCard(
    state: DashboardUiState,
    fillHeight: Boolean,
    modifier: Modifier = Modifier,
) {
    val visual = state.status.visual()
    val percent = (state.progress * 100).roundToInt()
    val description = stringResource(
        R.string.cd_budget_gauge,
        formatKg(state.totalKg),
        formatKg(state.budgetKg),
        percent,
        stringResource(visual.labelRes),
    )

    AppCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.dashboard_today_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            BudgetGauge(
                progress = state.progress,
                color = visual.color,
                totalText = formatKg(state.totalKg),
                budgetText = stringResource(R.string.gauge_of_budget, formatKg(state.budgetKg)),
                modifier = if (fillHeight) Modifier.weight(1f) else Modifier,
            )
            StatusChip(visual)
        }
    }
}

@Composable
private fun BudgetGauge(
    progress: Float,
    color: Color,
    totalText: String,
    budgetText: String,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.outlineVariant

    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val available = if (constraints.hasBoundedHeight) minOf(maxWidth, maxHeight) else maxWidth
        val gaugeSize = available.coerceIn(96.dp, 220.dp)

        Box(modifier = Modifier.size(gaugeSize), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = size.minDimension * 0.1f
                val inset = strokeWidth / 2f
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                // Arc de 270° ouvert vers le bas
                drawArc(
                    color = trackColor,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = stroke,
                )
                drawArc(
                    color = color,
                    startAngle = 135f,
                    sweepAngle = 270f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = stroke,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = totalText,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = budgetText,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun StatusChip(visual: StatusVisual) {
    Surface(
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(2.dp, visual.color),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = visual.icon, contentDescription = null, tint = visual.color)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(visual.labelRes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Répartition, mode éco, bouton d'ajout
// ---------------------------------------------------------------------------

@Composable
private fun BreakdownCard(state: DashboardUiState) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.dashboard_breakdown_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            state.breakdown.forEach { share ->
                BreakdownRow(share = share, totalKg = state.totalKg)
            }
        }
    }
}

@Composable
private fun BreakdownRow(share: CategoryShare, totalKg: Double) {
    val fraction = if (totalKg > 0) (share.co2eKg / totalKg).toFloat() else 0f
    val percent = (fraction * 100).roundToInt()

    // Une ligne = un seul élément lu par TalkBack : « Transport, 1,8 kg CO2e · 51 % »
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = share.category.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(share.category.labelRes()),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.breakdown_value, formatCo2e(share.co2eKg), percent),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clearAndSetSemantics {},
        )
    }
}

@Composable
private fun EcoModeCard(active: Boolean) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Eco,
                contentDescription = null,
                tint = if (active) LocalStatusColors.current.ok else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(if (active) R.string.eco_mode_on else R.string.eco_mode_off),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun AddActivityButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Icon(imageVector = Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.action_add_activity))
    }
}

// ---------------------------------------------------------------------------
// Aperçus Android Studio
// ---------------------------------------------------------------------------

@Preview(name = "Portrait", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun DashboardPortraitPreview() {
    DailyCarbonTheme {
        DashboardContent(state = CarbonViewModel.sampleState(), onAddActivity = {})
    }
}

@Preview(name = "Paysage haut contraste", showBackground = true, widthDp = 720, heightDp = 360)
@Composable
private fun DashboardLandscapeHcPreview() {
    DailyCarbonTheme(highContrast = true) {
        DashboardContent(state = CarbonViewModel.sampleState(), onAddActivity = {})
    }
}