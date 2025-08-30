package com.example.camera2application.mvi.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.camera2application.mvi.model.domain.useCase.CameraActionUseCase
import com.example.camera2application.mvi.model.domain.useCase.CameraInfoUseCase

class CameraViewModelFactory(
    private val cameraInfoUseCase: CameraInfoUseCase,
    private val cameraActionUseCase: CameraActionUseCase,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CameraViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CameraViewModel(cameraInfoUseCase, cameraActionUseCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}