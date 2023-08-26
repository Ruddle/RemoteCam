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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.example.android.camera.utils.OrientationLiveData
import com.samsung.android.scan3d.CameraActivity
import com.samsung.android.scan3d.R
import com.samsung.android.scan3d.databinding.FragmentCameraBinding
import com.samsung.android.scan3d.serv.CamEngine
import com.samsung.android.scan3d.util.ClipboardUtil
import com.samsung.android.scan3d.util.IpUtil
import com.samsung.android.scan3d.util.Selector
import kotlinx.parcelize.Parcelize

private const val DEFAULT_WIDTH = 1280
private const val DEFAULT_HEIGHT = 720

class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    /** Host's navigation controller */
    private val navController: NavController by lazy {
        Navigation.findNavController(requireActivity(), R.id.fragment_container)
    }

    /** AndroidX navigation arguments */
    //  private val args: CameraFragmentArgs by navArgs()

    private var resolutionWidth = DEFAULT_WIDTH
    private var resolutionHeight = DEFAULT_HEIGHT

    var viewState = ViewState(true, stream = false, cameraId = "0", quality = 80, resolutionIndex = null)

    lateinit var cameraActivity: CameraActivity

    /** Live data listener for changes in the device orientation relative to the camera */
    private lateinit var relativeOrientation: OrientationLiveData

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)

        binding.textView6.apply {
            text = "${IpUtil.getLocalIpAddress()}:8080/cam.mjpeg"
            setOnClickListener {
                ClipboardUtil.copyToClipboard(context, "ip", text.toString())
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }

        cameraActivity = requireActivity() as CameraActivity
        return binding.root
    }

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.extras?.getParcelable<CamEngine.Companion.DataQuick>("dataQuick")?.let {
                binding.qualFeedback.text = " " + it.rateKbs + "kB/sec"
                binding.ftFeedback.text = " " + it.ms + "ms"
            }

            intent.extras?.getParcelable<CamEngine.Companion.Data>("data")?.let {
                setViews(it)
            } ?: run {
                return
            }
        }

        private fun setViews(data: CamEngine.Companion.Data) {
            val resolution = data.resolutions[data.resolutionSelected]
            resolutionWidth = resolution.width
            resolutionHeight = resolution.height

            binding.viewFinder.setAspectRatio(resolutionWidth, resolutionHeight)
            setSwitchListeners()
            setSpinnerCam(data)
            setSpinnerQua()
            setSpinnerRes(data)
        }

        private fun setSwitchListeners() {
            binding.switch1.setOnCheckedChangeListener { _, prev ->
                viewState.preview = prev
                sendViewState()
            }
            binding.switch2.setOnCheckedChangeListener { _, prev ->
                viewState.stream = prev
                sendViewState()
            }
        }

        private fun setSpinnerRes(data: CamEngine.Companion.Data) {
            val outputFormats = data.resolutions
            val spinnerDataList = outputFormats.map(Size::toString)
            val spinnerAdapter = ArrayAdapter(
                requireContext(), android.R.layout.simple_spinner_item, spinnerDataList
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

            with(binding.spinnerRes) {
                adapter = spinnerAdapter
                viewState.resolutionIndex?.let {
                    setSelection(viewState.resolutionIndex!!)
                    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                            resolutionWidth = outputFormats[p2].width
                            resolutionHeight = outputFormats[p2].height
                            binding.viewFinder.setAspectRatio(resolutionWidth, resolutionHeight)
                            if (p2 != viewState.resolutionIndex) {
                                viewState.resolutionIndex = p2
                                sendViewState()
                            }
                        }

                        override fun onNothingSelected(p0: AdapterView<*>?) {}
                    }
                } ?: run {
                    Log.i("DEUIBGGGGGG", "NO PRIOR R, " + data.resolutionSelected)
                    viewState.resolutionIndex = data.resolutionSelected
                }
            }
        }

        private fun setSpinnerQua() {
            val spinnerDataList = arrayOf("1", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100")
            val spinnerAdapter = ArrayAdapter(
                requireContext(), android.R.layout.simple_spinner_item, spinnerDataList
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

            with(binding.spinnerQua) {
                adapter = spinnerAdapter
                setSelection(spinnerDataList.indexOfFirst { it.toInt() == viewState.quality })
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                        viewState.quality = spinnerDataList[p2].toInt()
                        sendViewState()
                    }

                    override fun onNothingSelected(p0: AdapterView<*>?) {}
                }
            }
        }

        private fun setSpinnerCam(data: CamEngine.Companion.Data) {
            val spinnerDataList = data.sensors.map(Selector.SensorDesc::title)
            val spinnerAdapter = ArrayAdapter(
                requireContext(), android.R.layout.simple_spinner_item, spinnerDataList
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

            with(binding.spinnerCam) {
                adapter = spinnerAdapter
                setSelection(data.sensors.indexOf(data.sensorSelected))
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                        if (viewState.cameraId != data.sensors[p2].cameraId) {
                            viewState.resolutionIndex = null
                        }
                        viewState.cameraId = data.sensors[p2].cameraId
                        sendViewState()
                    }

                    override fun onNothingSelected(p0: AdapterView<*>?) {}
                }
            }
        }
    }

    fun sendViewState() {
        cameraActivity.sendCam {
            it.action = "new_view_state"
            it.putExtra("data", viewState)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.i("onPause", "onPause")
        requireActivity().unregisterReceiver(receiver)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        Log.i("onResume", "onResume")
        requireActivity().registerReceiver(receiver, IntentFilter("UpdateFromCameraEngine"))
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.i("onViewCreated", "onViewCreated")


        cameraActivity.sendCam {
            it.action = "start_camera_engine"
        }
        // engine.start(requireContext())

        with(binding) {
            buttonKill.setOnClickListener {
                Log.i("CameraFrag", "KILL")
                val intent = Intent("KILL") //FILTER is a string to identify this intent
                requireContext().sendBroadcast(intent)
            }

            viewFinder.holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceDestroyed(holder: SurfaceHolder) = Unit

                override fun surfaceChanged(
                    holder: SurfaceHolder, format: Int, width: Int, height: Int
                ) = Unit

                override fun surfaceCreated(holder: SurfaceHolder) {
                    viewFinder.setAspectRatio(
                        resolutionWidth, resolutionHeight
                    )
                    cameraActivity.sendCam {
                        it.action = "new_preview_surface"
                        it.putExtra("surface", viewFinder.holder.surface)
                    }
                }
            })
        }
    }

    override fun onStop() {
        super.onStop()
        try {

        } catch (exc: Throwable) {
            Log.e(TAG, "Error closing camera", exc)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {

        private val TAG = CameraFragment::class.java.simpleName

        @Parcelize
        data class ViewState(
            var preview: Boolean,
            var stream: Boolean,
            var cameraId: String,
            var resolutionIndex: Int?,
            var quality: Int
        ) : Parcelable
    }
}
