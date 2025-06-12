package com.example.sakaylink

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*

class DriverSetupActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private lateinit var tvLicenseSelected : TextView
    private lateinit var tvBackgroundCheckSelected : TextView
    private lateinit var btnSelectLicense : Button
    private lateinit var btnSelectBackgroundCheck : Button
    private lateinit var btnSaveDriverInfo : Button
    private lateinit var btnSkipForNow : Button
    private lateinit var progressBar : ProgressBar
    private lateinit var etVehicleMake : EditText
    private lateinit var etVehicleModel : EditText
    private lateinit var etVehicleColor : EditText
    private lateinit var etPlateNumber : EditText
    private lateinit var etVehicleYear : EditText
    private lateinit var etLicenseNumber : EditText
    private lateinit var etLicenseExpiry : EditText


    private var driverLicenseUri: Uri? = null
    private var backgroundCheckUri: Uri? = null

    @SuppressLint("SetTextI18n")
    private val licensePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            driverLicenseUri = it
            tvLicenseSelected.text = "Driver License Selected ✓"
            tvLicenseSelected.setTextColor(getColor(R.color.success_color))
        }
    }

    @SuppressLint("SetTextI18n")
    private val backgroundCheckPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            backgroundCheckUri = it
            tvBackgroundCheckSelected.text = "Background Check Selected ✓"
            tvBackgroundCheckSelected.setTextColor(getColor(R.color.success_color))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_setup)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        tvLicenseSelected = findViewById(R.id.tvLicenseSelected)
        tvBackgroundCheckSelected = findViewById(R.id.tvBackgroundCheckSelected)
        btnSelectLicense = findViewById(R.id.btnSelectLicense)
        btnSelectBackgroundCheck = findViewById(R.id.btnSelectBackgroundCheck)
        btnSaveDriverInfo = findViewById(R.id.btnSaveDriverInfo)
        btnSkipForNow = findViewById(R.id.btnSkipForNow)
        progressBar = findViewById(R.id.progressBar)
        etVehicleMake = findViewById(R.id.etVehicleMake)
        etVehicleModel = findViewById(R.id.etVehicleModel)
        etVehicleColor = findViewById(R.id.etVehicleColor)
        etPlateNumber = findViewById(R.id.etPlateNumber)
        etVehicleYear = findViewById(R.id.etVehicleYear)
        etLicenseNumber = findViewById(R.id.etLicenseNumber)
        etLicenseExpiry = findViewById(R.id.etLicenseExpiry)

        setupClickListeners()
        setupDatePicker()
    }

    private fun setupClickListeners() {
        btnSelectLicense.setOnClickListener {
            licensePickerLauncher.launch("image/*")
        }

        btnSelectBackgroundCheck.setOnClickListener {
            backgroundCheckPickerLauncher.launch("*/*")
        }

        btnSaveDriverInfo.setOnClickListener {
            if (validateDriverInfo()) {
                saveDriverInformation()
            }
        }

        btnSkipForNow.setOnClickListener {
            // Skip to driver dashboard without completing setup
            startActivity(Intent(this, DriverDashboardActivity::class.java))
            finish()
        }
    }

    private fun setupDatePicker() {
        etLicenseExpiry.setOnClickListener {
            showDatePicker()
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val datePickerDialog = android.app.DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
                etLicenseExpiry.setText(dateFormat.format(calendar.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        // Set minimum date to today
        datePickerDialog.datePicker.minDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    private fun validateDriverInfo(): Boolean {
        val make = etVehicleMake.text.toString().trim()
        val model = etVehicleModel.text.toString().trim()
        val color = etVehicleColor.text.toString().trim()
        val plateNumber = etPlateNumber.text.toString().trim()
        val year = etVehicleYear.text.toString().trim()
        val licenseNumber = etLicenseNumber.text.toString().trim()
        val licenseExpiry = etLicenseExpiry.text.toString().trim()

        return when {
            make.isEmpty() -> {
                etVehicleMake.error = "Vehicle make is required"
                false
            }
            model.isEmpty() -> {
                etVehicleModel.error = "Vehicle model is required"
                false
            }
            color.isEmpty() -> {
                etVehicleColor.error = "Vehicle color is required"
                false
            }
            plateNumber.isEmpty() -> {
                etPlateNumber.error = "Plate number is required"
                false
            }
            year.isEmpty() -> {
                etVehicleYear.error = "Vehicle year is required"
                false
            }
            year.toIntOrNull() == null || year.toInt() < 1980 || year.toInt() > Calendar.getInstance().get(Calendar.YEAR) + 1 -> {
                etVehicleYear.error = "Please enter a valid year"
                false
            }
            licenseNumber.isEmpty() -> {
                etLicenseNumber.error = "License number is required"
                false
            }
            licenseExpiry.isEmpty() -> {
                etLicenseExpiry.error = "License expiry date is required"
                false
            }
            driverLicenseUri == null -> {
                Toast.makeText(this, "Please select your driver license image", Toast.LENGTH_SHORT).show()
                false
            }
            else -> true
        }
    }

    private fun saveDriverInformation() {
        val currentUser = auth.currentUser ?: return

        btnSaveDriverInfo.isEnabled = false
        btnSkipForNow.isEnabled = false
        progressBar.visibility = android.view.View.VISIBLE

        // Upload files first, then save driver data
        uploadDriverDocuments(currentUser.uid) { licenseUrl, backgroundCheckUrl ->
            updateDriverDocument(currentUser.uid, licenseUrl, backgroundCheckUrl)
        }
    }

    private fun uploadDriverDocuments(uid: String, callback: (String?, String?) -> Unit) {
        val storageRef = storage.reference.child("drivers/$uid")
        var licenseUrl: String? = null
        var backgroundCheckUrl: String? = null
        var uploadCount = 0
        val totalUploads = if (backgroundCheckUri != null) 2 else 1

        // Upload driver license
        driverLicenseUri?.let { uri ->
            val licenseRef = storageRef.child("driver_license.jpg")
            licenseRef.putFile(uri)
                .addOnSuccessListener {
                    licenseRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                        licenseUrl = downloadUrl.toString()
                        uploadCount++
                        if (uploadCount == totalUploads) {
                            callback(licenseUrl, backgroundCheckUrl)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    showError("Failed to upload driver license: ${e.message}")
                }
        }

        // Upload background check if selected
        backgroundCheckUri?.let { uri ->
            val backgroundRef = storageRef.child("background_check.pdf")
            backgroundRef.putFile(uri)
                .addOnSuccessListener {
                    backgroundRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                        backgroundCheckUrl = downloadUrl.toString()
                        uploadCount++
                        if (uploadCount == totalUploads) {
                            callback(licenseUrl, backgroundCheckUrl)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    showError("Failed to upload background check: ${e.message}")
                }
        } ?: run {
            // No background check to upload
            uploadCount++
            if (uploadCount == totalUploads) {
                callback(licenseUrl, backgroundCheckUrl)
            }
        }
    }

    private fun updateDriverDocument(uid: String, licenseUrl: String?, backgroundCheckUrl: String?) {
        val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
        val expiryDate = try {
            dateFormat.parse(etLicenseExpiry.text.toString())
        } catch (e: Exception) {
            null
        }

        val driverData = mapOf(
            "vehicleInfo" to mapOf(
                "make" to etVehicleMake.text.toString().trim(),
                "model" to etVehicleModel.text.toString().trim(),
                "color" to etVehicleColor.text.toString().trim(),
                "plateNumber" to etPlateNumber.text.toString().trim(),
                "year" to etVehicleYear.text.toString().toInt()
            ),
            "credentials" to mapOf(
                "driverLicenseUrl" to (licenseUrl ?: ""),
                "licenseNumber" to etLicenseNumber.text.toString().trim(),
                "licenseExpiry" to if (expiryDate != null) Timestamp(expiryDate) else null,
                "backgroundCheckUrl" to (backgroundCheckUrl ?: "")
            ),
            "isVerified" to false,
            "verifiedAt" to null
        )

        db.collection("drivers").document(uid)
            .update(driverData)
            .addOnSuccessListener {
                Toast.makeText(this, "Driver information saved successfully", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, DriverDashboardActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                showError("Failed to save driver information: ${e.message}")
            }
    }

    private fun showError(message: String) {
        btnSaveDriverInfo.isEnabled = true
        btnSkipForNow.isEnabled = true
        progressBar.visibility = android.view.View.GONE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}