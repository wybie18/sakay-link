package com.example.sakaylink

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.sakaylink.app.repository.LocationRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

class PassengerDashboardActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationManager: com.example.sakaylink.app.utils.LocationManager
    private lateinit var locationRepository: LocationRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_passenger_dashboard)

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationManager = com.example.sakaylink.app.utils.LocationManager(this, fusedLocationClient)
        locationRepository = LocationRepository()

        bottomNavigation = findViewById(R.id.bottom_navigation)
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_map -> {
                    replaceFragment(PassengerMapFragment())
                    true
                }
//                R.id.nav_drivers -> {
//                    // replaceFragment(DriversListFragment())
//                    true
//                }
//                R.id.nav_rides -> {
//                    // replaceFragment(RideHistoryFragment())
//                    true
//                }
                R.id.nav_profile -> {
                    replaceFragment(PassengerProfileFragment())
                    true
                }
                else -> false
            }
        }
        replaceFragment(PassengerMapFragment())
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            locationRepository.setUserOffline()
        }
    }
}