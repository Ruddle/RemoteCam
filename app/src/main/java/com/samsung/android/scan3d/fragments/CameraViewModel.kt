package com.samsung.android.scan3d.fragments

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class CameraViewModel : ViewModel() {

    var isPermissionsGranted = MutableStateFlow<Boolean?>(null)
    var uiState = MutableStateFlow(ViewState())
}