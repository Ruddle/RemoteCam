package com.samsung.android.scan3d.serv

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.os.Parcelable
import android.util.Log
import android.util.Size
import android.view.Surface
import com.samsung.android.scan3d.fragments.CameraFragment2
import com.samsung.android.scan3d.fragments.SelectorFragment
import com.samsung.android.scan3d.http.HttpService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.parcelize.Parcelize
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
/*
import org.libjpegturbo.turbojpeg.TJ
import org.libjpegturbo.turbojpeg.TJCompressor
import org.libjpegturbo.turbojpeg.YUVImage */
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class CamEngine(val context: Context) {

    var http: HttpService? = null

    /*
    val tjc = TJCompressor()
    val preplanes = arrayOf(ByteArray(1_000_000), ByteArray(1_000_000), ByteArray(1_000_000))
    val dstBuf = ByteArray(10_000_000)
    */

    var resW = 1280
    var resH = 720


    var insidePause = false

    var isShowingPreview: Boolean = false

    private var cameraManager: CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraList: List<SelectorFragment.Companion.SensorDesc> =
        SelectorFragment.enumerateCameras(cameraManager)

    val camOutPutFormat = ImageFormat.JPEG // ImageFormat.YUV_420_888// ImageFormat.JPEG

    val executor = Executors.newSingleThreadExecutor()


    fun getEncoder(mimeType: String, resW: Int, resH: Int): MediaCodec? {
        fun selectCodec(mimeType: String, needEncoder: Boolean): MediaCodecInfo? {
            val list = MediaCodecList(0).getCodecInfos()
            list.forEach {
                if (it.isEncoder) {
                    Log.i(
                        "CODECS",
                        "We got type " + it.name + " " + it.supportedTypes.contentToString()
                    )
                    if (it.supportedTypes.any { e -> e.equals(mimeType, ignoreCase = true) }) {
                        return it
                    }
                }
            }
            return null
        }

        val colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatCbYCrY;
        val codec = selectCodec("video/avc", true) ?: return null
        val format = MediaFormat.createVideoFormat("video/avc", resW, resH)
        format.setString(MediaFormat.KEY_MIME, "video/avc");
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000);
        Log.i("CODECS", "video/avc: " + codec)
        val encoder = MediaCodec.createByCodecName(codec.getName());
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();
        return encoder
    }


    var viewState: CameraFragment2.Companion.ViewState = CameraFragment2.Companion.ViewState(
        true,
        stream = false,
        cameraId = "0",
        quality = 80,
        resolutionIndex = null
    )

    /** [CameraCharacteristics] corresponding to the provided Camera ID */
    var characteristics: CameraCharacteristics =
        cameraManager.getCameraCharacteristics(viewState.cameraId)

    var sizes = characteristics.get(
        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
    )!!.getOutputSizes(camOutPutFormat).reversed()


    private lateinit var imageReader: ImageReader


    private val cameraThread = HandlerThread("CameraThread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    private lateinit var camera: CameraDevice

    var previewSurface: Surface? = null

    private var session: CameraCaptureSession? = null

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

    fun restart() {
        stopRunning()
        runBlocking { initializeCamera() }

    }

    @SuppressLint("MissingPermission")
    private suspend fun openCamera(
        manager: CameraManager,
        cameraId: String,
        handler: Handler? = null
    ): CameraDevice = suspendCancellableCoroutine { cont ->
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) = cont.resume(device)

            override fun onDisconnected(device: CameraDevice) {
                Log.w("CamEngine", "Camera $cameraId has been disconnected")

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
                Log.e("CamEngine", exc.message, exc)
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
                Log.e("CamEngine", exc.message, exc)
                cont.resumeWithException(exc)
            }
        }, handler)
    }

    suspend fun initializeCamera() {
        Log.i("CAMERA", "initializeCamera")


        val showLiveSurface = viewState.preview && !insidePause && previewSurface != null
        isShowingPreview = showLiveSurface

        stopRunning()


        characteristics = cameraManager.getCameraCharacteristics(viewState.cameraId)
        sizes = characteristics.get(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
        )!!.getOutputSizes(camOutPutFormat).reversed()

        if (viewState.resolutionIndex == null) {

            var selIndex = 0
            for (formatIndex in 0 until sizes.size) {
                if (sizes[formatIndex].height <= 720) {
                    selIndex = formatIndex
                }
            }
            viewState.resolutionIndex = selIndex
        }
        resW = sizes[viewState.resolutionIndex!!].width
        resH = sizes[viewState.resolutionIndex!!].height



        camera = openCamera(cameraManager, viewState.cameraId, cameraHandler)
        imageReader = ImageReader.newInstance(
            resW, resH, camOutPutFormat, 4
        )
        var targets = listOf(imageReader.surface)
        if (showLiveSurface) {


            targets = targets.plus(previewSurface!!)
        }
        session = createCaptureSession(camera, targets, cameraHandler)
        val captureRequest = camera.createCaptureRequest(
            CameraDevice.TEMPLATE_RECORD //TEMPLATE_PREVIEW
        )
        if (showLiveSurface) {
            captureRequest.addTarget(previewSurface!!)
        }
        captureRequest.addTarget(imageReader.surface)
        captureRequest.set(CaptureRequest.JPEG_QUALITY, viewState.quality.toByte())
        var lastTime = System.currentTimeMillis()


        var kodd = 0
        var aquired = AtomicInteger(0)
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

                    if (aquired.get() > 1 && lastImg != null) {
                        lastImg.close()
                        Log.i("COM", "EARLY CLOSE")
                        lastImg = null
                    }

                    val img = lastImg ?: return
                    aquired.incrementAndGet()
                    var curTime = System.currentTimeMillis()
                    val delta = curTime - lastTime
                    lastTime = curTime
                    kodd += 1

                    if (camOutPutFormat == ImageFormat.JPEG) {
                        // executor.execute(Runnable {
                        val buffer = img.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }

                        if (kodd % 10 == 0) {
                            updateViewQuick(
                                DataQuick(
                                    delta.toInt(),
                                    (30 * bytes.size / 1000)
                                )
                            )
                        }

                        img.close()
                        aquired.decrementAndGet()
                        if (viewState.stream) {

                            http?.channel?.trySend(
                                bytes
                            )

                        }
                        //     })

                    } else {
                        /*  executor.execute(Runnable {
                              for (i in 0 until 1) {
                                  img.planes[i].buffer.get(
                                      preplanes[i],
                                      0,
                                      img.planes[i].buffer.remaining()
                                  )
                              }
                              //  preplanes[0]= img.planes[0].buffer.array()
                              // val buffer2 : Array< ByteArray> =  img.planes.map{ByteArray(it.buffer.remaining()).apply { it.buffer.get(this) }}.toTypedArray()
                              val strieds: IntArray =
                                  img.planes.map { it.rowStride / it.pixelStride }.toIntArray()
                              // Log.i("JPEG", "places! " + img.planes.size)

                              val uPlane = img.planes[1]
                              val vPlane = img.planes[2]
                              val uvRowStride = uPlane.rowStride
                              val uvPixelStride = uPlane.pixelStride
                              var index = 0
                              val pp1 = preplanes[1]
                              val pp2 = preplanes[2]
                              for (j in 0 until resH / 2) {
                                  for (i in 0 until resW / 2) {
                                      val bufferIndex = j * uvRowStride + i * uvPixelStride
                                      val u = uPlane.buffer[bufferIndex]
                                      val v = uPlane.buffer[Math.min(460800 - 2, (bufferIndex + 1))]
                                      pp1[index] = u
                                      pp2[index] = v
                                      index += 1
                                  }
                              }


                              val yuvI = YUVImage(
                                  preplanes,
                                  null,
                                  resW,
                                  strieds,
                                  resH,
                                  TJ.SAMP_420
                              )
                              //   tjc.setSourceImage(bytes, 0, 0, resW, 0, resH, TJ.SAMP_420)
                              tjc.setSourceImage(yuvI)
                              tjc.setJPEGQuality(viewState.quality)
                              tjc.setSubsamp(TJ.SAMP_420)
                              val outputBytes: ByteArray = dstBuf
                              tjc.compress(dstBuf, 0)
                              img.close()

                              //val output = BitmapFactory.decodeByteArray(outputBytes, 0, tjc.compressedSize)
                              if (kodd % 10 == 0) {
                                  updateViewQuick(
                                      DataQuick(
                                          delta.toInt(),
                                          (30 * tjc.compressedSize / 1000)
                                      )
                                  )
                              }

                              if (viewState.stream) {
                                  http?.channel?.trySend(
                                      outputBytes.sliceArray(
                                          IntRange(
                                              0,
                                              tjc.compressedSize - 1
                                          )
                                      )
                                  ) //bytes)
                              }
                              aquired.decrementAndGet()

                          })*/

                    }


                }
            },
            cameraHandler
        )
        updateView()
    }

    fun destroy() {
        stopRunning()
        cameraThread.quitSafely()
    }

    fun updateView() {
        val intent = Intent("UpdateFromCameraEngine") //FILTER is a string to identify this intent
        intent.putExtra(
            "data",
            Data(
                cameraList,
                cameraList.find { it.cameraId == viewState.cameraId }!!,
                resolutions = sizes,
                resolutionSelected = viewState.resolutionIndex!!
            )
        )
        context.sendBroadcast(intent)
    }

    fun updateViewQuick(dq: DataQuick) {
        val intent = Intent("UpdateFromCameraEngine") //FILTER is a string to identify this intent
        intent.putExtra(
            "dataQuick", dq
        )
        context.sendBroadcast(intent)
    }

    companion object {
        @Parcelize
        data class Data(
            val sensors: List<SelectorFragment.Companion.SensorDesc>,
            val sensorSelected: SelectorFragment.Companion.SensorDesc,
            val resolutions: List<Size>,
            val resolutionSelected: Int,
        ) : Parcelable

        @Parcelize
        data class DataQuick(
            val ms: Int,
            val rateKbs: Int
        ) : Parcelable
    }


}