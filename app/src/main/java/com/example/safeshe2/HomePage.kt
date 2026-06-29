package com.example.safeshe2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.safeshe2.databinding.ActivityHomepageBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore

class HomePage : AppCompatActivity() {
    private lateinit var binding: ActivityHomepageBinding
    private lateinit var btnSos: ImageView
    private lateinit var btnLogout: ImageView
    private lateinit var communityBtn: ImageView
    private lateinit var mapsBtn: ImageView
    private lateinit var lawGuideBtn: ImageView
    private lateinit var accountManagementBtn: ImageView
    private lateinit var screamDetectionBtn: ImageView
    private lateinit var angelCodeTextView: TextView
    private lateinit var uidButton: ImageView
    private lateinit var imageView3: ImageView
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var database: FirebaseDatabase
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private val CALL_PHONE_PERMISSION_REQUEST_CODE = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomepageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Initialize Firebase
        db = FirebaseFirestore.getInstance()
        database = FirebaseDatabase.getInstance()

        // Set user online status
        setUserOnlineStatus(true)

        // Initialize views
        btnSos = binding.btnSos
        btnLogout = binding.btnLogout
        communityBtn = binding.communityBtn
        mapsBtn = binding.maps
        lawGuideBtn = binding.lawguide
        accountManagementBtn = binding.image11

        screamDetectionBtn = binding.screamDetection
        angelCodeTextView = binding.angelCodeTextView
        uidButton = binding.uidButton
        imageView3 = binding.imageView3

        // Hide angelCodeTextView by default
        angelCodeTextView.visibility = View.GONE
        imageView3.visibility = View.VISIBLE

        // Load and display angel code
        loadAngelCode()

        // Check and request location permissions
        checkLocationPermissions()

        // Set up click listeners
        btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // SOS Button - Direct emergency call
        btnSos.setOnClickListener {
            val emergencyNumber = "15"
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                callEmergencyNumber(emergencyNumber)
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), CALL_PHONE_PERMISSION_REQUEST_CODE)
            }
        }

        // Other button click listeners
        communityBtn.setOnClickListener {
            startActivity(Intent(this, CommunityActivity::class.java))
        }

        mapsBtn.setOnClickListener {
            startActivity(Intent(this, MapsActivity::class.java))
        }

        lawGuideBtn.setOnClickListener {
            startActivity(Intent(this, LawPath::class.java))
        }

        accountManagementBtn.setOnClickListener {
            startActivity(Intent(this, AccountSettings::class.java))
        }

        screamDetectionBtn.setOnClickListener {
            startActivity(Intent(this, ScreamDetectionActivity::class.java))
        }

        uidButton.setOnClickListener {
            if (angelCodeTextView.visibility != View.VISIBLE) {
                // Show angel code with slide-in
                angelCodeTextView.visibility = View.VISIBLE
                angelCodeTextView.translationY = -angelCodeTextView.height.toFloat()
                angelCodeTextView.alpha = 0f
                angelCodeTextView.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(400)
                    .start()
                // Hide imageView3
                imageView3.visibility = View.GONE
            } else {
                // Hide angel code with slide-out
                angelCodeTextView.animate()
                    .translationY(-angelCodeTextView.height.toFloat())
                    .alpha(0f)
                    .setDuration(400)
                    .withEndAction {
                        angelCodeTextView.visibility = View.GONE
                        // Show imageView3
                        imageView3.visibility = View.VISIBLE
                    }
                    .start()
            }
        }
    }

    private fun loadAngelCode() {
        val currentUser = auth.currentUser
        currentUser?.let { user ->
            db.collection("users")
                .document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val angelCode = document.getString("angelCode")
                        angelCode?.let {
                            angelCodeTextView.text = "Angel Code: $it"
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error loading angel code: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun checkLocationPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            startLocationTracking()
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
                startLocationTracking()
            } else {
                Toast.makeText(
                    this,
                    "Location permission is required for tracking",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else if (requestCode == CALL_PHONE_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                callEmergencyNumber("15")
            } else {
                Toast.makeText(this, "Call permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startLocationTracking() {
        val serviceIntent = Intent(this, LocationManagerService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Set user offline when leaving the app
        setUserOnlineStatus(false)
        // Don't stop the services here as they should run in background
    }

    private fun setUserOnlineStatus(isOnline: Boolean) {
        val userId = auth.currentUser?.uid ?: return
        val userRef = database.reference.child("users").child(userId)
        
        userRef.child("IsOnline").setValue(isOnline)
            .addOnSuccessListener {
                Log.d("HomePage", "User online status updated: $isOnline")
            }
            .addOnFailureListener { e ->
                Log.e("HomePage", "Error updating online status: ${e.message}")
            }
    }

    override fun onPause() {
        super.onPause()
        // Don't set offline on pause as we want to keep services running
    }

    override fun onStop() {
        super.onStop()
        // Don't set offline on stop as we want to keep services running
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // When back button is pressed, go to MainActivity
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun callEmergencyNumber(number: String) {
        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$number")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(callIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to place call", Toast.LENGTH_SHORT).show()
        }
    }
}