package com.example.sakaylink

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.sakaylink.app.utils.AuthManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class DriverUpdatePhoneActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var phoneEditText: EditText
    private lateinit var cancelButton: Button
    private lateinit var saveButton: Button
    private lateinit var profileInfoOption: LinearLayout
    private lateinit var changePasswordOption: LinearLayout
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update_phone)

        val toolbar: MaterialToolbar = findViewById(R.id.topAppBar)
        setSupportActionBar(toolbar)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Phone Number"
        }

        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        phoneEditText = findViewById(R.id.phone_edit_text)
        cancelButton = findViewById(R.id.cancel_button)
        saveButton = findViewById(R.id.save_button)
        profileInfoOption = findViewById(R.id.profile_info_option)
        changePasswordOption = findViewById(R.id.change_password_option)
        progressBar = findViewById(R.id.progress_bar)

        setupClickListeners()
        loadUserProfile()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupClickListeners() {
        cancelButton.setOnClickListener {
            finish()
        }

        saveButton.setOnClickListener {
            savePhone()
        }

        profileInfoOption.setOnClickListener {
            startActivity(Intent(this, PassengerProfileActivity::class.java))
        }

        changePasswordOption.setOnClickListener {
            startActivity(Intent(this, PassengerUpdatePasswordActivity::class.java))
        }

    }

    private fun loadUserProfile() {
        val currentUser = AuthManager.getCurrentUser() ?: return
        progressBar.visibility = View.VISIBLE

        firestore.collection("users")
            .document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                progressBar.visibility = View.GONE
                if (document.exists()) {
                    val phone = document.getString("phoneNumber") ?: ""

                    phoneEditText.setText(phone)
                }
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show()
            }
    }

    private fun savePhone() {
        val currentUser = AuthManager.getCurrentUser() ?: return
        val phone = phoneEditText.text.toString().trim()

        progressBar.visibility = View.VISIBLE

        savePhoneToFirestore(currentUser.uid, phone)
    }

    private fun savePhoneToFirestore(uid: String, phone: String) {
        val updates = hashMapOf<String, Any>(
            "phoneNumber" to phone,
        )

        firestore.collection("users")
            .document(uid)
            .update(updates)
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

}