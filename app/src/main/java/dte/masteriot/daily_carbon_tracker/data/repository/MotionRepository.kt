package dte.masteriot.daily_carbon_tracker.data.repository

import dte.masteriot.daily_carbon_tracker.data.sensor.DetectedMotion
import dte.masteriot.daily_carbon_tracker.data.sensor.MotionDetector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach

/**
 * Conserve la dernière suggestion de déplacement détectée (Marche ou Voiture)
 * afin qu'elle reste affichée dans le Journal même lorsque l'utilisateur s'arrête
 * pour saisir son trajet.
 */
class MotionRepository(motionDetector: MotionDetector) {

    private val _lastSuggestion = MutableStateFlow<DetectedMotion?>(null)

    val suggestedMotion: Flow<DetectedMotion?> = combine(
        motionDetector.observeMotion()
            .distinctUntilChanged()
            .onEach { motion ->
                if (motion != DetectedMotion.STATIC) {
                    _lastSuggestion.value = motion
                }
            },
        _lastSuggestion,
    ) { _, saved -> saved }.distinctUntilChanged()

    fun clearSuggestion() {
        _lastSuggestion.value = null
    }
}