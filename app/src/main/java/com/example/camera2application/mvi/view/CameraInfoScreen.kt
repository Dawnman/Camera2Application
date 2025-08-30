package com.example.camera2application.mvi.view

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.camera2application.mvi.model.data.CameraViewState
import com.example.camera2application.mvi.model.data.repository.CameraInfo

private const val TAG = "CameraInfoScreen"

@Composable
fun CameraInfoScreen(
    cameraViewState: CameraViewState,
) {
    Log.d(TAG, "CameraInfoScreen: $cameraViewState")
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        item {
            if (cameraViewState.cameraInfoList.isNotEmpty()) {
                Text(
                    text = "相机支持的并发流组合: ${cameraViewState.cameraInfoList[0].concurrentCameraIds}"
                )
            }
        }
        items(
            items = cameraViewState.cameraInfoList,
            itemContent = {
                CameraInfoListItem(cameraInfo = it)
            }
        )
    }
}

@Composable
fun CameraInfoListItem(cameraInfo: CameraInfo) {
    Row {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "摄像头 ID: ${cameraInfo.cameraId}"
            )
            Text(
                text = "焦距：\n" + cameraInfo.lens.toTypedArray().contentToString()
            )
            if (cameraInfo.isLogicalCamera) {
                Text(
                    text = "该摄像头为逻辑摄像头，包含的物理摄像头 ID：\n" + cameraInfo.physicalCameraIds
                )
            } else {
                Text(
                    text = "该摄像头为物理摄像头"
                )
                if (cameraInfo.physicalCameraIds.isNotEmpty()) {
                    Text(
                        text = "包含的物理摄像头 ID: \n ${cameraInfo.physicalCameraIds}"
                    )
                }
            }

            Text(
                text = "是否组合镜头：" + if (cameraInfo.physicalCameraIds.size > 1) "是" else "否"
            )

            Text(
                text = "支持的最大并发流数量：${cameraInfo.maxStreams}"
            )

            Text(
                text = "支持的分辨率：\n" + cameraInfo.resolution.toTypedArray().contentToString()
            )
            Text(
                text = "每个分辨率下支持的帧率：\n" + cameraInfo.resolutionFpsMap.entries.joinToString(
                    "\n"
                )
            )
            Text(
                text = "支持的白平衡模式：\n" + cameraInfo.awbAvailableModes
                    .map {
                        it.desc
                    }
                    .toTypedArray()
                    .contentToString()
            )
            Text(
                text = "支持的长曝光范围：\n" + (cameraInfo.aeAvailableModes ?: -1).toString()
            )
            Text(
                text = "支持的 iSO 参数： \n" + (cameraInfo.isoRange ?: -1).toString()
            )
            Text(
                text = "支持的曝光补偿范围：\n" + cameraInfo.evRange.toString()
            )
            Text(
                text = "支持的 zoom 范围：\n" + cameraInfo.availableMaxDigitalZoom.toString()
            )
            Text(
                text = "水平 FOV：\n ${cameraInfo.horizontalFov} \n 垂直 FOV：\n ${cameraInfo.verticalFov}"
            )
        }
    }
}