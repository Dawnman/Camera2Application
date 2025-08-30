package com.example.camera2application.mvi

import android.app.Application

class DefaultApplication : Application() {
    private var appContainer: AppContainer? = null

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }

    fun getAppContainer(): AppContainer? {
        return appContainer
    }
}