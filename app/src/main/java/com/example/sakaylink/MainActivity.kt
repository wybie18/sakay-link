package com.example.sakaylink

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Check if user is already logged in
        checkUserAuth()
    }

    private fun checkUserAuth() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // User is logged in, redirect to appropriate dashboard
            redirectToDashboard()
        } else {
            // User is not logged in, show login screen
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun redirectToDashboard() {
        val currentUser = auth.currentUser
        currentUser?.let { user ->
            db.collection("users").document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val role = document.getString("role")
                        val intent = when (role) {
                            "passenger" -> Intent(this, PassengerDashboardActivity::class.java)
                            "driver" -> Intent(this, DriverDashboardActivity::class.java)
                            else -> Intent(this, LoginActivity::class.java)
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        // User document doesn't exist, redirect to login
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    }
                }
                .addOnFailureListener {
                    // Error occurred, redirect to login
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
        }
    }
}