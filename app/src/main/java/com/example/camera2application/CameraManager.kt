package com.example.camera2application

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.view.Surface
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class CameraManager(private val context: Context) {
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    private var cameraId: String? = null
    private var previewSurface: Surface? = null
    private var listener: CameraManagerListener? = null
    private val isCleanedUp = AtomicBoolean(false)

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            if (!isCleanedUp.get()) {
                cameraDevice = camera
                createCaptureSession()
            } else {
                camera.close()
            }
        }

        override fun onDisconnected(camera: CameraDevice) {
            if (!isCleanedUp.get()) {
                camera.close()
                cameraDevice = null
            }
        }

        override fun onError(camera: CameraDevice, error: Int) {
            if (!isCleanedUp.get()) {
                camera.close()
                cameraDevice = null
                listener?.onError("Camera error: $error")
            }
        }
    }

    fun setListener(listener: CameraManagerListener?) {
        this.listener = listener
    }

    fun openCamera(): Boolean {
        if (isCleanedUp.get()) return false
        
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val manager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        try {
            // 选择合适的相机
            cameraId = chooseCameraId(manager)
            setUpImageReader()
            startBackgroundThread()
            manager.openCamera(cameraId!!, stateCallback, backgroundHandler)
        } catch (e: Exception) {
            e.printStackTrace()
            listener?.onError("Failed to open camera: ${e.message}")
            return false
        }
        return true
    }

    private fun chooseCameraId(manager: android.hardware.camera2.CameraManager): String {
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

    fun setPreviewSurface(surfaceHolder: SurfaceHolder) {
        if (isCleanedUp.get()) return
        
        previewSurface = surfaceHolder.surface
        // 如果相机已经打开，重新创建捕获会话
        if (cameraDevice != null) {
            createCaptureSession()
        }
    }

    private fun setUpImageReader() {
        if (isCleanedUp.get()) return
        
        // 获取相机支持的输出尺寸
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        val characteristics = manager.getCameraCharacteristics(cameraId ?: chooseCameraId(manager))
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        
        // 选择合适的预览尺寸
        val largest = map?.getOutputSizes(ImageFormat.JPEG)?.maxByOrNull { it.width * it.height }
        
        imageReader = ImageReader.newInstance(
            largest?.width ?: 640,
            largest?.height ?: 480,
            ImageFormat.JPEG,
            2
        ).apply {
            setOnImageAvailableListener({
                // 处理捕获到的图像
                val image = imageReader?.acquireLatestImage()
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
                    listener?.onPictureTaken(uri)
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
            val previewBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewBuilder.addTarget(previewSurface)
            previewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)

            cameraDevice!!.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (isCleanedUp.get()) {
                            session.close()
                            return
                        }
                        
                        if (cameraDevice == null) return
                        captureSession = session
                        try {
                            // 设置连续预览请求
                            captureSession?.setRepeatingRequest(previewBuilder.build(), null, backgroundHandler)
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
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            e.printStackTrace()
            if (!isCleanedUp.get()) {
                listener?.onError("Failed to create capture session: ${e.message}")
            }
        }
    }

    fun takePicture() {
        if (isCleanedUp.get()) return
        
        val imageSurface = imageReader?.surface ?: return
        if (cameraDevice == null || captureSession == null) return

        // 创建拍照请求
        val captureBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(imageSurface)
            set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            // 设置拍照参数，如对焦模式、闪光灯等
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }

        // 创建预览重新开始的请求
        val previewBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
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
                            captureSession?.setRepeatingRequest(previewBuilder.build(), null, backgroundHandler)
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

    fun closeCamera() {
        // 使用原子操作确保只清理一次
        if (isCleanedUp.getAndSet(true)) return
        
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
            stopBackgroundThread()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // 新增方法，用于显式清理资源
    fun cleanup() {
        closeCamera()
    }
}