package com.example.camera2application

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.camera2application.mvi.CameraViewModel

class CameraViewModelFactory(private val cameraManager: CameraManager) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CameraViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CameraViewModel(cameraManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}