package com.example.sakaylink

import android.app.Application
import com.example.sakaylink.app.CloudinaryConfig

class SakayLinkApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        CloudinaryConfig.initCloudinary(this)
    }
}