
package com.exclr8.locationtrackerdemo.locus

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.birjuvachhani.locus.Locus
import com.exclr8.locationtrackerdemo.MainActivity
import com.exclr8.locationtrackerdemo.service.Actions
import com.exclr8.locationtrackerdemo.service.EndlessService


internal class ServiceStopBroadcastReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "ServiceStopBroadcastReceiver"
    }

    @SuppressLint("LongLogTag")
    override fun onReceive(context: Context, intent: Intent) {
        Log.e(TAG, "Received broadcast to stop location updates")
        Locus.stopLocationUpdates()
        context.stopService(Intent(context, LocationService::class.java))
    }
}