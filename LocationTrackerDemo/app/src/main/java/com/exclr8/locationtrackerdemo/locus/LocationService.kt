package com.exclr8.locationtrackerdemo.locus

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelProvider
import com.birjuvachhani.locus.Locus
import com.birjuvachhani.locus.LocusResult
import com.exclr8.locationtrackerdemo.R
import com.exclr8.locationtrackerdemo.model.GeoFence
import com.exclr8.locationtrackerdemo.service.log
import com.exclr8.locationtrackerdemo.viewModel.ViewModelGeo
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import java.util.*

private const val TAG = "LocationService"
class LocationService : LifecycleService() {


    companion object {
        const val NOTIFICATION_ID = 787
        const val STOP_SERVICE_BROADCAST_ACTON =
            "com.exclr8.locationtrackerdemo.locus.ServiceStopBroadcastReceiver"
    }

    private val manager: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: throw Exception("No notification manager found")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Handler(Looper.getMainLooper()).postDelayed({
            start()
        }, 1000)
        return START_STICKY
    }

    private fun start() {
        startForeground(NOTIFICATION_ID, getNotification())
        Locus.configure {
            enableBackgroundUpdates = true
        }
        Locus.startLocationUpdates(this).observe(this@LocationService) { result ->
            log(result.location?.latitude.toString() + ", " + result.location?.longitude.toString())
            manager.notify(NOTIFICATION_ID, getNotification(result))
        }
    }

    @SuppressLint("MissingPermission")
    private fun getNotification(result: LocusResult? = null): Notification {

        val manager =
            getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: throw Exception("No notification manager found")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    "location",
                    "Location Updates",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        return with(NotificationCompat.Builder(this, "location")) {
            setContentTitle("Location Service")
            result?.apply {
                location?.let {
                    val geoF =  GeoFence(
                        name = "Exclr8",
                        latitude = -33.8002,
                        longitude = 18.5078,
                        radius = 200.0
                    )
                    //val geoF = ViewModelGeo().geoFenceVM
                     if (isInsideGeofence(
                             it.latitude,
                             it.longitude,
                           geoF
                     )){
                         setContentText("Inside GeoFence : ${geoF.name}")
                     }else{
                    setContentText("${it.latitude}, ${it.longitude}")
                     }
                } ?: setContentText("Error: ${error?.message}")
            } ?: setContentText("Trying to get location updates")
            setSmallIcon(R.drawable.ic_location)
            setAutoCancel(false)
            setOnlyAlertOnce(true)
            val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                else PendingIntent.FLAG_UPDATE_CURRENT
            addAction(
                0,
                "Stop Updates",
                PendingIntent.getBroadcast(
                    this@LocationService,
                    0,
                    Intent(this@LocationService, ServiceStopBroadcastReceiver::class.java).apply {
                        action = STOP_SERVICE_BROADCAST_ACTON
                    },
                    flags
                )
            )
            build()
        }
    }

    private fun isInsideGeofence(
        currentLat: Double,
        currentLng: Double,
        geoFence: GeoFence
    ): Boolean {

        val results = FloatArray(1)
        Location.distanceBetween(
            currentLat,
            currentLng,
            /*37.7766,
            -122.4508,*/
            geoFence.latitude,
            geoFence.longitude,
            results
        )
        val distanceInMeters = results[0]
        val isWithinRadius = distanceInMeters < geoFence.radius

        return if(isWithinRadius){
            Log.i(TAG, "is Inside GeoFence")
            true
        }else{
            Log.i(TAG, "not Inside GeoFence")
            false
        }
    }
}