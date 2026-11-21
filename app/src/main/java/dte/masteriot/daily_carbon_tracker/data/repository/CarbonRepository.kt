package dte.masteriot.daily_carbon_tracker.data.repository

import android.content.Context
import android.util.Log
import dte.masteriot.daily_carbon_tracker.data.model.ActivityCategory
import dte.masteriot.daily_carbon_tracker.data.model.ActivityEntry
import dte.masteriot.daily_carbon_tracker.data.model.QuantityUnit
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
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/** Origine des facteurs d'émission actuellement utilisés. */
enum class FactorSource { LOADING, ADEME_API, OFFLINE_FALLBACK }

/**
 * Source unique de vérité du Journal carbone :
 * - met en cache en mémoire les facteurs ADEME récupérés via l'API Impact CO2,
 *   avec un jeu de secours intégré si le réseau est coupé ;
 * - persiste les activités saisies en local pour les conserver après fermeture de l'app.
 */
class CarbonRepository(
    private val api: ImpactCo2Api,
    context: Context,
) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _factorValues = MutableStateFlow(FactorCatalog.fallbackValues)
    /** id de facteur → kg CO2e par unité. */
    val factorValues: StateFlow<Map<String, Double>> = _factorValues.asStateFlow()

    private val _factorSource = MutableStateFlow(FactorSource.LOADING)
    val factorSource: StateFlow<FactorSource> = _factorSource.asStateFlow()

    private val initialEntries = loadPersistedEntries()
    private val _entries = MutableStateFlow(initialEntries)
    /** Toutes les activités, la plus récente en premier. */
    val entries: StateFlow<List<ActivityEntry>> = _entries.asStateFlow()

    private val refreshMutex = Mutex()
    private val nextId = AtomicLong((initialEntries.maxOfOrNull { it.id } ?: 0L) + 1L)

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
            Log.w(TAG, "Impact CO2 API unavailable, using built-in ADEME factors", e)
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

    /** Ajoute une activité au Journal, la sauvegarde et renvoie l'entrée créée. */
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
        _entries.update { current ->
            val updated = listOf(entry) + current
            persistEntries(updated)
            updated
        }
        return entry
    }

    fun removeActivity(id: Long) {
        _entries.update { current ->
            val updated = current.filterNot { it.id == id }
            persistEntries(updated)
            updated
        }
    }

    private fun persistEntries(list: List<ActivityEntry>) {
        val array = JSONArray()
        list.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("category", entry.category.name)
                    put("factorId", entry.factorId)
                    put("quantity", entry.quantity)
                    put("unit", entry.unit.name)
                    put("co2eKg", entry.co2eKg)
                    put("timestampMillis", entry.timestampMillis)
                }
            )
        }
        prefs.edit().putString(KEY_ENTRIES_JSON, array.toString()).apply()
    }

    private fun loadPersistedEntries(): List<ActivityEntry> {
        val raw = prefs.getString(KEY_ENTRIES_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val factorId = obj.optString("factorId", "")
                    val spec = FactorCatalog.spec(factorId)
                    val category = runCatching {
                        ActivityCategory.valueOf(obj.optString("category", ""))
                    }.getOrElse { spec?.category ?: ActivityCategory.TRANSPORT }
                    val unit = runCatching {
                        QuantityUnit.valueOf(obj.optString("unit", ""))
                    }.getOrElse { spec?.unit ?: QuantityUnit.KM }
                    val labelRes = spec?.labelRes ?: FactorCatalog.transportSpecs.first().labelRes
                    add(
                        ActivityEntry(
                            id = obj.optLong("id", i.toLong() + 1L),
                            category = category,
                            factorId = factorId,
                            labelRes = labelRes,
                            quantity = obj.optDouble("quantity", 0.0),
                            unit = unit,
                            co2eKg = obj.optDouble("co2eKg", 0.0),
                            timestampMillis = obj.optLong("timestampMillis", 0L),
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore persisted logbook entries", e)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "CarbonRepository"
        const val PREFS_NAME = "carbon_logbook_prefs"
        const val KEY_ENTRIES_JSON = "entries_json"
    }
}