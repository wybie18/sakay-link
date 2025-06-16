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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.example.sakaylink.app.utils.AuthManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.firestore.SetOptions

class PassengerProfileFragment : Fragment() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    // UI Elements
    private lateinit var profileImage: ShapeableImageView
    private lateinit var editProfileButton: FloatingActionButton
    private lateinit var logoutButton: Button
    private lateinit var locationToggle: SwitchMaterial
    private lateinit var locationStatusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var profileInfoOption: LinearLayout
    private lateinit var phoneNumberOption: LinearLayout
    private lateinit var changePasswordOption: LinearLayout

    private var selectedImageUri: Uri? = null

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (!isAdded) return@registerForActivityResult

        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                Glide.with(requireContext())
                    .load(uri)
                    .into(profileImage)
                saveProfile()
            }
        }
    }

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
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
    }

    private fun initializeViews(view: View) {
        profileImage = view.findViewById(R.id.profile_image)
        editProfileButton = view.findViewById(R.id.edit_photo)
        profileInfoOption = view.findViewById(R.id.profile_info_option)
        phoneNumberOption = view.findViewById(R.id.phone_number_option)
        changePasswordOption = view.findViewById(R.id.change_password_option)
        logoutButton = view.findViewById(R.id.logout_button)
        locationToggle = view.findViewById(R.id.location_toggle)
        locationStatusText = view.findViewById(R.id.location_status_text)
        progressBar = view.findViewById(R.id.progress_bar)
    }

    private fun setupClickListeners() {
        editProfileButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            imagePickerLauncher.launch(intent)
        }

        profileInfoOption.setOnClickListener {
            startActivity(Intent(requireContext(), PassengerProfileActivity::class.java))
        }

        phoneNumberOption.setOnClickListener {
            startActivity(Intent(requireContext(), PassengerUpdatePhoneActivity::class.java))
        }

        changePasswordOption.setOnClickListener {
            startActivity(Intent(requireContext(), PassengerUpdatePasswordActivity::class.java))
        }

        logoutButton.setOnClickListener {
            logout()
        }

        locationToggle.setOnCheckedChangeListener { _, isChecked ->
            updateLocationVisibility(isChecked)
        }
    }

    private fun loadUserProfile() {
        val currentUser = AuthManager.getCurrentUser() ?: return
        progressBar.visibility = View.VISIBLE

        firestore.collection("users")
            .document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                if (!isAdded || isDetached) return@addOnSuccessListener
                progressBar.visibility = View.GONE
                if (document.exists()) {
                    val profileUrl = document.getString("profileUrl") ?: ""

                    if (profileUrl.isNotEmpty()) {
                        Glide.with(requireContext())
                            .load(profileUrl)
                            .placeholder(R.drawable.ic_person)
                            .into(profileImage)
                    }
                }
            }
            .addOnFailureListener {
                if (isAdded) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Failed to load profile", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun loadLocationSettings() {
        val currentUser = AuthManager.getCurrentUser() ?: return

        firestore.collection("locations")
            .document("passengers")
            .collection("passengers")
            .document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                if (!isAdded || isDetached) return@addOnSuccessListener
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
                if (isAdded) {
                    locationToggle.isChecked = false
                    updateLocationStatusText(false)
                }
            }
    }

    private fun updateLocationStatusText(isVisible: Boolean) {
        if (!isAdded || context == null) return
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
        val currentUser = AuthManager.getCurrentUser() ?: return

        val locationData = hashMapOf(
            "isVisible" to isVisible,
            "updatedAt" to com.google.firebase.Timestamp.now()
        )

        firestore.collection("locations")
            .document("passengers")
            .collection("passengers")
            .document(currentUser.uid)
            .set(locationData, SetOptions.merge())
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                updateLocationStatusText(isVisible)
            }
            .addOnFailureListener {
                if (isAdded) {
                    locationToggle.isChecked = !isVisible
                    Toast.makeText(context, "Failed to update location setting", Toast.LENGTH_SHORT)
                        .show()
                }
            }
    }

    private fun saveProfile() {
        val currentUser = AuthManager.getCurrentUser() ?: return
        progressBar.visibility = View.VISIBLE
        uploadImage(currentUser.uid)
    }

    private fun uploadImage(uid: String) {
        val imageRef = storage.reference.child("profile_images/$uid.jpg")

        selectedImageUri?.let { uri ->
            imageRef.putFile(uri)
                .addOnSuccessListener {
                    imageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                        saveProfileToFirestore(uid, downloadUrl.toString())
                    }
                }
                .addOnFailureListener {
                    progressBar.visibility = View.GONE
                    Toast.makeText(context, "Failed to upload image", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun saveProfileToFirestore(uid: String, profileUrl: String) {
        val updates = hashMapOf<String, Any>()

        updates["profileUrl"] = profileUrl

        firestore.collection("users")
            .document(uid)
            .update(updates)
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                selectedImageUri = null
                Toast.makeText(context, "Profile picture updated successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Failed to update profile picture", Toast.LENGTH_SHORT).show()
            }
    }

    private fun logout() {
        AuthManager.signOut()
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}