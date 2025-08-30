package com.example.camera2application.mvi.model.data.repository

import android.os.Handler
import java.util.concurrent.Executor

class HandlerExecutor(private val handler: Handler) : Executor {
    override fun execute(task: Runnable?) {
        task ?: return
        handler.post(task)
    }
}