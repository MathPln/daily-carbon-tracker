package dte.masteriot.daily_carbon_tracker

import android.app.Application
import dte.masteriot.daily_carbon_tracker.data.remote.ImpactCo2Client
import dte.masteriot.daily_carbon_tracker.data.repository.CarbonRepository

/**
 * Conteneur d'application : les Repositories vivent ici, une seule instance
 * partagée par tous les écrans (et indépendante des rotations).
 */
class CarbonApplication : Application() {
    val carbonRepository: CarbonRepository by lazy { CarbonRepository(ImpactCo2Client.api) }
}
