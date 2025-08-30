package com.example.camera2application.mvi.model.data.repository.autoTest

import android.util.Size

enum class TestResolution(val size: Size) {
    P720(Size(1280, 720)),
    P1080(Size(1920, 1080)),
    P4K(Size(3840, 2160)),
}