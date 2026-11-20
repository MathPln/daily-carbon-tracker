package dte.masteriot.daily_carbon_tracker.ui.logbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dte.masteriot.daily_carbon_tracker.R
import dte.masteriot.daily_carbon_tracker.data.model.ActivityCategory
import dte.masteriot.daily_carbon_tracker.data.model.ActivityEntry
import dte.masteriot.daily_carbon_tracker.data.model.QuantityUnit
import dte.masteriot.daily_carbon_tracker.data.repository.FactorCatalog
import dte.masteriot.daily_carbon_tracker.data.repository.FactorSource
import dte.masteriot.daily_carbon_tracker.data.sensor.DetectedMotion
import dte.masteriot.daily_carbon_tracker.ui.common.AppCard
import dte.masteriot.daily_carbon_tracker.ui.common.icon
import dte.masteriot.daily_carbon_tracker.ui.common.labelRes
import dte.masteriot.daily_carbon_tracker.ui.dashboard.CarbonViewModel
import dte.masteriot.daily_carbon_tracker.ui.dashboard.LogbookUiState
import dte.masteriot.daily_carbon_tracker.ui.theme.DailyCarbonTheme
import dte.masteriot.daily_carbon_tracker.ui.theme.LocalStatusColors
import dte.masteriot.daily_carbon_tracker.utils.Constants
import dte.masteriot.daily_carbon_tracker.utils.formatCo2e
import dte.masteriot.daily_carbon_tracker.utils.formatFactor
import dte.masteriot.daily_carbon_tracker.utils.formatNumber
import dte.masteriot.daily_carbon_tracker.utils.formatTime

@Composable
fun LogbookScreen(
    modifier: Modifier = Modifier,
    viewModel: CarbonViewModel = viewModel(factory = CarbonViewModel.Factory),
) {
    val state by viewModel.logbookState.collectAsStateWithLifecycle()
    LogbookContent(
        state = state,
        onAddActivity = viewModel::addActivity,
        onDeleteActivity = viewModel::removeActivity,
        onRetryFactors = viewModel::retryFactors,
        onDismissSuggestion = viewModel::dismissMotionSuggestion,
        modifier = modifier,
    )
}

@Composable
fun LogbookContent(
    state: LogbookUiState,
    onAddActivity: (factorId: String, quantity: Double) -> Unit,
    onDeleteActivity: (id: Long) -> Unit,
    onRetryFactors: () -> Unit,
    onDismissSuggestion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var suggestedFactorId by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "source") {
            FactorSourceBanner(source = state.factorSource, onRetry = onRetryFactors)
        }
        state.suggestedMotion?.let { motion ->
            if (motion != DetectedMotion.STATIC && motion.factorId != null) {
                item(key = "motion_suggestion") {
                    MotionSuggestionBanner(
                        motion = motion,
                        onAccept = {
                            suggestedFactorId = motion.factorId
                            onDismissSuggestion()
                        },
                        onDismiss = onDismissSuggestion,
                    )
                }
            }
        }
        item(key = "form") {
            NewActivityCard(
                factorValues = state.factorValues,
                suggestedFactorId = suggestedFactorId,
                onSuggestionConsumed = { suggestedFactorId = null },
                onAddActivity = onAddActivity,
            )
        }
        item(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.logbook_today),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.logbook_today_total, formatCo2e(state.todayTotalKg)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (state.todayEntries.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.logbook_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            items(state.todayEntries, key = { it.id }) { entry ->
                EntryRow(entry = entry, onDelete = onDeleteActivity)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Origine des facteurs (API ADEME ou secours hors ligne)
// ---------------------------------------------------------------------------

@Composable
private fun FactorSourceBanner(source: FactorSource, onRetry: () -> Unit) {
    val statusColors = LocalStatusColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (source) {
            FactorSource.LOADING -> {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.factors_loading), style = MaterialTheme.typography.bodyMedium)
            }
            FactorSource.ADEME_API -> {
                Icon(Icons.Filled.CloudDone, contentDescription = null, tint = statusColors.ok)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.factors_online), style = MaterialTheme.typography.bodyMedium)
            }
            FactorSource.OFFLINE_FALLBACK -> {
                Icon(Icons.Filled.CloudOff, contentDescription = null, tint = statusColors.warning)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.factors_offline),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Bannière d'autosuggestion par accéléromètre (Étape 4)
// ---------------------------------------------------------------------------

@Composable
private fun MotionSuggestionBanner(
    motion: DetectedMotion,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    val motionText = if (motion == DetectedMotion.VEHICLE) "Vehicle" else "Walking"
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (motion == DetectedMotion.VEHICLE) {
                        Icons.Filled.DirectionsCar
                    } else {
                        Icons.AutoMirrored.Filled.DirectionsWalk
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Detected: $motionText",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Button(
                onClick = onAccept,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Add?")
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss transport suggestion",
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Formulaire d'ajout
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewActivityCard(
    factorValues: Map<String, Double>,
    suggestedFactorId: String?,
    onSuggestionConsumed: () -> Unit,
    onAddActivity: (factorId: String, quantity: Double) -> Unit,
) {
    var category by rememberSaveable { mutableStateOf(ActivityCategory.TRANSPORT) }
    var factorId by rememberSaveable { mutableStateOf(FactorCatalog.transportSpecs.first().id) }
    var quantityText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(suggestedFactorId) {
        if (suggestedFactorId != null) {
            category = ActivityCategory.TRANSPORT
            factorId = suggestedFactorId
            quantityText = ""
            onSuggestionConsumed()
        }
    }

    val spec = FactorCatalog.spec(factorId) ?: FactorCatalog.transportSpecs.first()
    val isKm = spec.unit == QuantityUnit.KM
    val quantity = quantityText.replace(',', '.').toDoubleOrNull()
    val isValid = quantity != null && quantity > 0 &&
            quantity <= (if (isKm) Constants.MAX_DISTANCE_KM else Constants.MAX_MEALS_PER_ENTRY.toDouble())
    val estimateKg = if (quantity != null && isValid) FactorCatalog.co2eFor(spec.id, quantity, factorValues) else 0.0

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.logbook_new_activity),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val categories = FactorCatalog.journalCategories
                categories.forEachIndexed { index, item ->
                    SegmentedButton(
                        selected = item == category,
                        onClick = {
                            category = item
                            factorId = FactorCatalog.specsFor(item).first().id
                            quantityText = if (item == ActivityCategory.MEAL) "1" else ""
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = categories.size),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(item.labelRes()))
                    }
                }
            }

            if (category != ActivityCategory.FLIGHT) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FactorCatalog.specsFor(category).forEach { option ->
                        val selected = option.id == factorId
                        FilterChip(
                            selected = selected,
                            onClick = { factorId = option.id },
                            label = { Text(stringResource(option.labelRes)) },
                            leadingIcon = if (selected) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
                Text(
                    text = stringResource(
                        if (isKm) R.string.factor_value_km else R.string.factor_value_meal,
                        formatFactor(factorValues[spec.id] ?: spec.fallbackKgPerUnit),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.factor_flight_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val showError = quantityText.isNotEmpty() && !isValid
            OutlinedTextField(
                value = quantityText,
                onValueChange = { input ->
                    quantityText = if (isKm) {
                        input.filter { it.isDigit() || it == '.' || it == ',' }.take(8)
                    } else {
                        input.filter { it.isDigit() }.take(2)
                    }
                },
                label = { Text(stringResource(if (isKm) R.string.logbook_distance else R.string.logbook_meals)) },
                suffix = if (isKm) {
                    { Text("km") }
                } else {
                    null
                },
                singleLine = true,
                isError = showError,
                supportingText = if (showError) {
                    { Text(stringResource(R.string.logbook_invalid_quantity)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (isKm) KeyboardType.Decimal else KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.logbook_estimate, formatCo2e(estimateKg)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )

            Button(
                onClick = {
                    if (quantity != null && isValid) {
                        onAddActivity(spec.id, quantity)
                        quantityText = if (category == ActivityCategory.MEAL) "1" else ""
                    }
                },
                enabled = isValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.logbook_add))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Liste des activités du jour
// ---------------------------------------------------------------------------

@Composable
private fun EntryRow(entry: ActivityEntry, onDelete: (Long) -> Unit) {
    val label = stringResource(entry.labelRes)
    val quantityLabel = quantityLabel(entry)
    AppCard(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(label) },
            supportingContent = { Text("$quantityLabel · ${formatTime(entry.timestampMillis)}") },
            leadingContent = {
                Icon(
                    imageVector = entry.category.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatCo2e(entry.co2eKg),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(onClick = { onDelete(entry.id) }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.logbook_delete, label),
                        )
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun quantityLabel(entry: ActivityEntry): String = when (entry.unit) {
    QuantityUnit.KM -> stringResource(R.string.quantity_km, formatNumber(entry.quantity))
    QuantityUnit.MEAL -> {
        val count = entry.quantity.toInt()
        LocalContext.current.resources.getQuantityString(R.plurals.quantity_meals, count, count)
    }
}

// ---------------------------------------------------------------------------
// Aperçu Android Studio
// ---------------------------------------------------------------------------

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun LogbookPreview() {
    DailyCarbonTheme {
        LogbookContent(
            state = LogbookUiState(
                todayEntries = listOf(
                    ActivityEntry(
                        id = 1, category = ActivityCategory.TRANSPORT, factorId = "car_thermal",
                        labelRes = R.string.factor_car_thermal, quantity = 12.0, unit = QuantityUnit.KM,
                        co2eKg = 2.1, timestampMillis = 0L,
                    ),
                ),
                todayTotalKg = 2.1,
                factorSource = FactorSource.ADEME_API,
            ),
            onAddActivity = { _, _ -> },
            onDeleteActivity = {},
            onRetryFactors = {},
            onDismissSuggestion = {},
        )
    }
}