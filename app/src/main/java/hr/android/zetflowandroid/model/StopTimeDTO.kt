package hr.android.zetflowandroid.model

import kotlinx.serialization.Serializable

@Serializable
data class StopTimeDTO(
    val stopName: String? = null,
    val arrivalTime: String? = null,
    val departureTime: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)