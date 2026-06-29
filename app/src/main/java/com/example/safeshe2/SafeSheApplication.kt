package com.example.safeshe2

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp

class SafeSheApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        
        // Start the panic notification service
        ContextCompat.startForegroundService(
            this,
            Intent(this, PanicNotificationService::class.java)
        )
    }
} 