package com.example.sakaylink.app.repository

import com.google.firebase.Timestamp

data class VehicleInfo(
    val make: String = "",
    val model: String = "",
    val color: String = "",
    val plateNumber: String = "",
    val year: Long = 0L
)

data class DriverCredentials(
    val driverLicenseUrl: String = "",
    val licenseNumber: String = "",
    val licenseExpiry: Timestamp? = null,
    val backgroundCheckUrl: String = ""
)

data class DriverInfo(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val profileUrl: String? = null,
    val vehicleInfo: VehicleInfo? = null,
    val credentials: DriverCredentials? = null,
    val isVerified: Boolean = false,
    val verifiedAt: Timestamp? = null
)