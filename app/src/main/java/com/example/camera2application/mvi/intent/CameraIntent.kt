package com.example.camera2application.mvi.intent

sealed class CameraIntent {
    object StartCamera : CameraIntent()
    object StopCamera : CameraIntent()
    object TakePicture : CameraIntent()
    data class OnPermissionResult(val isGranted: Boolean) : CameraIntent()

    object GetCameraInfo : CameraIntent()

    object StartRecord : CameraIntent()
    object StopRecord : CameraIntent()

    object StartSlowMotionRecord : CameraIntent()
    object StopSlowMotionRecord : CameraIntent()
}