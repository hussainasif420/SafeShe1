package com.example.safeshe2

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {
    private lateinit var email: TextInputEditText
    private lateinit var password: TextInputEditText
    private lateinit var showPassword: CheckBox
    private lateinit var forgotPassword: TextView
    private lateinit var btnLogin: Button
    private lateinit var btnSignUp: Button
    private lateinit var btnGuardianMode: Button
    private lateinit var btnAdminLogin: Button

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()

        // Initialize views
        email = findViewById(R.id.email)
        password = findViewById(R.id.password)
        showPassword = findViewById(R.id.show)
        forgotPassword = findViewById(R.id.forgotpassword)
        btnLogin = findViewById(R.id.btn_login)
        btnSignUp = findViewById(R.id.signup1)
        btnGuardianMode = findViewById(R.id.guardianModeButton)
        btnAdminLogin = findViewById(R.id.adminLogin)

        // Set up click listeners
        btnLogin.setOnClickListener {
            val emailText = email.text.toString()
            val passwordText = password.text.toString()
            
            if (emailText.isEmpty() || passwordText.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            } else {
                loginUser(emailText, passwordText)
            }
        }

        btnSignUp.setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }

        btnGuardianMode.setOnClickListener {
            val intent = Intent(this, GuardianLoginActivity::class.java)
            startActivity(intent)
        }

        btnAdminLogin.setOnClickListener {
            val intent = Intent(this, AdminLoginActivity::class.java)
            startActivity(intent)
        }

        forgotPassword.setOnClickListener {
            val emailText = email.text.toString()
            if (emailText.isEmpty()) {
                email.error = "Please enter your email"
                return@setOnClickListener
            }
            resetPassword(emailText)
        }

        showPassword.setOnCheckedChangeListener { _, isChecked ->
            password.inputType = if (isChecked) {
                android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }
    }

    private fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        if (user.isEmailVerified) {
                            // Email is verified, proceed with login
                            Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this, HomePage::class.java)
                            startActivity(intent)
                            finish()
                        } else {
                            // Email is not verified
                            auth.signOut()
                            Toast.makeText(
                                this,
                                "Please verify your email before logging in. Check your inbox for the verification link.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } else {
                    // Login failed
                    Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun resetPassword(email: String) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(
                        this,
                        "Password reset email sent. Please check your inbox.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val errorMessage = when (task.exception) {
                        is com.google.firebase.auth.FirebaseAuthInvalidUserException -> "No account found with this email"
                        is com.google.firebase.auth.FirebaseAuthException -> "Failed to send reset email: ${task.exception?.message}"
                        else -> "An error occurred. Please try again later"
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
    }
} 