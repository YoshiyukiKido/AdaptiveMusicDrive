package jp.viscloud.adaptivemusicdrive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class SpeedLimitResult(
    val speedLimitKmh: Double,
    val roadName: String?,
    val maxspeedRaw: String?,
    val wayId: Long?,
    val usedFallback: Boolean
)

class OsmSpeedLimitRepository {

    companion object {
        const val DEFAULT_SPEED_LIMIT = 45.0
    }

    suspend fun getSpeedLimit(
        latitude: Double,
        longitude: Double
    ): SpeedLimitResult = withContext(Dispatchers.IO) {

        val query = """
            [out:json][timeout:10];
            way(around:30,$latitude,$longitude)
              ["highway"]
              ["maxspeed"]
              ["highway"!~"footway|path|cycleway|steps"];
            out tags;
        """.trimIndent()

        try {
            val url =
                URL("https://overpass-api.de/api/interpreter")

            val connection =
                url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.doOutput = true

            val body =
                "data=" + URLEncoder.encode(query, "UTF-8")

            connection.outputStream.bufferedWriter().use {
                it.write(body)
            }

            if (connection.responseCode !in 200..299) {
                return@withContext fallback()
            }

            val text =
                connection.inputStream.bufferedReader().use {
                    it.readText()
                }

            val root = JSONObject(text)
            val elements = root.getJSONArray("elements")

            for (i in 0 until elements.length()) {

                val element =
                    elements.getJSONObject(i)

                val tags =
                    element.optJSONObject("tags")
                        ?: continue

                val rawMaxspeed =
                    tags.optString("maxspeed", "")

                val speed =
                    parseMaxspeed(rawMaxspeed)
                        ?: continue

                val roadName =
                    tags.optString("name", "")
                        .ifBlank { null }

                val wayId =
                    element.optLong("id")

                return@withContext SpeedLimitResult(
                    speedLimitKmh = speed,
                    roadName = roadName,
                    maxspeedRaw = rawMaxspeed,
                    wayId = wayId,
                    usedFallback = false
                )
            }

            fallback()

        } catch (e: Exception) {

            e.printStackTrace()

            fallback()
        }
    }

    private fun fallback(): SpeedLimitResult {
        return SpeedLimitResult(
            speedLimitKmh = DEFAULT_SPEED_LIMIT,
            roadName = null,
            maxspeedRaw = null,
            wayId = null,
            usedFallback = true
        )
    }

    private fun parseMaxspeed(value: String): Double? {

        val text =
            value.trim().lowercase()

        if (text.isBlank()) {
            return null
        }

        // none, JP:urban などはこの簡易版では扱わない
        if (text == "none" || ":" in text) {
            return null
        }

        val match =
            Regex("""(\d+(?:\.\d+)?)""")
                .find(text)
                ?: return null

        val speed =
            match.groupValues[1].toDoubleOrNull()
                ?: return null

        return if ("mph" in text) {
            speed * 1.609344
        } else {
            speed
        }
    }
}