package com.example.camera2application.mvi.view

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.camera2application.mvi.DefaultApplication
import com.example.camera2application.mvi.model.CameraViewModelFactory
import com.example.camera2application.mvi.intent.CameraIntent
import com.example.camera2application.mvi.model.CameraViewModel
import com.example.camera2application.mvi.model.domain.useCase.CameraActionUseCase

class MainActivity : ComponentActivity() {
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var cameraActionUseCase: CameraActionUseCase

    private val viewModel: CameraViewModel by viewModels {
        CameraViewModelFactory(
            (application as DefaultApplication).getAppContainer()!!.cameraInfoUseCase,
            cameraActionUseCase,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 初始化CameraUseCase，使用Application Context
        cameraActionUseCase =
            (application as DefaultApplication).getAppContainer()!!.cameraActionUseCase

        cameraPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results: Map<String, @JvmSuppressWildcards Boolean> ->
            val isGranted = results.values.all { it }
            viewModel.processIntent(CameraIntent.OnPermissionResult(isGranted))
            if (isGranted) {
                viewModel.processIntent(CameraIntent.StartCamera)
            }
        }

        setContent {
            CameraApp(viewModel) { permissions: Array<String> ->
                cameraPermissionLauncher.launch(permissions)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清理监听器以避免潜在的内存泄漏
        cameraActionUseCase.setListener(null)
        cameraActionUseCase.cleanup()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraApp(
    viewModel: CameraViewModel,
    navController: NavHostController = rememberNavController(),
    permissionLauncher: (Array<String>) -> Unit
) {
    val viewState by viewModel.viewState.collectAsState()
    permissionLauncher(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                title = {
                    Text("Camera App")
                }
            )
        }
    ) { internalPadding ->
        NavHost(
            navController = navController,
            startDestination = CameraAppRouteName.Start.title,
            modifier = Modifier.fillMaxSize().padding(internalPadding),
        ) {
            composable(route = CameraAppRouteName.Start.title) {
                StartScreen(
                    onGetCameraInfo = {
                        viewModel.processIntent(CameraIntent.GetCameraInfo)
                        navController.navigate(CameraAppRouteName.CameraInfo.title)
                    },
                    onTakePhoto = {
                        navController.navigate(CameraAppRouteName.Preview.title)
                    },
                    onRecordVideo = {
                        if (!viewState.isCameraActive) {
                            viewModel.processIntent(CameraIntent.StartCamera)
                        }
                        navController.navigate(CameraAppRouteName.Record.title)
                    },
                    onSlowMotionRecordVideo = {
                        if (!viewState.isCameraActive) {
                            viewModel.processIntent(CameraIntent.StartCamera)
                        }
                        navController.navigate(CameraAppRouteName.SlowMotionRecord.title)
                    }
                )
            }
            composable(route = CameraAppRouteName.Preview.title) {
                CameraScreen(
                    viewState = viewState,
                    onSurfaceReady = { surfaceHolder ->
                        viewModel.setPreviewSurface(surfaceHolder)
                    },
                    onStartCamera = { viewModel.processIntent(CameraIntent.StartCamera) },
                    onStopCamera = { viewModel.processIntent(CameraIntent.StopCamera) },
                    onTakePicture = { viewModel.processIntent(CameraIntent.TakePicture) },
                )
            }
            composable(route = CameraAppRouteName.CameraInfo.title) {
                CameraInfoScreen(cameraViewState = viewState)
            }
            composable(route = CameraAppRouteName.Record.title) {
                RecordScreen(
                    viewState = viewState,
                    onSurfaceReady = { surfaceHolder ->
                        viewModel.setPreviewSurface(surfaceHolder)
                    },
                    onStartRecord = { viewModel.processIntent(CameraIntent.StartRecord) },
                    onStopRecord = { viewModel.processIntent(CameraIntent.StopRecord) },
                    onStartCamera = { viewModel.processIntent(CameraIntent.StartCamera) }
                )
            }
            composable(route = CameraAppRouteName.SlowMotionRecord.title) {
                SlowMotionRecordScreen(
                    viewState = viewState,
                    onSurfaceReady = { surfaceHolder ->
                        viewModel.setPreviewSurface(surfaceHolder)
                    },
                    onStartSlowMotionRecord = { viewModel.processIntent(CameraIntent.StartSlowMotionRecord) },
                    onStopSlowMotionRecord = { viewModel.processIntent(CameraIntent.StopSlowMotionRecord) },
                    onStartCamera = { viewModel.processIntent(CameraIntent.StartCamera) }
                )
            }
        }
    }
}

enum class CameraAppRouteName(val title: String) {
    Start("Start"),
    Preview("Preview"),
    CameraInfo("CameraInfo"),
    Record("Record"),
    SlowMotionRecord("SlowMotionRecord"),
}