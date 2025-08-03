package com.example.camera2application

import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

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
        
        surfaceView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        
        onDispose {
            // 显式移除回调以避免内存泄漏
            surfaceView.holder.removeCallback(surfaceCallback)
        }
    }
    
    AndroidView(
        factory = { surfaceView },
        modifier = modifier
    )
}