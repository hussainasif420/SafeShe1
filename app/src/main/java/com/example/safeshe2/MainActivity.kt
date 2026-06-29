package com.example.safeshe2

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private val SPLASH_DELAY = 3000L // 3 seconds
    private var handler: Handler? = null
    private var navigationRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        
        // Create handler and runnable
        handler = Handler(Looper.getMainLooper())
        navigationRunnable = Runnable {
            checkUserAndNavigate()
        }
        
        // Start navigation after delay
        handler?.postDelayed(navigationRunnable!!, SPLASH_DELAY)
    }

    private fun checkUserAndNavigate() {
        val currentUser = auth.currentUser
        
        if (currentUser != null && currentUser.isEmailVerified) {
            // User is signed in and email is verified, go to HomePage
            val intent = Intent(this, HomePage::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } else {
            // User is not signed in or email is not verified, go to LoginActivity
            auth.signOut() // Ensure user is signed out
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check authentication state when activity resumes
        if (handler != null && navigationRunnable != null) {
            handler?.removeCallbacks(navigationRunnable!!)
            handler?.postDelayed(navigationRunnable!!, SPLASH_DELAY)
        }
    }

    override fun onPause() {
        super.onPause()
        // Remove callbacks when activity is paused
        if (handler != null && navigationRunnable != null) {
            handler?.removeCallbacks(navigationRunnable!!)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up handler and runnable
        if (handler != null && navigationRunnable != null) {
            handler?.removeCallbacks(navigationRunnable!!)
        }
        handler = null
        navigationRunnable = null
    }

    override fun onRestart() {
        super.onRestart()
        // Handle app restart
        if (handler != null && navigationRunnable != null) {
            handler?.removeCallbacks(navigationRunnable!!)
            handler?.postDelayed(navigationRunnable!!, SPLASH_DELAY)
        }
    }
} 