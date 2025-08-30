package com.example.camera2application.mvi.model.data.repository

class CameraRepository(private val delegate: ICameraRepository) : ICameraRepository by delegate