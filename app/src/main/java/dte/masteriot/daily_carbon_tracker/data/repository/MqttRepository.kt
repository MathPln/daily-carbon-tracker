package dte.masteriot.daily_carbon_tracker.data.repository

import android.content.Context
import android.util.Log
import dte.masteriot.daily_carbon_tracker.data.model.EnergyReading
import dte.masteriot.daily_carbon_tracker.ui.dashboard.BudgetStatus
import dte.masteriot.daily_carbon_tracker.utils.Constants
import dte.masteriot.daily_carbon_tracker.utils.formatTime
import dte.masteriot.daily_carbon_tracker.utils.isToday
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.meshtastic.mqtt.ConnectionState
import org.meshtastic.mqtt.MqttClient
import org.meshtastic.mqtt.MqttEndpoint
import org.meshtastic.mqtt.QoS
import org.meshtastic.mqtt.transport.tcp.TcpTransportFactory
import java.util.Locale

enum class MqttStatus { DISCONNECTED, CONNECTING, CONNECTED }

data class BrokerConfig(
    val host: String = Constants.DEFAULT_BROKER_HOST,
    val port: Int = Constants.DEFAULT_BROKER_PORT,
)

class MqttRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _brokerConfig = MutableStateFlow(
        BrokerConfig(
            host = prefs.getString(KEY_HOST, Constants.DEFAULT_BROKER_HOST) ?: Constants.DEFAULT_BROKER_HOST,
            port = prefs.getInt(KEY_PORT, Constants.DEFAULT_BROKER_PORT),
        )
    )
    val brokerConfig: StateFlow<BrokerConfig> = _brokerConfig.asStateFlow()

    private val _status = MutableStateFlow(MqttStatus.DISCONNECTED)
    val status: StateFlow<MqttStatus> = _status.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _readings = MutableStateFlow<List<EnergyReading>>(emptyList())
    val readings: StateFlow<List<EnergyReading>> = _readings.asStateFlow()

    val todayEnergyCo2eKg: StateFlow<Double> = _readings
        .map { list -> list.filter { isToday(it.receivedAtMillis) }.sumOf { it.toCo2eKg() } }
        .stateIn(scope, SharingStarted.Eagerly, 0.0)

    private val _sentAlerts = MutableStateFlow<List<String>>(emptyList())
    val sentAlerts: StateFlow<List<String>> = _sentAlerts.asStateFlow()

    private var client: MqttClient? = null
    private var collectorJobs: List<Job> = emptyList()
    private var lastAlertedStatus: BudgetStatus = BudgetStatus.WITHIN

    fun updateBrokerConfig(host: String, port: Int, reconnectNow: Boolean = true) {
        val cleanHost = host.trim().ifEmpty { Constants.DEFAULT_BROKER_HOST }
        val validPort = port.takeIf { it in 1..65535 } ?: Constants.DEFAULT_BROKER_PORT
        prefs.edit().putString(KEY_HOST, cleanHost).putInt(KEY_PORT, validPort).apply()
        _brokerConfig.value = BrokerConfig(cleanHost, validPort)
        if (reconnectNow) {
            connect()
        }
    }

    fun connect() {
        val config = _brokerConfig.value
        scope.launch {
            disconnectInternal()
            _status.value = MqttStatus.CONNECTING
            _lastError.value = null

            try {
                val mqttClient = MqttClient(
                    clientId = "daily-carbon-tracker-${System.currentTimeMillis()}",
                    scope = scope,
                ) {
                    transportFactory = TcpTransportFactory()
                }
                client = mqttClient

                val stateJob = launch {
                    mqttClient.connectionState.collect { state ->
                        when (state) {
                            is ConnectionState.Connected -> {
                                _status.value = MqttStatus.CONNECTED
                                _lastError.value = null
                            }
                            is ConnectionState.Disconnected -> {
                                _status.value = MqttStatus.DISCONNECTED
                                state.reason?.message?.let { _lastError.value = it }
                            }
                            else -> _status.value = MqttStatus.CONNECTING
                        }
                    }
                }

                val msgJob = launch {
                    mqttClient.messages.collect { msg ->
                        if (msg.topic == Constants.TOPIC_ENERGY) {
                            parseEnergyReading(msg.payloadAsString())?.let { reading ->
                                _readings.update { listOf(reading) + it }
                            }
                        }
                    }
                }
                collectorJobs = listOf(stateJob, msgJob)

                val endpoint = MqttEndpoint.parse("tcp://${config.host}:${config.port}")
                mqttClient.connect(endpoint)
                mqttClient.subscribe(Constants.TOPIC_ENERGY, QoS.AT_LEAST_ONCE)
                _status.value = MqttStatus.CONNECTED
            } catch (e: Exception) {
                Log.w(TAG, "MQTT connection failed to ${config.host}:${config.port}", e)
                _status.value = MqttStatus.DISCONNECTED
                _lastError.value = e.message ?: "Connection error"
            }
        }
    }

    fun disconnect() {
        scope.launch {
            disconnectInternal()
            _status.value = MqttStatus.DISCONNECTED
        }
    }

    private suspend fun disconnectInternal() {
        collectorJobs.forEach { it.cancel() }
        collectorJobs = emptyList()
        runCatching { client?.disconnect() }
        runCatching { client?.close() }
        client = null
    }

    fun onBudgetEvaluated(status: BudgetStatus, totalKg: Double, budgetKg: Double) {
        if (status == BudgetStatus.WITHIN) {
            lastAlertedStatus = BudgetStatus.WITHIN
            return
        }
        if (status != lastAlertedStatus) {
            lastAlertedStatus = status
            publishBudgetAlert(status, totalKg, budgetKg)
        }
    }

    fun publishBudgetAlert(status: BudgetStatus, totalKg: Double, budgetKg: Double) {
        scope.launch {
            val activeClient = client ?: return@launch
            if (_status.value != MqttStatus.CONNECTED) return@launch

            val payload = JSONObject().apply {
                put("status", status.name)
                put("eco_mode", true)
                put("total_co2e_kg", String.format(Locale.US, "%.2f", totalKg).toDouble())
                put("budget_kg", budgetKg)
                put(
                    "message",
                    if (status == BudgetStatus.OVER) {
                        "Daily carbon budget exceeded (${String.format(Locale.US, "%.2f", totalKg)} / $budgetKg kg CO2e)"
                    } else {
                        "Eco mode activated: 80% of daily carbon budget reached"
                    }
                )
            }.toString()

            try {
                activeClient.publish(
                    topic = Constants.TOPIC_ALERTS,
                    payload = payload,
                    qos = QoS.AT_LEAST_ONCE,
                )
                val logLine = "[${formatTime(System.currentTimeMillis())}] $payload"
                _sentAlerts.update { listOf(logLine) + it.take(19) }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to publish alert on ${Constants.TOPIC_ALERTS}", e)
                _lastError.value = e.message
            }
        }
    }

    private fun parseEnergyReading(payload: String): EnergyReading? {
        return try {
            val json = JSONObject(payload.trim())
            val kwh = json.optDouble("electricity_kwh", Double.NaN)
            val gas = json.optDouble("gas_m3", Double.NaN)
            if (kwh.isNaN() && gas.isNaN()) return null
            EnergyReading(
                electricityKwh = if (kwh.isNaN()) 0.0 else kwh.coerceAtLeast(0.0),
                gasM3 = if (gas.isNaN()) 0.0 else gas.coerceAtLeast(0.0),
                receivedAtMillis = System.currentTimeMillis(),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Invalid JSON payload on ${Constants.TOPIC_ENERGY}: $payload", e)
            null
        }
    }

    companion object {
        private const val TAG = "MqttRepository"
        private const val PREFS_NAME = "mqtt_broker_prefs"
        private const val KEY_HOST = "broker_host"
        private const val KEY_PORT = "broker_port"

        // Facteurs ADEME : 0,052 kg CO2e/kWh (électricité) et 2,04 kg CO2e/m³ (gaz naturel)
        const val ELECTRICITY_KG_CO2E_PER_KWH = 0.052
        const val GAS_KG_CO2E_PER_M3 = 2.04

        fun EnergyReading.toCo2eKg(): Double =
            electricityKwh * ELECTRICITY_KG_CO2E_PER_KWH + gasM3 * GAS_KG_CO2E_PER_M3
    }
}