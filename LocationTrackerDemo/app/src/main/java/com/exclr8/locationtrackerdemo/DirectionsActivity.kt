package com.exclr8.locationtrackerdemo

import android.Manifest.permission
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.directions.route.*
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.snackbar.Snackbar
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

private const val LOCATION_REQUEST_CODE = 23
class DirectionsActivity : FragmentActivity(), OnMapReadyCallback,
    GoogleApiClient.OnConnectionFailedListener, RoutingListener {
    //google map object
    private var mMap: GoogleMap? = null

    //current and destination location objects
    var myLocation: Location? = null
    var destinationLocation: Location? = null
    var start: LatLng? = null
    var end: LatLng? = null
    var locationPermission = false
    var isRouteEnabled = false

    val routeList = mutableListOf<LatLng>()
    private lateinit var previousRouteLocation : LatLng

    //polyline object
    private var polylines: MutableList<Polyline>? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_direction)

        //request location permission.
        requestPermision()

        //init google map fragment to show map.
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment!!.getMapAsync(this)

        //Search
        val btnSearch = findViewById<Button>(R.id.btnSearch)
        btnSearch.setOnClickListener {  searchLocation() }
        //Maps
        val btnMaps = findViewById<Button>(R.id.btnMaps)
        btnMaps.setOnClickListener {
            if (end != null) {
                launchGoogleMaps(
                    this,
                    end!!.latitude,
                    end!!.longitude,
                    "Destination"
                )
            } else {
                showToast("Invalid Destination")
            }
        }
        //Route
        val btnRoute = findViewById<Button>(R.id.btnRoute)
        btnRoute.setOnClickListener {
            isRouteEnabled = !isRouteEnabled
            if (isRouteEnabled) {
                btnRoute.text = "Route Mode Enabled"
            } else {
                btnRoute.text = "Create Route"
            }
        }
    }

    private fun requestPermision() {
        if (ContextCompat.checkSelfPermission(
                this,
                permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(permission.ACCESS_COARSE_LOCATION),
                LOCATION_REQUEST_CODE
            )
        } else {
            locationPermission = true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_REQUEST_CODE -> {
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //if permission granted.
                    locationPermission = true
                    getMyLocation()
                } else {
                    locationPermission = false
                }
            }
        }
    }

    //to get user location
    @SuppressLint("MissingPermission")
    private fun getMyLocation() {

        mMap!!.isMyLocationEnabled = true
        mMap!!.setOnMyLocationChangeListener { location: Location? -> myLocation = location }

        //get destination location when user click on map
        mMap!!.setOnMapClickListener { latLng ->
            end = latLng
            previousRouteLocation = latLng
            routeList.addAll(mutableListOf(previousRouteLocation))

            if(!isRouteEnabled){
                mMap!!.clear()
                routeList.clear()
            }
            start = LatLng(myLocation!!.latitude, myLocation!!.longitude)

            //Start route finding
            if (end != null) {
                findRoutes(start, end)
            } else {
                showToast("Invalid Destination")
            }
        }
    }

    fun showToast(message: String?) {
        Toast.makeText(applicationContext, "No end destination", Toast.LENGTH_SHORT).show()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        getMyLocation()
    }

    // function to find Routes.
    fun findRoutes(Start: LatLng?, End: LatLng?) {
        if (Start == null || End == null) {
            Toast.makeText(this@DirectionsActivity, "Unable to get location", Toast.LENGTH_LONG)
                .show()
        } else {
            val routing = Routing.Builder()
                .travelMode(AbstractRouting.TravelMode.DRIVING)
                .withListener(this)
                //.waypoints()
                .alternativeRoutes(true)
                .waypoints(Start, End)
                .key(getString(R.string.google_directions_key)) //also define your api key here.
                .build()
            routing.execute()
        }
    }

    //Routing call back functions.
    override fun onRoutingFailure(e: RouteException) {
        val parentLayout = findViewById<View>(android.R.id.content)
        val snackbar = Snackbar.make(parentLayout, e.toString(), Snackbar.LENGTH_LONG)
        snackbar.show()
        findRoutes(start, end)
    }

    override fun onRoutingStart() {
        //Toast.makeText(this@DirectionsActivity, "Finding Route...", Toast.LENGTH_LONG).show()
    }

    //If Route finding success..
    override fun onRoutingSuccess(route: ArrayList<Route>, shortestRouteIndex: Int) {

        //CameraUpdate center = CameraUpdateFactory.newLatLng(start);
        //CameraUpdate zoom = CameraUpdateFactory.zoomTo(16);

        /*if (polylines != null) {
            polylines!!.clear()
        }*/
        val polyOptions = PolylineOptions()
        var polylineStartLatLng: LatLng? = null
        var polylineEndLatLng: LatLng? = null
        polylines = ArrayList()
        //add route(s) to the map using polyline
        for (i in route.indices) {
            if (i == shortestRouteIndex) {
                polyOptions.color(resources.getColor(R.color.colorPrimary))
                polyOptions.width(7f)
                polyOptions.addAll(route[shortestRouteIndex].points)
                val polyline = mMap!!.addPolyline(polyOptions)
                polylineStartLatLng = polyline.points[0]
                val k = polyline.points.size
                polylineEndLatLng = polyline.points[k - 1]
                (polylines as ArrayList<Polyline>).add(polyline)
            }
        }

        //Add Marker on route starting position
        val startMarker = MarkerOptions()
        startMarker.position(polylineStartLatLng!!)
        startMarker.title("My Location")
        mMap!!.addMarker(startMarker)

        //Add Marker on route ending position
        val endMarker = MarkerOptions()
        endMarker.position(polylineEndLatLng!!)
        endMarker.title("Destination")
        mMap!!.addMarker(endMarker)
    }

    override fun onRoutingCancelled() {
        findRoutes(start, end)
    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        findRoutes(start, end)
    }

    private fun searchLocation() {
        val locationSearch = findViewById<View>(R.id.editText) as EditText
        val location = locationSearch.text.toString()
        var addressList: List<Address>? = null
        if (location != null || location != "") {
            val geocoder = Geocoder(this)
            try {
                addressList = geocoder.getFromLocationName(location, 1)
            } catch (e: IOException) {
                e.printStackTrace()
            }
            val address = addressList!![0]
            val latLng = LatLng(address.latitude, address.longitude)
            mMap!!.addMarker(MarkerOptions().position(latLng).title(location))
            mMap!!.animateCamera(CameraUpdateFactory.newLatLng(latLng))
            Toast.makeText(
                applicationContext,
                address.latitude.toString() + " " + address.longitude,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun initializeAutoComplete() {}

    //to get location permissions.
    private fun launchGoogleMaps(context: Context, latitude: Double, longitude: Double, label: String) {
        val format = "geo:0,0?q=$latitude,$longitude($label)"
        val uri = Uri.parse(format)
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        context.startActivity(intent)
    }
}