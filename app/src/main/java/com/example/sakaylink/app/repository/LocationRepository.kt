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
//                    "isAvailable" to true
                )
            } else {
                hashMapOf(
                    "geo" to geoPoint,
                    "updatedAt" to FieldValue.serverTimestamp(),
//                    "isVisible" to true
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
    private suspend fun getUserRole(uid: String? = null): Result<String> {
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
                .set(
                    mapOf(
                        "isAvailable" to isAvailable,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
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
                .set(
                    mapOf(
                        "isVisible" to isVisible,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
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

    /**
     * Set user as offline (useful when app is closed)
     */
    suspend fun setUserOffline(): Result<Unit> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(Exception("User not authenticated"))

            val uid = currentUser.uid
            val userRole = getUserRole().getOrNull()
                ?: return Result.failure(Exception("User role not found"))

            val subcollection = if (userRole == "driver") DRIVERS_SUBCOLLECTION else PASSENGERS_SUBCOLLECTION

            val updateData = if (userRole == "driver") {
                mapOf("isAvailable" to false)
            } else {
                mapOf("isVisible" to false)
            }

            firestore.collection(LOCATIONS_COLLECTION)
                .document(subcollection)
                .collection(subcollection)
                .document(uid)
                .update(updateData)
                .await()

            Log.d(TAG, "User set as offline")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting user offline", e)
            Result.failure(e)
        }
    }

    /**
     * Get driver information by UID
     */
    suspend fun getDriverInfo(driverUid: String): Result<DriverInfo?> {
        return try {
            // Get basic user info from users collection
            val userDocument = firestore.collection(USERS_COLLECTION)
                .document(driverUid)
                .get()
                .await()

            if (!userDocument.exists()) {
                return Result.success(null)
            }

            // Get driver-specific info from drivers collection
            val driverDocument = firestore.collection("drivers")
                .document(driverUid)
                .get()
                .await()

            val name = userDocument.getString("name") ?: ""
            val email = userDocument.getString("email") ?: ""
            val phoneNumber = userDocument.getString("phoneNumber") ?: ""
            val profileUrl = userDocument.getString("profileUrl")

            val vehicleInfo = if (driverDocument.exists()) {
                val vehicleData = driverDocument.get("vehicleInfo") as? Map<String, Any>
                vehicleData?.let {
                    VehicleInfo(
                        make = it["make"] as? String ?: "",
                        model = it["model"] as? String ?: "",
                        color = it["color"] as? String ?: "",
                        plateNumber = it["plateNumber"] as? String ?: "",
                        year = it["year"] as? Long ?: 0L
                    )
                }
            } else null

            val credentials = if (driverDocument.exists()) {
                val credentialsData = driverDocument.get("credentials") as? Map<String, Any>
                credentialsData?.let {
                    DriverCredentials(
                        driverLicenseUrl = it["driverLicenseUrl"] as? String ?: "",
                        licenseNumber = it["licenseNumber"] as? String ?: "",
                        licenseExpiry = it["licenseExpiry"] as? com.google.firebase.Timestamp,
                        backgroundCheckUrl = it["backgroundCheckUrl"] as? String ?: ""
                    )
                }
            } else null

            val isVerified = driverDocument.getBoolean("isVerified") ?: false
            val verifiedAt = driverDocument.getTimestamp("verifiedAt")

            val driverInfo = DriverInfo(
                uid = driverUid,
                name = name,
                email = email,
                phoneNumber = phoneNumber,
                profileUrl = profileUrl,
                vehicleInfo = vehicleInfo,
                credentials = credentials,
                isVerified = isVerified,
                verifiedAt = verifiedAt
            )

            Result.success(driverInfo)

        } catch (e: Exception) {
            Log.e(TAG, "Error getting driver info", e)
            Result.failure(e)
        }
    }

    /**
     * Check if current user is available (for drivers) or visible (for passengers)
     * @return Result<Boolean> - true if user is available/visible, false otherwise
     */
    suspend fun isUserAvailableOrVisible(): Result<Boolean> {
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

            if (!document.exists()) {
                Log.d(TAG, "User document not found, returning false")
                return Result.success(false)
            }

            val status = if (userRole == "driver") {
                document.getBoolean("isAvailable") ?: false
            } else {
                document.getBoolean("isVisible") ?: false
            }

            Log.d(TAG, "User $userRole status: $status")
            Result.success(status)

        } catch (e: Exception) {
            Log.e(TAG, "Error checking user availability/visibility", e)
            Result.failure(e)
        }
    }

    /**
     * Check if a specific user is available (for drivers) or visible (for passengers)
     * @param targetUid The UID of the user to check
     * @param targetRole The role of the target user ("driver" or "passenger")
     * @return Result<Boolean> - true if user is available/visible, false otherwise
     */
    suspend fun isUserAvailableOrVisible(targetUid: String, targetRole: String): Result<Boolean> {
        return try {
            val subcollection = if (targetRole == "driver") DRIVERS_SUBCOLLECTION else PASSENGERS_SUBCOLLECTION

            val document = firestore.collection(LOCATIONS_COLLECTION)
                .document(subcollection)
                .collection(subcollection)
                .document(targetUid)
                .get()
                .await()

            if (!document.exists()) {
                Log.d(TAG, "Target user document not found, returning false")
                return Result.success(false)
            }

            val status = if (targetRole == "driver") {
                document.getBoolean("isAvailable") ?: false
            } else {
                document.getBoolean("isVisible") ?: false
            }

            Log.d(TAG, "Target user $targetRole status: $status")
            Result.success(status)

        } catch (e: Exception) {
            Log.e(TAG, "Error checking target user availability/visibility", e)
            Result.failure(e)
        }
    }
}