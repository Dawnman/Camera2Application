package com.example.camera2application.mvi

import android.content.Context
import com.example.camera2application.mvi.model.domain.useCase.CameraActionUseCase
import com.example.camera2application.mvi.model.domain.useCase.CameraInfoUseCase
import com.example.camera2application.mvi.model.data.repository.CameraManager
import com.example.camera2application.mvi.model.data.repository.CameraRepository

class AppContainer(context: Context) {
    private val cameraManager = CameraManager(context)

    val cameraInfoUseCase = CameraInfoUseCase(CameraRepository(cameraManager))
    val cameraActionUseCase = CameraActionUseCase(CameraRepository(cameraManager))
}