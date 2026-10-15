
package com.example.dronetrackerdemo

import android.content.Context
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class SettlementGeocoder(context: Context) {

    private val appContext = context.applicationContext

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun findSettlement(name: String): LatLng? {
        if (name.isBlank()) return null

        return try {
            val query = URLEncoder.encode(
                "$name, Ukraine",
                "UTF-8"
            )

            val url =
                "https://nominatim.openstreetmap.org/search" +
                        "?q=$query&format=json&limit=1"

            val request = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "DroneTrackerDemo/1.0 (Android app)"
                )
                .header(
                    "Accept-Language",
                    "uk,ru,en"
                )
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(
                        "SettlementGeocoder",
                        "Ошибка HTTP: ${response.code}"
                    )
                    return null
                }

                val body = response.body?.string()
                    ?: return null

                val results = JSONArray(body)

                if (results.length() == 0) {
                    Log.w(
                        "SettlementGeocoder",
                        "Ничего не найдено: $name"
                    )
                    return null
                }

                val result = results.getJSONObject(0)

                val latitude = result.getString("lat").toDouble()
                val longitude = result.getString("lon").toDouble()

                Log.i(
                    "SettlementGeocoder",
                    "Найдено: $name -> $latitude, $longitude"
                )

                LatLng(latitude, longitude)
            }
        } catch (e: Exception) {
            Log.e(
                "SettlementGeocoder",
                "Ошибка поиска: $name",
                e
            )
            null
        }
    }
}
