package com.example.sakaylink

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import com.example.sakaylink.app.repository.LocationRepository
import com.example.sakaylink.app.utils.LocationManager
import com.google.android.gms.location.LocationServices
import com.example.sakaylink.ui.theme.components.MapboxMapContent
import kotlinx.coroutines.launch

class PassengerMapFragment : Fragment() {

    private lateinit var locationManager: LocationManager
    private lateinit var locationRepository: LocationRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView =  inflater.inflate(R.layout.fragment_passenger_map, container, false)
        locationManager = LocationManager(
            context = requireContext(),
            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireContext())
        )
        locationRepository = LocationRepository()

        val composeView = rootView.findViewById<ComposeView>(R.id.compose_map_view)
        composeView.setContent {
            MapboxMapContent(
                locationManager = locationManager,
                locationRepository = locationRepository,
                role = "passenger"
            )
        }

        return rootView
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            locationRepository.updateDriverAvailability(true)
        }
    }

    override fun onPause() {
        super.onPause()
        lifecycleScope.launch {
            locationRepository.updateDriverAvailability(false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        lifecycleScope.launch {
            locationRepository.setUserOffline()
        }
    }
}