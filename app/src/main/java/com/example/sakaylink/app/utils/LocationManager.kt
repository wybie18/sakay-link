package com.example.sakaylink.app.utils

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

class LocationManager(
    private val context: Context,
    private val fusedLocationProviderClient: FusedLocationProviderClient
) {

    companion object {
        private const val TAG = "LocationManager"
        private const val LOCATION_UPDATE_INTERVAL = 5000L // 5 seconds
        private const val LOCATION_FASTEST_INTERVAL = 2000L // 2 seconds
        private const val LOCATION_SMALLEST_DISPLACEMENT = 10f // 10 meters
    }

    /**
     * Get current location
     */
    suspend fun getLocation(): Location? {
        if (!hasLocationPermissions()) {
            Log.w(TAG, "Location permissions not granted")
            return null
        }

        if (!isGpsEnabled()) {
            Log.w(TAG, "GPS not enabled")
            return null
        }

        return suspendCancellableCoroutine { cont ->
            try {
                fusedLocationProviderClient.lastLocation.apply {
                    if (isComplete) {
                        if (isSuccessful) {
                            cont.resume(result)
                        } else {
                            cont.resume(null)
                        }
                        return@suspendCancellableCoroutine
                    }

                    addOnSuccessListener { location ->
                        Log.d(TAG, "Location retrieved successfully: $location")
                        cont.resume(location)
                    }

                    addOnFailureListener { exception ->
                        Log.e(TAG, "Failed to get location", exception)
                        cont.resume(null)
                    }

                    addOnCanceledListener {
                        Log.w(TAG, "Location request was cancelled")
                        cont.cancel()
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception when getting location", e)
                cont.resume(null)
            }
        }
    }

    /**
     * Get continuous location updates as Flow
     */
    fun getLocationUpdates(): Flow<Location> = callbackFlow {
        if (!hasLocationPermissions()) {
            Log.w(TAG, "Location permissions not granted")
            close()
            return@callbackFlow
        }

        if (!isGpsEnabled()) {
            Log.w(TAG, "GPS not enabled")
            close()
            return@callbackFlow
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_UPDATE_INTERVAL
        ).apply {
            setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL)
            setMinUpdateDistanceMeters(LOCATION_SMALLEST_DISPLACEMENT)
        }.build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    Log.d(TAG, "Location update: ${location.latitude}, ${location.longitude}")
                    trySend(location)
                }
            }
        }

        try {
            fusedLocationProviderClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception when requesting location updates", e)
            close()
        }

        awaitClose {
            Log.d(TAG, "Stopping location updates")
            fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        }
    }

    /**
     * Check if location permissions are granted
     */
    private fun hasLocationPermissions(): Boolean {
        val hasGrantedFineLocationPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasGrantedCoarseLocationPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return hasGrantedFineLocationPermission || hasGrantedCoarseLocationPermission
    }

    /**
     * Check if GPS is enabled
     */
    private fun isGpsEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}