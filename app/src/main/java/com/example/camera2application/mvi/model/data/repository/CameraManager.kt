package com.example.camera2application.mvi.model.data.repository

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
import android.hardware.camera2.CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE
import android.hardware.camera2.CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP
import android.hardware.camera2.CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES
import android.hardware.camera2.CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
import android.hardware.camera2.CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
import android.hardware.camera2.CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO
import android.hardware.camera2.CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_PROC
import android.hardware.camera2.CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM
import android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
import android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE
import android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE
import android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.camera2application.mvi.model.utils.getVideoFilePath
import com.example.camera2application.mvi.model.utils.saveVideoToGallery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.HashMap
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.atan

private const val TAG = "CameraManager"

class CameraManager(private val context: Context) : ICameraRepository {
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    private var cameraId: String? = null
    private var previewSurface: Surface? = null
    private var listener: CameraManagerListener? = null
    private val isCleanedUp = AtomicBoolean(false)
    private var mediaRecorder: MediaRecorder? = null

    @Volatile
    private var currentRecordVideoFilePath: String? = null

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.d(TAG, "openCamera state callback onOpened")
            if (!isCleanedUp.get()) {
                cameraDevice = camera
                createCaptureSession()
            } else {
                camera.close()
            }
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.e(TAG, "openCamera state callback onDisconnected")
            if (!isCleanedUp.get()) {
                camera.close()
                cameraDevice = null
            }
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.e(TAG, "openCamera state callback onError: $error")
            if (!isCleanedUp.get()) {
                camera.close()
                cameraDevice = null
                listener?.onError("Camera error: $error")
            }
        }
    }

    override fun setListener(listener: CameraManagerListener?) {
        this.listener = listener
    }

    override fun openCamera(resolution: Size): Boolean {
        isCleanedUp.getAndSet(false)
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val manager =
            context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            // 选择合适的相机
            cameraId = chooseCameraId(manager)
            setUpImageReader(resolution)
            startBackgroundThread()
            manager.openCamera(cameraId!!, stateCallback, backgroundHandler)
        } catch (e: Exception) {
            e.printStackTrace()
            listener?.onError("Failed to open camera: ${e.message}")
            return false
        }
        return true
    }

    private fun chooseCameraId(manager: CameraManager): String {
        val cameraIds = manager.cameraIdList
        for (id in cameraIds) {
            val characteristics = manager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            // 优先选择后置摄像头
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                return id
            }
        }
        // 如果没有后置摄像头，就用第一个
        return cameraIds[0]
    }

    override fun setPreviewSurface(surfaceHolder: SurfaceHolder) {
        if (isCleanedUp.get()) return

        previewSurface = surfaceHolder.surface
        // 如果相机已经打开，重新创建捕获会话
        if (cameraDevice != null) {
            createCaptureSession()
        }
    }

    private fun setUpImageReader(resolution: Size) {
        if (isCleanedUp.get()) return

        // 获取相机支持的输出尺寸
        val manager =
            context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val characteristics = manager.getCameraCharacteristics(cameraId ?: chooseCameraId(manager))
        val map = characteristics.get(SCALER_STREAM_CONFIGURATION_MAP)

        // 选择合适的预览尺寸
        val targetResolution = map?.getOutputSizes(ImageFormat.JPEG)?.onEach {
            Log.d(TAG, "getOutputSizes: width: ${it.width}, height: ${it.height}")
        }?.find {
            it == resolution
        }
        if (targetResolution == null) {
            Log.e(TAG, "setUpImageReader: $resolution not supported")
            listener?.onError("Unsupported resolution: $resolution")
        }

        imageReader = ImageReader.newInstance(
            targetResolution?.width ?: 1920,
            targetResolution?.height ?: 1080,
            ImageFormat.JPEG,
            2
        ).apply {
            setOnImageAvailableListener({ reader ->
                Log.d(TAG, "setUpImageReader: Image available")
                // 处理捕获到的图像
                val image = reader.acquireLatestImage()
                image?.let { img ->
                    val buffer = img.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    img.close()

                    // 保存图片到相册
                    if (!isCleanedUp.get()) {
                        saveImageToGallery(bytes)
                    }
                }
            }, backgroundHandler)
        }
    }

    private fun saveImageToGallery(jpegData: ByteArray) {
        if (isCleanedUp.get()) return

        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val filename = "JPEG_${sdf.format(Date())}_camera2.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Camera2App")
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            try {
                resolver.openOutputStream(it)?.use { output ->
                    output.write(jpegData)
                }
                // 通知UI图片已保存
                if (!isCleanedUp.get()) {
                    listener?.onPictureTaken(it)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (!isCleanedUp.get()) {
                    listener?.onError("Failed to save image: ${e.message}")
                }
            }
        }
    }

    private fun startBackgroundThread() {
        if (isCleanedUp.get()) return

        backgroundThread = HandlerThread("CameraBackground").apply {
            start()
            backgroundHandler = Handler(looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join(1000) // 等待最多1秒
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } finally {
            backgroundThread = null
            backgroundHandler = null
        }
    }

    private fun createCaptureSession() {
        if (isCleanedUp.get()) return

        val previewSurface = previewSurface ?: return
        val imageSurface = imageReader?.surface ?: return

        try {
            val surfaces = listOf(previewSurface, imageSurface)

            // 创建预览请求
            val previewBuilder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(previewSurface)
                    set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                }
            val stateCallback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (isCleanedUp.get()) {
                        session.close()
                        return
                    }

                    if (cameraDevice == null) return
                    captureSession = session
                    try {
                        // 设置连续预览请求
                        captureSession?.setRepeatingRequest(
                            previewBuilder.build(),
                            null,
                            backgroundHandler
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                        if (!isCleanedUp.get()) {
                            listener?.onError("Failed to start preview: ${e.message}")
                        }
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    if (!isCleanedUp.get()) {
                        listener?.onError("Failed to configure camera session")
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                configureSessionAfterT(surfaces, stateCallback = stateCallback)
            } else {
                configureSessionBeforeT(surfaces, stateCallback)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (!isCleanedUp.get()) {
                listener?.onError("Failed to create capture session: ${e.message}")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun configureSessionAfterT(
        targets: List<Surface>,
        sessionType: Int = SessionConfiguration.SESSION_REGULAR,
        stateCallback: CameraCaptureSession.StateCallback
    ) {
        Log.d(TAG, "configureSessionAfterT: ")
        val handler = backgroundHandler ?: return

        val configs = mutableListOf<OutputConfiguration>()
        val streamUseCase = CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW_VIDEO_STILL
        targets.onEach {
            val config = OutputConfiguration(it)
            // todo: stream use case do what?
            // config.streamUseCase = streamUseCase.toLong()
            configs.add(config)
        }
        SessionConfiguration(
            sessionType,
            configs,
            HandlerExecutor(handler),
            stateCallback
        ).also {
            cameraDevice?.createCaptureSession(it)
        }
    }

    private fun configureSessionBeforeT(
        targets: List<Surface>,
        stateCallback: CameraCaptureSession.StateCallback
    ) {
        Log.d(TAG, "configureSessionBeforeT: ")
        cameraDevice?.createCaptureSession(
            targets,
            stateCallback,
            backgroundHandler
        )
    }

    override fun takePicture() {
        if (isCleanedUp.get()) return

        val imageSurface = imageReader?.surface ?: return
        if (cameraDevice == null || captureSession == null) return

        // 创建拍照请求
        val captureBuilder =
            cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(imageSurface)
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                // 设置拍照参数，如对焦模式、闪光灯等
                set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                )
            }

        // 创建预览重新开始的请求
        val previewBuilder =
            cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(previewSurface!!)
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            }

        val thread = HandlerThread("CameraPicture")
        thread.start()
        val backgroundHandler = Handler(thread.looper)

        val captureCallback = object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                super.onCaptureCompleted(session, request, result)
                // 拍照完成后恢复预览
                try {
                    // 检查会话是否仍然有效
                    if (!isCleanedUp.get() && captureSession != null) {
                        try {
                            captureSession?.setRepeatingRequest(
                                previewBuilder.build(),
                                null,
                                backgroundHandler
                            )
                        } catch (e: Exception) {
                            // 忽略异常，因为这可能发生在会话关闭时
                            if (!isCleanedUp.get()) {
                                listener?.onError("Failed to restart preview: ${e.message}")
                            }
                        }
                    }
                } finally {
                    // 确保线程被终止
                    thread.quitSafely()
                }
            }

            override fun onCaptureFailed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                failure: CaptureFailure
            ) {
                super.onCaptureFailed(session, request, failure)
                thread.quitSafely()
                if (!isCleanedUp.get()) {
                    listener?.onError("Failed to capture image")
                }
            }
        }

        try {
            // 先停止预览
            try {
                captureSession!!.stopRepeating()
            } catch (e: Exception) {
                // 忽略异常，因为这可能发生在会话关闭时
            }
            // 执行拍照
            captureSession!!.capture(captureBuilder.build(), captureCallback, backgroundHandler)
        } catch (e: Exception) {
            e.printStackTrace()
            thread.quitSafely()
            if (!isCleanedUp.get()) {
                listener?.onError("Failed to take picture: ${e.message}")
            }
        }
    }

    private fun closeCamera() {
        // 使用原子操作确保只清理一次
        if (isCleanedUp.getAndSet(true)) return

        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null

            mediaRecorder?.release()
            mediaRecorder = null

            stopBackgroundThread()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 用于显式清理资源
    override fun cleanup() {
        closeCamera()
    }

    override fun startRecord(resolution: Size) {
        Log.d(TAG, "startRecord: ")
        // 创建MediaRecorder对象
        setUpMediaRecorder(resolution, fps = 120)

        val previewSurface = previewSurface ?: return
        val recorderSurface = mediaRecorder?.surface ?: return
        val surfaces = mutableListOf<Surface>().apply {
            add(previewSurface)
            add(recorderSurface)
        }

        val previewBuilder =
            cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)?.apply {
                addTarget(previewSurface)
                addTarget(recorderSurface)
            } ?: return

        val stateCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                if (isCleanedUp.get()) {
                    session.close()
                    return
                }

                if (cameraDevice == null) return
                captureSession = session
                try {
                    // 设置连续预览请求
                    captureSession?.setRepeatingRequest(
                        previewBuilder.build(),
                        null,
                        backgroundHandler
                    )
                    // 开始录制
                    mediaRecorder?.start()
                } catch (e: Exception) {
                    e.printStackTrace()
                    if (!isCleanedUp.get()) {
                        listener?.onError("Failed to start preview: ${e.message}")
                    }
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                if (!isCleanedUp.get()) {
                    listener?.onError("Failed to configure camera session")
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            configureSessionAfterT(surfaces, stateCallback = stateCallback)
        } else {
            configureSessionBeforeT(surfaces, stateCallback)
        }
    }

    private fun setUpMediaRecorder(resolution: Size, fps: Int = 30, surface: Surface? = null) {
        Log.d(TAG, "setUpMediaRecorder: resolution: $resolution, fps: $fps")
        mediaRecorder = MediaRecorder().apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(getVideoFilePath(context).also {
                currentRecordVideoFilePath = it
            })
            setVideoEncodingBitRate(10_000_000)
            setVideoFrameRate(fps)
            setVideoSize(resolution.width, resolution.height)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setOrientationHint(90)
            surface?.let {
                setInputSurface(it)
            }
        }
        kotlin.runCatching {
            mediaRecorder?.prepare()
        }.onFailure {
            Log.e(TAG, "setUpMediaRecorder: ${it.message}")
        }
    }

    override fun stopRecord() {
        Log.d(TAG, "stopRecord: ")
        mediaRecorder?.apply {
            stop()
            reset()
        }

        // 重新开启预览
        createCaptureSession()
        // 保存到相册，以便相册可以查看录制的视频
        currentRecordVideoFilePath?.let {
            saveVideoToGallery(context, it)
        }
    }

    override fun startSlowMotionRecord(resolution: Size) {
        Log.d(TAG, "startSlowMotionRecord: ")
        val cameraId = cameraId ?: return
        val maxFps = getSlowMotionMaxFps(context, cameraId, resolution).getOrElse {
            it.printStackTrace()
            return
        }.also {
            Log.d(TAG, "startSlowMotionRecord: maxFps: $it")
        }

        setUpMediaRecorder(resolution, fps = maxFps.upper)
        val previewSurface = previewSurface ?: return
        val recorderSurface = mediaRecorder?.surface ?: return
        val surfaces = mutableListOf<Surface>().apply {
            add(previewSurface)
            add(recorderSurface)
        }

        val previewBuilder =
            cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)?.apply {
                addTarget(previewSurface)
                addTarget(recorderSurface)
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, maxFps)
            } ?: return

        val stateCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                if (isCleanedUp.get()) {
                    session.close()
                    return
                }

                if (cameraDevice == null) return
                captureSession = session
                try {
                    if (session is CameraConstrainedHighSpeedCaptureSession) {
                        Log.d(TAG, "onConfigured: High speed session")
                        val previewBuilderBurst =
                            session.createHighSpeedRequestList(previewBuilder.build())
                        session.setRepeatingBurst(previewBuilderBurst, null, backgroundHandler)
                    } else {
                        session.setRepeatingRequest(
                            previewBuilder.build(),
                            null,
                            backgroundHandler
                        )
                    }
                    // 开始录制
                    mediaRecorder?.start()
                } catch (e: Exception) {
                    e.printStackTrace()
                    if (!isCleanedUp.get()) {
                        listener?.onError("Failed to start preview: ${e.message}")
                    }
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                if (!isCleanedUp.get()) {
                    listener?.onError("Failed to configure camera session")
                }
            }
        }
//        cameraDevice?.createConstrainedHighSpeedCaptureSession(surfaces, stateCallback, backgroundHandler)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            configureSessionAfterT(
                surfaces,
                sessionType = SessionConfiguration.SESSION_HIGH_SPEED,
                stateCallback = stateCallback
            )
        } else {
            configureSessionBeforeT(surfaces, stateCallback)
        }
    }

    override fun stopSlowMotionRecord() {
        Log.d(TAG, "stopSlowMotionRecord: ")
        mediaRecorder?.apply {
            stop()
            reset()
        }

        // 重新开启预览
//        createCaptureSession()
        // 保存到相册，以便相册可以查看录制的视频
        currentRecordVideoFilePath?.let {
            saveVideoToGallery(context, it)
        }
    }

    private fun getSlowMotionMaxFps(
        context: Context,
        cameraId: String,
        resolution: Size
    ): Result<Range<Int>> {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val isCapabilitySupported = characteristics.get(REQUEST_AVAILABLE_CAPABILITIES)?.contains(
            REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO
        ) ?: false
        if (!isCapabilitySupported) {
            return Result.failure(Exception("High speed video is not supported"))
        }
        val streamConfigurationMap =
            characteristics.get(SCALER_STREAM_CONFIGURATION_MAP)
                ?: return Result.failure(Exception("High speed video is not supported - configuration"))
        val availableSizes = streamConfigurationMap.getHighSpeedVideoSizes()
        if (availableSizes.isEmpty() || !availableSizes.contains(resolution)) {
            return Result.failure(Exception("High speed video is not supported - available size: ${availableSizes.contentToString()}"))
        }

        val fpsRanges = streamConfigurationMap.getHighSpeedVideoFpsRangesFor(resolution)
        if (fpsRanges.isEmpty()) {
            return Result.failure(Exception("High speed video is not supported - fps is empty"))
        }
        Log.d(TAG, "fps range:${fpsRanges.contentToString()}")
        val maxFps = fpsRanges.filter { it.upper == it.lower }.maxByOrNull { it.upper }
            ?: return Result.failure(Exception("High speed video is not supported - fps is null"))
        return Result.success(maxFps)
    }

    override fun getCameraAllInfos(): Flow<List<CameraInfo>> {
        return flow {
            val manager =
                context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

            // 相机支持的并发流组合
            val concurrentCameraIds = manager.concurrentCameraIds

            val cameraInfos = mutableListOf<CameraInfo>()
            manager.cameraIdList.forEach { id ->
                Log.d(TAG, "getCameraAllInfos: cameraId: $id")
                val characteristics = manager.getCameraCharacteristics(id)
                // 判断是否是逻辑摄像头
                val isLogicalCamera = characteristics.get(REQUEST_AVAILABLE_CAPABILITIES)?.contains(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA
                ) ?: false

                // 逻辑摄像头包含的物理摄像头
                val physicalIdSet: Set<String> = characteristics.physicalCameraIds

                // 获取相机支持的最大并发流数
                val maxStreams = characteristics.get(REQUEST_MAX_NUM_OUTPUT_PROC) ?: -1

                val streamConfigurationMap = characteristics.get(SCALER_STREAM_CONFIGURATION_MAP)
                // 获取相机支持的分辨率
                val resolution =
                    streamConfigurationMap?.getOutputSizes(ImageFormat.JPEG)?.toList()?.onEach {
                        Log.d(TAG, "getCameraAllInfos: resolution: $it")
                    }

                // 获取相机支持的焦距
                val availableLens = characteristics.get(LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList()
                    ?.onEach {
                        Log.d(TAG, "getCameraAllInfos: availableLens: $it")
                    }
                // 获取相机支持的帧率范围
                val fpsRange = characteristics.get(CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.toList()
                // 遍历分辨率，获取相机支持的帧率范围
                val resolutionFpsMap = HashMap<Size, List<Int>>()
                resolution?.forEach { size ->
                    val minFrameDuration = kotlin.runCatching {
                        streamConfigurationMap.getOutputMinFrameDuration(
                            SurfaceTexture::class.java,
                            size
                        )
                    }.onFailure {
                        Log.e(
                            TAG,
                            "getCameraAllInfos: getOutputMinFrameDuration error: ${it.message}"
                        )
                    }.getOrNull()?.also {
                        Log.d(TAG, "getCameraAllInfos: minFrameDuration: $it")
                    } ?: -1
                    val maxFps = if (minFrameDuration <= 0) {
                        30
                    } else {
                        1000_000_000 / minFrameDuration
                    }
                    val fpsList = mutableListOf<Int>()
                    fpsRange?.forEach { range ->
                        if (range.upper <= maxFps) {
                            if (range.lower == range.upper) {
                                fpsList.add(range.lower)
                            }
                        }
                    }
                    resolutionFpsMap[size] = fpsList
                }

                // 获取相机支持的白平衡模式
                val awbAvailableModes = characteristics.get(CONTROL_AWB_AVAILABLE_MODES)
                    ?.toList()
                    ?.mapNotNull { mode ->
                        mode.toAWBMode()
                    }
                // 获取相机支持的曝光模式
                val aeAvailableModes = characteristics.get(SENSOR_INFO_EXPOSURE_TIME_RANGE)
                // 获取相机支持的ISO范围
                val isoRange = characteristics.get(SENSOR_INFO_SENSITIVITY_RANGE)
                // 获取相机支持的曝光补偿范围
                val aeCompensationRange = characteristics.get(CONTROL_AE_COMPENSATION_RANGE)
                val aeCompensationStep = characteristics.get(CONTROL_AE_COMPENSATION_STEP)
                val evRange = if (aeCompensationRange == null || aeCompensationStep == null) {
                    Range(-1f, 1f)
                } else {
                    Range(
                        aeCompensationRange.lower * aeCompensationStep.toFloat(),
                        aeCompensationRange.upper * aeCompensationStep.toFloat()
                    )
                }

                // 获取相机支持的最大数字变焦倍数
                val availableMaxDigitalZoom = characteristics.get(SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
                // 计算相机镜头的 fov
                val sensorSize = characteristics.get(SENSOR_INFO_PHYSICAL_SIZE)
                val horizontalFov = if (sensorSize == null || availableLens?.first() == null) {
                    -1f
                } else {
                    2 * atan(sensorSize.width / (2 * availableLens.first()))
                }
                val verticalFov = if (sensorSize == null || availableLens?.first() == null) {
                    -1f
                } else {
                    2 * atan(sensorSize.height / (2 * availableLens.first()))
                }

                cameraInfos.add(
                    CameraInfo(
                        cameraId = id,
                        concurrentCameraIds = concurrentCameraIds,
                        maxStreams = maxStreams,
                        isLogicalCamera = isLogicalCamera,
                        physicalCameraIds = physicalIdSet,
                        lens = availableLens ?: emptyList(),
                        resolution = resolution ?: emptyList(),
                        fpsRange = fpsRange ?: emptyList(),
                        resolutionFpsMap = resolutionFpsMap,
                        awbAvailableModes = awbAvailableModes ?: emptyList(),
                        aeAvailableModes = aeAvailableModes,
                        isoRange = isoRange,
                        evRange = evRange,
                        availableMaxDigitalZoom = availableMaxDigitalZoom ?: -1f,
                        horizontalFov = horizontalFov,
                        verticalFov = verticalFov,
                    )
                )
            }
            emit(cameraInfos)
        }
    }
}