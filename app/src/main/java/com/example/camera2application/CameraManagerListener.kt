package com.example.camera2application

import android.net.Uri

interface CameraManagerListener {
    fun onPictureTaken(uri: Uri)
    fun onError(error: String)
}