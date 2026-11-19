package dte.masteriot.daily_carbon_tracker.utils

import java.text.DateFormat
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Constants {
    /** Objectif national : 2 t CO2e/an/personne ≈ 5,5 kg CO2e/jour. */
    const val DAILY_CARBON_BUDGET_KG = 5.5

    // --- Journal ---
    /** Distance maximale acceptée pour un trajet (≈ tour de la Terre / 2). */
    const val MAX_DISTANCE_KM = 20_000.0
    const val MAX_MEALS_PER_ENTRY = 10

    // --- MQTT (broker Mosquitto de la VM Ubuntu du cours) ---
    /** Depuis l'émulateur, 10.0.2.2 = machine hôte (redirection de port NAT vers la VM). */
    const val DEFAULT_BROKER_HOST = "10.0.2.2"
    const val DEFAULT_BROKER_PORT = 1883
    const val TOPIC_ENERGY = "home/energy"
    const val TOPIC_ALERTS = "carbon/alerts"
}

/** Formate un nombre de kg avec une décimale (« 3,2 » en français, « 3.2 » en anglais). */
fun formatKg(kg: Double): String =
    String.format(Locale.getDefault(), "%.1f", kg)

/** Formate une valeur en « 3,2 kg CO2e » selon la langue du téléphone. */
fun formatCo2e(kg: Double): String =
    String.format(Locale.getDefault(), "%.1f kg CO2e", kg)

/** Nombre sans zéros inutiles : 12 → « 12 », 12.5 → « 12,5 ». */
fun formatNumber(value: Double): String =
    DecimalFormat("#,##0.#", DecimalFormatSymbols.getInstance(Locale.getDefault())).format(value)

/** Facteur d'émission avec jusqu'à 3 décimales : 0.17689 → « 0,177 ». */
fun formatFactor(value: Double): String =
    DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.getDefault())).format(value)

/** Heure courte selon la langue du téléphone (« 14:32 » ou « 2:32 PM »). */
fun formatTime(millis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(millis))

/** true si l'instant donné tombe aujourd'hui (fuseau du téléphone). */
fun isToday(millis: Long, now: Long = System.currentTimeMillis()): Boolean {
    val day = Calendar.getInstance().apply { timeInMillis = millis }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    return day.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
        day.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
}
