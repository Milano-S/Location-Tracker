package com.exclr8.locationtrackerdemo.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.location.Location
import android.os.*
import android.util.Log
import android.widget.Toast
import com.exclr8.locationtrackerdemo.MainActivity
import com.exclr8.locationtrackerdemo.R
import com.exclr8.locationtrackerdemo.handler.AndroidHandlerThread
import com.exclr8.locationtrackerdemo.handler.SimpleWorker
import com.exclr8.locationtrackerdemo.handler.UseHandlerThread
import com.exclr8.locationtrackerdemo.locus.LocationService
import com.exclr8.locationtrackerdemo.locus.ServiceStopBroadcastReceiver
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.jsonBody
import fr.quentinklein.slt.LocationTracker
import fr.quentinklein.slt.ProviderError
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*


private const val TAG = "EndlessService"

class EndlessService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceStarted = false

    var simpleWorker: SimpleWorker? = null
    var customHandlerThread: UseHandlerThread? = null
    var androidHandlerThread: AndroidHandlerThread? = null


    override fun onBind(intent: Intent): IBinder? {
        log("Some component want to bind with the service")
        // We don't provide binding, so return null
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("onStartCommand executed with startId: $startId")
        if (intent != null) {
            val action = intent.action
            log("using an intent with action $action")
            when (action) {
                Actions.START.name -> startService()
                Actions.STOP.name -> stopService()
                else -> log("This should never happen. No action in the received intent")
            }
        } else {
            log(
                "with a null intent. It has been probably restarted by the system."
            )
        }
        // by returning this we make sure the service is restarted if the system kills the service
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        log("The service has been created")
        val notification = createNotification()
        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        log("The service has been destroyed")
        Toast.makeText(this, "Service destroyed", Toast.LENGTH_SHORT).show()
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        val restartServiceIntent = Intent(applicationContext, EndlessService::class.java).also {
            it.setPackage(packageName)
        };
        val restartServicePendingIntent: PendingIntent =
            PendingIntent.getService(this, 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT);
        applicationContext.getSystemService(Context.ALARM_SERVICE);
        val alarmService: AlarmManager =
            applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager;
        alarmService.set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + 1000,
            restartServicePendingIntent
        )
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun startService() {
        if (isServiceStarted) return
        log("Starting the foreground service task")
        Toast.makeText(this, "Service starting its task", Toast.LENGTH_SHORT).show()
        isServiceStarted = true
        setServiceState(this, ServiceState.STARTED)

        // we need this lock so our service gets not affected by Doze Mode
        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EndlessService::lock").apply {
                    acquire(10 * 60 * 1000L /*10 minutes*/)
                }
            }

        // we're starting a loop in a coroutine
        GlobalScope.launch(Dispatchers.IO) {
            while (isServiceStarted) {
                GlobalScope.launch(Dispatchers.IO) {
                    pingFakeServer()
                }
                //Interval
                delay(3000)
            }
            log("End of the loop for the service")
        }
    }

    private fun executeOnCustomHandler() {
        customHandlerThread = UseHandlerThread()
        androidHandlerThread = AndroidHandlerThread()
        val task = java.lang.Runnable { startTracker(
            context = application.baseContext,
            minTimeBetweenUpdates = 1000L
        ) }
        androidHandlerThread?.execute(task)
    }

    private fun stopService() {
        log("Stopping the foreground service")
        Toast.makeText(this, "Service stopping", Toast.LENGTH_SHORT).show()
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            stopForeground(true)
            stopSelf()
        } catch (e: Exception) {
            log("Service stopped without being started: ${e.message}")
        }
        isServiceStarted = false
        setServiceState(this, ServiceState.STOPPED)
    }

    private fun pingFakeServer() {

        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val gmtTime = df.format(Date())

        val deviceId = Build.ID

        val json =
            """
                {
                    "deviceId": "$deviceId",
                    "createdAt": "$gmtTime"
                }
            """

        try {
            Fuel.post("https://jsonplaceholder.typicode.com/posts")
                .jsonBody(json)
                .response { _, _, result ->
                    val (bytes, error) = result
                    if (bytes != null) {
                        Log.i("JSON", "[response bytes] ${String(bytes)}")
                    } else {
                        log("[response error] ${error?.message}")
                    }
                }
        } catch (e: Exception) {
            log("Error making the request: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission", "SetTextI18n")
    private fun startTracker(context: Context, minTimeBetweenUpdates: Long) {

        val tracker = LocationTracker(
            minTimeBetweenUpdates = minTimeBetweenUpdates,
            minDistanceBetweenUpdates = 1F, // one meter
            shouldUseGPS = true,
            shouldUseNetwork = true,
            shouldUsePassive = true
        )

        tracker.addListener(object : LocationTracker.Listener {
            override fun onLocationFound(location: Location) {
                val lat = location.latitude.toString()
                val lng = location.longitude.toString()
                log("$lat, $lng")
            }

            override fun onProviderError(providerError: ProviderError) {
                log(providerError.message.toString())
            }
        })
        try {
            tracker.startListening(context)
        } catch (e: Exception) {
            Log.i(TAG, e.message.toString())
        }
    }

    private fun createNotification(): Notification {
        val notificationChannelId = "ENDLESS SERVICE CHANNEL"

        // depending on the Android API that we're dealing with we will have
        // to use a specific method to create the notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                notificationChannelId,
                "Endless Service notifications channel",
                NotificationManager.IMPORTANCE_DEFAULT
            ).let {
                it.description = "Endless Service channel"
                it.enableLights(true)
                it.lightColor = Color.RED
                it.enableVibration(true)
                it.vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
                it
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, 0)
            }

        val builder: Notification.Builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(
                this,
                notificationChannelId
            ) else Notification.Builder(this)


        return builder
            .setContentTitle("Endless Api Service")
            .setContentText("This is your favorite endless service working")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.ic_location)
            .setTicker("Ticker text")
            .build()
    }
}
