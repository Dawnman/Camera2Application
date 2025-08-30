package com.example.camera2application.mvi.model.utils

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream

private const val TAG = "VideoFileUtils"

fun getVideoFilePath(context: Context): String {
    val fileName = "${System.currentTimeMillis()}.mp4"
    val dir = context.getExternalFilesDir(null)
    return if (dir == null) {
        fileName
    } else {
        "${dir.absolutePath}/$fileName".also {
            Log.d(TAG, "getVideoFilePath: $it")
        }
    }
}

fun saveVideoToGallery(context: Context, videoPath: String) {
    Log.d(TAG, "saveVideoToGallery: $videoPath")
    val tempFile = File(videoPath)
    if (!tempFile.exists()) {
        Log.e(TAG, "saveVideoToGallery: tempFile not exists")
        return
    }

    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        put(MediaStore.MediaColumns.SIZE, tempFile.length())
        put(
            MediaStore.Video.Media.RELATIVE_PATH,
            Environment.DIRECTORY_MOVIES + "/Camera2Application"
        )
        put(MediaStore.Video.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    resolver.insert(collection, contentValues)?.also {
        val outputStream = resolver.openOutputStream(it)
        outputStream?.use { os ->
            FileInputStream(tempFile).use { inputStream ->
                inputStream.copyTo(os)
            }
        }

        contentValues.clear()
        contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(it, contentValues, null, null)
    }
}