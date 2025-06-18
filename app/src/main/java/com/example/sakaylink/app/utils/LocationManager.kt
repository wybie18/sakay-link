package com.example.sakaylink.app.utils

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationManager(
    private val context: Context,
    private val fusedLocationProviderClient: FusedLocationProviderClient
) {

    companion object {
        private const val TAG = "LocationManager"
    }

    /**
     * Get current location
     */
    suspend fun getLocation(): Location? {
        val hasGrantedFineLocationPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasGrantedCoarseLocationPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val locationManager = context.getSystemService(
            Context.LOCATION_SERVICE
        ) as LocationManager

        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

        if (!isGpsEnabled && !(hasGrantedCoarseLocationPermission || hasGrantedFineLocationPermission)) {
            Log.w(TAG, "GPS not enabled and location permissions not granted")
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
     * Check if location permissions are granted
     */
    fun hasLocationPermissions(): Boolean {
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
    fun isGpsEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}