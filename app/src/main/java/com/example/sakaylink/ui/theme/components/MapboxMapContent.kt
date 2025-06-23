package com.example.sakaylink.ui.theme.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
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
import com.example.sakaylink.app.repository.LocationRepository
import com.example.sakaylink.app.utils.LocationManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
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

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapboxMapContent(
    locationManager: LocationManager,
    locationRepository: LocationRepository,
    role: String
) {

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
    )

    var availableDrivers by remember { mutableStateOf<List<com.example.sakaylink.app.repository.UserLocation>>(emptyList()) }
    var visiblePassengers by remember { mutableStateOf<List<com.example.sakaylink.app.repository.UserLocation>>(emptyList()) }

    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(Point.fromLngLat(125.97710, 8.50503))
            zoom(14.0)
            pitch(0.0)
        }
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

    LaunchedEffect(locationPermissions.allPermissionsGranted) {
        if (locationPermissions.allPermissionsGranted) {
            try {
                val location = locationManager.getLocation()
                if (location != null) {
                    locationRepository.saveUserLocation(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        userRole = role
                    ).fold(
                        onSuccess = {
                            Toast.makeText(context, "Location saved successfully!", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { error ->
                            Toast.makeText(context, "Failed to save location: ${error.message}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error getting location: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
        ) {
            MapEffect(key1 = Unit) { mapView ->
                mapView.location.updateSettings {
                    locationPuck = createDefault2DPuck(withBearing = true)
                    enabled = true
                    puckBearing = PuckBearing.COURSE
                    puckBearingEnabled = true
                }

                if (locationPermissions.allPermissionsGranted) {
                    mapViewportState.transitionToFollowPuckState()
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
            MapEffect(visiblePassengers) { mapView ->
                val annotationApi = mapView.annotations
                val pointAnnotationManager = annotationApi.createPointAnnotationManager()

                pointAnnotationManager.deleteAll()

                val context = mapView.context

                visiblePassengers.forEach { passenger ->
                    val passengerIcon = bitmapFromDrawableRes(context, R.drawable.pin_drop_24px, ContextCompat.getColor(context, R.color.success_color))
                    passengerIcon?.let { icon ->
                        val annotation = PointAnnotationOptions()
                            .withPoint(Point.fromLngLat(passenger.geo.longitude, passenger.geo.latitude))
                            .withIconImage(icon)

                        val marker = pointAnnotationManager.create(annotation)

                        pointAnnotationManager.addClickListener { clickedMarker ->
                            if (clickedMarker.id == marker.id) {
                                Toast.makeText(context, "Passenger location", Toast.LENGTH_SHORT).show()
                            }
                            true
                        }
                    }
                }
            }
        }

        IconButton(
            onClick = {
                mapViewportState.transitionToFollowPuckState()
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .background(Color.White, shape = CircleShape)
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = "Center on My Location",
                tint = Color.Black
            )
        }
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