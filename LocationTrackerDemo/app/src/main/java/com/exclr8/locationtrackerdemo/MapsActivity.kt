package com.exclr8.locationtrackerdemo

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Criteria
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.exclr8.locationtrackerdemo.databinding.ActivityMapsBinding
import com.exclr8.locationtrackerdemo.model.GeoFence
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import java.util.*
import kotlin.math.roundToInt

private const val TAG = "MapActivity"
class MapsActivity : AppCompatActivity(), GoogleMap.OnMarkerClickListener, OnMapReadyCallback {

    //private lateinit var mMap: GoogleMap
    private var mMap: GoogleMap? = null

    private lateinit var binding: ActivityMapsBinding

    private var geofence = GeoFence()

    private var geoFenceList = mutableListOf<GeoFence>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.newReminder.setOnClickListener {
            showConfigureLocationStep()
        }
        binding.currentLocation.setOnClickListener {
            val locationManager =
                this.getSystemService<LocationManager>() ?: return@setOnClickListener
            val bestProvider =
                locationManager.getBestProvider(Criteria(), false) ?: return@setOnClickListener

            @SuppressLint("MissingPermission")
            val location = locationManager.getLastKnownLocation(bestProvider)
            if (location != null) {
                val latLng = LatLng(location.latitude, location.longitude)
                mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
            }
        }
        binding.clearAll.setOnClickListener {
            Toast.makeText(this, "All Geofences Cleared", Toast.LENGTH_SHORT).show()
            mMap?.clear()
            geoFenceList.clear()
            showGeoFences()
        }
    }

    private fun showConfigureRadiusStep() {
        binding.apply {
            marker.isGone = true
            instructionTitle.isVisible = true
            instructionSubtitle.isGone = true
            radiusBar.isVisible = true
            radiusDescription.isVisible = true
            message.isGone = true
            instructionTitle.text = getString(R.string.instruction_radius_description)
            next.setOnClickListener {
                showConfigureMessageStep()
            }
            radiusBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    updateRadiusWithProgress(progress)
                    showGeoFenceUpdate()
                }

                override fun onStartTrackingTouch(p0: SeekBar?) {}

                override fun onStopTrackingTouch(p0: SeekBar?) {}
            })
            updateRadiusWithProgress(radiusBar.progress)
            mMap?.animateCamera(CameraUpdateFactory.zoomTo(15f))
            showGeoFenceUpdate()
        }
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        Log.i(TAG, marker.tag.toString())
        Toast.makeText(this, marker.title, Toast.LENGTH_SHORT).show()
        return true
    }

    private fun updateRadiusWithProgress(progress: Int) {
        val radius = getRadius(progress)
        geofence.radius = radius
        binding.radiusDescription.text =
            getString(R.string.radius_description, radius.roundToInt().toString())
    }

    private fun getRadius(progress: Int): Double {
        return 100 + (2 * progress.toDouble() + 1) * 100
    }

    private fun showConfigureMessageStep() {
        binding.apply {
            marker.isGone = true
            instructionTitle.isVisible = true
            instructionSubtitle.isGone = true
            radiusBar.isGone = true
            radiusDescription.isGone = true
            message.isVisible = true
            instructionTitle.text = getString(R.string.instruction_message_description)
            next.setOnClickListener {
                hideKeyBoard(this@MapsActivity, message)
                geofence.name = message.text.toString()
                if (geofence.name.isEmpty()) {
                    message.error = getString(R.string.error_required)
                } else {
                    addGeofence(geofence)
                }
            }
            message.requestFocus()
        }
        showGeoFenceUpdate()
    }

    private fun showGeoFenceUpdate(/*updatedGeofence: List<GeoFence>*/) {
        //mMap?.clear()
        mMap?.let { showGeofenceInMap(this, it, geofence) }
    }

    private fun addGeofence(geofence: GeoFence) {
        geoFenceList.addAll(mutableListOf(geofence))
        binding.container.isGone = true
        showGeoFences()
    }

    private fun showGeoFences() {
        mMap?.run {
            //clear()
            for (geoFence in geoFenceList) {
                mMap?.let { showGeofenceInMap(this@MapsActivity, it, geoFence) }
            }
        }
    }

    private fun hideKeyBoard(context: Context, message: EditText) {
        val keyboard = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        keyboard.hideSoftInputFromWindow(message.windowToken, 0)
    }

    private fun showConfigureLocationStep() {
        binding.apply {
            container.isVisible = true
            marker.isVisible = true
            instructionTitle.isVisible = true
            instructionSubtitle.isVisible = true
            radiusBar.isGone = true
            radiusDescription.isGone = true
            message.isGone = true
            instructionTitle.text = getString(R.string.instruction_where_description)
            next.setOnClickListener {
                geofence.latitude = mMap?.cameraPosition?.target?.latitude ?: 0.0
                geofence.longitude = mMap?.cameraPosition?.target?.longitude ?: 0.0
                showConfigureRadiusStep()
            }
            showGeoFenceUpdate()
        }
    }

    private fun showGeofenceInMap(
        context: Context,
        map: GoogleMap,
        geofence: GeoFence
    ) {

        val latLng = LatLng(geofence.latitude, geofence.longitude)
        val vectorToBitmap = vectorToBitmap(
            context.resources,
            R.drawable.ic_twotone_location_on_48px
        )
        val marker = map.addMarker(MarkerOptions().position(latLng).icon(vectorToBitmap))
        marker?.tag = geofence.name
        val radius = geofence.radius
        map.addCircle(
            CircleOptions()
                .center(latLng)
                .radius(radius)
                .strokeColor(
                    ContextCompat.getColor(
                        context,
                        R.color.colorAccent
                    )
                )
                .fillColor(
                    ContextCompat.getColor(
                        context,
                        R.color.colorReminderFill
                    )
                )
        )
    }

    private fun vectorToBitmap(resources: Resources, @DrawableRes id: Int): BitmapDescriptor {
        val vectorDrawable = ResourcesCompat.getDrawable(resources, id, null)
        val bitmap = Bitmap.createBitmap(
            vectorDrawable!!.intrinsicWidth,
            vectorDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        val locationManager = this.getSystemService<LocationManager>()
        val bestProvider = locationManager!!.getBestProvider(Criteria(), false)

        @SuppressLint("MissingPermission")
        val location = locationManager.getLastKnownLocation(bestProvider!!)
        if (location != null) {
            val latLng = LatLng(location.latitude, location.longitude)

            mMap!!.addMarker(MarkerOptions().position(latLng).title("You are here"))
            mMap!!.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        }
    }
}