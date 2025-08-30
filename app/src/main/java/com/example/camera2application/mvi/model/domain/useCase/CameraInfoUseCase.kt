package com.example.camera2application.mvi.model.domain.useCase

import com.example.camera2application.mvi.model.data.repository.CameraInfo
import com.example.camera2application.mvi.model.data.repository.CameraRepository
import kotlinx.coroutines.flow.Flow

class CameraInfoUseCase(private val cameraRepository: CameraRepository) {
    /**
     * 获取相机焦距
     */
    fun getCameraAllInfos(): Flow<List<CameraInfo>> {
        return cameraRepository.getCameraAllInfos()
    }
}