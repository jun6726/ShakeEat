package com.woody.shakeeat

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ReportFragment.Companion.reportFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchNearbyRequest

class MainActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMyLocationButtonClickListener, GoogleMap.OnMyLocationClickListener {
    private var permissionDenied = false
    private lateinit var mMap: GoogleMap
    var myLocationListener: LocationListener? = null
    val recyclerView by lazy { findViewById<RecyclerView>(R.id.list) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val gridLayoutManager = GridLayoutManager(this, 3) // 2는 열의 수를 나타냅니다
        recyclerView.layoutManager = gridLayoutManager

        Places.initialize(applicationContext, "AIzaSyAKA6asYYNg8YnUmOv_xehPdvsuis86TvM")
        placesClient = Places.createClient(this)

        val mapFragment: SupportMapFragment? =
            supportFragmentManager.findFragmentById(com.woody.shakeeat.R.id.map) as SupportMapFragment?

        if (mapFragment != null) {
            mapFragment.getMapAsync(this)
        }
    }

    // NULL이 아닌 GoogleMap 객체를 파라미터로 제공해 줄 수 있을 때 호출
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setOnMyLocationButtonClickListener(this)
        mMap.setOnMyLocationClickListener(this)
        enableMyLocation()
    }

    private fun displayPlaceNames(names: List<String>, metadatas: List<MutableList<PhotoMetadata>>) {
        val adapter = MainAdapter(this, names.toMutableList(), metadatas.toMutableList())
        recyclerView.adapter = adapter
    }

    override fun onMyLocationClick(p0: Location) {
        setMyLocation(p0)
    }

    override fun onMyLocationButtonClick(): Boolean {
        return false
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

            myLocationListener = object : LocationListener {
                override fun onLocationChanged(p0: Location) {
                    setMyLocation(p0)
                }
            }

            if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, myLocationListener!!)
            }

            if(locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0f, myLocationListener!!)
            }

            mMap?.isMyLocationEnabled = true
            return
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION) ||
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            return
        }

        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE) {
            super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
            )
            return
        }

        if (isPermissionGranted(
                permissions,
                grantResults,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) || isPermissionGranted(
                permissions,
                grantResults,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        ) {
            enableMyLocation()
        } else {
            permissionDenied = true
        }
    }

    fun setMyLocation(location: Location){

        // 위치 측정 중단
        if(myLocationListener != null) {
            val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
            locationManager.removeUpdates(myLocationListener!!)
            myLocationListener = null
        }

        // 위도 & 경도 관리 객체
        val latLng = LatLng(location.latitude, location.longitude)

        // 지도를 이용시키기 위한 객체
        val cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 15f)

        // mainGoogleMap.moveCamera(cameraUpdate)
        mMap?.animateCamera(cameraUpdate)

        val circle = CircularBounds.newInstance(latLng, 1000.0)

        // Define a list of types to include.
        val includedTypes = listOf("restaurant", "cafe")
        // Define a list of types to exclude.
        val excludedTypes = listOf("pizza_restaurant", "american_restaurant")

        // Use the builder to create a SearchNearbyRequest object.
        val searchNearbyRequest = SearchNearbyRequest.builder(circle, placeFields)
            .setIncludedTypes(includedTypes)
            .setExcludedTypes(excludedTypes)
            .setMaxResultCount(10)
            .build()

        searchNearbyPlaces(searchNearbyRequest)
    }

    companion object {
        /*** Request code for location permission request.
            @see .onRequestPermissionsResult ***/
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

    fun isPermissionGranted(
        grantPermissions: Array<String>, grantResults: IntArray,
        permission: String
    ): Boolean {
        for (i in grantPermissions.indices) {
            if (permission == grantPermissions[i]) {
                return grantResults[i] == PackageManager.PERMISSION_GRANTED
            }
        }
        return false
    }

    // Define a list of fields to include in the response for each returned place.
    val placeFields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.PHOTO_METADATAS)

    // Define the search area as a 1000 meter diameter circle in New York, NY.
    val center = LatLng(40.7580, -73.9855)
    val circle = CircularBounds.newInstance(center, 1000.0)

    // Define a list of types to include.
    val includedTypes = listOf("restaurant", "cafe")
    // Define a list of types to exclude.
    val excludedTypes = listOf("pizza_restaurant", "american_restaurant")

    // Use the builder to create a SearchNearbyRequest object.
    val searchNearbyRequest = SearchNearbyRequest.builder(circle, placeFields)
        .setIncludedTypes(includedTypes)
        .setExcludedTypes(excludedTypes)
        .setMaxResultCount(10)
        .build()

    private lateinit var placesClient: PlacesClient


    fun searchNearbyPlaces(searchNearbyRequest: SearchNearbyRequest) {
        if (placesClient != null) {
            placesClient.searchNearby(searchNearbyRequest)
                .addOnSuccessListener { response ->
                    val places = response.places
                    val placeNames = places.mapNotNull { it.name }
                    val metadatas = places.mapNotNull { it.photoMetadatas }

                    displayPlaceNames(placeNames, metadatas)
                }
                .addOnFailureListener { exception ->
                    // Handle any errors here
                }
        }
    }
}
