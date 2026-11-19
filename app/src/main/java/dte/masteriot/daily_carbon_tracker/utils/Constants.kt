package dte.masteriot.daily_carbon_tracker.utils

import java.util.Locale

object Constants {
    /** Objectif national : 2 t CO2e/an/personne ≈ 5,5 kg CO2e/jour. */
    const val DAILY_CARBON_BUDGET_KG = 5.5

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
