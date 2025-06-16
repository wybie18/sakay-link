@file:OptIn(ExperimentalPermissionsApi::class)
package com.example.sakaylink.app.utils

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.android.gms.location.FusedLocationProviderClient
import android.location.LocationManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationManager(
    private val context: Context,
    private val fusedLocationProviderClient: FusedLocationProviderClient
) {

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
            return null
        }

        return suspendCancellableCoroutine { cont ->

            fusedLocationProviderClient.lastLocation.apply {
                if (isComplete) {
                    if (isSuccessful) {
                        cont.resume(result)
                    } else {
                        cont.resume(null)
                    }
                    return@suspendCancellableCoroutine
                }

                addOnSuccessListener {
                    cont.resume(result)
                }

                addOnFailureListener {
                    cont.resume(null)
                }

                addOnCanceledListener {
                    cont.cancel()
                }
            }
        }
    }
}