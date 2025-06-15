package com.example.sakaylink

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class PassengerProfileFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    // UI Elements
//    private lateinit var profileImage: CircleImageView
//    private lateinit var editProfileButton: ImageButton
    private lateinit var nameEditText: EditText
    private lateinit var emailEditText: EditText
    private lateinit var phoneEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var logoutButton: Button
    private lateinit var locationToggle: SwitchMaterial
    private lateinit var locationStatusText: TextView
    private lateinit var progressBar: ProgressBar

    private var selectedImageUri: Uri? = null
    private var isEditMode = false

//    private val imagePickerLauncher = registerForActivityResult(
//        ActivityResultContracts.StartActivityForResult()
//    ) { result ->
//        if (result.resultCode == Activity.RESULT_OK) {
//            result.data?.data?.let { uri ->
//                selectedImageUri = uri
//                Glide.with(requireContext())
//                    .load(uri)
//                    .into(profileImage)
//            }
//        }
//    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_passenger_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeFirebase()
        initializeViews(view)
        setupClickListeners()
        loadUserProfile()
        loadLocationSettings()
    }

    private fun initializeFirebase() {
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
    }

    private fun initializeViews(view: View) {
//        profileImage = view.findViewById(R.id.profile_image)
//        editProfileButton = view.findViewById(R.id.edit_profile_button)
        nameEditText = view.findViewById(R.id.name_edit_text)
        emailEditText = view.findViewById(R.id.email_edit_text)
        phoneEditText = view.findViewById(R.id.phone_edit_text)
        saveButton = view.findViewById(R.id.save_button)
        logoutButton = view.findViewById(R.id.logout_button)
        locationToggle = view.findViewById(R.id.location_toggle)
        locationStatusText = view.findViewById(R.id.location_status_text)
        progressBar = view.findViewById(R.id.progress_bar)

        // Initially disable editing
        setEditMode(false)
    }

    private fun setupClickListeners() {
//        editProfileButton.setOnClickListener {
//            if (!isEditMode) {
//                setEditMode(true)
//            } else {
//                // Open image picker
//                val intent = Intent(Intent.ACTION_PICK)
//                intent.type = "image/*"
//                imagePickerLauncher.launch(intent)
//            }
//        }

        saveButton.setOnClickListener {
            saveProfile()
        }

        logoutButton.setOnClickListener {
            logout()
        }

        locationToggle.setOnCheckedChangeListener { _, isChecked ->
            updateLocationVisibility(isChecked)
        }
    }

    private fun setEditMode(enabled: Boolean) {
        isEditMode = enabled
        nameEditText.isEnabled = enabled
        phoneEditText.isEnabled = enabled
        saveButton.visibility = if (enabled) View.VISIBLE else View.GONE
//        editProfileButton.setImageResource(
//            if (enabled) R.drawable.ic_camera else R.drawable.ic_edit
//        )
    }

    private fun loadUserProfile() {
        val currentUser = auth.currentUser ?: return
        progressBar.visibility = View.VISIBLE

        firestore.collection("users")
            .document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                progressBar.visibility = View.GONE
                if (document.exists()) {
                    val name = document.getString("name") ?: ""
                    val email = document.getString("email") ?: ""
                    val phone = document.getString("phoneNumber") ?: ""
                    val profileUrl = document.getString("profileUrl") ?: ""

                    nameEditText.setText(name)
                    emailEditText.setText(email)
                    phoneEditText.setText(phone)

//                    if (profileUrl.isNotEmpty()) {
//                        Glide.with(requireContext())
//                            .load(profileUrl)
//                            .placeholder(R.drawable.ic_person)
//                            .into(profileImage)
//                    }
                }
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Failed to load profile", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadLocationSettings() {
        val currentUser = auth.currentUser ?: return

        firestore.collection("locations")
            .document("passengers")
            .collection("passengers")
            .document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val isVisible = document.getBoolean("isVisible") ?: false
                    locationToggle.isChecked = isVisible
                    updateLocationStatusText(isVisible)
                } else {
                    locationToggle.isChecked = false
                    updateLocationStatusText(false)
                }
            }
            .addOnFailureListener {
                locationToggle.isChecked = false
                updateLocationStatusText(false)
            }
    }

    private fun updateLocationStatusText(isVisible: Boolean) {
        locationStatusText.text = if (isVisible) {
            "Your location is visible to drivers"
        } else {
            "Your location is hidden from drivers"
        }
        locationStatusText.setTextColor(
            if (isVisible)
                resources.getColor(R.color.success_color, null)
            else
                resources.getColor(R.color.text_secondary, null)
        )
    }

    private fun updateLocationVisibility(isVisible: Boolean) {
        val currentUser = auth.currentUser ?: return

        val locationData = hashMapOf(
            "isVisible" to isVisible,
            "updatedAt" to com.google.firebase.Timestamp.now()
        )

        firestore.collection("locations")
            .document("passengers")
            .collection("passengers")
            .document(currentUser.uid)
            .update(locationData as Map<String, Any>)
            .addOnSuccessListener {
                updateLocationStatusText(isVisible)
                Toast.makeText(
                    context,
                    if (isVisible) "Location is now visible" else "Location is now hidden",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener {
                locationToggle.isChecked = !isVisible
                Toast.makeText(context, "Failed to update location setting", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveProfile() {
        val currentUser = auth.currentUser ?: return
        val name = nameEditText.text.toString().trim()
        val phone = phoneEditText.text.toString().trim()

        if (name.isEmpty()) {
            nameEditText.error = "Name is required"
            return
        }

        progressBar.visibility = View.VISIBLE

        if (selectedImageUri != null) {
            uploadImageAndSaveProfile(currentUser.uid, name, phone)
        } else {
            saveProfileToFirestore(currentUser.uid, name, phone, null)
        }
    }

    private fun uploadImageAndSaveProfile(uid: String, name: String, phone: String) {
        val imageRef = storage.reference.child("profile_images/$uid.jpg")

        selectedImageUri?.let { uri ->
            imageRef.putFile(uri)
                .addOnSuccessListener {
                    imageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                        saveProfileToFirestore(uid, name, phone, downloadUrl.toString())
                    }
                }
                .addOnFailureListener {
                    progressBar.visibility = View.GONE
                    Toast.makeText(context, "Failed to upload image", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun saveProfileToFirestore(uid: String, name: String, phone: String, profileUrl: String?) {
        val updates = hashMapOf<String, Any>(
            "name" to name,
            "phoneNumber" to phone
        )

        if (profileUrl != null) {
            updates["profileUrl"] = profileUrl
        }

        firestore.collection("users")
            .document(uid)
            .update(updates)
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                setEditMode(false)
                selectedImageUri = null
                Toast.makeText(context, "Profile updated successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Failed to update profile", Toast.LENGTH_SHORT).show()
            }
    }

    private fun logout() {
        auth.signOut()
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}