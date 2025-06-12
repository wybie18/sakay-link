package com.example.sakaylink.app.models

import com.google.firebase.Timestamp

data class User(
    val name: String = "",
    val email: String = "",
    val role: String = "",
    val profileUrl: String = "",
    val phoneNumber: String = "",
    val createdAt: Timestamp? = null
)

data class Driver(
    val vehicleInfo: VehicleInfo = VehicleInfo(),
    val credentials: Credentials = Credentials(),
    val isVerified: Boolean = false,
    val verifiedAt: Timestamp? = null
)

data class VehicleInfo(
    val make: String = "",
    val model: String = "",
    val color: String = "",
    val plateNumber: String = "",
    val year: Int = 0
)

data class Credentials(
    val driverLicenseUrl: String = "",
    val licenseNumber: String = "",
    val licenseExpiry: Timestamp? = null,
    val backgroundCheckUrl: String = ""
)