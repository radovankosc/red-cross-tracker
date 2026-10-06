package com.radovankosc.ridelog.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Google Routes API (distances) and Places API (address suggestions), called with the key from Settings. */
object GoogleMaps {

    /** Asks for the car routes between two addresses, including alternatives, and returns the shortest in km. */
    suspend fun shortestDrivingKm(apiKey: String, from: String, to: String): Double = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("origin", JSONObject().put("address", from))
            .put("destination", JSONObject().put("address", to))
            .put("travelMode", "DRIVE")
            .put("routingPreference", "TRAFFIC_UNAWARE")
            .put("computeAlternativeRoutes", true)
        val json = post(
            "https://routes.googleapis.com/directions/v2:computeRoutes",
            apiKey,
            fieldMask = "routes.distanceMeters",
            body = body,
        )
        val routes = json.optJSONArray("routes")
        var shortest = Int.MAX_VALUE
        if (routes != null) {
            for (i in 0 until routes.length()) {
                val meters = routes.getJSONObject(i).optInt("distanceMeters", Int.MAX_VALUE)
                if (meters in 1 until shortest) shortest = meters
            }
        }
        if (shortest == Int.MAX_VALUE) throw IOException("Google found no car route between these addresses")
        round1(shortest / 1000.0)
    }

    suspend fun suggestAddresses(apiKey: String, input: String, regionCode: String): List<String> =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("input", input)
            if (regionCode.isNotBlank()) body.put("includedRegionCodes", JSONArray().put(regionCode.trim().lowercase()))
            val json = post("https://places.googleapis.com/v1/places:autocomplete", apiKey, fieldMask = null, body = body)
            val list = json.optJSONArray("suggestions") ?: return@withContext emptyList()
            (0 until list.length()).mapNotNull { i ->
                list.getJSONObject(i).optJSONObject("placePrediction")
                    ?.optJSONObject("text")?.optString("text")?.takeIf { it.isNotBlank() }
            }
        }

    private fun post(url: String, apiKey: String, fieldMask: String?, body: JSONObject): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("X-Goog-Api-Key", apiKey.trim())
            if (fieldMask != null) conn.setRequestProperty("X-Goog-FieldMask", fieldMask)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrNull()
                throw IOException(message ?: "Google returned error $code")
            }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }
}
