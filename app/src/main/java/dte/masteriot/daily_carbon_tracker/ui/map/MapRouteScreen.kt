package dte.masteriot.daily_carbon_tracker.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.location.Geocoder
import android.location.LocationManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dte.masteriot.daily_carbon_tracker.R
import dte.masteriot.daily_carbon_tracker.data.model.TransportMode
import dte.masteriot.daily_carbon_tracker.utils.formatCo2e
import dte.masteriot.daily_carbon_tracker.utils.formatNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale

@Composable
fun MapRouteScreen(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = viewModel(factory = MapViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val isDarkTheme = isSystemInDarkTheme()

    var startInput by remember { mutableStateOf("Calle de Nikola Tesla, Madrid") }
    var endInput by remember { mutableStateOf("Puerta del Sol, Madrid") }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(13.0)
            controller.setCenter(GeoPoint(40.3897, -3.6279))
        }
    }

    val moveToCurrentLocation = {
        val loc = getLastLocation(context)
        if (loc != null) {
            val pt = GeoPoint(loc.latitude, loc.longitude)
            startInput = String.format(Locale.US, "%.4f, %.4f", pt.latitude, pt.longitude)
            mapView.controller.animateTo(pt)
            viewModel.setStartPoint(pt)
        } else {
            Toast.makeText(context, "Location unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) moveToCurrentLocation()
        else Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) mapView.onResume()
            else if (event == Lifecycle.Event.ON_PAUSE) mapView.onPause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    // Invert and slightly darken map tiles when system dark mode is enabled
                    view.overlayManager.tilesOverlay.setColorFilter(
                        if (isDarkTheme) {
                            val matrix = ColorMatrix(
                                floatArrayOf(
                                    -0.85f,  0.0f,   0.0f,  0f, 220f,
                                    0.0f, -0.85f,   0.0f,  0f, 220f,
                                    0.0f,  0.0f,  -0.85f,  0f, 230f,
                                    0.0f,  0.0f,   0.0f,  1f,   0f,
                                )
                            )
                            ColorMatrixColorFilter(matrix)
                        } else {
                            null
                        }
                    )

                    view.overlays.clear()
                    view.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            viewModel.onMapTap(p)
                            return true
                        }
                        override fun longPressHelper(p: GeoPoint) = false
                    }))

                    // Draw dashed route line for every mode (yellow in dark mode)
                    if (state.routePoints.size >= 2) {
                        view.overlays.add(Polyline(view).apply {
                            setPoints(state.routePoints)
                            outlinePaint.strokeWidth = 9f
                            outlinePaint.pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
                            outlinePaint.color = when {
                                isDarkTheme -> AndroidColor.parseColor("#FFD54F")
                                state.selectedMode == TransportMode.PUBLIC_TRANSPORT -> AndroidColor.parseColor("#1565C0")
                                else -> AndroidColor.parseColor("#1B6C45")
                            }
                        })
                    }

                    state.startPoint?.let { pt ->
                        view.overlays.add(Marker(view).apply { position = pt; title = "Start" })
                    }
                    state.endPoint?.let { pt ->
                        view.overlays.add(Marker(view).apply { position = pt; title = "Destination" })
                    }
                    view.invalidate()
                },
            )

            Button(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (granted) moveToCurrentLocation()
                    else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).heightIn(min = 48.dp),
            ) {
                Text("My location")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Plan a trip", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = startInput,
                onValueChange = { startInput = it },
                label = { Text("Start (address or lat, lon)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = endInput,
                onValueChange = { endInput = it },
                label = { Text("Destination (address or lat, lon)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    scope.launch {
                        val startGeo = withContext(Dispatchers.IO) { resolveLocation(context, startInput) }
                        val endGeo = withContext(Dispatchers.IO) { resolveLocation(context, endInput) }
                        if (startGeo == null && endGeo == null) {
                            Toast.makeText(context, "Location not found", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.setRoutePoints(startGeo, endGeo)
                            (startGeo ?: endGeo)?.let { mapView.controller.animateTo(it) }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(if (state.isLoading) "Calculating..." else "Update route")
            }

            state.routeOptions.forEach { option ->
                val (icon, descriptionRes) = when (option.mode) {
                    TransportMode.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk to R.string.factor_walk
                    TransportMode.BIKE -> Icons.AutoMirrored.Filled.DirectionsBike to R.string.factor_bike
                    TransportMode.PUBLIC_TRANSPORT -> Icons.Filled.DirectionsBus to R.string.factor_bus
                    TransportMode.CAR -> Icons.Filled.DirectionsCar to R.string.factor_car_thermal
                }
                val description = stringResource(descriptionRes)
                FilterChip(
                    selected = option.mode == state.selectedMode,
                    onClick = { viewModel.selectMode(option.mode) },
                    leadingIcon = {
                        Icon(imageVector = icon, contentDescription = description)
                    },
                    label = {
                        Text("~${formatNumber(option.distanceKm)} km · ${formatDuration(option.durationMinutes)} · ${formatCo2e(option.co2eKg)}")
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                )
            }

            Button(
                onClick = {
                    viewModel.logSelectedRoute()
                    Toast.makeText(context, "Trip added to logbook", Toast.LENGTH_SHORT).show()
                },
                enabled = state.routeOptions.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("Add trip to logbook")
            }
        }
    }
}

private fun formatDuration(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes < 24 * 60 -> {
        val h = minutes / 60
        val m = minutes % 60
        if (m == 0) "$h h" else "$h h $m min"
    }
    else -> {
        val d = minutes / (24 * 60)
        val h = (minutes % (24 * 60)) / 60
        if (h == 0) "$d d" else "$d d $h h"
    }
}

private fun resolveLocation(context: Context, input: String): GeoPoint? {
    val parts = input.trim().split(",")
    if (parts.size == 2) {
        val lat = parts[0].trim().toDoubleOrNull()
        val lon = parts[1].trim().toDoubleOrNull()
        if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
            return GeoPoint(lat, lon)
        }
    }
    return try {
        if (!Geocoder.isPresent()) return null
        @Suppress("DEPRECATION")
        val match = Geocoder(context, Locale.getDefault()).getFromLocationName(input.trim(), 1)?.firstOrNull()
        if (match != null) GeoPoint(match.latitude, match.longitude) else null
    } catch (_: Exception) {
        null
    }
}

private fun getLastLocation(context: Context) = try {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        ?: manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
} catch (_: SecurityException) {
    null
}