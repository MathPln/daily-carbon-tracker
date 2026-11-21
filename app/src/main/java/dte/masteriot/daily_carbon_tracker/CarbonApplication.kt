package dte.masteriot.daily_carbon_tracker

import android.app.Application
import dte.masteriot.daily_carbon_tracker.data.remote.ImpactCo2Client
import dte.masteriot.daily_carbon_tracker.data.repository.CarbonRepository
import dte.masteriot.daily_carbon_tracker.data.repository.MotionRepository
import dte.masteriot.daily_carbon_tracker.data.repository.MqttRepository
import dte.masteriot.daily_carbon_tracker.data.sensor.MotionDetector

class CarbonApplication : Application() {
    val carbonRepository: CarbonRepository by lazy { CarbonRepository(ImpactCo2Client.api, this) }
    val motionRepository: MotionRepository by lazy { MotionRepository(MotionDetector(this)) }
    val mqttRepository: MqttRepository by lazy { MqttRepository(this) }
}