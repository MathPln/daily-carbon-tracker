package dte.masteriot.daily_carbon_tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dte.masteriot.daily_carbon_tracker.ui.navigation.CarbonApp
import dte.masteriot.daily_carbon_tracker.ui.theme.DailyCarbonTheme

/** Point d'entrée Single-Activity : héberge le thème et la navigation Compose. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // rememberSaveable : le choix survit aux rotations d'écran
            var highContrast by rememberSaveable { mutableStateOf(false) }
            DailyCarbonTheme(highContrast = highContrast) {
                CarbonApp(
                    highContrast = highContrast,
                    onHighContrastChange = { highContrast = it },
                )
            }
        }
    }
}
