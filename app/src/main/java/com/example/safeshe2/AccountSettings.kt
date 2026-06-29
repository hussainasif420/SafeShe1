package com.example.safeshe2

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AccountSettings : AppCompatActivity() {
    private lateinit var emergencyContact1Name: TextInputEditText
    private lateinit var emergencyContact1: TextInputEditText
    private lateinit var emergencyContact2Name: TextInputEditText
    private lateinit var emergencyContact2: TextInputEditText
    private lateinit var btnSave: MaterialButton

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_settings)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize views
        emergencyContact1Name = findViewById(R.id.emergencyContact1Name)
        emergencyContact1 = findViewById(R.id.emergencyContact1)
        emergencyContact2Name = findViewById(R.id.emergencyContact2Name)
        emergencyContact2 = findViewById(R.id.emergencyContact2)
        btnSave = findViewById(R.id.btnSave)

        // Load existing emergency contacts
        loadEmergencyContacts()

        btnSave.setOnClickListener {
            saveEmergencyContacts()
        }
    }

    private fun loadEmergencyContacts() {
        val currentUser = auth.currentUser
        currentUser?.let { user ->
            db.collection("users")
                .document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val contact1Name = document.getString("emergencyContact1Name")
                        val contact1 = document.getString("emergencyContact1")
                        val contact2Name = document.getString("emergencyContact2Name")
                        val contact2 = document.getString("emergencyContact2")

                        contact1Name?.let { emergencyContact1Name.setText(it) }
                        contact1?.let { emergencyContact1.setText(it) }
                        contact2Name?.let { emergencyContact2Name.setText(it) }
                        contact2?.let { emergencyContact2.setText(it) }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error loading contacts: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun saveEmergencyContacts() {
        val contact1Name = emergencyContact1Name.text.toString()
        val contact1 = emergencyContact1.text.toString()
        val contact2Name = emergencyContact2Name.text.toString()
        val contact2 = emergencyContact2.text.toString()

        if (contact1Name.isEmpty()) {
            emergencyContact1Name.error = "Contact name is required"
            return
        }

        if (contact1.isEmpty()) {
            emergencyContact1.error = "Phone number is required"
            return
        }

        val currentUser = auth.currentUser
        currentUser?.let { user ->
            val userData = hashMapOf(
                "emergencyContact1Name" to contact1Name,
                "emergencyContact1" to contact1,
                "emergencyContact2Name" to contact2Name,
                "emergencyContact2" to contact2
            )

            db.collection("users")
                .document(user.uid)
                .update(userData as Map<String, Any>)
                .addOnSuccessListener {
                    Toast.makeText(this, "Emergency contacts saved successfully", Toast.LENGTH_SHORT).show()
                    loadEmergencyContacts()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error saving contacts: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
} 