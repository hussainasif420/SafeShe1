package com.example.safeshe2

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class GuardianSignUpActivity : AppCompatActivity() {
    private lateinit var firstName: TextInputEditText
    private lateinit var lastName: TextInputEditText
    private lateinit var email: TextInputEditText
    private lateinit var password: TextInputEditText
    private lateinit var confirmPassword: TextInputEditText
    private lateinit var btnSignUp: Button
    private lateinit var textViewLogin: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guardian_signup)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize views
        firstName = findViewById(R.id.firstName)
        lastName = findViewById(R.id.lastName)
        email = findViewById(R.id.email)
        password = findViewById(R.id.password)
        confirmPassword = findViewById(R.id.confirmPassword)
        btnSignUp = findViewById(R.id.btnSignUp)
        textViewLogin = findViewById(R.id.textViewLogin)

        // Set up click listeners
        btnSignUp.setOnClickListener {
            if (validateInputs()) {
                signUpGuardian()
            }
        }

        textViewLogin.setOnClickListener {
            val intent = Intent(this, GuardianLoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun signUpGuardian() {
        val emailText = email.text.toString()
        val passwordText = password.text.toString()
        val firstNameText = firstName.text.toString()
        val lastNameText = lastName.text.toString()

        auth.createUserWithEmailAndPassword(emailText, passwordText)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Send verification email
                    val user = auth.currentUser
                    user?.sendEmailVerification()
                        ?.addOnCompleteListener { verificationTask ->
                            if (verificationTask.isSuccessful) {
                                // Sign out the user until they verify their email
                                auth.signOut()
                                Toast.makeText(
                                    this,
                                    "Verification email sent. Please verify your email before logging in.",
                                    Toast.LENGTH_LONG
                                ).show()
                                
                                // Save guardian data to Firestore
                                val guardianData = hashMapOf(
                                    "uid" to user.uid,
                                    "firstName" to firstNameText,
                                    "lastName" to lastNameText,
                                    "email" to emailText,
                                    "createdAt" to System.currentTimeMillis(),
                                    "isEmailVerified" to false,
                                    "userType" to "guardian",
                                    "status" to "active"
                                )

                                // Save to guardians collection with the user's UID as document ID
                                db.collection("guardians")
                                    .document(user.uid)
                                    .set(guardianData)
                                    .addOnSuccessListener {
                                        Toast.makeText(this, "Account created! Please verify your email to login.", Toast.LENGTH_LONG).show()
                                        val intent = Intent(this, GuardianLoginActivity::class.java)
                                        startActivity(intent)
                                        finish()
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(this, "Error saving guardian data: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                            } else {
                                Toast.makeText(this, "Failed to send verification email: ${verificationTask.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                } else {
                    // Sign up failed
                    Toast.makeText(this, "Sign up failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun validateInputs(): Boolean {
        val firstNameText = firstName.text.toString()
        val lastNameText = lastName.text.toString()
        val emailText = email.text.toString()
        val passwordText = password.text.toString()
        val confirmPasswordText = confirmPassword.text.toString()

        if (firstNameText.isEmpty()) {
            firstName.error = "First name is required"
            return false
        }

        if (lastNameText.isEmpty()) {
            lastName.error = "Last name is required"
            return false
        }

        if (emailText.isEmpty()) {
            email.error = "Email is required"
            return false
        }

        if (passwordText.isEmpty()) {
            password.error = "Password is required"
            return false
        }

        if (confirmPasswordText.isEmpty()) {
            confirmPassword.error = "Please confirm your password"
            return false
        }

        if (passwordText != confirmPasswordText) {
            confirmPassword.error = "Passwords do not match"
            return false
        }

        return true
    }
} 