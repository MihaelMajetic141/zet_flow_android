package hr.android.zetflowandroid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hr.android.zetflowandroid.model.TripDTO
import hr.android.zetflowandroid.model.VehicleDTO
import hr.android.zetflowandroid.network.HttpClientProvider
import hr.android.zetflowandroid.network.TripRepository
import hr.android.zetflowandroid.network.TripRepositoryImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.hildan.krossbow.stomp.StompClient
import org.hildan.krossbow.stomp.StompSession
import org.hildan.krossbow.stomp.frame.StompFrame
import org.hildan.krossbow.stomp.subscribe
import org.hildan.krossbow.websocket.ktor.KtorWebSocketClient

class MapViewModel(
    private val tripRepository: TripRepository = TripRepositoryImpl(),
    private val httpClientProvider: HttpClientProvider = HttpClientProvider,
    private val webSocketUrl: String = "ws://10.0.2.2:8080/ws"
) : ViewModel() {

    private val _vehicles = MutableStateFlow<List<VehicleDTO>>(emptyList())
    val vehicles: StateFlow<List<VehicleDTO>> = _vehicles.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _tripDetails = MutableStateFlow<TripDTO?>(null)
    val tripDetails: StateFlow<TripDTO?> = _tripDetails.asStateFlow()

    private var stompSession: StompSession? = null

    init {
        connectAndSubscribe()
    }

    private fun connectAndSubscribe() {
        viewModelScope.launch {
            try {
                val stompClient = StompClient(
                    webSocketClient = KtorWebSocketClient(httpClient = httpClientProvider.client)
                )
                val session = stompClient.connect(
                    url = webSocketUrl
                )
                stompSession = session

                val subscription: Flow<StompFrame.Message> = session
                    .subscribe(destination = "/topic/gtfs-updates")

                subscription
                    .mapNotNull { frame ->
                        val body = frame.body ?: return@mapNotNull null
                        val jsonString = String(body.bytes.toByteArray(), Charsets.UTF_8)

                        try {
                            // use the shared Json instance
                            Json.decodeFromString(
                                deserializer = ListSerializer(
                                    elementSerializer = VehicleDTO.serializer()
                                ), jsonString
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                            _error.value = "Deserialization failed: ${e.message}"
                            null
                        }
                    }
                    .catch { e ->
                        e.printStackTrace()
                        _error.value = "Subscribe error: ${e.message}"
                    }
                    .collect { vehicleList ->
                        _vehicles.value = vehicleList
                    }

            } catch (e: Exception) {
                _error.value = "Connection failed: ${e.message}"
            }
        }
    }

    fun fetchTripById(tripId: String) {
        viewModelScope.launch {
            try {
                val trip = tripRepository.getTripById(tripId)
                _tripDetails.value = trip
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = "Failed fetching trip: ${e.message}"
            }
        }
    }

    fun clearTripDetails() { _tripDetails.value = null }

    override fun onCleared() {
        super.onCleared()
        stompSession?.let { session ->
            viewModelScope.launch {
                try {
                    session.disconnect()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

}