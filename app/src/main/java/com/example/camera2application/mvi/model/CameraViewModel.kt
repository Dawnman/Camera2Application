package com.example.camera2application.mvi.model

import android.net.Uri
import android.util.Log
import android.view.SurfaceHolder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.camera2application.mvi.model.data.repository.CameraManagerListener
import com.example.camera2application.mvi.intent.CameraIntent
import com.example.camera2application.mvi.model.data.CameraViewState
import com.example.camera2application.mvi.model.domain.useCase.CameraActionUseCase
import com.example.camera2application.mvi.model.domain.useCase.CameraInfoUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "CameraViewModel"

class CameraViewModel(
    private val cameraInfoUseCase: CameraInfoUseCase,
    private val cameraActionUseCase: CameraActionUseCase,
) : ViewModel(), CameraManagerListener {
    private val _viewState = MutableStateFlow(CameraViewState())
    val viewState: StateFlow<CameraViewState> = _viewState.asStateFlow()
    private val isCleanedUp = AtomicBoolean(false)

    init {
        cameraActionUseCase.setListener(this)
    }

    fun processIntent(intent: CameraIntent) {
        if (isCleanedUp.get()) return

        when (intent) {
            is CameraIntent.StartCamera -> startCamera()
            is CameraIntent.StopCamera -> stopCamera()
            is CameraIntent.TakePicture -> takePicture()
            is CameraIntent.OnPermissionResult -> handlePermissionResult(intent.isGranted)
            is CameraIntent.GetCameraInfo -> getCameraInfos()
            is CameraIntent.StartRecord -> startRecord()
            is CameraIntent.StopRecord -> stopRecord()
            is CameraIntent.StartSlowMotionRecord -> startSlowMotionRecord()
            is CameraIntent.StopSlowMotionRecord -> stopSlowMotionRecord()
        }
    }

    private fun startCamera() {
        if (isCleanedUp.get()) return

        viewModelScope.launch {
            val success = cameraActionUseCase.openCamera()
            if (success) {
                _viewState.value = _viewState.value.copy(isCameraActive = true, errorMessage = null)
            } else {
                _viewState.value = _viewState.value.copy(
                    isCameraActive = false,
                    errorMessage = "Failed to open camera"
                )
            }
        }
    }

    fun setPreviewSurface(surfaceHolder: SurfaceHolder) {
        if (isCleanedUp.get()) return

        cameraActionUseCase.setPreviewSurface(surfaceHolder)
    }

    private fun stopCamera() {
        if (isCleanedUp.get()) return

        viewModelScope.launch {
            cameraActionUseCase.cleanup()
            _viewState.value = _viewState.value.copy(isCameraActive = false)
        }
    }

    private fun takePicture() {
        if (isCleanedUp.get()) return

        viewModelScope.launch {
            cameraActionUseCase.takePicture()
        }
    }

    private fun handlePermissionResult(isGranted: Boolean) {
        if (isCleanedUp.get()) return

        _viewState.value = _viewState.value.copy(isPermissionGranted = isGranted)
        if (!isGranted) {
            _viewState.value = _viewState.value.copy(errorMessage = "Camera permission is required")
        }
    }

    override fun onCleared() {
        // 使用原子操作确保只清理一次
        if (isCleanedUp.getAndSet(true)) return

        super.onCleared()
        // 清理监听器引用以避免潜在的内存泄漏
        cameraActionUseCase.setListener(null)
        cameraActionUseCase.cleanup()
    }

    override fun onPictureTaken(uri: Uri) {
        if (isCleanedUp.get()) return

        viewModelScope.launch {
            _viewState.value = _viewState.value.copy(imageUri = uri.toString())
        }
    }

    override fun onError(error: String) {
        if (isCleanedUp.get()) return

        viewModelScope.launch {
            _viewState.value = _viewState.value.copy(
                errorMessage = error
            )
        }
    }

    private fun getCameraInfos() {
        viewModelScope.launch {
            cameraInfoUseCase.getCameraAllInfos().collect { infos ->
                Log.d(TAG, "getCameraInfos success: $infos")
                _viewState.value = _viewState.value.copy(
                    cameraInfoList = infos.toList()
                )
            }
        }
    }

    private fun startRecord() {
        viewModelScope.launch {
            cameraActionUseCase.startRecord()
            _viewState.value = _viewState.value.copy(isRecording = true)
        }
    }

    private fun stopRecord() {
        viewModelScope.launch {
            cameraActionUseCase.stopRecord()
            _viewState.value = _viewState.value.copy(isRecording = false)
        }
    }

    private fun startSlowMotionRecord() {
        viewModelScope.launch {
            cameraActionUseCase.startSlowMotionRecord()
            _viewState.value = _viewState.value.copy(isRecording = true)
        }
    }

    private fun stopSlowMotionRecord() {
        viewModelScope.launch {
            cameraActionUseCase.stopSlowMotionRecord()
            _viewState.value = _viewState.value.copy(isRecording = false)
        }
    }
}