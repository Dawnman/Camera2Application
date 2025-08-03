package com.example.camera2application

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.camera2application.mvi.CameraIntent
import com.example.camera2application.mvi.CameraViewModel
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var cameraManager: CameraManager

    private val viewModel: CameraViewModel by viewModels {
        CameraViewModelFactory(cameraManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化CameraManager，使用Application Context
        cameraManager = CameraManager(application)

        cameraPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            viewModel.processIntent(CameraIntent.OnPermissionResult(isGranted))
            if (isGranted) {
                viewModel.processIntent(CameraIntent.StartCamera)
            }
        }

        setContent {
            CameraApp(viewModel) { permission ->
                cameraPermissionLauncher.launch(permission)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 清理监听器以避免潜在的内存泄漏
        cameraManager.setListener(null)
        cameraManager.cleanup()
    }
}

@Composable
fun CameraApp(
    viewModel: CameraViewModel,
    permissionLauncher: (String) -> Unit
) {
    val viewState by viewModel.viewState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        // 相机预览
        if (viewState.isCameraActive) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                onSurfaceReady = { surfaceHolder ->
                    viewModel.setPreviewSurface(surfaceHolder)
                }
            )
        } else {
            // 相机未激活时显示黑色背景
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }

        // 控制按钮
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            viewState.errorMessage?.let { errorMessage ->
                Text(
                    text = errorMessage,
                    color = Color.Red,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Button(
                onClick = {
                    if (viewState.isPermissionGranted) {
                        viewModel.processIntent(CameraIntent.StartCamera)
                    } else {
                        permissionLauncher(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = if (viewState.isCameraActive) "Restart Camera" else "Start Camera"
                )
            }

            Button(
                onClick = {
                    viewModel.processIntent(CameraIntent.TakePicture)
                },
                enabled = viewState.isCameraActive,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                Text("Take Picture")
            }

            Button(
                onClick = {
                    viewModel.processIntent(CameraIntent.StopCamera)
                },
                enabled = viewState.isCameraActive,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Stop Camera")
            }

            viewState.imageUri?.let { imageUri ->
                Text(
                    text = "Picture taken: $imageUri",
                    color = Color.Green,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}