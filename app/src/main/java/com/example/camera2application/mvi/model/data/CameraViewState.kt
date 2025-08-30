package com.example.camera2application.mvi.model.data

import com.example.camera2application.mvi.model.data.repository.CameraInfo

data class CameraViewState(
    val isCameraActive: Boolean = false,
    val isPermissionGranted: Boolean = false,
    val imageUri: String? = null,
    val errorMessage: String? = null,
    val cameraInfoList: List<CameraInfo> = emptyList(),
    val isRecording: Boolean = false,
)