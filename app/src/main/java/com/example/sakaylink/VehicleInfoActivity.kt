package com.example.sakaylink

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.sakaylink.app.utils.AuthManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.firestore.FirebaseFirestore

class VehicleInfoActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore

    private lateinit var makeEditText: EditText
    private lateinit var modelEditText: EditText
    private lateinit var colorEditText: EditText
    private lateinit var plateNumberEditText: EditText
    private lateinit var yearEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vehicle_info)

        val toolbar: MaterialToolbar = findViewById(R.id.topAppBar)
        setSupportActionBar(toolbar)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Vehicle Information"
        }

        initializeFirebase()
        initializeViews()
        setupClickListeners()
        loadVehicleInfo()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun initializeFirebase() {
        firestore = FirebaseFirestore.getInstance()
    }

    private fun initializeViews() {
        makeEditText = findViewById(R.id.make_edit_text)
        modelEditText = findViewById(R.id.model_edit_text)
        colorEditText = findViewById(R.id.color_edit_text)
        plateNumberEditText = findViewById(R.id.plate_number_edit_text)
        yearEditText = findViewById(R.id.year_edit_text)
        saveButton = findViewById(R.id.save_button)
        cancelButton = findViewById(R.id.cancel_button)
        progressBar = findViewById(R.id.progress_bar)
    }

    private fun setupClickListeners() {
        cancelButton.setOnClickListener {
            finish()
        }

        saveButton.setOnClickListener {
            saveVehicleInfo()
        }
    }

    private fun loadVehicleInfo() {
        val currentUser = AuthManager.getCurrentUser() ?: return
        progressBar.visibility = View.VISIBLE

        firestore.collection("drivers")
            .document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                progressBar.visibility = View.GONE
                if (document.exists()) {
                    val vehicleInfo = document.get("vehicleInfo") as? Map<*, *>
                    vehicleInfo?.let {
                        val make = it["make"] as? String ?: ""
                        val model = it["model"] as? String ?: ""
                        val color = it["color"] as? String ?: ""
                        val plateNumber = it["plateNumber"] as? String ?: ""
                        val year = it["year"] as? Long ?: 0

                        makeEditText.setText(make)
                        modelEditText.setText(model)
                        colorEditText.setText(color)
                        plateNumberEditText.setText(plateNumber)
                        if (year > 0) {
                            yearEditText.setText(year.toString())
                        }
                    }
                }
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to load vehicle information", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveVehicleInfo() {
        val currentUser = AuthManager.getCurrentUser() ?: return

        val make = makeEditText.text.toString().trim()
        val model = modelEditText.text.toString().trim()
        val color = colorEditText.text.toString().trim()
        val plateNumber = plateNumberEditText.text.toString().trim()
        val yearText = yearEditText.text.toString().trim()

        // Validation
        if (make.isEmpty()) {
            makeEditText.error = "Vehicle make is required"
            return
        }

        if (model.isEmpty()) {
            modelEditText.error = "Vehicle model is required"
            return
        }

        if (color.isEmpty()) {
            colorEditText.error = "Vehicle color is required"
            return
        }

        if (plateNumber.isEmpty()) {
            plateNumberEditText.error = "Plate number is required"
            return
        }

        if (yearText.isEmpty()) {
            yearEditText.error = "Year is required"
            return
        }

        val year = try {
            yearText.toInt()
        } catch (e: NumberFormatException) {
            yearEditText.error = "Please enter a valid year"
            return
        }

        if (year < 1900 || year > 2030) {
            yearEditText.error = "Please enter a valid year between 1900 and 2030"
            return
        }

        progressBar.visibility = View.VISIBLE
        saveVehicleInfoToFirestore(currentUser.uid, make, model, color, plateNumber, year)
    }

    private fun saveVehicleInfoToFirestore(
        uid: String,
        make: String,
        model: String,
        color: String,
        plateNumber: String,
        year: Int
    ) {
        val vehicleInfo = mapOf(
            "make" to make,
            "model" to model,
            "color" to color,
            "plateNumber" to plateNumber,
            "year" to year
        )

        val updates = mapOf(
            "vehicleInfo" to vehicleInfo
        )

        firestore.collection("drivers")
            .document(uid)
            .update(updates)
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Vehicle information updated successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to update vehicle information", Toast.LENGTH_SHORT).show()
            }
    }
}