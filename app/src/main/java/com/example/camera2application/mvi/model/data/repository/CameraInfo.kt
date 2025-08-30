package com.example.camera2application.mvi.model.data.repository

import android.util.Range
import android.util.Size

data class CameraInfo(
    /*
     * 相机id
     */
    val cameraId: String,

    /*
     * 相机支持的并发流组合
     */
    val concurrentCameraIds: Set<Set<String>>,

    /*
     * 相机支持的最大并发流数
     */
    val maxStreams: Int,

    /*
     * 是否是逻辑摄像头
     */
    val isLogicalCamera: Boolean,

    /*
     * 物理相机镜头id
     */
    val physicalCameraIds: Set<String>,

    /*
     * 相机镜头焦距
     */
    val lens: List<Float>,

    /*
     * 相机镜头分辨率
     */
    val resolution: List<Size>,

    /*
     * 相机镜头帧率
     */
    val fpsRange: List<Range<Int>>,

    /*
     * 相机镜头分辨率支持的帧率范围
     */
    val resolutionFpsMap: Map<Size, List<Int>>,

    /*
     * 相机镜头支持的白平衡模式
     */
    val awbAvailableModes: List<AWBMode>,

    /*
     * 相机镜头支持的长曝光范围，不支持为空
     */
    val aeAvailableModes: Range<Long>?,

    /*
     * 相机镜头支持的 iSO 参数
     */
    val isoRange: Range<Int>?,

    /*
     * 相机镜头支持的曝光补偿
     */
    val evRange: Range<Float>,

    /*
     * 相机镜头支持的 zoom 范围【理论上是fov、需要转换为倍数】
     */
    val availableMaxDigitalZoom: Float,

    /*
     * 相机镜头支持的水平 fov
     */
    val horizontalFov: Float,

    /*
     * 相机镜头支持的垂直 fov
     */
    val verticalFov: Float,
)

enum class AWBMode(val value: Int, val desc: String) {
    AUTO(1, "自动"),
    INCANDESCENT(2, "白炽灯"),
    FLUORESCENT(3, "荧光灯"),
    WARM_FLUORESCENT(4, "暖色荧光灯"),
    DAYLIGHT(5, "日光"),
    CLOUDY_DAYLIGHT(6, "阴天"),
    TWILIGHT(7, "黄昏"),
    SHADE(8, "阴影"),
}

fun Int.toAWBMode(): AWBMode? {
    return AWBMode.entries.find { it.value == this }
}