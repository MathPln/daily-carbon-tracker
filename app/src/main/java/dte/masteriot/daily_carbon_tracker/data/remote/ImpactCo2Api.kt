package dte.masteriot.daily_carbon_tracker.data.remote

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * API publique Impact CO2 de l'ADEME (données Base Carbone et Agribalyse).
 * Documentation : https://impactco2.fr/doc/api
 *
 * Aucune clé n'est obligatoire : sans clé, l'API répond normalement et ajoute
 * seulement un champ « warning ». Le jeu de facteurs de secours du
 * CarbonRepository prend le relais si l'API est injoignable.
 */
interface ImpactCo2Api {

    /**
     * Émissions en kg CO2e pour [km] kilomètres, par mode de transport.
     * [transports] : identifiants séparés par des virgules (1 = avion, 4 = voiture thermique…).
     */
    @GET("transport")
    suspend fun transport(
        @Query("km") km: Int,
        @Query("transports") transports: String,
    ): ImpactCo2Response<TransportEmissionDto>

    /** Émissions par élément d'une thématique (« repas » → repas avec du bœuf, végétarien…). */
    @GET("thematiques/ecv/{id}")
    suspend fun thematique(@Path("id") id: String): ImpactCo2Response<EcvItemDto>
}

data class ImpactCo2Response<T>(
    val data: List<T>?,
    val warning: String?,
)

/** [value] : émission en kg CO2e pour la distance demandée. */
data class TransportEmissionDto(
    val id: Int,
    val name: String?,
    val value: Double,
)

/** [ecv] : émission totale en kg CO2e de l'élément (ici : par repas). */
data class EcvItemDto(
    val name: String?,
    val slug: String,
    val ecv: Double,
)

object ImpactCo2Client {
    private const val BASE_URL = "https://impactco2.fr/api/v1/"

    val api: ImpactCo2Api by lazy {
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ImpactCo2Api::class.java)
    }
}
