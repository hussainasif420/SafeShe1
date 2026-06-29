package com.example.safeshe2

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.concurrent.TimeUnit

class LocationManagerService : Service() {
    private val TAG = "LocationManagerService"
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private var isTracking = false

    private val _currentLocation = MutableLiveData<Location>()
    val currentLocation: LiveData<Location> = _currentLocation
    
    private var lastUploadedLocation: Location? = null
    private val MIN_DISTANCE_CHANGE_METERS = 10f
    private var hasNotifiedRedZone = false

    private val RED_ZONE_CENTER = android.location.Location("").apply {
        latitude = 31.472326
        longitude = 74.313164
    }
    private val RED_ZONE_RADIUS = 170.0

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "location_tracking_channel"
        private const val UPDATE_INTERVAL = 30L
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        createLocationCallback()
        createNotificationChannel()
        startLocationUpdates()
        
        // Set service as foreground to prevent it from being killed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                createForegroundNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, createForegroundNotification())
        }
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    _currentLocation.postValue(location)
                    handleLocationUpdate(location)
                    Log.d(TAG, "Location updated: ${location.latitude}, ${location.longitude}")
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tracks user location in background"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundNotification(): Notification {
        val notificationIntent = Intent(this, HomePage::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SafeShe")
            .setContentText("Location tracking is active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // Make notification non-dismissible
            .build()
    }

    fun startLocationUpdates() {
        if (isTracking) return

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted")
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, TimeUnit.SECONDS.toMillis(UPDATE_INTERVAL))
            .setMinUpdateIntervalMillis(TimeUnit.SECONDS.toMillis(15))
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            isTracking = true
            Log.d(TAG, "Location updates started")

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    handleLocationUpdate(it)
                    Log.d(TAG, "Initial location set: ${it.latitude}, ${it.longitude}")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception while requesting location updates: ${e.message}")
        }
    }

    fun stopLocationUpdates() {
        if (!isTracking) return
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            isTracking = false
            stopForeground(true)
            Log.d(TAG, "Location updates stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location updates: ${e.message}")
        }
    }

    private fun handleLocationUpdate(location: Location) {
        val lastLocation = lastUploadedLocation
        if (lastLocation == null || hasLocationChangedSignificantly(lastLocation, location)) {
            uploadLocationToFirebase(location)
            checkRedZone(location)
            lastUploadedLocation = location
        }
    }

    private fun hasLocationChangedSignificantly(oldLocation: Location, newLocation: Location): Boolean {
        return oldLocation.distanceTo(newLocation) >= MIN_DISTANCE_CHANGE_METERS
    }

    private fun checkRedZone(location: Location) {
        val distanceToRedZone = location.distanceTo(RED_ZONE_CENTER)
        val isInRedZone = distanceToRedZone <= RED_ZONE_RADIUS

        val userId = auth.currentUser?.uid ?: return
        val userRef = database.reference.child("users").child(userId)

        userRef.child("IsInRedZone").setValue(isInRedZone)
            .addOnSuccessListener {
                Log.d(TAG, "Red zone status updated: $isInRedZone")
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to update red zone status: ${exception.message}")
            }

        if (isInRedZone && !hasNotifiedRedZone) {
            sendRedZoneNotification()
            hasNotifiedRedZone = true
        } else if (!isInRedZone) {
            hasNotifiedRedZone = false
        }
    }

    private fun sendRedZoneNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "red_zone_channel",
                "Red Zone Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for red zone alerts"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MapsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "red_zone_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Red Zone Alert")
            .setContentText("You have entered a red zone. Please be cautious.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(1, notification)
    }
    
    private fun uploadLocationToFirebase(location: Location) {
        val userId = auth.currentUser?.uid ?: run {
            Log.e(TAG, "User not authenticated")
            return
        }
        
        val locationData = hashMapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "timestamp" to System.currentTimeMillis(),
            "accuracy" to location.accuracy,
            "speed" to location.speed,
            "bearing" to location.bearing
        )
        
        val userRef = database.reference.child("users").child(userId)
        
        userRef.child("location").setValue(locationData)
            .addOnSuccessListener {
                Log.d(TAG, "Location updated successfully: ${location.latitude}, ${location.longitude}")
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to update location: ${exception.message}")
            }
        
        userRef.child("lastLocationUpdate").setValue(System.currentTimeMillis())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Restart service if it gets killed
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Restart service if app is removed from recent tasks
        val restartServiceIntent = Intent(applicationContext, LocationManagerService::class.java)
        restartServiceIntent.setPackage(packageName)
        ContextCompat.startForegroundService(applicationContext, restartServiceIntent)
        super.onTaskRemoved(rootIntent)
    }
} 