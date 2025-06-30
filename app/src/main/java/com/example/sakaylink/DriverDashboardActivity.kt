package com.example.sakaylink

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.sakaylink.app.repository.LocationRepository
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

class DriverDashboardActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var locationRepository: LocationRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_dashboard)

        locationRepository = LocationRepository()

        bottomNavigation = findViewById(R.id.bottom_navigation)
        bottomNavigation.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.nav_map -> {
                    replaceFragment(DriverMapFragment())
                    true
                }
//                R.id.nav_passengers -> {
////                    replaceFragment(PassengersListFragment())
//                    true
//                }
                R.id.nav_profile -> {
                    replaceFragment(DriverProfileFragment())
                    true
                }
                else -> false
            }
        }

        // Default fragment
        replaceFragment(DriverMapFragment())
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