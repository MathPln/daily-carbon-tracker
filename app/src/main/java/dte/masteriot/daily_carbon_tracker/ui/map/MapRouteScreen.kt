package dte.masteriot.daily_carbon_tracker.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.location.Geocoder
import android.location.LocationManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalConfiguration
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
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var locationTrigger by remember { mutableStateOf(0) }
    var lastHandledTrigger by remember { mutableStateOf(0) }
    var userMarkerPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var startInput by remember { mutableStateOf("Calle de Nikola Tesla, Madrid") }
    var endInput by remember { mutableStateOf("Puerta del Sol, Madrid") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            locationTrigger++
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    val requestLocation: () -> Unit = {
        val fineOk = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseOk = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fineOk || coarseOk) {
            locationTrigger++
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(13.0)
            controller.setCenter(GeoPoint(40.3897, -3.6279))
        }
    }

    // Keep osmdroid lifecycle in sync with the screen
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    val mapBox: @Composable (Modifier) -> Unit = { boxModifier ->
        Box(modifier = boxModifier.clipToBounds()) {
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    if (locationTrigger > lastHandledTrigger) {
                        lastHandledTrigger = locationTrigger
                        val loc = getLastLocation(context)
                        if (loc != null) {
                            val pt = GeoPoint(loc.latitude, loc.longitude)
                            userMarkerPoint = pt
                            startInput = "${formatCoord(pt.latitude)}, ${formatCoord(pt.longitude)}"
                            view.controller.animateTo(pt)
                            viewModel.setStartPoint(pt)
                        } else {
                            Toast.makeText(context, "Location unavailable", Toast.LENGTH_SHORT).show()
                        }
                    }

                    view.overlays.clear()
                    view.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            viewModel.onMapTap(p)
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint): Boolean = false
                    }))

                    if (state.startPoint != null && state.endPoint != null) {
                        view.overlays.add(Polyline(view).apply {
                            setPoints(listOf(state.startPoint, state.endPoint))
                            outlinePaint.color = AndroidColor.parseColor("#1B6C45")
                            outlinePaint.strokeWidth = 10f
                        })
                    }
                    userMarkerPoint?.let { pt ->
                        view.overlays.add(Marker(view).apply {
                            position = pt
                            title = "My location"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        })
                    }
                    state.startPoint?.let { pt ->
                        view.overlays.add(Marker(view).apply {
                            position = pt
                            title = "Start"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        })
                    }
                    state.endPoint?.let { pt ->
                        view.overlays.add(Marker(view).apply {
                            position = pt
                            title = "Destination"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        })
                    }
                    view.invalidate()
                },
            )
            // Filled button keeps high contrast over light map tiles
            Button(
                onClick = requestLocation,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .heightIn(min = 48.dp),
            ) {
                Text("My location")
            }
        }
    }

    val panel: @Composable (Modifier) -> Unit = { panelModifier ->
        Column(
            modifier = panelModifier
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Start & destination (address, lat,lon or map)", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = startInput,
                onValueChange = { startInput = it },
                label = { Text("Start address or coordinates") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = endInput,
                onValueChange = { endInput = it },
                label = { Text("Destination address or coordinates") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    scope.launch {
                        val startGeo = withContext(Dispatchers.IO) { resolveAddressOrCoords(context, startInput) }
                        val endGeo = withContext(Dispatchers.IO) { resolveAddressOrCoords(context, endInput) }
                        if (startGeo == null && endGeo == null) {
                            Toast.makeText(context, "Address or coordinates not found", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.setRoutePoints(startGeo, endGeo)
                            (startGeo ?: endGeo)?.let { mapView.controller.animateTo(it) }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text("Update route")
            }
            state.routeOptions.forEach { option ->
                val label = when (option.mode) {
                    TransportMode.WALK -> stringResource(R.string.factor_walk)
                    TransportMode.BIKE -> stringResource(R.string.factor_bike)
                    TransportMode.PUBLIC_TRANSPORT -> "Bus"
                    TransportMode.CAR -> stringResource(R.string.factor_car_thermal)
                }
                FilterChip(
                    selected = option.mode == state.selectedMode,
                    onClick = { viewModel.selectMode(option.mode) },
                    label = {
                        Text("$label · ${formatNumber(option.distanceKm)} km · ${option.durationMinutes} min · ${formatCo2e(option.co2eKg)}")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                )
            }
            Button(
                onClick = {
                    viewModel.logSelectedRoute()
                    Toast.makeText(context, "Trip added to logbook", Toast.LENGTH_SHORT).show()
                },
                enabled = state.routeOptions.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text("Add trip to logbook")
            }
        }
    }

    if (isLandscape) {
        Row(modifier = modifier.fillMaxSize()) {
            mapBox(Modifier.weight(1.1f).fillMaxHeight())
            panel(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            mapBox(Modifier.fillMaxWidth().weight(1f))
            panel(Modifier.fillMaxWidth().weight(1.1f))
        }
    }
}

private fun resolveAddressOrCoords(context: Context, input: String): GeoPoint? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val parts = trimmed.split(",")
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
        val matches = Geocoder(context, Locale.getDefault()).getFromLocationName(trimmed, 1)
        val match = matches?.firstOrNull()
        if (match != null) GeoPoint(match.latitude, match.longitude) else null
    } catch (_: Exception) {
        null
    }
}

private fun formatCoord(value: Double): String = String.format(Locale.US, "%.4f", value)

private fun getLastLocation(context: Context) = try {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        ?: manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
} catch (_: SecurityException) {
    null
}