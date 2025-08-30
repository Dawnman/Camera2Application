package com.example.camera2application.mvi.view

import android.Manifest
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.camera2application.mvi.model.data.CameraViewState

private const val TAG = "CameraScreen"

@Composable
fun CameraScreen(
    viewState: CameraViewState,
    onSurfaceReady: (SurfaceHolder) -> Unit,
    onStartCamera: () -> Unit,
    onStopCamera: () -> Unit,
    onTakePicture: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 相机预览
        if (viewState.isCameraActive) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                onSurfaceReady = onSurfaceReady,
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
                    onStartCamera()
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
                    onTakePicture()
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
                    onStopCamera()
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

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onSurfaceReady: (SurfaceHolder) -> Unit
) {
    val context = LocalContext.current
    val surfaceView = remember { SurfaceView(context) }
    val surfaceCallback = remember {
        object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                onSurfaceReady(holder)
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                // 当Surface大小改变时通知CameraManager
                onSurfaceReady(holder)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                // Surface被销毁时的处理
            }
        }
    }
    
    DisposableEffect(surfaceView, surfaceCallback) {
        // 添加回调
        surfaceView.holder.addCallback(surfaceCallback)

        // todo: 大小需要可以定制
        surfaceView.layoutParams = ViewGroup.LayoutParams(
            1920,
            1080
        )
        surfaceView.holder.setFixedSize(1920, 1080)
        
        onDispose {
            Log.d(TAG, "CameraPreview: onDispose")
            // 显式移除回调以避免内存泄漏
             surfaceView.holder.removeCallback(surfaceCallback)
        }
    }
    
    AndroidView(
        factory = { surfaceView },
        modifier = modifier
    )
}