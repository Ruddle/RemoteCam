/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.samsung.android.scan3d.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.LayoutInflater
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.example.android.camera.utils.OrientationLiveData
import com.example.android.camera.utils.getPreviewOutputSize
import com.samsung.android.scan3d.CameraActivity
import com.samsung.android.scan3d.R
import com.samsung.android.scan3d.databinding.FragmentCameraBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


class CameraFragment : Fragment() {

    /** Android ViewBinding */
    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding get() = _fragmentCameraBinding!!

    /** AndroidX navigation arguments */
    //  private val args: CameraFragmentArgs by navArgs()

    var resW = 1280
    var resH = 720
    var showLive = true
    var enableStream = false

    /** Host's navigation controller */
    private val navController: NavController by lazy {
        Navigation.findNavController(requireActivity(), R.id.fragment_container)
    }

    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    private lateinit var cameraManager: CameraManager

    private lateinit var cameraList: List<SelectorFragment.Companion.FormatItem>
    private lateinit var selectedCameraId: String
    private var quality: Int = 80

    /** [CameraCharacteristics] corresponding to the provided Camera ID */
    private lateinit var characteristics: CameraCharacteristics


    /** Readers used as buffers for camera still shots */
    private lateinit var imageReader: ImageReader

    /** [HandlerThread] where all camera operations run */
    private val cameraThread = HandlerThread("CameraThread").apply { start() }

    /** [Handler] corresponding to [cameraThread] */
    private val cameraHandler = Handler(cameraThread.looper)


    /** The [CameraDevice] that will be opened in this fragment */
    private lateinit var camera: CameraDevice

    /** Internal reference to the ongoing [CameraCaptureSession] configured with our parameters */
    private var session: CameraCaptureSession? = null

    /** Live data listener for changes in the device orientation relative to the camera */
    private lateinit var relativeOrientation: OrientationLiveData

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)
        return fragmentCameraBinding.root
    }

    fun recomp(view: View) {
        run {

            val outputFormats = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
            )!!.getOutputSizes(ImageFormat.JPEG)
            outputFormats.reverse()
            val spinner = fragmentCameraBinding.spinnerRes
            val spinnerDataList = ArrayList<Any>()
            outputFormats.forEach { spinnerDataList.add(it.toString()) }
            // Initialize the ArrayAdapter
            val spinnerAdapter =
                ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    spinnerDataList
                )
            // Set the dropdown layout style
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Set the adapter for the Spinner
            spinner!!.adapter = spinnerAdapter

            var selIndex = 0
            for (formatIndex in 0 until outputFormats.size) {
                if (outputFormats[formatIndex].height <= 720) {
                    selIndex = formatIndex
                }
            }
            spinner.setSelection(selIndex)
            spinner.onItemSelectedListener = object : OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    resW = outputFormats[p2].width
                    resH = outputFormats[p2].height
                    Log.i("CAMERA", "onItemSelected " + resW)
                    if (session != null) {
                        view.post { initializeCamera() }
                    }
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                }
            }
        }

    }

    override fun onPause() {
        super.onPause()
        Log.i("onPause", "onPause")
        stopRunning()

    }

    override fun onResume() {
        super.onResume()
        Log.i("onResume", "onResume")
    }


    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.i("onViewCreated", "onViewCreated")

        /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
        cameraManager = run {
            val context = requireContext().applicationContext
            context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        }

        cameraList = SelectorFragment.enumerateCameras(cameraManager)
        selectedCameraId = cameraList[0].cameraId
        /** [CameraCharacteristics] corresponding to the provided Camera ID */
        characteristics =
            cameraManager.getCameraCharacteristics(selectedCameraId)


        run {
            val spinner = fragmentCameraBinding.spinnerQua
            val spinnerDataList = ArrayList<Any>()
            val quals = arrayOf(1, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100)
            quals.forEach { spinnerDataList.add(it.toString()) }
            // Initialize the ArrayAdapter
            val spinnerAdapter =
                ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    spinnerDataList
                )
            // Set the dropdown layout style
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Set the adapter for the Spinner
            spinner!!.adapter = spinnerAdapter
            spinner.setSelection(8)
            spinner.onItemSelectedListener = object : OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    quality = quals[p2]
                    Log.i("CAMERA", "onItemSelected " + quality)
                    if (session != null) {
                        view.post { initializeCamera() }
                    }
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                }
            }
        }


        run {
            val spinner = fragmentCameraBinding.spinnerCam
            val spinnerDataList = ArrayList<Any>()
            cameraList.forEach { spinnerDataList.add(it.title) }
            // Initialize the ArrayAdapter
            val spinnerAdapter =
                ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    spinnerDataList
                )
            // Set the dropdown layout style
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Set the adapter for the Spinner
            spinner!!.adapter = spinnerAdapter
            spinner.onItemSelectedListener = object : OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    selectedCameraId = cameraList[p2].cameraId
                    characteristics = cameraManager.getCameraCharacteristics(selectedCameraId)
                    Log.i("CAMERA", "onItemSelected " + resW)


                    recomp(view)
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                }
            }
        }




        recomp(view)


        fragmentCameraBinding.switch1?.setOnCheckedChangeListener(object : OnCheckedChangeListener {
            override fun onCheckedChanged(p0: CompoundButton?, p1: Boolean) {
                showLive = p1
                Log.i("CAMERA", "show live " + p1)
                if (session != null) {
                    view.post { initializeCamera() }
                }
            }
        })
        fragmentCameraBinding.switch2?.setOnCheckedChangeListener(object : OnCheckedChangeListener {
            override fun onCheckedChanged(p0: CompoundButton?, p1: Boolean) {
                enableStream = p1
                Log.i("CAMERA", "enableStream " + p1)
                if (session != null) {
                    view.post { initializeCamera() }
                }
            }
        })
        fragmentCameraBinding.viewFinder.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) = Unit

            override fun surfaceCreated(holder: SurfaceHolder) {
                // Selects appropriate preview size and configures view finder
                val previewSize = getPreviewOutputSize(
                    fragmentCameraBinding.viewFinder.display,
                    characteristics,
                    SurfaceHolder::class.java
                )
                Log.d(
                    TAG,
                    "View finder size: ${fragmentCameraBinding.viewFinder.width} x ${fragmentCameraBinding.viewFinder.height}"
                )
                Log.d(TAG, "Selected preview size: $previewSize")

                fragmentCameraBinding.viewFinder.setAspectRatio(
                    resW, resH
                    //previewSize.width,
                    //previewSize.height
                )

                // To ensure that size is set, initialize camera in the view's thread
                view.post { initializeCamera() }
            }
        })


    }


    private fun stopRunning() {
        if (session != null) {
            Log.i("CAMERA", "close")
            session!!.stopRepeating()
            session!!.close()
            session = null
            camera.close()
            imageReader.close()
        }
    }

    /**
     * Begin all camera operations in a coroutine in the main thread. This function:
     * - Opens the camera
     * - Configures the camera session
     * - Starts the preview by dispatching a repeating capture request
     * - Sets up the still image capture listeners
     */
    private fun initializeCamera() = lifecycleScope.launch(Dispatchers.Main) {
        Log.i("CAMERA", "initializeCamera")
        stopRunning()
        fragmentCameraBinding.viewFinder.setAspectRatio(resW, resH)
        // Open the selected camera
        camera = openCamera(cameraManager, selectedCameraId, cameraHandler)

        // Initialize an image reader which will be used to capture still photos
        val size = characteristics.get(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
        )!!
            .getOutputSizes(ImageFormat.JPEG).maxByOrNull { it.height * it.width }!!

        imageReader = ImageReader.newInstance(
            resW, resH, ImageFormat.JPEG, IMAGE_BUFFER_SIZE
        )

        // Creates list of Surfaces where the camera will output frames
        var targets = listOf(imageReader.surface)

        if (showLive) {

            targets = targets.plus(fragmentCameraBinding.viewFinder.holder.surface)

        }

        // Start a capture session using our open camera and list of Surfaces where frames will go
        session = createCaptureSession(camera, targets, cameraHandler)


        val captureRequest = camera.createCaptureRequest(
            CameraDevice.TEMPLATE_PREVIEW
        )

        if (showLive) {
            captureRequest.addTarget(fragmentCameraBinding.viewFinder.holder.surface)

        } else {
            /*      val holder = fragmentCameraBinding.viewFinder.holder
                  val canvas = holder.lockHardwareCanvas()
                  canvas.drawRGB(0, 0, 0);
                  holder.unlockCanvasAndPost(canvas) */


        }
        captureRequest.addTarget(imageReader.surface)
        captureRequest.set(CaptureRequest.JPEG_QUALITY, quality.toByte())
        // This will keep sending the capture request as frequently as possible until the
        // session is torn down or session.stopRepeating() is called

        var lastTime = System.currentTimeMillis()

        var encoding = false

        var kodd = 0
        session!!.setRepeatingRequest(
            captureRequest.build(),
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)

                    var lastImg = imageReader.acquireNextImage()
                    while (lastImg != null) {
                        val next = imageReader.acquireNextImage()
                        if (next == null) {
                            break;
                        } else {
                            lastImg.close()
                            lastImg = next
                        }
                    }
                    // val img = imageReader.acquireNextImage() ?: return
                    val img = lastImg ?: return

                    if (encoding) {
                        img.close()
                        return
                    }

                    var curTime = System.currentTimeMillis()
                    //    Log.i("TIME", "" + (curTime - lastTime))
                    lastTime = curTime


                    // Convert to jpeg

                    // Convert to jpeg
                    if (enableStream) {
                        kodd += 1

                        /*
                        encoding=true
                        lifecycleScope.launch(Dispatchers.Main) {
                            val yuvImage = toYuvImage(img)
                            val width: Int = img.getWidth()
                            val height: Int = img.getHeight()
                            var jpegImage: ByteArray? = null
                            ByteArrayOutputStream().use { out ->
                                yuvImage!!.compressToJpeg(Rect(0, 0, width, height), 80, out)
                                jpegImage = out.toByteArray()

                                (activity as CameraActivity?)?.http?.channel?.trySend(jpegImage!!)

                                img.close()
                                encoding=false
                            }


                        }
                        */

                        val buffer = img.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }

                        if (kodd % 10 == 0) {
                            activity?.runOnUiThread(Runnable {
                                // Stuff that updates the UI
                                fragmentCameraBinding.qualFeedback?.text =
                                    " " + (30 * bytes.size / 1000) + "kB/sec"
                            })

                        }

                        (activity as CameraActivity?)?.http?.channel?.trySend(bytes)
                        img.close()
                    } else {
                        img.close()
                    }


                }
            },
            cameraHandler
        )


    }

    /** Opens the camera and returns the opened device (as the result of the suspend coroutine) */
    @SuppressLint("MissingPermission")
    private suspend fun openCamera(
        manager: CameraManager,
        cameraId: String,
        handler: Handler? = null
    ): CameraDevice = suspendCancellableCoroutine { cont ->
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) = cont.resume(device)

            override fun onDisconnected(device: CameraDevice) {
                Log.w(TAG, "Camera $cameraId has been disconnected")
                requireActivity().finish()
            }

            override fun onError(device: CameraDevice, error: Int) {
                val msg = when (error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                val exc = RuntimeException("Camera $cameraId error: ($error) $msg")
                Log.e(TAG, exc.message, exc)
                if (cont.isActive) cont.resumeWithException(exc)
            }
        }, handler)
    }

    /**
     * Starts a [CameraCaptureSession] and returns the configured session (as the result of the
     * suspend coroutine
     */
    private suspend fun createCaptureSession(
        device: CameraDevice,
        targets: List<Surface>,
        handler: Handler? = null
    ): CameraCaptureSession = suspendCoroutine { cont ->

        // Create a capture session using the predefined targets; this also involves defining the
        // session state callback to be notified of when the session is ready
        device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {

            override fun onConfigured(session: CameraCaptureSession) = cont.resume(session)

            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }, handler)
    }


    override fun onStop() {
        super.onStop()
        try {
            camera.close()
        } catch (exc: Throwable) {
            Log.e(TAG, "Error closing camera", exc)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraThread.quitSafely()
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()
    }

    companion object {
        private val TAG = CameraFragment::class.java.simpleName

        /** Maximum number of images that will be held in the reader's buffer */
        private const val IMAGE_BUFFER_SIZE: Int = 3

        /** Maximum time allowed to wait for the result of an image capture */
        private const val IMAGE_CAPTURE_TIMEOUT_MILLIS: Long = 5000

        /** Helper data class used to hold capture metadata with their associated image */
        data class CombinedCaptureResult(
            val image: Image,
            val metadata: CaptureResult,
            val orientation: Int,
            val format: Int
        ) : Closeable {
            override fun close() = image.close()
        }

        /**
         * Create a [File] named a using formatted timestamp with the current date and time.
         *
         * @return [File] created.
         */
        private fun createFile(context: Context, extension: String): File {
            val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
            return File(context.filesDir, "IMG_${sdf.format(Date())}.$extension")
        }

        fun toYuvImage(image: Image): YuvImage? {
            require(image.format == ImageFormat.YUV_420_888) { "Invalid image format" }
            val width = image.width
            val height = image.height

            // Order of U/V channel guaranteed, read more:
            // https://developer.android.com/reference/android/graphics/ImageFormat#YUV_420_888
            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]
            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer

            // Full size Y channel and quarter size U+V channels.
            val numPixels = (width * height * 1.5f).toInt()
            val nv21 = ByteArray(numPixels)
            var index = 0

            // Copy Y channel.
            val yRowStride = yPlane.rowStride
            val yPixelStride = yPlane.pixelStride
            for (y in 0 until height) {
                for (x in 0 until width) {
                    nv21[index++] = yBuffer[y * yRowStride + x * yPixelStride]
                }
            }

            // Copy VU data; NV21 format is expected to have YYYYVU packaging.
            // The U/V planes are guaranteed to have the same row stride and pixel stride.
            val uvRowStride = uPlane.rowStride
            val uvPixelStride = uPlane.pixelStride
            val uvWidth = width / 2
            val uvHeight = height / 2
            for (y in 0 until uvHeight) {
                for (x in 0 until uvWidth) {
                    val bufferIndex = y * uvRowStride + x * uvPixelStride
                    // V channel.
                    nv21[index++] = vBuffer[bufferIndex]
                    // U channel.
                    nv21[index++] = uBuffer[bufferIndex]
                }
            }
            return YuvImage(
                nv21, ImageFormat.NV21, width, height,  /* strides= */null
            )
        }
    }
}
