package hr.android.zetflowandroid.model

import kotlinx.serialization.Serializable

@Serializable
data class TripDTO (
    val tripId: String,
    val routeId: String? = null,
    val routeName: String? = null,
    val stopTimes: List<StopTimeDTO>? = null
)