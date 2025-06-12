package com.example.sakaylink

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp

class RegisterActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var etName : EditText
    private lateinit var etEmail: EditText
    private lateinit var etPhoneNumber: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var rgRole: RadioGroup
    private lateinit var btnRegister: Button
    private lateinit var tvLogin: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupClickListeners()
    }

    private fun setupClickListeners() {
        btnRegister.setOnClickListener {
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val phoneNumber = etPhoneNumber.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            val selectedRoleId = rgRole.checkedRadioButtonId
            val selectedRole = if (selectedRoleId != -1) {
                findViewById<RadioButton>(selectedRoleId).text.toString().lowercase()
            } else ""

            if (validateInput(name, email, phoneNumber, password, confirmPassword, selectedRole)) {
                registerUser(name, email, phoneNumber, password, selectedRole)
            }
        }

        tvLogin.setOnClickListener {
            finish()
        }
    }

    private fun validateInput(
        name: String,
        email: String,
        phoneNumber: String,
        password: String,
        confirmPassword: String,
        role: String
    ): Boolean {
        return when {
            name.isEmpty() -> {
                etName.error = "Name is required"
                false
            }
            email.isEmpty() -> {
                etEmail.error = "Email is required"
                false
            }
            phoneNumber.isEmpty() -> {
                etPhoneNumber.error = "Phone number is required"
                false
            }
            password.isEmpty() -> {
                etPassword.error = "Password is required"
                false
            }
            password.length < 6 -> {
                etPassword.error = "Password must be at least 6 characters"
                false
            }
            confirmPassword.isEmpty() -> {
                etConfirmPassword.error = "Please confirm your password"
                false
            }
            password != confirmPassword -> {
                etConfirmPassword.error = "Passwords do not match"
                false
            }
            role.isEmpty() -> {
                Toast.makeText(this, "Please select a role", Toast.LENGTH_SHORT).show()
                false
            }
            else -> true
        }
    }

    private fun registerUser(name: String, email: String, phoneNumber: String, password: String, role: String) {
        btnRegister.isEnabled = false
        progressBar.visibility = android.view.View.VISIBLE

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.let {
                        createUserDocument(it.uid, name, email, phoneNumber, role)
                    }
                } else {
                    btnRegister.isEnabled = true
                    progressBar.visibility = android.view.View.GONE
                    Toast.makeText(this, "Registration failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun createUserDocument(uid: String, name: String, email: String, phoneNumber: String, role: String) {
        val userData = hashMapOf(
            "name" to name,
            "email" to email,
            "role" to role,
            "profileUrl" to "",
            "phoneNumber" to phoneNumber,
            "createdAt" to Timestamp.now()
        )

        db.collection("users").document(uid)
            .set(userData)
            .addOnSuccessListener {
                if (role == "driver") {
                    createDriverDocument(uid)
                } else {
                    finishRegistration(role)
                }
            }
            .addOnFailureListener { e ->
                btnRegister.isEnabled = true
                progressBar.visibility = android.view.View.GONE
                Toast.makeText(this, "Error creating user profile: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun createDriverDocument(uid: String) {
        val driverData = hashMapOf(
            "vehicleInfo" to hashMapOf(
                "make" to "",
                "model" to "",
                "color" to "",
                "plateNumber" to "",
                "year" to 0
            ),
            "credentials" to hashMapOf(
                "driverLicenseUrl" to "",
                "licenseNumber" to "",
                "licenseExpiry" to null,
                "backgroundCheckUrl" to ""
            ),
            "isVerified" to false,
            "verifiedAt" to null
        )

        db.collection("drivers").document(uid)
            .set(driverData)
            .addOnSuccessListener {
                finishRegistration("driver")
            }
            .addOnFailureListener { e ->
                btnRegister.isEnabled = true
                progressBar.visibility = android.view.View.GONE
                Toast.makeText(this, "Error creating driver profile: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun finishRegistration(role: String) {
        btnRegister.isEnabled = true
        progressBar.visibility = android.view.View.GONE

        Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show()

        val intent = when (role) {
            "passenger" -> Intent(this, PassengerDashboardActivity::class.java)
            "driver" -> Intent(this, DriverSetupActivity::class.java) // For completing driver info
            else -> Intent(this, LoginActivity::class.java)
        }

        startActivity(intent)
        finish()
    }
}