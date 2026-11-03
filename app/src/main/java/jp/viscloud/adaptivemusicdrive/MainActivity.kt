package jp.viscloud.adaptivemusicdrive

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope

import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width

import java.util.Locale
import org.maplibre.android.MapLibre
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.Color

enum class ExperimentMode {
    NO_MUSIC,
    FIXED_TEMPO,
    ADAPTIVE
}


class MainActivity : ComponentActivity() {

    // ------------------------------------------------------------
    // GPS
    // ------------------------------------------------------------

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val locationRequest =
        LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L
        )
            .setMinUpdateIntervalMillis(1000L)
            .build()

    private var currentLatitude by mutableStateOf<Double?>(null)
    private var currentLongitude by mutableStateOf<Double?>(null)

    private var speedKmh by mutableStateOf(0.0)

    private var lastLocation: Location? = null


    // ------------------------------------------------------------
    // MapLibre
    // ------------------------------------------------------------

    private lateinit var mapView: MapView

    private var mapLibreMap: MapLibreMap? = null

    private var locationComponent: LocationComponent? = null


    // ------------------------------------------------------------
    // OSM speed limit
    // ------------------------------------------------------------

    private val osmRepository = OsmSpeedLimitRepository()

    // OSMから取得できなかった場合の初期値
    private var speedLimitKmh by mutableStateOf(45.0)

    private var roadName by mutableStateOf<String?>(null)

    private var speedLimitFromOsm by mutableStateOf(false)

    private var lastOsmQueryTime = 0L

    private val osmQueryIntervalMillis = 10_000L


    // ------------------------------------------------------------
    // Experiment
    // ------------------------------------------------------------

    private var experimentMode by mutableStateOf(
        ExperimentMode.ADAPTIVE
    )

    private var isExperimentRunning by mutableStateOf(false)

    private val fixedTempoBpm = 80


    // ------------------------------------------------------------
    // Music
    // ------------------------------------------------------------

    private lateinit var musicPlayer: MusicPlayer

    /**
     * UI表示用
     *
     * NO_MUSICの場合はnull
     */
    private var selectedBpm by mutableStateOf<Int?>(null)

    /**
     * 実際に再生しているBPM
     */
    private var currentPlayingBpm: Int? = null


    // ------------------------------------------------------------
    // CSV
    // ------------------------------------------------------------

    private lateinit var csvLogger: CsvLogger

    private var currentLogFileName by mutableStateOf<String?>(null)


    // ------------------------------------------------------------
    // Location callback
    // ------------------------------------------------------------

    private val locationCallback =
        object : LocationCallback() {

            override fun onLocationResult(result: LocationResult) {

                val location =
                    result.lastLocation ?: return

                updateLocation(location)
            }
        }


    // ============================================================
    // onCreate
    // ============================================================

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        // ============================================
        // MapLibreを最初に初期化
        // ============================================
        MapLibre.getInstance(this)

        // GPS
        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(this)

        // Music
        musicPlayer = MusicPlayer(this)

        // CSV
        csvLogger = CsvLogger(this)

        // MapLibre
        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)

        initializeMap()

        setContent {

            MaterialTheme {

                MainScreen()
            }
        }
    }


    // ============================================================
    // Map
    // ============================================================

    private fun initializeMap() {

        mapView.getMapAsync { map ->

            mapLibreMap = map

            map.setStyle(
                "https://tiles.openfreemap.org/styles/liberty"
            ) { style ->

                locationComponent =
                    map.locationComponent

                val locationOptions =
                    LocationComponentOptions
                        .builder(this@MainActivity)
                        .pulseEnabled(true)
                        .build()

                val activationOptions =
                    LocationComponentActivationOptions
                        .builder(
                            this@MainActivity,
                            style
                        )
                        .locationComponentOptions(
                            locationOptions
                        )
                        .useDefaultLocationEngine(false)
                        .build()

                locationComponent
                    ?.activateLocationComponent(
                        activationOptions
                    )

                map.cameraPosition =
                    CameraPosition.Builder()
                        .target(
                            LatLng(
                                35.0,
                                135.0
                            )
                        )
                        .zoom(14.0)
                        .build()

                if (
                    isExperimentRunning &&
                    hasLocationPermission()
                ) {

                    enableLocationComponent()
                }
            }
        }
    }


    // ============================================================
    // Compose screen
    // ============================================================

    @Composable
    fun MainScreen() {

        val permissionLauncher =
            rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->

                val fine =
                    permissions[
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ] == true

                val coarse =
                    permissions[
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ] == true

                if (fine || coarse) {

                    startExperiment()
                }
            }


        Box(
            modifier = Modifier.fillMaxSize()
        ) {

            // ----------------------------------------------------
            // Map
            // ----------------------------------------------------

            AndroidView(
                factory = {
                    mapView
                },
                modifier = Modifier.fillMaxSize()
            )


            // ----------------------------------------------------
            // Control panel
            // ----------------------------------------------------

            Card(
                /*
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .widthIn(max = 340.dp)

                 */
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .widthIn(max = 340.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.7f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {

                    Text(
                        text = "Experiment condition",
                        style = MaterialTheme.typography.titleMedium
                    )


                    // ============================================
                    // No music
                    // ============================================

                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        RadioButton(
                            selected =
                                experimentMode ==
                                        ExperimentMode.NO_MUSIC,
                            enabled =
                                !isExperimentRunning,
                            onClick = {

                                experimentMode =
                                    ExperimentMode.NO_MUSIC
                            }
                        )

                        Text("No music")
                    }


                    // ============================================
                    // Fixed tempo
                    // ============================================

                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        RadioButton(
                            selected =
                                experimentMode ==
                                        ExperimentMode.FIXED_TEMPO,
                            enabled =
                                !isExperimentRunning,
                            onClick = {

                                experimentMode =
                                    ExperimentMode.FIXED_TEMPO
                            }
                        )

                        Text(
                            "Fixed tempo ($fixedTempoBpm BPM)"
                        )
                    }

                    // ============================================
                    // Adaptive
                    // ============================================
                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        RadioButton(
                            selected =
                                experimentMode ==
                                        ExperimentMode.ADAPTIVE,
                            enabled =
                                !isExperimentRunning,
                            onClick = {

                                experimentMode =
                                    ExperimentMode.ADAPTIVE
                            }
                        )

                        Text("Adaptive")
                    }

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    // ============================================
                    // Driving information
                    // ============================================
                    Text(
                        "Speed: %.1f km/h".format(
                            Locale.US,
                            speedKmh
                        )
                    )
                    Text(
                        "Speed limit: %.1f km/h".format(
                            Locale.US,
                            speedLimitKmh
                        )
                    )
                    Text(
                        "Δv: %.1f km/h".format(
                            Locale.US,
                            speedLimitKmh - speedKmh
                        )
                    )
                    Text(
                        "Road: ${roadName ?: "-"}"
                    )
                    Text(
                        "Speed-limit source: " +
                                if (speedLimitFromOsm) {
                                    "OSM"
                                } else {
                                    "Default"
                                }
                    )

                    if (selectedBpm != null) {
                        Text(
                            "Music: $selectedBpm BPM"
                        )
                    } else {
                        Text(
                            "Music: OFF"
                        )
                    }

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    // ============================================
                    // START
                    // ============================================
                    Button(
                        enabled = !isExperimentRunning,
                        modifier =
                            //Modifier.fillMaxWidth(),
                            Modifier.width(100.dp).height(42.dp),
                    onClick = {
                            if (hasLocationPermission()) {
                                startExperiment()
                            } else {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        }
                    ) {
                        Text("START")
                    }
                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )

                    // ============================================
                    // STOP
                    // ============================================
                    Button(
                        enabled = isExperimentRunning,
                        modifier =
                            //Modifier.fillMaxWidth(),
                            Modifier.width(100.dp).height(42.dp),
                        onClick = {

                            stopExperiment()
                        }
                    ) {

                        Text("STOP")
                    }


                    // ============================================
                    // CSV filename
                    // ============================================

                    currentLogFileName?.let {

                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        Text(
                            text = "CSV: $it",
                            style =
                                MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }


    // ============================================================
    // Start experiment
    // ============================================================

    @SuppressLint("MissingPermission")
    private fun startExperiment() {

        if (isExperimentRunning) {
            return
        }

        if (!hasLocationPermission()) {
            return
        }

        // 新しいCSVを作成
        val file =
            csvLogger.startNewLog()

        currentLogFileName =
            file.name

        // 前回の実験状態をリセット
        currentPlayingBpm = null
        selectedBpm = null

        lastOsmQueryTime = 0L

        isExperimentRunning = true

        enableLocationComponent()

        fusedLocationClient
            .requestLocationUpdates(
                locationRequest,
                locationCallback,
                mainLooper
            )
    }


    // ============================================================
    // Stop experiment
    // ============================================================

    private fun stopExperiment() {

        if (!isExperimentRunning) {
            return
        }

        /*
         * 音楽再生中なら、
         * STOPイベントをCSVへ記録する。
         */
        val oldBpm =
            currentPlayingBpm

        if (oldBpm != null) {

            lastLocation?.let { location ->

                appendCsv(
                    location = location,
                    musicEvent = "STOP",
                    bpm = oldBpm
                )
            }
        }

        fusedLocationClient
            .removeLocationUpdates(
                locationCallback
            )

        musicPlayer.stop()

        currentPlayingBpm = null
        selectedBpm = null

        isExperimentRunning = false

        locationComponent
            ?.cameraMode =
            CameraMode.NONE
    }


    // ============================================================
    // Location update
    // ============================================================

    private fun updateLocation(
        location: Location
    ) {

        lastLocation = location

        currentLatitude =
            location.latitude

        currentLongitude =
            location.longitude


        speedKmh =
            if (location.hasSpeed()) {

                location.speed * 3.6

            } else {

                0.0
            }


        // MapLibre current-location marker
        locationComponent
            ?.forceLocationUpdate(location)


        /*
         * 現在の実験条件に従って
         * 音楽を制御する。
         *
         * START / SWITCH / NONE
         * が返ってくる。
         */
        val musicEvent =
            updateMusic()


        // GPSデータをCSVへ保存
        appendCsv(
            location = location,
            musicEvent = musicEvent,
            bpm = currentPlayingBpm
        )


        // --------------------------------------------------------
        // OSM query
        // --------------------------------------------------------

        val now =
            SystemClock.elapsedRealtime()

        if (
            now - lastOsmQueryTime >=
            osmQueryIntervalMillis
        ) {

            lastOsmQueryTime = now

            querySpeedLimit(
                latitude = location.latitude,
                longitude = location.longitude
            )
        }
    }


    // ============================================================
    // Music control
    // ============================================================

    /**
     * 実験条件に応じて音楽を制御する。
     *
     * 戻り値
     *
     * START  : 音楽再生開始
     * SWITCH : BPM切替
     * STOP   : 音楽停止
     * NONE   : 変更なし
     */
    private fun updateMusic(): String {

        if (!isExperimentRunning) {

            return "NONE"
        }


        val oldBpm =
            currentPlayingBpm


        return when (experimentMode) {


            // ====================================================
            // NO MUSIC
            // ====================================================

            ExperimentMode.NO_MUSIC -> {

                selectedBpm = null

                if (oldBpm != null) {

                    musicPlayer.stop()

                    currentPlayingBpm = null

                    "STOP"

                } else {

                    "NONE"
                }
            }


            // ====================================================
            // FIXED TEMPO
            // ====================================================

            ExperimentMode.FIXED_TEMPO -> {

                val newBpm =
                    fixedTempoBpm

                selectedBpm =
                    newBpm


                if (oldBpm != newBpm) {

                    musicPlayer.playForBpm(
                        newBpm
                    )

                    currentPlayingBpm =
                        newBpm


                    if (oldBpm == null) {

                        "START"

                    } else {

                        "SWITCH"
                    }

                } else {

                    "NONE"
                }
            }


            // ====================================================
            // ADAPTIVE
            // ====================================================

            ExperimentMode.ADAPTIVE -> {

                val newBpm =
                    calculateAdaptiveBpm()

                selectedBpm =
                    newBpm


                if (oldBpm != newBpm) {

                    musicPlayer.playForBpm(
                        newBpm
                    )

                    currentPlayingBpm =
                        newBpm


                    if (oldBpm == null) {

                        "START"

                    } else {

                        "SWITCH"
                    }

                } else {

                    "NONE"
                }
            }
        }
    }


    // ============================================================
    // Adaptive BPM
    // ============================================================

    private fun calculateAdaptiveBpm(): Int {

        val deltaV =
            speedLimitKmh - speedKmh


        return when {

            deltaV >= 10.0 -> {
                120
            }

            deltaV >= 5.0 -> {
                80
            }

            else -> {
                60
            }
        }
    }


    // ============================================================
    // OSM
    // ============================================================

    private fun querySpeedLimit(
        latitude: Double,
        longitude: Double
    ) {

        lifecycleScope.launch {

            try {

                /*
                 * OsmSpeedLimitRepository側の関数名が
                 * 違う場合は、この1行だけ
                 * 現在の実装に合わせて変更してください。
                 */
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


                /*
                 * 制限速度が変わることで
                 * BPMが変化する可能性がある。
                 */
                val musicEvent =
                    updateMusic()


                /*
                 * OSM更新によってBPMが変わった場合、
                 * その瞬間を別のCSVレコードとして残す。
                 */
                if (
                    musicEvent == "SWITCH" ||
                    musicEvent == "START"
                ) {

                    lastLocation?.let { location ->

                        appendCsv(
                            location = location,
                            musicEvent = musicEvent,
                            bpm = currentPlayingBpm
                        )
                    }
                }

            } catch (e: Exception) {

                e.printStackTrace()

                /*
                 * OSM取得失敗時は、
                 * 現在のspeedLimitKmhを維持する。
                 */
            }
        }
    }


    // ============================================================
    // CSV
    // ============================================================

    private fun appendCsv(
        location: Location,
        musicEvent: String,
        bpm: Int?
    ) {

        csvLogger.append(

            condition =
                experimentMode,

            latitude =
                location.latitude,

            longitude =
                location.longitude,

            gpsAccuracy =
                location.accuracy,

            speedKmh =
                speedKmh,

            speedLimitKmh =
                speedLimitKmh,

            bpm =
                bpm,

            musicEvent =
                musicEvent,

            roadName =
                roadName,

            speedLimitFromOsm =
                speedLimitFromOsm
        )
    }


    // ============================================================
    // Permission
    // ============================================================

    private fun hasLocationPermission(): Boolean {

        val fine =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) ==
                    PackageManager.PERMISSION_GRANTED


        val coarse =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) ==
                    PackageManager.PERMISSION_GRANTED


        return fine || coarse
    }


    // ============================================================
    // MapLibre location component
    // ============================================================

    @SuppressLint("MissingPermission")
    private fun enableLocationComponent() {

        if (!hasLocationPermission()) {
            return
        }

        locationComponent
            ?.isLocationComponentEnabled =
            true

        locationComponent
            ?.renderMode =
            RenderMode.GPS

        locationComponent
            ?.cameraMode =
            CameraMode.TRACKING
    }


    // ============================================================
    // Lifecycle
    // ============================================================

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


    override fun onSaveInstanceState(
        outState: Bundle
    ) {

        super.onSaveInstanceState(outState)

        mapView.onSaveInstanceState(outState)
    }


    override fun onDestroy() {

        fusedLocationClient
            .removeLocationUpdates(
                locationCallback
            )

        musicPlayer.release()

        mapView.onDestroy()

        super.onDestroy()
    }
}