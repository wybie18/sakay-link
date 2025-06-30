package com.example.sakaylink

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.sakaylink.app.repository.LocationRepository
import com.example.sakaylink.app.utils.LocationManager
import com.example.sakaylink.ui.theme.components.MapboxMapContent
import com.google.android.gms.location.LocationServices
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class DriverMapFragment : Fragment() {

    private lateinit var locationManager: LocationManager
    private lateinit var locationRepository: LocationRepository
    private lateinit var startButton: FloatingActionButton

    private var isDriverAvailable = false

    // Permission launcher
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            // Permission granted, check if GPS is enabled
            checkGpsAndProceed()
        } else {
            // Permission denied
            Toast.makeText(
                requireContext(),
                "Location permission is required for this feature",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView = inflater.inflate(R.layout.fragment_driver_map, container, false)

        locationManager = LocationManager(
            context = requireContext(),
            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireContext())
        )
        locationRepository = LocationRepository()
        startButton = rootView.findViewById(R.id.start_button)

        lifecycleScope.launch {
            val result = locationRepository.isUserAvailableOrVisible()
            if (result.isSuccess) {
                isDriverAvailable = result.getOrNull() ?: false
                updateButtonAppearance()
            } else {
                isDriverAvailable = false
                updateButtonAppearance()
                Log.e("DriverMapFragment", "Error getting driver availability: ${result.exceptionOrNull()}")
            }
        }

        startButton.setOnClickListener {
            isDriverAvailable = !isDriverAvailable
            lifecycleScope.launch {
                Toast.makeText(requireContext(), "Driver availability changed", Toast.LENGTH_SHORT).show()
                locationRepository.updateDriverAvailability(isDriverAvailable)
            }
            updateButtonAppearance()
        }

        val composeView = rootView.findViewById<ComposeView>(R.id.compose_map_view)
        composeView.setContent {
            MapboxMapContent(
                locationManager = locationManager,
                locationRepository = locationRepository,
                role = "driver"
            )
        }

        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Check permissions and GPS when fragment is created
        checkPermissionsAndGps()
    }

    private fun updateButtonAppearance() {
        startButton.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), if (isDriverAvailable) R.color.success_color else R.color.accent_color)
        )
    }

    private fun checkPermissionsAndGps() {
        if (!locationManager.hasLocationPermissions()) {
            requestLocationPermissions()
        } else {
            checkGpsAndProceed()
        }
    }

    private fun requestLocationPermissions() {
        locationPermissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun checkGpsAndProceed() {
        if (!locationManager.isGpsEnabled()) {
            promptEnableGps()
        }
        // If GPS is enabled or user chooses not to enable it, the map will still work
        // but location features may be limited
    }

    private fun showGpsEnableDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Enable GPS")
            .setMessage("Location services are required for this app. Please enable GPS.")
            .setPositiveButton("Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(
                    requireContext(),
                    "Location services are still disabled. Some features may not work properly.",
                    Toast.LENGTH_LONG
                ).show()
            }
            .create()
            .show()
    }

    private fun promptEnableGps() {
        Toast.makeText(
            requireContext(),
            "Please enable location services for better accuracy",
            Toast.LENGTH_LONG
        ).show()
        try {
            showGpsEnableDialog()
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Unable to open location settings",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}