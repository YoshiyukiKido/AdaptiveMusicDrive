package jp.viscloud.adaptivemusicdrive

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CsvLogger(
    private val context: Context
) {

    private var csvFile: File? = null

    fun startNewLog(): File {

        val directory =
            context.getExternalFilesDir(
                Environment.DIRECTORY_DOCUMENTS
            )

        val timestamp =
            SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.getDefault()
            ).format(Date())

        csvFile =
            File(
                directory,
                "driving_log_$timestamp.csv"
            )
        /*
        csvFile?.writeText(
            "datetime," +
                    "latitude," +
                    "longitude," +
                    "speed_kmh," +
                    "speed_limit_kmh," +
                    "delta_v," +
                    "bpm," +
                    "bpm_changed," +
                    "road_name," +
                    "speed_limit_source\n"
        )*/

        csvFile?.writeText(
            "datetime," +
                    "condition," +
                    "latitude," +
                    "longitude," +
                    "gps_accuracy_m," +
                    "speed_kmh," +
                    "speed_limit_kmh," +
                    "delta_v," +
                    "bpm," +
                    "music_event," +
                    "road_name," +
                    "speed_limit_source\n"
        )

        return csvFile!!
    }

    fun append(
        condition: ExperimentMode,
        latitude: Double,
        longitude: Double,
        gpsAccuracy: Float,
        speedKmh: Double,
        speedLimitKmh: Double,
        bpm: Int?,
        musicEvent: String,
        roadName: String?,
        speedLimitFromOsm: Boolean
    ) {

        val file = csvFile ?: return

        val datetime =
            SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS",
                Locale.getDefault()
            ).format(Date())

        val deltaV =
            speedLimitKmh - speedKmh

        val source =
            if (speedLimitFromOsm) {
                "OSM"
            } else {
                "DEFAULT"
            }

        val safeRoadName =
            "\"${(roadName ?: "").replace("\"", "\"\"")}\""

        val bpmText =
            bpm?.toString() ?: ""

        val line =
            "$datetime," +
                    "${condition.name}," +
                    "$latitude," +
                    "$longitude," +
                    "%.2f".format(Locale.US, gpsAccuracy) + "," +
                    "%.3f".format(Locale.US, speedKmh) + "," +
                    "%.1f".format(Locale.US, speedLimitKmh) + "," +
                    "%.3f".format(Locale.US, deltaV) + "," +
                    "$bpmText," +
                    "$musicEvent," +
                    "$safeRoadName," +
                    "$source\n"

        file.appendText(line)
    }

    fun getCurrentFile(): File? {
        return csvFile
    }
}