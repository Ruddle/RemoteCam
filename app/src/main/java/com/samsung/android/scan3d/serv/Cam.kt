package com.samsung.android.scan3d.serv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Surface
import androidx.core.app.NotificationCompat
import com.samsung.android.scan3d.CameraActivity
import com.samsung.android.scan3d.R
import com.samsung.android.scan3d.fragments.CameraFragment
import com.samsung.android.scan3d.http.HttpService
import kotlinx.coroutines.runBlocking


class Cam : Service() {
    var engine: CamEngine? = null
    var http: HttpService? = null
    val CHANNEL_ID = "REMOTE_CAM"


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("CAM", "onStartCommand " + intent?.action)

        if (intent == null) return START_STICKY

        when (intent.action) {
            "start" -> {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_ID,
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                channel.description = "RemoteCam run"
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.createNotificationChannel(channel)

                // Create a notification for the foreground service
                val notificationIntent = Intent(this, CameraActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    System.currentTimeMillis().toInt(),
                    notificationIntent,
                    FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT
                )

                val intentKill = Intent("KILL")
                val pendingIntentKill = PendingIntent.getBroadcast(
                    this,
                    System.currentTimeMillis().toInt(),
                    intentKill,
                    FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT
                )


                var builer =
                    NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("RemoteCam (active)")
                        .setContentText("Click to open").setOngoing(true)
                        .setSmallIcon(R.drawable.ic_linked_camera).addAction(R.drawable.ic_close, "Kill",pendingIntentKill)
                        .setContentIntent(pendingIntent)



                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    //      builer?.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                }
                val notification: Notification = builer.build()
                startForeground(123, notification) // Start the foreground service

                http = HttpService()
                http?.main()

            }

            "onPause" -> {
                engine?.insidePause = true
                if (engine?.isShowingPreview == true) {
                    engine?.restart()
                }

            }

            "onResume" -> {
                engine?.insidePause = false;
            }

            "start_camera_engine" -> {
                engine = CamEngine(this)
                engine?.http = http
                runBlocking { engine?.initializeCamera() }
            }

            "new_view_state" -> {

                val old = engine?.viewState?.cameraIndex
                val parcelableViewState = intent.extras?.getParcelable<CameraFragment.Companion.ViewState>("data")
                val new = parcelableViewState?.cameraIndex
                Log.i("CAM", "new_view_state: " + new)
                Log.i("CAM", "from:           " + old)
                parcelableViewState?.let {
                    engine?.viewState = it
                }
                if (old != new) {
                    Log.i("CAM", "diff")
                    engine?.restart()
                }
            }

            "new_preview_surface" -> {
                val surface: Surface? = intent.extras?.getParcelable("surface")
                // Toast.makeText(this, "SURFACE", Toast.LENGTH_SHORT).show()
                engine?.previewSurface = surface
                if (engine?.viewState?.preview == true) {
                    runBlocking { engine?.initializeCamera() }
                }
            }

            else -> {
               kill()
            }

        }

        return START_STICKY
    }

    fun kill(){
        engine?.destroy()
        http?.engine?.stop(500,500)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("CAM", "OnDestroy")
        kill()
    }

    companion object {
        sealed class ToCam()
        class Start() : ToCam()
        class NewSurface(surface: Surface) : ToCam()

    }
}