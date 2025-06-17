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
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class PassengerUpdatePasswordActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var oldPasswordEditText: EditText
    private lateinit var newPasswordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var cancelButton: Button
    private lateinit var saveButton: Button
    private lateinit var profileInfoOption: LinearLayout
    private lateinit var phoneNumberOption: LinearLayout
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update_password)

        val toolbar: MaterialToolbar = findViewById(R.id.topAppBar)
        setSupportActionBar(toolbar)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Change Password"
        }

        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        oldPasswordEditText = findViewById(R.id.old_password_text)
        newPasswordEditText = findViewById(R.id.new_password_text)
        confirmPasswordEditText = findViewById(R.id.confirm_password_text)
        cancelButton = findViewById(R.id.cancel_button)
        saveButton = findViewById(R.id.save_button)
        profileInfoOption = findViewById(R.id.profile_info_option)
        phoneNumberOption = findViewById(R.id.phone_number_option)
        progressBar = findViewById(R.id.progress_bar)

        setupClickListeners()
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
            savePassword()
        }

        profileInfoOption.setOnClickListener {
            startActivity(Intent(this, PassengerProfileActivity::class.java))
        }

        phoneNumberOption.setOnClickListener {
            startActivity(Intent(this, PassengerUpdatePhoneActivity::class.java))
        }

    }

    private fun savePassword() {
        val currentUser = AuthManager.getCurrentUser() ?: return

        val oldPassword = oldPasswordEditText.text.toString().trim()
        val newPassword = newPasswordEditText.text.toString().trim()
        val confirmPassword = confirmPasswordEditText.text.toString().trim()

        if (oldPassword.isEmpty()) {
            oldPasswordEditText.error = "Old password is required"
            return
        }
        if (newPassword.isEmpty()) {
            newPasswordEditText.error = "New password is required"
            return
        }
        if (newPassword.length < 6) {
            newPasswordEditText.error = "Password must be at least 6 characters"
            return
        }
        if (confirmPassword.isEmpty()) {
            confirmPasswordEditText.error = "Confirm password is required"
            return
        }
        if (newPassword != confirmPassword) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE

        updatePasswordToFirestore(currentUser, oldPassword, newPassword)
    }

    private fun updatePasswordToFirestore(currentUser: FirebaseUser, oldPassword: String, newPassword: String) {
        val credential = EmailAuthProvider.getCredential(currentUser.email!!, oldPassword)
        currentUser.reauthenticate(credential)
            .addOnCompleteListener { reauthTask ->
                if (reauthTask.isSuccessful) {
                    currentUser.updatePassword(newPassword)
                        .addOnCompleteListener { updateTask ->
                            progressBar.visibility = View.GONE
                            if (updateTask.isSuccessful) {
                                Toast.makeText(
                                    this,
                                    "Password updated successfully",
                                    Toast.LENGTH_SHORT
                                ).show()
                                finish()
                            } else {
                                Toast.makeText(
                                    this,
                                    "Update failed: ${updateTask.exception?.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                } else {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        this,
                        "Authentication failed: ${reauthTask.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

}