package com.example.safeshe2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AdminLoginActivity : AppCompatActivity() {
    private lateinit var adminEmail: TextInputEditText
    private lateinit var adminPassword: TextInputEditText
    private lateinit var btnAdminLogin: Button

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()
    private val TAG = "AdminLoginActivity"

    // Predefined list of admin emails
    private val adminEmails = listOf(
        "admin1@safeshe.com",
        "admin2@safeshe.com"
        // Add more admin emails as needed
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_login)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()

        adminEmail = findViewById(R.id.adminEmail)
        adminPassword = findViewById(R.id.adminPassword)
        btnAdminLogin = findViewById(R.id.btnAdminLogin)

        btnAdminLogin.setOnClickListener {
            val email = adminEmail.text.toString()
            val password = adminPassword.text.toString()

            if (email.isEmpty()) {
                adminEmail.error = "Email is required"
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                adminPassword.error = "Password is required"
                return@setOnClickListener
            }

            // First try to authenticate with Firebase Auth
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { authTask ->
                    if (authTask.isSuccessful) {
                        // After successful auth, check if user is admin
                        checkAdminStatus(email)
                    } else {
                        Toast.makeText(this, "Authentication failed: ${authTask.exception?.message}", Toast.LENGTH_SHORT).show()
                        Log.e(TAG, "Authentication failed", authTask.exception)
                    }
                }
        }
    }

    private fun checkAdminStatus(email: String) {
        Log.d(TAG, "Checking admin status for email: $email")
        
        db.collection("admins")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { documents ->
                Log.d(TAG, "Admin check successful. Found ${documents.size()} documents")
                if (!documents.isEmpty) {
                    // User is admin, proceed to dashboard
                    Toast.makeText(this, "Admin login successful!", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, AdminDashboardActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this, "You are not authorized as admin", Toast.LENGTH_SHORT).show()
                    auth.signOut() // Sign out since not an admin
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error checking admin status", exception)
                Toast.makeText(this, "Error checking admin status: ${exception.message}", Toast.LENGTH_SHORT).show()
                auth.signOut() // Sign out on error
            }
    }

    override fun onBackPressed() {
        // When back is pressed, go to LoginActivity instead of MainActivity
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
} 