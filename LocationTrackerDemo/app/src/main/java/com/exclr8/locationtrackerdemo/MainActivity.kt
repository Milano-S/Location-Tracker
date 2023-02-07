package com.exclr8.locationtrackerdemo

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.birjuvachhani.locus.Locus
import com.exclr8.locationtrackerdemo.locus.LocationService
import com.exclr8.locationtrackerdemo.service.*
import com.google.android.gms.location.*

private const val TAG = "Location"

class MainActivity : AppCompatActivity() {

    private val startBtn: Button by lazy { findViewById(R.id.btn_start) }
    private val stopBtn: Button by lazy { findViewById(R.id.btn_stop) }
    private val addGeo: Button by lazy { findViewById(R.id.btn_addGeoFence) }
    private val directionsBtn: Button by lazy { findViewById(R.id.btn_directions) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val request = LocationRequest.create()
        Intent(this, MainActivity::class.java).apply {
            putExtra("request", request)
        }
        Locus.setLogging(true)
    }

    override fun onStart() {
        super.onStart()
        initTracker()
        log("onStart")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        initTracker()
    }

    private fun initTracker() {
        val hasFineLocation = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFineLocation || !hasCoarseLocation) {
            return ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                1337
            )
        }
        startBtn.setOnClickListener {
            actionOnService(Actions.START)
            startService(Intent(this, LocationService::class.java))
            finish()
        }
        stopBtn.setOnClickListener {
            Locus.stopLocationUpdates()
            actionOnService(Actions.STOP)
        }
        addGeo.setOnClickListener {
            startActivity(
                Intent(this, MapsActivity::class.java)
            )
        }
        directionsBtn.setOnClickListener {
            startActivity(
                Intent(this, DirectionsActivity::class.java)
            )
        }
    }

    fun actionOnService(action: Actions) {
        if (getServiceState(this) == ServiceState.STOPPED && action == Actions.STOP) return
        Intent(this, EndlessService::class.java).also {
            it.action = action.name
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(it)
                return
            }
            startService(it)
        }
    }
}