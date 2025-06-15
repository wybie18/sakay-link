package com.example.sakaylink

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.example.sakaylink.app.utils.AuthManager

class DriverProfileFragment : Fragment() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private lateinit var logoutButton: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_driver_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeFirebase()
        initializeViews(view)
        setupClickListeners()
//        loadUserProfile()
//        loadLocationSettings()
    }

    private fun initializeFirebase() {
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
    }

    private fun initializeViews(view: View) {
        logoutButton = view.findViewById(R.id.logout_button)
        progressBar = view.findViewById(R.id.progress_bar)
    }

    private fun setupClickListeners() {
        logoutButton.setOnClickListener {
            logout()
        }
    }

    private fun logout() {
        AuthManager.signOut()
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}