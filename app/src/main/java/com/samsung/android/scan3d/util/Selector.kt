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
    data class SensorDesc(
        val title: String,
        val cameraId: String,
        val logicalCameraId: String?,
        val format: Int
    ) : Parcelable

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

        cameraManager.cameraIdList.forEach { it ->
            cameraIds.add(it.toString())
        }

        val cameraIds2 = mutableListOf<SensorDesc>()
        val openableCameraIds = mutableListOf<String>()

        cameraIds.forEach { id ->
            try {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val capabilities = characteristics.get(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
                ) ?: return@forEach

                if (capabilities.contains(
                        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA
                    )
                ) {
                    // We got evil logical camera here, split it up into physical ones
                    characteristics.physicalCameraIds.forEach { physId ->
                        cameraIds2.add(SensorDesc("", physId, id, 0))
                    }
                } else {
                    cameraIds2.add(SensorDesc("", id, null, 0))
                    openableCameraIds.add(id)
                }
            } catch (_: Exception) {


            } catch (e: Exception) {
                false
            }
        }.forEach { cameraIds2.add(it) }

        // There can be physical cameras that you can access both from a logical camera and as a
        // physical camera. (For example, on Galaxy S23 Ultra, the ultra-wide camera)
        // We remove the duplicates here.
        // We enumerate all directly openable camera IDs, then search in cameraIds2 for a logical
        // camera entry that contains it, then delete it
        val removeList = mutableListOf<SensorDesc>()
        openableCameraIds.forEach { id ->
            cameraIds2.removeAll {
                it.logicalCameraId != null && it.cameraId == id
            }
        }

        // Iterate over the list of cameras and return all the compatible ones
        cameraIds2.forEach { desc ->

            Log.i("SELECTOR", "Camera ${desc.cameraId} @ LogicalCam ${desc.logicalCameraId}")
            val characteristics = cameraManager.getCameraCharacteristics(desc.cameraId)
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

            val camId = if (desc.logicalCameraId == null)
                "${desc.cameraId}"
            else
                "${desc.cameraId}@${desc.logicalCameraId}"

            val title = "$camId vfov:$vfov $foc $ape $orientation"
            if (!availableCameras.any { it -> it.title == title }) {
                availableCameras.add(
                    SensorDesc(
                        title, desc.cameraId, desc.logicalCameraId, ImageFormat.JPEG
                    )
                )
            }
        }

        return availableCameras
    }
}