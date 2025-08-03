package com.example.camera2application.mvi

sealed class CameraIntent {
    object StartCamera : CameraIntent()
    object StopCamera : CameraIntent()
    object TakePicture : CameraIntent()
    data class OnPermissionResult(val isGranted: Boolean) : CameraIntent()
}