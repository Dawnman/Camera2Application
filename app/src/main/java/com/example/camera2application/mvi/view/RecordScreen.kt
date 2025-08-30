package com.example.camera2application.mvi.view

import android.view.SurfaceHolder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.camera2application.mvi.model.data.CameraViewState

@Composable
fun RecordScreen(
    viewState: CameraViewState,
    onSurfaceReady: (SurfaceHolder) -> Unit,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onStartCamera: () -> Unit,
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
            if (viewState.isRecording) {
                Text(
                    text = "Recording...",
                    color = Color.Red,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Button(
                onClick = {
                    if (!viewState.isCameraActive) {
                        onStartCamera()
                    } else {
                        if (viewState.isRecording) {
                            onStopRecord()
                        } else {
                            onStartRecord()
                        }
                    }
                },
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = if (!viewState.isCameraActive) {
                        "Start Camera"
                    } else {
                        if (viewState.isRecording) "Stop Record" else "Start Record"
                    }
                )
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
