package com.example.sakaylink

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ListenerRegistration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import java.net.URL

class PassengerMapFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var requestRideButton: FloatingActionButton

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private var locationListener: ListenerRegistration? = null
    private var driversListener: ListenerRegistration? = null
    private var currentLocationMarker: Marker? = null
    private val driverMarkers = mutableMapOf<String, Marker>()


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_passenger_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requestRideButton = view.findViewById(R.id.request_ride_button)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupLocationCallback()
        setupClickListeners()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.mapType = GoogleMap.MAP_TYPE_NONE

        val tileProvider = object : UrlTileProvider(256, 256) {
            override fun getTileUrl(x: Int, y: Int, zoom: Int): URL {
                // Choose layer: 'm' = standard, 's' = satellite, 'y' = hybrid, 't' = terrain
                val layer = "m"
                val urlString = "https://maps.gomaps.pro/vt/lyrs=$layer&x=$x&y=$y&z=$zoom"
                return URL(urlString)
            }
        }

        val tileOverlayOptions = TileOverlayOptions()
            .tileProvider(tileProvider)
        mMap.addTileOverlay(tileOverlayOptions)

        mMap.uiSettings.isZoomControlsEnabled = true

        val testLatLng = LatLng(14.5995, 120.9842)
        val zoomLevel = 10f
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(testLatLng, zoomLevel))

//        if (ActivityCompat.checkSelfPermission(requireContext(),
//                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
//            mMap.isMyLocationEnabled = true
//            getCurrentLocation()
//            startLocationUpdates()
//            listenToDrivers()
//        } else {
//            Toast.makeText(
//                requireContext(),
//                "Location permission is needed for this feature",
//                Toast.LENGTH_SHORT
//            ).show()
//        }
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                updateLocationInFirestore(location)
                updateMapLocation(location)
            }
        }
    }

    private fun setupClickListeners() {

        requestRideButton.setOnClickListener {
            // TODO: Implement ride request functionality
            Toast.makeText(context, "Ride request feature coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                updateMapLocation(it)
                updateLocationInFirestore(it)
            }
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10_000L
        ).apply {
            setMinUpdateIntervalMillis(5_000L)
            setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            setWaitForAccurateLocation(true)
        }.build()

        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        }
    }

    private fun updateMapLocation(location: Location) {
        val latLng = LatLng(location.latitude, location.longitude)

        currentLocationMarker?.remove()
        currentLocationMarker = mMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("My Location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
        )

        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
    }

    private fun updateLocationInFirestore(location: Location) {
        val userId = auth.currentUser?.uid ?: return
        val geoPoint = GeoPoint(location.latitude, location.longitude)

        val locationData = hashMapOf(
            "geo" to geoPoint,
            "updatedAt" to com.google.firebase.Timestamp.now(),
        )

        firestore.collection("locations")
            .document("passengers")
            .collection("passengers")
            .document(userId)
            .set(locationData)
    }

    private fun updateVisibilityInFirestore(isVisible: Boolean) {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("locations")
            .document("passengers")
            .collection("passengers")
            .document(userId)
            .update("isVisible", isVisible)
    }

    private fun listenToDrivers() {
        driversListener = firestore.collection("locations")
            .document("drivers")
            .collection("drivers")
            .whereEqualTo("isAvailable", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                // Clear existing driver markers
                driverMarkers.values.forEach { it.remove() }
                driverMarkers.clear()

                snapshot?.documents?.forEach { document ->
                    val driverId = document.id
                    val geoPoint = document.getGeoPoint("geo")

                    geoPoint?.let {
                        val latLng = LatLng(it.latitude, it.longitude)
                        val marker = mMap.addMarker(
                            MarkerOptions()
                                .position(latLng)
                                .title("Available Driver")
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                        )
                        marker?.let { m -> driverMarkers[driverId] = m }
                    }
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationListener?.remove()
        driversListener?.remove()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}