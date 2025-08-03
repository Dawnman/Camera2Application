package com.example.camera2application.mvi

import android.net.Uri
import android.view.SurfaceHolder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.camera2application.CameraManager
import com.example.camera2application.CameraManagerListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class CameraViewModel(private val cameraManager: CameraManager) : ViewModel(), CameraManagerListener {
    private val _viewState = MutableStateFlow(CameraViewState())
    val viewState: StateFlow<CameraViewState> = _viewState.asStateFlow()
    private val isCleanedUp = AtomicBoolean(false)

    init {
        cameraManager.setListener(this)
    }

    fun processIntent(intent: CameraIntent) {
        if (isCleanedUp.get()) return
        
        when (intent) {
            is CameraIntent.StartCamera -> startCamera()
            is CameraIntent.StopCamera -> stopCamera()
            is CameraIntent.TakePicture -> takePicture()
            is CameraIntent.OnPermissionResult -> handlePermissionResult(intent.isGranted)
        }
    }

    private fun startCamera() {
        if (isCleanedUp.get()) return
        
        viewModelScope.launch {
            val success = cameraManager.openCamera()
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
        
        cameraManager.setPreviewSurface(surfaceHolder)
    }

    private fun stopCamera() {
        if (isCleanedUp.get()) return
        
        viewModelScope.launch {
            cameraManager.closeCamera()
            _viewState.value = _viewState.value.copy(isCameraActive = false)
        }
    }

    private fun takePicture() {
        if (isCleanedUp.get()) return
        
        viewModelScope.launch {
            cameraManager.takePicture()
            // 在实际应用中，这里应该返回实际的图片URI
            //_viewState.value = _viewState.value.copy(imageUri = "content://captured_image")
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
        cameraManager.setListener(null)
        cameraManager.cleanup()
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
            _viewState.value = _viewState.value.copy(errorMessage = error)
        }
    }
}