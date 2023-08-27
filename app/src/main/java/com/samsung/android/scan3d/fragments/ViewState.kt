package com.samsung.android.scan3d.fragments

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ViewState(
    var preview: Boolean? = true,
    var stream: Boolean? = false,
    var cameraId: String = "0",
    var resolutionIndex: Int? = null,
    var quality: Int? = 80
) : Parcelable