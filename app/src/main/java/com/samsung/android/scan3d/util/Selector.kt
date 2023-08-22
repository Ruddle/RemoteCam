package com.samsung.android.scan3d.util

import android.annotation.SuppressLint
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Parcelable
import android.util.Log
import kotlinx.parcelize.Parcelize
import java.lang.Exception
import kotlin.math.atan
import kotlin.math.roundToInt

object Selector {
    /** Helper class used as a data holder for each selectable camera format item */
    @Parcelize
    data class SensorDesc(val title: String, val cameraId: String, val format: Int) : Parcelable

    /** Helper function used to convert a lens orientation enum into a human-readable string */
    private fun lensOrientationString(value: Int) = when (value) {
        CameraCharacteristics.LENS_FACING_BACK -> "Back"
        CameraCharacteristics.LENS_FACING_FRONT -> "Front"
        CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
        else -> "Unknown"
    }

    /** Helper function used to list all compatible cameras and supported pixel formats */

    fun getCapStringAtIndex(index: Int): String {
        val strings = listOf(
            "BACKWARD_COMPATIBLE",
            "MANUAL_SENSOR",
            "MANUAL_POST_PROCESSING",
            "RAW",
            "PRIVATE_REPROCESSING",
            "READ_SENSOR_SETTINGS",
            "BURST_CAPTURE",
            "YUV_REPROCESSING",
            "DEPTH_OUTPUT",
            "CONSTRAINED_HIGH_SPEED_VIDEO",
            "MOTION_TRACKING",
            "LOGICAL_MULTI_CAMERA",
            "MONOCHROME",
            "SECURE_IMAGE_DATA",
            "SYSTEM_CAMERA",
            "OFFLINE_PROCESSING",
            "ULTRA_HIGH_RESOLUTION_SENSOR",
            "REMOSAIC_REPROCESSING",
            "DYNAMIC_RANGE_TEN_BIT",
            "STREAM_USE_CASE",
            "COLOR_SPACE_PROFILES"
        )

        if (index in 0 until strings.size) {
            return strings[index]
        } else {
            return "Invalid index"
        }
    }

    @SuppressLint("InlinedApi")
    fun enumerateCameras(cameraManager: CameraManager): List<SensorDesc> {
        val availableCameras: MutableList<SensorDesc> = mutableListOf()

        // Get list of all compatible cameras
        val cameraIds = mutableListOf<String>()

        for (i in 0..100) {

            val istr = i.toString()
            if (!cameraIds.contains(istr)) {
                cameraIds.add(istr)
            }
        }

        val cameraIds2 = mutableListOf<String>()
        cameraIds.filter {

            try {
                val characteristics = cameraManager.getCameraCharacteristics(it)
                val capabilities = characteristics.get(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
                )

                if (capabilities == null) {
                    false
                } else if (capabilities.contains(
                        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA
                    )
                ) {
                    false
                } else if (capabilities.contains(
                        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE
                    )
                ) {
                    true
                } else {
                    false
                }


            } catch (e: Exception) {
                false
            }
        }.forEach { cameraIds2.add(it) }


        // Iterate over the list of cameras and return all the compatible ones
        cameraIds2.forEach { id ->

            Log.i("SELECTOR", "id: " + id)
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val orientation = lensOrientationString(
                characteristics.get(CameraCharacteristics.LENS_FACING)!!
            )

            // Query the available capabilities and output formats
            val capabilities = characteristics.get(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
            )!!

            capabilities.forEach { Log.i("CAP", "" + getCapStringAtIndex(it)) }


            val outputFormats = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
            )!!.outputFormats

            val outputSizes = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
            )!!.getOutputSizes(ImageFormat.JPEG)


            val foaclmm = characteristics.get(
                CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
            )!![0]
            val foc = ("" + foaclmm + "mm").padEnd(6, ' ')
            val ape = ("f" + characteristics.get(
                CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES
            )!![0] + "").padEnd(4, ' ')

            val sensorSize = characteristics.get(
                CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE
            )!!

            val vfov =(( 2.0*(180.0 / 3.141592) * atan(sensorSize.height / (2.0 * foaclmm))).roundToInt().toString()+"°").padEnd(4,' ')

            // All cameras *must* support JPEG output so we don't need to check characteristics

            val title=  "vfov:$vfov $foc $ape $orientation"
            if(!availableCameras.any {it-> it.title==title } ){
                availableCameras.add(
                    SensorDesc(
                        title, id, ImageFormat.JPEG
                    )
                )
            }



        }

        return availableCameras
    }
}