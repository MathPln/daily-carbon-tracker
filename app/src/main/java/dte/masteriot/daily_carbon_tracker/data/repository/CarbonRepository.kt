package dte.masteriot.daily_carbon_tracker.data.repository

import dte.masteriot.daily_carbon_tracker.data.model.ActivityEntry
import dte.masteriot.daily_carbon_tracker.data.remote.ImpactCo2Api
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/** Origine des facteurs d'émission actuellement utilisés. */
enum class FactorSource { LOADING, ADEME_API, OFFLINE_FALLBACK }

/**
 * Source unique de vérité du Journal carbone :
 * - met en cache en mémoire les facteurs ADEME récupérés via l'API Impact CO2,
 *   avec un jeu de secours intégré si le réseau est coupé ;
 * - conserve les activités saisies et calcule leurs émissions.
 *
 * Une seule instance pour toute l'application (voir CarbonApplication) :
 * l'écran Journal et le Dashboard lisent donc les mêmes données.
 */
class CarbonRepository(private val api: ImpactCo2Api) {

    private val _factorValues = MutableStateFlow(FactorCatalog.fallbackValues)
    /** id de facteur → kg CO2e par unité. */
    val factorValues: StateFlow<Map<String, Double>> = _factorValues.asStateFlow()

    private val _factorSource = MutableStateFlow(FactorSource.LOADING)
    val factorSource: StateFlow<FactorSource> = _factorSource.asStateFlow()

    private val _entries = MutableStateFlow<List<ActivityEntry>>(emptyList())
    /** Toutes les activités, la plus récente en premier. */
    val entries: StateFlow<List<ActivityEntry>> = _entries.asStateFlow()

    private val refreshMutex = Mutex()
    private val nextId = AtomicLong(1)

    /** Charge les facteurs une seule fois (appelé par chaque ViewModel au démarrage). */
    suspend fun ensureFactorsLoaded() {
        if (_factorSource.value != FactorSource.ADEME_API) refreshFactors()
    }

    /** Interroge l'API Impact CO2 ; en cas d'échec, garde les facteurs de secours. */
    suspend fun refreshFactors() = refreshMutex.withLock {
        if (_factorSource.value == FactorSource.ADEME_API) return@withLock
        _factorSource.value = FactorSource.LOADING
        try {
            val values = fetchFactorsFromApi()
            _factorValues.value = values
            _factorSource.value = FactorSource.ADEME_API
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Pas de réseau, API indisponible ou réponse inattendue : jeu de secours.
            _factorSource.value = FactorSource.OFFLINE_FALLBACK
        }
    }

    private suspend fun fetchFactorsFromApi(): Map<String, Double> = coroutineScope {
        val transportIds = FactorCatalog.transportSpecs.mapNotNull { it.apiTransportId }.joinToString(",")

        // Transports au sol : avec km = 1, la valeur renvoyée est directement le facteur par km.
        val transportCall = async { api.transport(km = 1, transports = transportIds) }
        // Avion : une requête par tranche de distance, puis valeur / distance = facteur par km.
        val flightCalls = FactorCatalog.flightBands.map { band ->
            async { band to api.transport(km = band.probeKm, transports = FactorCatalog.FLIGHT_API_ID.toString()) }
        }
        val mealCall = async { api.thematique("repas") }

        val values = FactorCatalog.fallbackValues.toMutableMap()

        val transports = transportCall.await().data.orEmpty()
        FactorCatalog.transportSpecs.forEach { spec ->
            transports.firstOrNull { it.id == spec.apiTransportId }?.let { values[spec.id] = it.value }
        }

        flightCalls.awaitAll().forEach { (band, response) ->
            response.data?.firstOrNull { it.id == FactorCatalog.FLIGHT_API_ID }?.let {
                values[band.id] = it.value / band.probeKm
            }
        }
        values[FactorCatalog.FLIGHT_ID] = values.getValue(FactorCatalog.flightBands.first().id)

        val meals = mealCall.await().data.orEmpty()
        FactorCatalog.mealSpecs.forEach { spec ->
            meals.firstOrNull { it.slug == spec.apiMealSlug }?.let { values[spec.id] = it.ecv }
        }

        // Réponse vide = l'API ne renvoie plus le format attendu : on la considère indisponible.
        check(transports.isNotEmpty() && meals.isNotEmpty()) { "Unexpected Impact CO2 response" }
        values
    }

    /** Ajoute une activité au Journal et renvoie l'entrée créée avec ses émissions calculées. */
    fun addActivity(factorId: String, quantity: Double): ActivityEntry? {
        val spec = FactorCatalog.spec(factorId) ?: return null
        val entry = ActivityEntry(
            id = nextId.getAndIncrement(),
            category = spec.category,
            factorId = spec.id,
            labelRes = spec.labelRes,
            quantity = quantity,
            unit = spec.unit,
            co2eKg = FactorCatalog.co2eFor(spec.id, quantity, _factorValues.value),
            timestampMillis = System.currentTimeMillis(),
        )
        _entries.update { listOf(entry) + it }
        return entry
    }

    fun removeActivity(id: Long) {
        _entries.update { entries -> entries.filterNot { it.id == id } }
    }
}
