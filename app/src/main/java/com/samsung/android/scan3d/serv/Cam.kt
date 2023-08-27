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
import com.samsung.android.scan3d.KILL_THE_APP
import com.samsung.android.scan3d.R
import com.samsung.android.scan3d.fragments.ViewState
import com.samsung.android.scan3d.http.HttpService
import com.samsung.android.scan3d.serv.CameraActionState.NEW_PREVIEW_SURFACE
import com.samsung.android.scan3d.serv.CameraActionState.NEW_VIEW_STATE
import com.samsung.android.scan3d.serv.CameraActionState.ON_PAUSE
import com.samsung.android.scan3d.serv.CameraActionState.ON_RESUME
import com.samsung.android.scan3d.serv.CameraActionState.START
import com.samsung.android.scan3d.serv.CameraActionState.START_ENGINE
import kotlinx.coroutines.runBlocking

class Cam : Service() {

    var engine: CamEngine? = null
    var http: HttpService? = null
    val CHANNEL_ID = "REMOTE_CAM"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("CAM", "onStartCommand " + intent?.action)

        if (intent == null) return START_STICKY

        when (intent.action) {
            START.name -> {
                startNotificationService()
                startHttpService()
            }

            ON_PAUSE.name -> {
                engine?.insidePause = true
                if (engine?.isShowingPreview == true) {
                    engine?.restart()
                }
            }

            ON_RESUME.name -> {
                engine?.insidePause = false;
            }

            START_ENGINE.name -> {
                engine = CamEngine(this)
                engine?.http = http
                runBlocking { engine?.initializeCamera() }
            }

            NEW_VIEW_STATE.name -> {
                val old = engine?.viewState!!
                val new: ViewState = intent.extras?.getParcelable("data")!!
                engine?.viewState = new
                if (old != new) {
                    engine?.restart()
                }
            }

            NEW_PREVIEW_SURFACE.name -> {
                val surface: Surface? = intent.extras?.getParcelable("surface")
                engine?.previewSurface = surface
                if (engine?.viewState?.preview == true) {
                    runBlocking { engine?.initializeCamera() }
                }
            }

            else -> kill()
        }

        return START_STICKY
    }

    private fun startNotificationService() {
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_ID, NotificationManager.IMPORTANCE_DEFAULT
        ).also { it.description = "RemoteCam run" }

        val notificationManager = getSystemService(NotificationManager::class.java).also {
            it.createNotificationChannel(channel)
        }

        // Create a notification for the foreground service
        val notificationIntent = Intent(this, CameraActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            notificationIntent,
            FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT
        )

        val intentKill = Intent(KILL_THE_APP)
        val pendingIntentKill = PendingIntent.getBroadcast(
            this, System.currentTimeMillis().toInt(), intentKill, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("RemoteCam (active)")
            .setContentText("Click to open").setOngoing(true).setSmallIcon(R.drawable.ic_linked_camera)
            .addAction(R.drawable.ic_close, "Kill", pendingIntentKill).setContentIntent(pendingIntent)


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            //      builder?.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        val notification: Notification = builder.build()
        startForeground(123, notification) // Start the foreground service
    }

    private fun startHttpService() {
        http = HttpService()
        http?.main()
    }

    private fun kill() {
        engine?.destroy()
        http?.engine?.stop(500, 500)
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

    companion object { sealed class ToCam()
        class Start() : ToCam()
        class NewSurface(surface: Surface) : ToCam()
    }
}