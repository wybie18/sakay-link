package com.example.sakaylink.app.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object FileUtils {

    /**
     * Converts a content URI to a temporary file that can be uploaded
     */
    fun getFileFromUri(context: Context, uri: Uri): File? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val fileName = getFileName(context, uri)
            val tempFile = File(context.cacheDir, fileName)

            inputStream?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Gets the original filename from the URI
     */
    private fun getFileName(context: Context, uri: Uri): String {
        var fileName = "temp_image_${System.currentTimeMillis()}.jpg"

        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = it.getString(nameIndex) ?: fileName
                }
            }
        }

        return fileName
    }

    /**
     * Clean up temporary files
     */
    fun cleanupTempFile(file: File?) {
        try {
            file?.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}