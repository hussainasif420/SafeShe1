package com.example.safeshe2

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore

class GuardianModeActivity : AppCompatActivity() {
    private lateinit var guardianCode: TextInputEditText
    private lateinit var btnSubmit: Button
    private lateinit var btnLogout: ImageView
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var realtimeDb: FirebaseDatabase
    private lateinit var sharedPreferences: SharedPreferences
    private val PREFS_NAME = "GuardianPrefs"
    private val KEY_ANGEL_UID = "angel_uid"
    
    // Angel details views
    private lateinit var angelDetailsScrollView: ScrollView
    private lateinit var textViewAngelName: TextView
    private lateinit var textViewAngelEmail: TextView
    private lateinit var textViewIsInPanic: TextView
    private lateinit var textViewIsInRedZone: TextView
    private lateinit var textViewIsOnline: TextView
    private lateinit var textViewIsScream: TextView
    private lateinit var textViewLastLocationUpdate: TextView
    private lateinit var textViewLocationAccuracy: TextView
    private lateinit var textViewLocationBearing: TextView
    private lateinit var textViewLocationLatitude: TextView
    private lateinit var textViewLocationLongitude: TextView
    private lateinit var textViewLocationSpeed: TextView

    private var angelListener: com.google.firebase.database.ValueEventListener? = null
    private var angelUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guardian_mode)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        realtimeDb = FirebaseDatabase.getInstance()

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // Initialize views
        guardianCode = findViewById(R.id.guardianCode)
        btnSubmit = findViewById(R.id.btnSubmit)
        btnLogout = findViewById(R.id.btnLogout)
        
        // Initialize angel details views
        angelDetailsScrollView = findViewById(R.id.angelDetailsScrollView)
        textViewAngelName = findViewById(R.id.textViewAngelName)
        textViewAngelEmail = findViewById(R.id.textViewAngelEmail)
        textViewIsInPanic = findViewById(R.id.textViewIsInPanic)
        textViewIsInRedZone = findViewById(R.id.textViewIsInRedZone)
        textViewIsOnline = findViewById(R.id.textViewIsOnline)
        textViewIsScream = findViewById(R.id.textViewIsScream)
        textViewLastLocationUpdate = findViewById(R.id.textViewLastLocationUpdate)
        textViewLocationAccuracy = findViewById(R.id.textViewLocationAccuracy)
        textViewLocationBearing = findViewById(R.id.textViewLocationBearing)
        textViewLocationLatitude = findViewById(R.id.textViewLocationLatitude)
        textViewLocationLongitude = findViewById(R.id.textViewLocationLongitude)
        textViewLocationSpeed = findViewById(R.id.textViewLocationSpeed)

        // Check if angel UID is already saved
        val savedAngelUid = sharedPreferences.getString(KEY_ANGEL_UID, null)
        if (savedAngelUid != null) {
            // Hide code entry and submit button
            guardianCode.visibility = View.GONE
            btnSubmit.visibility = View.GONE
            // Fetch name/email from Firestore and details from Realtime DB
            loadAngelDetails(savedAngelUid)
        } else {
            // Set up submit button
        btnSubmit.setOnClickListener {
                val code = guardianCode.text.toString().trim()
            if (code.isEmpty()) {
                    guardianCode.error = "Please enter angel's code"
                    return@setOnClickListener
                }
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    verifyAngelCode(currentUser.uid, code)
                } else {
                    Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, GuardianLoginActivity::class.java))
                    finish()
                }
            }
        }

        // Set up logout button
        btnLogout.setOnClickListener {
            // Clear saved angel UID
            sharedPreferences.edit().remove(KEY_ANGEL_UID).apply()
            auth.signOut()
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun loadAngelDetails(angelUid: String) {
        angelUserId = angelUid
        // Fetch name/email from Firestore
        db.collection("users").document(angelUid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val angelName = (document.getString("firstName") ?: "") + " " + (document.getString("lastName") ?: "")
                    val angelEmail = document.getString("email") ?: ""
                    // Fetch details from Realtime DB
                    realtimeDb.reference.child("users").child(angelUid)
                        .get()
                        .addOnSuccessListener { dataSnapshot ->
                            if (dataSnapshot.exists()) {
                                displayAngelDetails(angelName, angelEmail, dataSnapshot)
                                angelDetailsScrollView.visibility = View.VISIBLE
                                listenForPanicAndScream(angelUid)
                            } else {
                                Toast.makeText(this, "Angel details not found", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Error fetching angel details: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(this, "Angel not found in Firestore", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching angel Firestore data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun verifyAngelCode(userId: String, code: String) {
        db.collection("users")
            .whereEqualTo("angelCode", code)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val document = documents.documents[0]
                    angelUserId = document.id
                    // Save angel UID in SharedPreferences
                    sharedPreferences.edit().putString(KEY_ANGEL_UID, angelUserId).apply()
                    // Hide code entry and submit button
                    guardianCode.visibility = View.GONE
                    btnSubmit.visibility = View.GONE
                    // Fetch name and email from Firestore
                    val angelName = (document.getString("firstName") ?: "") + " " + (document.getString("lastName") ?: "")
                    val angelEmail = document.getString("email") ?: ""
                    // Fetch angel details from Realtime Database
                    realtimeDb.reference.child("users").child(angelUserId!!)
                        .get()
                        .addOnSuccessListener { dataSnapshot ->
                            if (dataSnapshot.exists()) {
                                displayAngelDetails(angelName, angelEmail, dataSnapshot)
                                angelDetailsScrollView.visibility = View.VISIBLE
                                listenForPanicAndScream(angelUserId!!)
                                val guardianData = hashMapOf(
                                    "guardianId" to userId,
                                    "angelId" to angelUserId,
                                    "status" to "active",
                                    "createdAt" to System.currentTimeMillis()
                                )
                                db.collection("guardian_relationships")
                                    .add(guardianData)
                                    .addOnSuccessListener {
                                        Toast.makeText(this, "Guardian mode activated successfully!", Toast.LENGTH_SHORT).show()
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(this, "Error activating guardian mode: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                            } else {
                                Toast.makeText(this, "Angel details not found", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Error fetching angel details: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
            } else {
                    Toast.makeText(this, "Invalid angel code", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error verifying code: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun listenForPanicAndScream(angelId: String) {
        val ref = realtimeDb.reference.child("users").child(angelId)
        angelListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val isInPanic = snapshot.child("IsInPanic").getValue(Boolean::class.java) == true
                val isScream = snapshot.child("IsScream").getValue(Boolean::class.java) == true
                if (isInPanic) {
                    Toast.makeText(this@GuardianModeActivity, "Alert: Angel is in PANIC!", Toast.LENGTH_LONG).show()
                }
                if (isScream) {
                    Toast.makeText(this@GuardianModeActivity, "Alert: Angel is SCREAMING!", Toast.LENGTH_LONG).show()
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        ref.addValueEventListener(angelListener!!)
    }

    private fun displayAngelDetails(name: String, email: String, dataSnapshot: com.google.firebase.database.DataSnapshot) {
        angelDetailsScrollView.visibility = View.VISIBLE
        textViewAngelName.text = "Name: $name"
        textViewAngelEmail.text = "Email: $email"
        textViewIsInPanic.text = "IsInPanic: ${dataSnapshot.child("IsInPanic").value}"
        textViewIsInRedZone.text = "IsInRedZone: ${dataSnapshot.child("IsInRedZone").value}"
        textViewIsOnline.text = "IsOnline: ${dataSnapshot.child("IsOnline").value}"
        textViewIsScream.text = "IsScream: ${dataSnapshot.child("IsScream").value}"
        textViewLastLocationUpdate.text = "LastLocationUpdate: ${dataSnapshot.child("lastLocationUpdate").value}"
        val location = dataSnapshot.child("location")
        textViewLocationAccuracy.text = "Location Accuracy: ${location.child("accuracy").value}"
        textViewLocationBearing.text = "Location Bearing: ${location.child("bearing").value}"
        textViewLocationLatitude.text = "Location Latitude: ${location.child("latitude").value}"
        textViewLocationLongitude.text = "Location Longitude: ${location.child("longitude").value}"
        textViewLocationSpeed.text = "Location Speed: ${location.child("speed").value}"
    }

    override fun onBackPressed() {
        // When back is pressed, go to GuardianLoginActivity
        val intent = Intent(this, GuardianLoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove the listener if set
        angelUserId?.let {
            angelListener?.let { listener ->
                realtimeDb.reference.child("users").child(it).removeEventListener(listener)
            }
        }
    }
} 