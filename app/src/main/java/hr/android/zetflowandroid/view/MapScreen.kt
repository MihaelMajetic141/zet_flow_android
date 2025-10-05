package hr.android.zetflowandroid.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.text.TextPaint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import hr.android.zetflowandroid.R
import hr.android.zetflowandroid.model.StopTimeDTO
import hr.android.zetflowandroid.model.TripDTO
import hr.android.zetflowandroid.model.VehicleDTO
import hr.android.zetflowandroid.viewmodel.MapViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = viewModel()
) {

    val vehicles by viewModel.vehicles.collectAsState()
    val tripDetails by viewModel.tripDetails.collectAsState()
    val error by viewModel.error.collectAsState()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(45.8, 15.985),
            12f
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedVehicle by remember { mutableStateOf<VehicleDTO?>(null) }
    var activeMarkerId = selectedVehicle?.id
    var userInteracted by remember { mutableStateOf(false) }

    val filteredVehicles by remember(vehicles, searchQuery) {
        derivedStateOf {
            val term = searchQuery.trim().lowercase()
            if (term.isBlank()) {
                vehicles
            } else {
                vehicles.filter {
                    it.routeId?.lowercase()?.contains(term) == true ||
                            it.routeLongName?.lowercase()?.contains(term) == true
                }
            }
        }
    }
    val activePosition by remember(activeMarkerId, filteredVehicles) {
        derivedStateOf {
            activeMarkerId?.let { id ->
                filteredVehicles.find { it.id == id }?.let { vehicle ->
                    vehicle.latitude?.let { lat ->
                        vehicle.longitude?.let { lon ->
                            LatLng(lat, lon)
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(activePosition) {
        if (activePosition != null && !userInteracted) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(activePosition!!, 16f),
                2000
            )
        }
    }

    val searchResults by remember(filteredVehicles, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                emptyList()
            } else {
                userInteracted = true
                val uniqueRoutes = LinkedHashMap<String, SearchResult>()
                filteredVehicles.forEach {
                    val routeId = it.routeId ?: ""
                    if (routeId.isNotBlank() && !uniqueRoutes.containsKey(routeId)) {
                        uniqueRoutes[routeId] = SearchResult(
                            routeId = routeId,
                            routeName = it.routeLongName ?: "",
                            routeType = it.routeType ?: ""
                        )
                    }
                }
                uniqueRoutes.values.toList()
            }
        }
    }

    val scaffoldState = rememberBottomSheetScaffoldState()
    LaunchedEffect(searchQuery, tripDetails) {
        if (searchQuery.isNotBlank() || tripDetails != null) {
            coroutineScope.launch {
                scaffoldState.bottomSheetState.expand()
            }
        } else {
            coroutineScope.launch {
                scaffoldState.bottomSheetState.partialExpand()
            }
        }
    }

    BottomSheetScaffold(
        modifier = modifier.padding(bottom = 10.dp),
        scaffoldState = scaffoldState,
        sheetPeekHeight = 120.dp,
        sheetContent = {
            SheetContent(
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onClearSearch = { searchQuery = "" },
                searchResults = searchResults,
                onSelectRoute = { result ->
                    searchQuery = result.routeName
                },
                tripDetails = tripDetails,
                onCloseTripDetails = {
                    selectedVehicle = null
                    activeMarkerId = null
                    viewModel.clearTripDetails()
                    userInteracted = true
                    coroutineScope.launch {
                        scaffoldState.bottomSheetState.partialExpand()
                    }
                },
                onFlyToStop = { lat, lon ->
                    if (lat != null && lon != null) {
                        coroutineScope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(lat, lon),
                                    16f
                                ),
                                600
                            )
                        }
                    }
                }
            )
        },
        content = {
            Box(modifier = modifier.fillMaxSize()) {
                GoogleMap(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { _ ->
                                userInteracted = true
                            }
                            detectDragGestures { _, _ ->
                                userInteracted = true
                            }
                        },
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(isMyLocationEnabled = false)
                ) {
                    filteredVehicles.forEach { vehicle ->
                        val lat = vehicle.latitude
                        val lon = vehicle.longitude
                        val id = vehicle.id ?: return@forEach
                        if (lat == null || lon == null) return@forEach

                        val hasSelection = activeMarkerId != null
                        val isSelected = activeMarkerId == id

                        val bitmapDescriptor = remember(
                            vehicle.routeId,
                            isSelected,
                            hasSelection
                        ) {
                            BitmapDescriptorFactory.fromBitmap(
                                createVehicleMarkerBitmap(
                                    context,
                                    vehicle.routeId ?: "",
                                    vehicle.routeType, isSelected, hasSelection
                                )
                            )
                        }

                        Marker(
                            state = MarkerState(position = LatLng(lat, lon)),
                            title = vehicle.routeId ?: "",
                            snippet = vehicle.routeLongName ?: "",
                            icon = bitmapDescriptor,
                            onClick = {
                                selectedVehicle = vehicle
                                activeMarkerId = id
                                userInteracted = false
                                coroutineScope.launch {
                                    cameraPositionState.animate(
                                        update = CameraUpdateFactory.newLatLngZoom(
                                            LatLng(lat, lon), 16f
                                        ),
                                        durationMs = 800
                                    )
                                }
                                vehicle.tripId?.let { id -> viewModel.fetchTripById(id) }
                                true // return true consumes click event (prevents bubbling)
                            }
                        )
                    }

                    tripDetails?.stopTimes?.forEachIndexed { index, stop ->
                        val lat = stop.latitude ?: return@forEachIndexed
                        val lon = stop.longitude ?: return@forEachIndexed
                        Marker(
                            state = MarkerState(position = LatLng(lat, lon)),
                            title = stop.stopName ?: "N/A",
                            snippet = "Arrival: ${stop.arrivalTime ?: "N/A"}",
                            icon = BitmapDescriptorFactory.fromBitmap(
                                createStopMarkerBitmap(context)
                            )
                        )
                    }

                }

                error?.let {
                    if (it.isNotBlank()) {
                        Text(
                            text = it,
                            color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(12.dp)
                                .background(
                                    androidx.compose.ui.graphics.Color(0xAA000000),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun SheetContent(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    searchResults: List<SearchResult>,
    onSelectRoute: (SearchResult) -> Unit,
    tripDetails: TripDTO?,
    onCloseTripDetails: () -> Unit,
    onFlyToStop: (Double?, Double?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(330.dp)
    ) {
        SearchInput(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            onClear = onClearSearch
        )

        if (tripDetails != null) {
            TripDetailsSection(
                tripDetails = tripDetails,
                onClose = onCloseTripDetails,
                onStopClick = onFlyToStop
            )
        } else if (searchResults.isNotEmpty()) {
            SearchResultsSection(
                results = searchResults,
                onResultClick = onSelectRoute
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchInput(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    SearchBarDefaults.InputField(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = { },
        expanded = false,
        onExpandedChange = { },
        placeholder = { Text("Search by Route ID or Name") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = "Clear")
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun TripDetailsSection(
    tripDetails: TripDTO,
    onClose: () -> Unit,
    onStopClick: (Double?, Double?) -> Unit
) {
    Column(modifier = Modifier.padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = (tripDetails.routeId ?: "N/A") +" "+ (tripDetails.routeName ?: "N/A"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            tripDetails.stopTimes?.let { stops ->
                items(stops) { stop ->
                    StopItem(
                        stop = stop,
                        index = stops.indexOf(stop),
                        onClick = { onStopClick(stop.latitude, stop.longitude) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StopItem(
    stop: StopTimeDTO,
    index: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${index + 1}. " + stop.stopName,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (stop.arrivalTime == stop.departureTime)
                (stop.arrivalTime ?: "N/A")
            else
                "Arr: ${stop.arrivalTime ?: "N/A"} • Dep: ${stop.departureTime ?: "N/A"}",
            fontSize = 12.sp
        )
    }
}

@Composable
private fun SearchResultsSection(
    results: List<SearchResult>,
    onResultClick: (SearchResult) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(results) { result ->
            RouteItem(
                result = result,
                onClick = { onResultClick(result) }
            )
        }
    }
}

@Composable
private fun RouteItem(
    result: SearchResult,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    color = androidx.compose.ui.graphics.Color(
                        routeColor(result.routeType).toColorInt()
                    ),
                    shape = RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            val iconRes = if (result.routeType == "0")
                R.drawable.outline_tram_24 else R.drawable.outline_directions_bus_24

            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(androidx.compose.ui.graphics.Color.White),
                contentScale = ContentScale.Fit
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = result.routeId, fontWeight = FontWeight.Bold)
            Text(text = result.routeName, fontSize = 12.sp)
        }
    }
}

private fun createVehicleMarkerBitmap(
    context: Context,
    routeId: String,
    routeType: String?,
    isSelected: Boolean,
    hasSelection: Boolean
): Bitmap {
    val size = 128
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.TRANSPARENT)

    val bgColor = try {
        routeColor(routeType).toColorInt()
    } catch (e: Exception) {
        "#6B7280".toColorInt()
    }

    val opacity = if (hasSelection) (if (isSelected) 255 else (255 * 0.3).toInt()) else 255

    // Draw circular background
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = bgColor
        alpha = opacity
    }
    val radius = size * 0.36f
    val cx = size / 2f
    val cy = size / 2f
    canvas.drawCircle(cx, cy, radius, paint)

    val iconRes = if (routeType == "0")
        R.drawable.outline_tram_24 else R.drawable.outline_directions_bus_24

    val drawable = ContextCompat.getDrawable(context, iconRes)
    drawable?.let {
        it.alpha = opacity // Apply opacity to icon as well, matching Angular div opacity
        val iconSize = size * 0.4f // Adjust size to fit above text
        val iconY = cy - radius * 0.3f // Position above center
        it.setBounds(
            (cx - iconSize / 2).toInt(),
            (iconY - iconSize / 2).toInt(),
            (cx + iconSize / 2).toInt(),
            (iconY + iconSize / 2).toInt()
        )
        it.draw(canvas)
    }

    // Draw routeId text below the icon
    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = size * 0.2f // Smaller to fit below
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        alpha = opacity // Match background opacity
    }

    val drawText = routeId.takeIf { it.isNotBlank() } ?: ""
    var ts = textPaint.textSize
    val maxTextWidth = radius * 1.8f
    while (textPaint.measureText(drawText) > maxTextWidth && ts > 8f) {
        ts -= 1f
        textPaint.textSize = ts
    }

    val textY = cy + radius * 0.75f // Position below center
    canvas.drawText(drawText, cx, textY, textPaint)

    return bitmap
}

private fun createStopMarkerBitmap(context: Context): Bitmap {
    val size = 80
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)

    val iconRes = R.drawable.outline_location_on_24
    val drawable = ContextCompat.getDrawable(context, iconRes)
    drawable?.let {
        it.setBounds(0, 0, size, size)
        it.draw(canvas)
    }

    return bitmap
}

private fun routeColor(routeType: String?): String {
    return when (routeType) {
        "0" -> "#2c8bff"   // tram
        "3" -> "#294cc2"   // bus
        else -> "#6B7280"
    }
}

data class SearchResult(
    val routeId: String,
    val routeName: String,
    val routeType: String
)