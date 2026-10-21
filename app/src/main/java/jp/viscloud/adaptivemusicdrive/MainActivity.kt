package jp.viscloud.adaptivemusicdrive

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
// map information
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
// location information
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
//music player


class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null
    private var speedKmh by mutableStateOf(0.0)
    private var selectedBpm by mutableStateOf(80)
    private var locationComponent: LocationComponent? = null

    private var currentLatitude by mutableStateOf<Double?>(null)
    private var currentLongitude by mutableStateOf<Double?>(null)

    private lateinit var musicPlayer: MusicPlayer


    private val locationRequest =
        LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L
        ).build()

    private val locationCallback =
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                updateLocation(location)
            }
        }

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->

            val fine =
                result[Manifest.permission.ACCESS_FINE_LOCATION] == true

            val coarse =
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (fine || coarse) {
                startLocationUpdates()
            }
        }

    private val osmRepository =
        OsmSpeedLimitRepository()

    private var speedLimitKmh by
    mutableStateOf(45.0)

    private var roadName by
    mutableStateOf<String?>(null)

    private var speedLimitFromOsm by
    mutableStateOf(false)

    private var lastOsmQueryTime = 0L


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(this)

        // music player
        musicPlayer = MusicPlayer(this)

        // MapLibreの初期化
        MapLibre.getInstance(this)

        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)

        /**
        mapView.getMapAsync { map ->

            mapLibreMap = map

            map.setStyle(
                // "https://demotiles.maplibre.org/style.json"
                "https://tiles.openfreemap.org/styles/liberty"
            )


            map.cameraPosition =
                CameraPosition.Builder()
                    .target(LatLng(34.78, 135.50))
                    .zoom(14.0)
                    .build()
        }*/

        mapView.getMapAsync { map ->

            mapLibreMap = map

            map.setStyle(
                "https://tiles.openfreemap.org/styles/liberty"
            ) { style ->

                // MapLibreの現在位置表示機能を初期化
                locationComponent = map.locationComponent

                val locationOptions =
                    LocationComponentOptions
                        .builder(this)
                        .pulseEnabled(true)
                        .build()

                val activationOptions =
                    LocationComponentActivationOptions
                        .builder(this, style)
                        .locationComponentOptions(locationOptions)
                        // Google FusedLocationProviderで取得するので
                        // MapLibre側ではGPSを取得しない
                        .useDefaultLocationEngine(false)
                        .build()

                locationComponent?.activateLocationComponent(
                    activationOptions
                )

                // 初期表示
                map.cameraPosition =
                    CameraPosition.Builder()
                        .target(LatLng(35.0, 135.0))
                        .zoom(14.0)
                        .build()
            }
        }

        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }

    @Composable
    fun MainScreen() {

        Box(
            modifier = Modifier.fillMaxSize()
        ) {

            AndroidView(
                factory = {
                    mapView
                },
                modifier = Modifier.fillMaxSize()
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {

                Text(
                    text = "Adaptive Music Driving Support",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Speed: %.1f km/h".format(speedKmh)
                )
                Text(
                    text = "Speed limit: %.0f km/h".format(
                        speedLimitKmh
                    )
                )

                Text(
                    text = if (speedLimitFromOsm) {
                        "Speed limit source: OpenStreetMap"
                    } else {
                        "Speed limit source: Default"
                    }
                )

                Text(
                    text = "Road: ${roadName ?: "Unknown"}"
                )

                Text(
                    text = "Selected BPM: $selectedBpm"
                )

                Text(
                    text = if (currentLatitude != null) {
                        "Latitude: %.6f".format(currentLatitude)
                    } else {
                        "Latitude: --"
                    }
                )

                Text(
                    text = if (currentLongitude != null) {
                        "Longitude: %.6f".format(currentLongitude)
                    } else {
                        "Longitude: --"
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        requestLocationPermission()
                    }
                ) {
                    Text("START")
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        stopLocationUpdates()
                    }
                ) {
                    Text("STOP")
                }
            }
        }
    }

    private fun updateLocation(location: Location) {

        currentLatitude = location.latitude
        currentLongitude = location.longitude

        speedKmh =
            if (location.hasSpeed()) {
                location.speed * 3.6
            } else {
                0.0
            }

        // 現在の制限速度でBPMを計算
        updateSelectedBpm()

        // MapLibreへGPS位置を通知
        locationComponent?.forceLocationUpdate(location)

        // OSMは毎秒問い合わせない
        val now =
            System.currentTimeMillis()

        if (now - lastOsmQueryTime >= 10000) {

            lastOsmQueryTime = now

            querySpeedLimit(
                location.latitude,
                location.longitude
            )
        }
    }

    private fun updateSelectedBpm() {

        val deltaV =
            speedLimitKmh - speedKmh

        val newBpm =
            when {
                deltaV >= 10.0 -> 120
                deltaV >= 5.0 -> 80
                else -> 60
            }

        if (newBpm != selectedBpm) {

            selectedBpm = newBpm

            musicPlayer.playForBpm(
                selectedBpm
            )
        }
    }

    private fun querySpeedLimit(
        latitude: Double,
        longitude: Double
    ) {

        lifecycleScope.launch {

            val result =
                osmRepository.getSpeedLimit(
                    latitude,
                    longitude
                )

            speedLimitKmh =
                result.speedLimitKmh

            roadName =
                result.roadName

            speedLimitFromOsm =
                !result.usedFallback

            // 制限速度が更新されたので再判定
            updateSelectedBpm()
        }
    }

    private fun requestLocationPermission() {

        val fineGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted) {
            startLocationUpdates()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {

        // 地図上の現在位置表示をON
        locationComponent?.isLocationComponentEnabled = true

        // GPSの進行方向を含む表示
        locationComponent?.renderMode =
            RenderMode.GPS

        // 現在位置を追尾
        locationComponent?.cameraMode =
            CameraMode.TRACKING

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            mainLooper
        )
    }

    private fun stopLocationUpdates() {

        fusedLocationClient.removeLocationUpdates(
            locationCallback
        )

        musicPlayer.stop()
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        musicPlayer.release()
        mapView.onDestroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}