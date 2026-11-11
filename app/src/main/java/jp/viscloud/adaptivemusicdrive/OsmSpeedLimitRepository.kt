package jp.viscloud.adaptivemusicdrive

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SpeedLimitResult(
    val speedLimitKmh: Double,
    val roadName: String?,
    val maxspeedRaw: String?,
    val wayId: Long?,
    val usedFallback: Boolean
)


class OsmSpeedLimitRepository {

    companion object {

        private const val TAG = "OSM"

        private const val FALLBACK_SPEED_LIMIT = 45.0

        private const val SEARCH_RADIUS = 50
    }


    suspend fun getSpeedLimit(
        latitude: Double,
        longitude: Double
    ): SpeedLimitResult = withContext(Dispatchers.IO) {

        val overpassUrl = BuildConfig.OVERPASS_URL

        if (overpassUrl.isBlank()) {

            Log.e(
                TAG,
                "OVERPASS_URL is not configured."
            )

            return@withContext fallbackResult()
        }

        val query = """
        [out:json][timeout:10];
        way(around:$SEARCH_RADIUS,$latitude,$longitude)
          ["highway"];
        out tags center;
    """.trimIndent()

        Log.d(
            TAG,
            "Query location: lat=$latitude lon=$longitude"
        )

        Log.d(
            TAG,
            "Overpass query: $query"
        )

        try {

            val response =
                executeQuery(
                    overpassUrl,
                    query
                )

            parseResponse(response)

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to query Overpass API",
                e
            )

            fallbackResult()
        }
    }


    // ============================================================
    // HTTP request
    // ============================================================

    private fun executeQuery(
        endpoint: String,
        query: String
    ): String {

        val url =
            URL(endpoint)

        val connection =
            url.openConnection()
                    as HttpURLConnection

        try {

            connection.requestMethod =
                "POST"

            connection.connectTimeout =
                10_000

            connection.readTimeout =
                15_000

            connection.doOutput =
                true

            connection.setRequestProperty(
                "User-Agent",
                "AdaptiveMusicDrive/0.1 (+https://viscloud.jp/)"
            )

            connection.setRequestProperty(
                "Content-Type",
                "application/x-www-form-urlencoded; charset=UTF-8"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )


            val body =
                "data=" +
                        URLEncoder.encode(
                            query,
                            "UTF-8"
                        )


            connection.outputStream.use { output ->

                output.write(
                    body.toByteArray(
                        Charsets.UTF_8
                    )
                )
            }


            val responseCode =
                connection.responseCode


            Log.d(
                TAG,
                "HTTP response = $responseCode"
            )


            val stream =
                if (responseCode in 200..299) {

                    connection.inputStream

                } else {

                    connection.errorStream
                }


            val response =
                stream
                    ?.bufferedReader()
                    ?.use {
                        it.readText()
                    }
                    ?: ""


            if (
                responseCode !in 200..299
            ) {

                Log.e(
                    TAG,
                    "Overpass error: " +
                            "$responseCode\n$response"
                )

                throw RuntimeException(
                    "Overpass HTTP error: " +
                            responseCode
                )
            }


            Log.d(
                TAG,
                "Overpass response: $response"
            )


            return response

        } finally {

            connection.disconnect()
        }
    }


    // ============================================================
    // JSON parsing
    // ============================================================

    private fun parseResponse(
        jsonText: String
    ): SpeedLimitResult {

        val root =
            JSONObject(jsonText)

        val elements =
            root.getJSONArray(
                "elements"
            )


        Log.d(
            TAG,
            "Number of ways = ${elements.length()}"
        )


        /*
         * 最初にmaxspeedを持つ道路を探す。
         *
         * 現時点では簡易方式。
         * 後ほどgeometryを利用した
         * nearest-road matchingに改善可能。
         */
        for (
        i in 0 until elements.length()
        ) {

            val element =
                elements.getJSONObject(i)

            val tags =
                element.optJSONObject(
                    "tags"
                ) ?: continue


            val highway =
                tags.optString(
                    "highway",
                    ""
                )

            val roadName =
                tags.optString(
                    "name",
                    null
                )

            val wayId =
                element.optLong(
                    "id"
                )


            var maxspeedRaw: String? =
                null


            if (
                tags.has("maxspeed")
            ) {

                maxspeedRaw =
                    tags.optString(
                        "maxspeed"
                    )

            } else if (
                tags.has("maxspeed:forward")
            ) {

                maxspeedRaw =
                    tags.optString(
                        "maxspeed:forward"
                    )

            } else if (
                tags.has("maxspeed:backward")
            ) {

                maxspeedRaw =
                    tags.optString(
                        "maxspeed:backward"
                    )
            }


            Log.d(
                TAG,
                "way=$wayId " +
                        "highway=$highway " +
                        "road=$roadName " +
                        "maxspeed=$maxspeedRaw"
            )


            if (
                !maxspeedRaw.isNullOrBlank()
            ) {

                val speed =
                    parseMaxSpeed(
                        maxspeedRaw
                    )


                if (speed != null) {

                    Log.d(
                        TAG,
                        "Selected way=$wayId " +
                                "road=$roadName " +
                                "maxspeed=$speed"
                    )


                    return SpeedLimitResult(

                        speedLimitKmh =
                            speed,

                        roadName =
                            roadName,

                        maxspeedRaw =
                            maxspeedRaw,

                        wayId =
                            wayId,

                        usedFallback =
                            false
                    )
                }
            }
        }


        /*
         * maxspeed付き道路がなかった場合。
         *
         * 道路名だけでも取得しておく。
         */
        var firstRoadName: String? =
            null

        var firstWayId: Long? =
            null


        if (
            elements.length() > 0
        ) {

            val element =
                elements.getJSONObject(0)

            val tags =
                element.optJSONObject(
                    "tags"
                )

            firstRoadName =
                tags?.optString(
                    "name",
                    null
                )

            firstWayId =
                element.optLong(
                    "id"
                )
        }


        Log.w(
            TAG,
            "No maxspeed found. " +
                    "Using fallback speed."
        )


        return SpeedLimitResult(

            speedLimitKmh =
                FALLBACK_SPEED_LIMIT,

            roadName =
                firstRoadName,

            maxspeedRaw =
                null,

            wayId =
                firstWayId,

            usedFallback =
                true
        )
    }


    // ============================================================
    // maxspeed parser
    // ============================================================

    private fun parseMaxSpeed(
        value: String
    ): Double? {

        val normalized =
            value
                .trim()
                .lowercase(
                    Locale.US
                )


        /*
         * mph対応
         *
         * 例:
         * "30 mph"
         */
        if (
            normalized.contains("mph")
        ) {

            val number =
                Regex("""[\d.]+""")
                    .find(normalized)
                    ?.value
                    ?.toDoubleOrNull()
                    ?: return null


            return number * 1.609344
        }


        /*
         * 通常のkm/h
         *
         * 例:
         * "50"
         * "40 km/h"
         */
        val number =
            Regex("""[\d.]+""")
                .find(normalized)
                ?.value
                ?.toDoubleOrNull()


        return number
    }


    // ============================================================
    // fallback
    // ============================================================

    private fun fallbackResult():
            SpeedLimitResult {

        return SpeedLimitResult(

            speedLimitKmh =
                FALLBACK_SPEED_LIMIT,

            roadName =
                null,

            maxspeedRaw =
                null,

            wayId =
                null,

            usedFallback =
                true
        )
    }
}