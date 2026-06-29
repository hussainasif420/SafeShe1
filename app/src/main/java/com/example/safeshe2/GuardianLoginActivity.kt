package com.example.safeshe2

import android.content.Intent
import android.os.Bundle
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth

class GuardianLoginActivity : AppCompatActivity() {
    private lateinit var email: TextInputEditText
    private lateinit var password: TextInputEditText
    private lateinit var showPassword: CheckBox
    private lateinit var btnLogin: MaterialButton
    private lateinit var textViewSignUp: TextView

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guardian_login)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()

        // Initialize views
        email = findViewById(R.id.emailEditText)
        password = findViewById(R.id.passwordEditText)
        showPassword = findViewById(R.id.showPassword)
        btnLogin = findViewById(R.id.loginButton)
        textViewSignUp = findViewById(R.id.signUpText)

        // Set up click listeners
        btnLogin.setOnClickListener {
            val emailText = email.text.toString().trim()
            val passwordText = password.text.toString().trim()
            
            if (emailText.isEmpty() || passwordText.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(emailText, passwordText)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        if (user != null) {
                            if (user.isEmailVerified) {
                                // Email is verified, proceed with login
                                Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                                val intent = Intent(this, GuardianModeActivity::class.java)
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
                        Toast.makeText(
                            this,
                            "Login failed: ${task.exception?.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }

        textViewSignUp.setOnClickListener {
            // Navigate to GuardianSignUpActivity
            val intent = Intent(this, GuardianSignUpActivity::class.java)
            startActivity(intent)
        }

        showPassword.setOnCheckedChangeListener { _, isChecked ->
            password.inputType = if (isChecked) {
                android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Check if user is signed in
        val currentUser = auth.currentUser
        if (currentUser != null && currentUser.isEmailVerified) {
            val intent = Intent(this, GuardianModeActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
} 