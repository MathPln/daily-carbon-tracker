package dte.masteriot.daily_carbon_tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dte.masteriot.daily_carbon_tracker.ui.navigation.CarbonApp
import dte.masteriot.daily_carbon_tracker.ui.theme.DailyCarbonTheme
import org.osmdroid.config.Configuration as OsmConfiguration

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Required by osmdroid to download map tiles
        OsmConfiguration.getInstance().userAgentValue = packageName
        enableEdgeToEdge()
        setContent {
            val app = application as CarbonApplication
            val fontScale by app.fontScale.collectAsStateWithLifecycle()
            val baseDensity = LocalDensity.current
            var highContrast by rememberSaveable { mutableStateOf(false) }

            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = baseDensity.density,
                    fontScale = fontScale,
                )
            ) {
                DailyCarbonTheme(highContrast = highContrast) {
                    CarbonApp(
                        highContrast = highContrast,
                        onHighContrastChange = { highContrast = it },
                    )
                }
            }
        }
    }
}