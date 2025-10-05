package hr.android.zetflowandroid.model

import kotlinx.serialization.Serializable


@Serializable
data class VehicleDTO(
    val id: String? = null,
    val routeId: String? = null,
    val routeType: String? = null,
    val routeLongName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val tripId: String? = null
)

