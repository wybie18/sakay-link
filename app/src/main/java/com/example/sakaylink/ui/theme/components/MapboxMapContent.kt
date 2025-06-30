package com.example.sakaylink.ui.theme.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.DrawableCompat
import com.example.sakaylink.R
import com.example.sakaylink.app.repository.DriverInfo
import com.example.sakaylink.app.repository.LocationRepository
import com.example.sakaylink.app.utils.LocationManager
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraBoundsOptions
import com.mapbox.maps.CoordinateBounds
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.core.net.toUri

@Composable
fun MapboxMapContent(
    locationManager: LocationManager,
    locationRepository: LocationRepository,
    role: String
) {

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var availableDrivers by remember { mutableStateOf<List<com.example.sakaylink.app.repository.UserLocation>>(emptyList()) }
    var visiblePassengers by remember { mutableStateOf<List<com.example.sakaylink.app.repository.UserLocation>>(emptyList()) }
    var isLocationUpdating by remember { mutableStateOf(false) }
    var hasPermissions by remember { mutableStateOf(locationManager.hasLocationPermissions()) }

    // Dialog states
    var showDriverDialog by remember { mutableStateOf(false) }
    var selectedDriverInfo by remember { mutableStateOf<DriverInfo?>(null) }
    var isLoadingDriverInfo by remember { mutableStateOf(false) }

    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(Point.fromLngLat(125.97710, 8.50503))
            zoom(14.0)
            pitch(0.0)
        }
    }

    // Check permissions periodically
    LaunchedEffect(Unit) {
        hasPermissions = locationManager.hasLocationPermissions()
    }

    LaunchedEffect(Unit) {
        if (role == "driver") {
            locationRepository.getVisiblePassengers().collectLatest { passengers ->
                visiblePassengers = passengers
            }
        } else {
            // Passengers listen to driver locations
            locationRepository.getAvailableDrivers().collectLatest { drivers ->
                availableDrivers = drivers
            }
        }
    }

    LaunchedEffect(hasPermissions) {
        if (hasPermissions && locationManager.isGpsEnabled() && !isLocationUpdating) {
            isLocationUpdating = true
            try {
                locationManager.getLocationUpdates().collectLatest { location ->
                    try {
                        locationRepository.saveUserLocation(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            userRole = role
                        ).fold(
                            onSuccess = {
                                // Location saved successfully
                            },
                            onFailure = { error ->
//                                Toast.makeText(context, "Failed to save location: ${error.message}", Toast.LENGTH_SHORT).show()
                                Log.e("LocationError", "Failed to save location", error)
                            }
                        )
                    } catch (e: Exception) {
//                        Toast.makeText(context, "Error processing location: ${e.message}", Toast.LENGTH_SHORT).show()
                        Log.e("LocationError", "Error processing location", e)
                    }
                }
            } catch (e: Exception) {
//                Toast.makeText(context, "Error starting location updates: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("LocationError", "Error starting location updates", e)
                isLocationUpdating = false
            }
        }
    }

    // Function to handle driver marker click
    val handleDriverClick = { driverUid: String ->
        coroutineScope.launch {
            isLoadingDriverInfo = true
            locationRepository.getDriverInfo(driverUid).fold(
                onSuccess = { driverInfo ->
                    selectedDriverInfo = driverInfo
                    showDriverDialog = true
                    isLoadingDriverInfo = false
                },
                onFailure = { error ->
                    Toast.makeText(context, "Failed to load driver info: ${error.message}", Toast.LENGTH_SHORT).show()
                    isLoadingDriverInfo = false
                }
            )
        }
    }

    // Function to handle phone call
    val handleCallDriver = { phoneNumber: String ->
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = "tel:$phoneNumber".toUri()
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to make call", Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
        ) {
            MapEffect(key1 = hasPermissions) { mapView ->
                if (hasPermissions && locationManager.isGpsEnabled()) {
                    mapView.location.updateSettings {
                        locationPuck = createDefault2DPuck(withBearing = true)
                        enabled = true
                        puckBearing = PuckBearing.COURSE
                        puckBearingEnabled = true
                    }
                    mapViewportState.transitionToFollowPuckState()
                } else {
                    // Disable location puck if no permissions
                    mapView.location.updateSettings {
                        enabled = false
                    }
                }

                val cameraBoundsOptions = CameraBoundsOptions.Builder()
                    .bounds(
                        CoordinateBounds(
                            Point.fromLngLat(116.0, 4.75),
                            Point.fromLngLat(127.0, 20.0),
                            false
                        )
                    )
                    .minZoom(14.0)
                    .maxZoom(18.0)
                    .build()

                mapView.mapboxMap.setBounds(cameraBoundsOptions)
            }

            MapEffect(key1 = availableDrivers, key2 = visiblePassengers, key3 = role) { mapView ->
                val annotationApi = mapView.annotations
                val pointAnnotationManager = annotationApi.createPointAnnotationManager()

                pointAnnotationManager.deleteAll()

                val context = mapView.context

                if (role == "driver") {
                    visiblePassengers.forEach { passenger ->
                        val passengerIcon = bitmapFromDrawableRes(
                            context,
                            R.drawable.pin_drop_24px,
                            ContextCompat.getColor(context, R.color.success_color)
                        )
                        passengerIcon?.let { icon ->
                            val annotation = PointAnnotationOptions()
                                .withPoint(
                                    Point.fromLngLat(
                                        passenger.geo.longitude,
                                        passenger.geo.latitude
                                    )
                                )
                                .withIconImage(icon)

                            val marker = pointAnnotationManager.create(annotation)

                            pointAnnotationManager.addClickListener { clickedMarker ->
                                if (clickedMarker.id == marker.id) {
                                    Toast.makeText(
                                        context,
                                        "Passenger requesting ride",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                true
                            }
                        }
                    }
                } else {
                    // Show driver locations to passengers
                    availableDrivers.forEach { driver ->
                        val driverIcon = bitmapFromDrawableRes(
                            context,
                            R.drawable.pin_drop_24px,
                            ContextCompat.getColor(context, R.color.error_color)
                        )
                        driverIcon?.let { icon ->
                            val annotation = PointAnnotationOptions()
                                .withPoint(
                                    Point.fromLngLat(
                                        driver.geo.longitude,
                                        driver.geo.latitude
                                    )
                                )
                                .withIconImage(icon)

                            val marker = pointAnnotationManager.create(annotation)

                            pointAnnotationManager.addClickListener { clickedMarker ->
                                if (clickedMarker.id == marker.id) {
                                    Toast.makeText(context, "Driver clicked", Toast.LENGTH_SHORT).show()
                                    handleDriverClick(driver.uid)
                                }
                                true
                            }
                        }
                    }
                }
            }
        }

        IconButton(
            onClick = {
                if (hasPermissions && locationManager.isGpsEnabled()) {
                    mapViewportState.transitionToFollowPuckState()
                } else {
                    Toast.makeText(
                        context,
                        "Location permissions required to center on your location",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .background(Color.White, shape = CircleShape)
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = "Center on My Location",
                tint = if (hasPermissions && locationManager.isGpsEnabled()) Color.Black else Color.Gray
            )
        }

        // Loading indicator
        if (isLoadingDriverInfo) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White
                )
            }
        }
    }

    // Driver Info Dialog
    if (showDriverDialog) {
        DriverInfoDialog(
            driverInfo = selectedDriverInfo,
            onDismiss = {
                showDriverDialog = false
                selectedDriverInfo = null
            },
            onCallDriver = { phoneNumber ->
                handleCallDriver(phoneNumber)
                showDriverDialog = false
                selectedDriverInfo = null
            }
        )
    }
}

fun bitmapFromDrawableRes(context: Context, resId: Int, tintColor: Int? = null): Bitmap? {
    val drawable = AppCompatResources.getDrawable(context, resId) ?: return null

    tintColor?.let {
        val wrappedDrawable = DrawableCompat.wrap(drawable).mutate()
        DrawableCompat.setTint(wrappedDrawable, it)
    }

    val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)

    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)

    return bitmap
}