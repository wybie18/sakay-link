package com.example.sakaylink

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.sakaylink.app.utils.AuthManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.*

class DriverLicenseActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private lateinit var licenseNumberEditText: EditText
    private lateinit var expiryDateEditText: EditText
    private lateinit var licenseImageView: ImageView
    private lateinit var uploadLicenseButton: Button
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var licenseImageLabel: TextView

    private var selectedLicenseUri: Uri? = null
    private var licenseExpiryDate: Date? = null

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedLicenseUri = uri
                Glide.with(this)
                    .load(uri)
                    .into(licenseImageView)
                licenseImageView.clearColorFilter()
                licenseImageLabel.text = "License image selected"
                licenseImageLabel.setTextColor(resources.getColor(R.color.success_color, null))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_license)

        val toolbar: MaterialToolbar = findViewById(R.id.topAppBar)
        setSupportActionBar(toolbar)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Driver License"
        }

        initializeFirebase()
        initializeViews()
        setupClickListeners()
        loadDriverCredentials()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun initializeFirebase() {
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
    }

    private fun initializeViews() {
        licenseNumberEditText = findViewById(R.id.license_number_edit_text)
        expiryDateEditText = findViewById(R.id.expiry_date_edit_text)
        licenseImageView = findViewById(R.id.license_image_view)
        uploadLicenseButton = findViewById(R.id.upload_license_button)
        saveButton = findViewById(R.id.save_button)
        cancelButton = findViewById(R.id.cancel_button)
        progressBar = findViewById(R.id.progress_bar)
        licenseImageLabel = findViewById(R.id.license_image_label)
    }

    private fun setupClickListeners() {
        cancelButton.setOnClickListener {
            finish()
        }

        saveButton.setOnClickListener {
            saveDriverCredentials()
        }

        uploadLicenseButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            imagePickerLauncher.launch(intent)
        }

        expiryDateEditText.setOnClickListener {
            showDatePicker()
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(selectedYear, selectedMonth, selectedDay)
                licenseExpiryDate = selectedCalendar.time

                val dateString = "${selectedMonth + 1}/$selectedDay/$selectedYear"
                expiryDateEditText.setText(dateString)
            },
            year, month, day
        )

        // Set minimum date to today
        datePickerDialog.datePicker.minDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    private fun loadDriverCredentials() {
        val currentUser = AuthManager.getCurrentUser() ?: return
        progressBar.visibility = View.VISIBLE

        firestore.collection("drivers")
            .document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                progressBar.visibility = View.GONE
                if (document.exists()) {
                    val credentials = document.get("credentials") as? Map<*, *>
                    credentials?.let {
                        val licenseNumber = it["licenseNumber"] as? String ?: ""
                        val licenseExpiry = it["licenseExpiry"] as? Timestamp
                        val driverLicenseUrl = it["driverLicenseUrl"] as? String ?: ""

                        licenseNumberEditText.setText(licenseNumber)

                        licenseExpiry?.let { timestamp ->
                            licenseExpiryDate = timestamp.toDate()
                            val calendar = Calendar.getInstance()
                            calendar.time = licenseExpiryDate!!
                            val dateString = "${calendar.get(Calendar.MONTH) + 1}/${calendar.get(Calendar.DAY_OF_MONTH)}/${calendar.get(Calendar.YEAR)}"
                            expiryDateEditText.setText(dateString)
                        }

                        if (driverLicenseUrl.isNotEmpty()) {
                            Glide.with(this)
                                .load(driverLicenseUrl)
                                .placeholder(R.drawable.image_24px)
                                .into(licenseImageView)
                            licenseImageLabel.text = "Current license image"
                            licenseImageLabel.setTextColor(resources.getColor(R.color.text_secondary, null))
                        }
                    }
                }
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to load driver credentials", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveDriverCredentials() {
        val currentUser = AuthManager.getCurrentUser() ?: return
        val licenseNumber = licenseNumberEditText.text.toString().trim()

        if (licenseNumber.isEmpty()) {
            licenseNumberEditText.error = "License number is required"
            return
        }

        if (licenseExpiryDate == null) {
            expiryDateEditText.error = "Expiry date is required"
            return
        }

        progressBar.visibility = View.VISIBLE

        if (selectedLicenseUri != null) {
            uploadLicenseImage(currentUser.uid, licenseNumber)
        } else {
            saveCredentialsToFirestore(currentUser.uid, licenseNumber, null)
        }
    }

    private fun uploadLicenseImage(uid: String, licenseNumber: String) {
        val imageRef = storage.reference.child("driver_licenses/$uid.jpg")

        selectedLicenseUri?.let { uri ->
            imageRef.putFile(uri)
                .addOnSuccessListener {
                    imageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                        saveCredentialsToFirestore(uid, licenseNumber, downloadUrl.toString())
                    }
                }
                .addOnFailureListener {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Failed to upload license image", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun saveCredentialsToFirestore(uid: String, licenseNumber: String, licenseUrl: String?) {
        val credentials = mutableMapOf<String, Any>(
            "licenseNumber" to licenseNumber,
            "licenseExpiry" to Timestamp(licenseExpiryDate!!)
        )

        licenseUrl?.let {
            credentials["driverLicenseUrl"] = it
        }

        val updates = mapOf(
            "credentials" to credentials
        )

        firestore.collection("drivers")
            .document(uid)
            .update(updates)
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Driver credentials updated successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to update driver credentials", Toast.LENGTH_SHORT).show()
            }
    }
}