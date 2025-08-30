package com.example.camera2application.mvi.model.domain.useCase

import android.view.SurfaceHolder
import com.example.camera2application.mvi.model.data.repository.CameraManagerListener
import com.example.camera2application.mvi.model.data.repository.ICameraRepository

class CameraActionUseCase(private val repository: ICameraRepository) {
    fun setListener(listener: CameraManagerListener?) {
        repository.setListener(listener)
    }

    fun cleanup() {
        repository.cleanup()
    }

    fun openCamera(): Boolean {
        return repository.openCamera()
    }

    fun setPreviewSurface(surfaceHolder: SurfaceHolder) {
        repository.setPreviewSurface(surfaceHolder)
    }

    fun takePicture() {
        repository.takePicture()
    }

    fun startRecord() {
        repository.startRecord()
    }

    fun stopRecord() {
        repository.stopRecord()
    }

    fun startSlowMotionRecord() {
        repository.startSlowMotionRecord()
    }

    fun stopSlowMotionRecord() {
        repository.stopSlowMotionRecord()
    }
}