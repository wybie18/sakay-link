package com.example.sakaylink.app

import android.content.Context
import android.net.Uri
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.sakaylink.app.utils.FileUtils

object CloudinaryConfig {
    private const val CLOUD_NAME = "dt8e0x6wq"
    private const val UPLOAD_PRESET = "sakaylink_app_preset"

    fun initCloudinary(context: Context) {
        val config = HashMap<String, String>()
        config["cloud_name"] = CLOUD_NAME

        MediaManager.init(context, config)
    }

    fun uploadImageUnsigned(
        context: Context,
        imageUri: Uri,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
        onProgress: (Int) -> Unit = {}
    ) {
        // Convert URI to file first
        val tempFile = FileUtils.getFileFromUri(context, imageUri)

        if (tempFile == null) {
            onError("Failed to process image file")
            return
        }

        MediaManager.get().upload(tempFile.absolutePath)
            .unsigned(UPLOAD_PRESET)
            .option("folder", "sakaylink_app")
            .option("public_id", "sakaylink_${System.currentTimeMillis()}")
            .callback(object : UploadCallback {
                override fun onStart(requestId: String) {
                    // Upload started
                }

                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                    val progress = ((bytes * 100) / totalBytes).toInt()
                    onProgress(progress)
                }

                override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                    val imageUrl = resultData["secure_url"] as? String
                    if (imageUrl != null) {
                        onSuccess(imageUrl)
                    } else {
                        onError("Failed to get image URL")
                    }

                    // Clean up temp file
                    FileUtils.cleanupTempFile(tempFile)
                }

                override fun onError(requestId: String, error: ErrorInfo) {
                    onError("Upload failed: ${error.description}")

                    // Clean up temp file
                    FileUtils.cleanupTempFile(tempFile)
                }

                override fun onReschedule(requestId: String, error: ErrorInfo) {
                    // Upload rescheduled
                }
            })
            .dispatch()
    }
}