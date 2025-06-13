package com.example.sakaylink

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class DriverDashboardActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_dashboard)

        bottomNavigation = findViewById(R.id.bottom_navigation)
        bottomNavigation.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.nav_map -> {
                    replaceFragment(DriverMapFragment())
                    true
                }
                R.id.nav_passengers -> {
//                    replaceFragment(PassengersListFragment())
                    true
                }
                R.id.nav_profile -> {
//                    replaceFragment(ProfileFragment())
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
}