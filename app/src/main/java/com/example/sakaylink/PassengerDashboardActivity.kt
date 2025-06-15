package com.example.sakaylink

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class PassengerDashboardActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_passenger_dashboard)

        bottomNavigation = findViewById(R.id.bottom_navigation)
        bottomNavigation.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.nav_map -> {
                    replaceFragment(PassengerMapFragment())
                    true
                }
                R.id.nav_drivers -> {
//                    replaceFragment(DriversListFragment())
                    true
                }
                R.id.nav_rides -> {
//                    replaceFragment(RideHistoryFragment())
                    true
                }
                R.id.nav_profile -> {
                    replaceFragment(PassengerProfileFragment())
                    true
                }
                else -> false
            }
        }

        // Default fragment
        replaceFragment(PassengerMapFragment())
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}