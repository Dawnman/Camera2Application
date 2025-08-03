package com.example.camera2application.mvi

data class CameraViewState(
    val isCameraActive: Boolean = false,
    val isPermissionGranted: Boolean = false,
    val imageUri: String? = null,
    val errorMessage: String? = null
)