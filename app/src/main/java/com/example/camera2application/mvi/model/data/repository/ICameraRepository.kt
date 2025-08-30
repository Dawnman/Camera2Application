package com.example.camera2application.mvi.model.data.repository

import android.util.Size
import android.view.SurfaceHolder
import kotlinx.coroutines.flow.Flow

interface ICameraRepository {
    /**
     * 获取相机镜头的全部信息
     */
    fun getCameraAllInfos(): Flow<List<CameraInfo>>

    fun setListener(listener: CameraManagerListener?)

    fun cleanup()

    fun openCamera(resolution: Size = Size(1920, 1080)): Boolean

    fun setPreviewSurface(surfaceHolder: SurfaceHolder)

    fun takePicture()

    fun startRecord(resolution: Size = Size(1920, 1080))

    fun stopRecord()

    fun startSlowMotionRecord(resolution: Size = Size(1920, 1080))

    fun stopSlowMotionRecord()
}