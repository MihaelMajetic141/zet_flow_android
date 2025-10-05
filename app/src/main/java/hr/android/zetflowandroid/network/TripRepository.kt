package hr.android.zetflowandroid.network

import hr.android.zetflowandroid.model.TripDTO
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface TripRepository {
    suspend fun getTripById(tripId: String): TripDTO?
}

class TripRepositoryImpl(
    private val baseUrl: String = "http://10.0.2.2:8080/api/trip",
    private val clientProvider: HttpClientProvider = HttpClientProvider
) : TripRepository {

    override suspend fun getTripById(tripId: String): TripDTO? = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/getTripById/$tripId"
            clientProvider.client.get(url) {
                contentType(ContentType.Application.Json)
            }.body<TripDTO>()
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }

}