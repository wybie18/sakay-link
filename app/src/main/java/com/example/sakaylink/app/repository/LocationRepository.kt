package com.example.sakaylink.app.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class UserLocation(
    val uid: String,
    val geo: GeoPoint,
    val updatedAt: Any? = null,
    val isAvailable: Boolean? = null, // for drivers
    val isVisible: Boolean? = null    // for passengers
)

class LocationRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "LocationRepository"
        private const val USERS_COLLECTION = "users"
        private const val LOCATIONS_COLLECTION = "locations"
        private const val PASSENGERS_SUBCOLLECTION = "passengers"
        private const val DRIVERS_SUBCOLLECTION = "drivers"
    }

    /**
     * Save user location to Firestore
     */
    suspend fun saveUserLocation(
        latitude: Double,
        longitude: Double,
        userRole: String
    ): Result<Unit> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(Exception("User not authenticated"))

            val uid = currentUser.uid
            val geoPoint = GeoPoint(latitude, longitude)
            val subcollection = if (userRole == "driver") DRIVERS_SUBCOLLECTION else PASSENGERS_SUBCOLLECTION

            val locationData = if (userRole == "driver") {
                hashMapOf(
                    "geo" to geoPoint,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "isAvailable" to true
                )
            } else {
                hashMapOf(
                    "geo" to geoPoint,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "isVisible" to true
                )
            }

            firestore.collection(LOCATIONS_COLLECTION)
                .document(subcollection)
                .collection(subcollection)
                .document(uid)
                .set(locationData, SetOptions.merge())
                .await()

            Log.d(TAG, "Location saved successfully for $userRole: $latitude, $longitude")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Error saving location to Firebase", e)
            Result.failure(e)
        }
    }

    /**
     * Get user role from Firestore
     */
    suspend fun getUserRole(uid: String? = null): Result<String> {
        return try {
            val userId = uid ?: auth.currentUser?.uid
            ?: return Result.failure(Exception("User not authenticated"))

            val document = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .get()
                .await()

            val role = document.getString("role")
                ?: return Result.failure(Exception("User role not found"))

            Result.success(role)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user role", e)
            Result.failure(e)
        }
    }

    /**
     * Update driver availability
     */
    suspend fun updateDriverAvailability(isAvailable: Boolean): Result<Unit> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(Exception("User not authenticated"))

            val uid = currentUser.uid

            firestore.collection(LOCATIONS_COLLECTION)
                .document(DRIVERS_SUBCOLLECTION)
                .collection(DRIVERS_SUBCOLLECTION)
                .document(uid)
                .update("isAvailable", isAvailable, "updatedAt", FieldValue.serverTimestamp())
                .await()

            Log.d(TAG, "Driver availability updated: $isAvailable")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating driver availability", e)
            Result.failure(e)
        }
    }

    /**
     * Update passenger visibility
     */
    suspend fun updatePassengerVisibility(isVisible: Boolean): Result<Unit> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(Exception("User not authenticated"))

            val uid = currentUser.uid

            firestore.collection(LOCATIONS_COLLECTION)
                .document(PASSENGERS_SUBCOLLECTION)
                .collection(PASSENGERS_SUBCOLLECTION)
                .document(uid)
                .update("isVisible", isVisible, "updatedAt", FieldValue.serverTimestamp())
                .await()

            Log.d(TAG, "Passenger visibility updated: $isVisible")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating passenger visibility", e)
            Result.failure(e)
        }
    }

    /**
     * Get all available drivers in real-time
     */
    fun getAvailableDrivers(): Flow<List<UserLocation>> = callbackFlow {
        val listener = firestore.collection(LOCATIONS_COLLECTION)
            .document(DRIVERS_SUBCOLLECTION)
            .collection(DRIVERS_SUBCOLLECTION)
            .whereEqualTo("isAvailable", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to available drivers", error)
                    return@addSnapshotListener
                }

                val drivers = snapshot?.documents?.mapNotNull { document ->
                    try {
                        UserLocation(
                            uid = document.id,
                            geo = document.getGeoPoint("geo") ?: return@mapNotNull null,
                            updatedAt = document.get("updatedAt"),
                            isAvailable = document.getBoolean("isAvailable")
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing driver document", e)
                        null
                    }
                } ?: emptyList()

                trySend(drivers)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Get all visible passengers in real-time
     */
    fun getVisiblePassengers(): Flow<List<UserLocation>> = callbackFlow {
        val listener = firestore.collection(LOCATIONS_COLLECTION)
            .document(PASSENGERS_SUBCOLLECTION)
            .collection(PASSENGERS_SUBCOLLECTION)
            .whereEqualTo("isVisible", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to visible passengers", error)
                    return@addSnapshotListener
                }

                val passengers = snapshot?.documents?.mapNotNull { document ->
                    try {
                        UserLocation(
                            uid = document.id,
                            geo = document.getGeoPoint("geo") ?: return@mapNotNull null,
                            updatedAt = document.get("updatedAt"),
                            isVisible = document.getBoolean("isVisible")
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing passenger document", e)
                        null
                    }
                } ?: emptyList()

                trySend(passengers)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Get current user location
     */
    suspend fun getCurrentUserLocation(): Result<UserLocation?> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(Exception("User not authenticated"))

            val uid = currentUser.uid
            val userRole = getUserRole().getOrNull()
                ?: return Result.failure(Exception("User role not found"))

            val subcollection = if (userRole == "driver") DRIVERS_SUBCOLLECTION else PASSENGERS_SUBCOLLECTION

            val document = firestore.collection(LOCATIONS_COLLECTION)
                .document(subcollection)
                .collection(subcollection)
                .document(uid)
                .get()
                .await()

            if (document.exists()) {
                val location = UserLocation(
                    uid = uid,
                    geo = document.getGeoPoint("geo") ?: return Result.success(null),
                    updatedAt = document.get("updatedAt"),
                    isAvailable = document.getBoolean("isAvailable"),
                    isVisible = document.getBoolean("isVisible")
                )
                Result.success(location)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current user location", e)
            Result.failure(e)
        }
    }

    /**
     * Delete user location
     */
    suspend fun deleteUserLocation(): Result<Unit> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(Exception("User not authenticated"))

            val uid = currentUser.uid
            val userRole = getUserRole().getOrNull()
                ?: return Result.failure(Exception("User role not found"))

            val subcollection = if (userRole == "driver") DRIVERS_SUBCOLLECTION else PASSENGERS_SUBCOLLECTION

            firestore.collection(LOCATIONS_COLLECTION)
                .document(subcollection)
                .collection(subcollection)
                .document(uid)
                .delete()
                .await()

            Log.d(TAG, "User location deleted successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting user location", e)
            Result.failure(e)
        }
    }
}