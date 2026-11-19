package dte.masteriot.daily_carbon_tracker.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.ui.graphics.vector.ImageVector
import dte.masteriot.daily_carbon_tracker.R

/** Les 4 onglets de la barre de navigation inférieure. */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    DASHBOARD("dashboard", R.string.tab_dashboard, Icons.Filled.Home, Icons.Outlined.Home),
    LOGBOOK("logbook", R.string.tab_logbook, Icons.Filled.Book, Icons.Outlined.Book),
    ENERGY("energy", R.string.tab_energy, Icons.Filled.Bolt, Icons.Outlined.Bolt),
    MAP("map", R.string.tab_map, Icons.Filled.Map, Icons.Outlined.Map),
}
