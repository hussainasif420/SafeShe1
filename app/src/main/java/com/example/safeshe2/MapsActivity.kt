package com.example.safeshe2

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1002

    // Fixed Zone Coordinates (Replace with your actual values)
    private val RED_ZONE = LatLng(31.472326, 74.313164)    // Example: Bangalore
    private val GREEN_ZONE = LatLng(31.39181, 74.241022)  // 1km North
    private val ORANGE_ZONE = LatLng(34.073608, 72.651158) // 1km South

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        checkLocationPermission()
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation()
            } else {
                Toast.makeText(
                    this,
                    "Location permission is required for better accuracy",
                    Toast.LENGTH_LONG
                ).show()
                addSafetyZones() // Show zones even without permission
            }
        }
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                mMap.isMyLocationEnabled = true
                getCurrentLocation()
            } catch (e: SecurityException) {
                Toast.makeText(this, "Error enabling location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        val currentLatLng = LatLng(it.latitude, it.longitude)
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                    }
                    addSafetyZones() // Always show zones
                }
            } catch (e: SecurityException) {
                Toast.makeText(this, "Error getting location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addSafetyZones() {
        // Darker colors (less transparency)
        val DARK_RED = 0x66FF0000     // 40% opacity red
        val DARK_GREEN = 0x6600FF00   // 40% opacity green
        val DARK_ORANGE = 0x66FFA500  // 40% opacity orange

        // Red Zone (300m radius)
        mMap.addCircle(
            CircleOptions()
                .center(RED_ZONE)
                .radius(170.0)
                .fillColor(DARK_RED)
                .strokeColor(android.graphics.Color.RED)
                .strokeWidth(3f)
        )

        // Green Zone (300m radius)
        mMap.addCircle(
            CircleOptions()
                .center(GREEN_ZONE)
                .radius(330.0)
                .fillColor(DARK_GREEN)
                .strokeColor(android.graphics.Color.GREEN)
                .strokeWidth(3f)
        )

        // Orange Zone (300m radius)
        mMap.addCircle(
            CircleOptions()
                .center(ORANGE_ZONE)
                .radius(300.0)
                .fillColor(DARK_ORANGE)
                .strokeColor(android.graphics.Color.parseColor("#FFA500"))
                .strokeWidth(3f)
        )

        // Center map on Red Zone if location is unavailable
        if (!::mMap.isInitialized || !mMap.isMyLocationEnabled) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(RED_ZONE, 13f))
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        enableMyLocation()
    }
}