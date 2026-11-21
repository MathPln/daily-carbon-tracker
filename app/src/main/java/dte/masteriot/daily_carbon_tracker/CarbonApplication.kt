package dte.masteriot.daily_carbon_tracker

import android.app.Application
import android.content.Context
import dte.masteriot.daily_carbon_tracker.data.remote.ImpactCo2Client
import dte.masteriot.daily_carbon_tracker.data.repository.CarbonRepository
import dte.masteriot.daily_carbon_tracker.data.repository.MotionRepository
import dte.masteriot.daily_carbon_tracker.data.repository.MqttRepository
import dte.masteriot.daily_carbon_tracker.data.sensor.MotionDetector
import dte.masteriot.daily_carbon_tracker.utils.Constants
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CarbonApplication : Application() {

    private val _fontScale = MutableStateFlow(1.0f)
    val fontScale: StateFlow<Float> = _fontScale.asStateFlow()

    private val _annualBudgetKg = MutableStateFlow(defaultAnnualBudgetKg())
    val annualBudgetKg: StateFlow<Double> = _annualBudgetKg.asStateFlow()

    val carbonRepository: CarbonRepository by lazy { CarbonRepository(ImpactCo2Client.api, this) }
    val motionRepository: MotionRepository by lazy { MotionRepository(MotionDetector(this)) }
    val mqttRepository: MqttRepository by lazy { MqttRepository(this) }

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _fontScale.value = prefs.getFloat(KEY_FONT_SCALE, 1.0f).coerceIn(0.8f, 1.5f)
        val rawAnnual = prefs.getString(KEY_ANNUAL_BUDGET_KG, null)?.toDoubleOrNull()
        if (rawAnnual != null && rawAnnual > 0.0) {
            _annualBudgetKg.value = rawAnnual
        }
    }

    fun updatePreferences(fontScale: Float, annualBudgetKg: Double) {
        val validScale = fontScale.coerceIn(0.8f, 1.5f)
        val validAnnual = if (annualBudgetKg > 0.0) annualBudgetKg else defaultAnnualBudgetKg()
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_FONT_SCALE, validScale)
            .putString(KEY_ANNUAL_BUDGET_KG, validAnnual.toString())
            .apply()
        _fontScale.value = validScale
        _annualBudgetKg.value = validAnnual
    }

    private fun defaultAnnualBudgetKg(): Double {
        val daysInYear = Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_YEAR).coerceAtLeast(365)
        return Constants.DAILY_CARBON_BUDGET_KG * daysInYear
    }

    companion object {
        private const val PREFS_NAME = "app_settings_prefs"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_ANNUAL_BUDGET_KG = "annual_budget_kg"
    }
}