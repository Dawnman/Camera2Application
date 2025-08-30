package com.example.camera2application.mvi.model.data.repository

import android.net.Uri

interface CameraManagerListener {
    fun onPictureTaken(uri: Uri)
    fun onError(error: String)
}