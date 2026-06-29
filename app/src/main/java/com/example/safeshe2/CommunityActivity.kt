package com.example.safeshe2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.example.safeshe2.databinding.ActivityCommunityBinding
import kotlin.math.*
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

data class UserLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = 0,
    val accuracy: Float = 0f,
    val speed: Float = 0f,
    val bearing: Float = 0f
)

class CommunityActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var binding: ActivityCommunityBinding
    private lateinit var mMap: GoogleMap
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private val LOCATION_PERMISSION_REQUEST_CODE = 1003
    private var isMapReady = false
    private val panicMarkers = mutableMapOf<String, com.google.android.gms.maps.model.Marker>()
    private val panicCircles = mutableMapOf<String, Circle>()
    private val TAG = "CommunityActivity"
    private val PANIC_NOTIFICATION_ID = 1001
    private val PANIC_CHANNEL_ID = "panic_alert_channel"
    private val PANIC_OFF_NOTIFICATION_ID = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommunityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        // Get the SupportMapFragment and request notification when the map is ready
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.community_map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Set up emergency switch listener
        binding.emergencySwitch.setOnCheckedChangeListener { _, isChecked ->
            Log.d(TAG, "Emergency switch toggled: $isChecked")
            updateEmergencyStatus(isChecked)
            // Force update the map markers
            setupPanicListeners()
        }

        // Check location permissions
        checkLocationPermissions()

        createNotificationChannel()
    }

    private fun checkLocationPermissions() {
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
        } else {
            startLocationService()
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
                startLocationService()
                if (isMapReady) {
                    enableMyLocation()
                }
            } else {
                Toast.makeText(
                    this,
                    "Location permission is required for community features",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun startLocationService() {
        val serviceIntent = Intent(this, LocationManagerService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                mMap.isMyLocationEnabled = true
            } catch (e: SecurityException) {
                Toast.makeText(this, "Error enabling location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateEmergencyStatus(isEmergency: Boolean) {
        val userId = auth.currentUser?.uid ?: return
        val userRef = database.reference.child("users").child(userId)
        
        userRef.child("IsInPanic").setValue(isEmergency)
            .addOnSuccessListener {
                Log.d(TAG, "Emergency status updated: $isEmergency")
                if (isEmergency) {
                    Toast.makeText(this, "Panic mode activated", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Panic mode deactivated", Toast.LENGTH_SHORT).show()
                    showPanicOffNotification()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update emergency status", e)
                Toast.makeText(this, "Failed to update emergency status", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupPanicListeners() {
        val usersRef = database.reference.child("users")
        usersRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "Panic data changed, updating markers")
                // Clear existing markers
                panicMarkers.values.forEach { it.remove() }
                panicMarkers.clear()
                // Clear existing circles
                panicCircles.values.forEach { it.remove() }
                panicCircles.clear()

                // Get current user's location
                var currentUserLocation: LatLng? = null
                val currentUserId = auth.currentUser?.uid
                snapshot.children.forEach { userSnapshot ->
                    val userId = userSnapshot.key ?: return@forEach
                    if (userId == currentUserId) {
                        val location = userSnapshot.child("location").getValue(UserLocation::class.java)
                        location?.let { currentUserLocation = LatLng(it.latitude, it.longitude) }
                    }
                }

                var notified = false
                var anyNearbyPanic = false

                snapshot.children.forEach { userSnapshot ->
                    val userId = userSnapshot.key ?: return@forEach
                    val location = userSnapshot.child("location").getValue(UserLocation::class.java)
                    val isInPanic = userSnapshot.child("IsInPanic").getValue(Boolean::class.java) ?: false

                    Log.d(TAG, "User $userId - Panic: $isInPanic, Location: $location")

                    // Update emergency switch if it's the current user
                    if (userId == auth.currentUser?.uid) {
                        binding.emergencySwitch.isChecked = isInPanic
                    }

                    location?.let {
                        val userLocation = LatLng(it.latitude, it.longitude)

                        if (isInPanic) {
                            val marker = mMap.addMarker(
                                MarkerOptions()
                                    .position(userLocation)
                                    .title("Panic Alert")
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                            )
                            marker?.let { panicMarkers[userId] = it }
                            // Add a 100m radius circle
                            val circle = mMap.addCircle(
                                CircleOptions()
                                    .center(userLocation)
                                    .radius(100.0)
                                    .strokeColor(0x55FF0000)
                                    .fillColor(0x22FF0000)
                                    .strokeWidth(4f)
                            )
                            panicCircles[userId] = circle
                            Log.d(TAG, "Added panic marker for user $userId at $userLocation")

                            // Notify if another user in panic is near the current user
                            if (!notified && userId != currentUserId && currentUserLocation != null) {
                                val distance = haversine(currentUserLocation!!.latitude, currentUserLocation!!.longitude, it.latitude, it.longitude)
                                if (distance <= 500.0) {
                                    showPanicNotification()
                                    notified = true
                                    anyNearbyPanic = true
                        }
                    }
                            // If current user is in panic, don't show notification to self
                            if (userId == currentUserId) {
                                anyNearbyPanic = false
                            }
                        }
                    }
                }
                // If no nearby panic, cancel the notification
                if (!anyNearbyPanic) {
                    cancelPanicNotification()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error reading panic data: ${error.message}")
                Toast.makeText(this@CommunityActivity, "Error reading panic data", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        isMapReady = true
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            enableMyLocation()
        }
        setupPanicListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(this, LocationManagerService::class.java))
    }

    // Haversine formula to calculate distance in meters between two lat/lng points
    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    private fun showPanicNotification() {
        val notificationManager = NotificationManagerCompat.from(this)
        val intent = intent // Open current activity
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, PANIC_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // Make sure you have this icon in your drawable
            .setContentTitle("Panic Alert Nearby")
            .setContentText("Someone near you is in panic!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
        notificationManager.notify(PANIC_NOTIFICATION_ID, builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Panic Alert Channel"
            val descriptionText = "Notifies when someone near you is in panic"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(PANIC_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun cancelPanicNotification() {
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.cancel(PANIC_NOTIFICATION_ID)
    }

    private fun showPanicOffNotification() {
        val notificationManager = NotificationManagerCompat.from(this)
        val intent = intent // Open current activity
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, PANIC_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Panic Off")
            .setContentText("Your panic mode is now off.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
        notificationManager.notify(PANIC_OFF_NOTIFICATION_ID, builder.build())
    }
} 