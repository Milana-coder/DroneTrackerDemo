package com.example.dronetrackerdemo.data

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import org.json.JSONObject
import java.net.URL

object GeoJsonLoader {

    // =========================================================
    // БАЗОВЫЙ АДРЕС GEOJSON
    // =========================================================

    private const val CDN_BASE =
        "https://cdn.jsdelivr.net/gh/darmat1/ukraine-geo-data@main/geodata"


    // =========================================================
    // ЗАГРУЗКА ОБЛАСТЕЙ ИЗ ЛОКАЛЬНОГО Ukraine.geojson
    // =========================================================

    fun loadOblasts(
        context: Context
    ): List<OblastPolygon> {

        val jsonText =
            context.assets
                .open("Ukraine.geojson")
                .bufferedReader()
                .use {
                    it.readText()
                }

        return parseOblastGeoJson(jsonText)
    }


    // =========================================================
    // ЗАГРУЗКА РАЙОНОВ КОНКРЕТНОЙ ОБЛАСТИ
    //
    // Например:
    //
    // Харківська область
    //        ↓
    // kharkivska_oblast.geojson
    //
    // Файл содержит районы этой области.
    // =========================================================

    fun loadRaions(
        oblastName: String
    ): List<RaionPolygon> {

        val oblastSlug =
            toSlug(oblastName)

        val url =
            "$CDN_BASE/$oblastSlug.geojson"

        return try {

            val jsonText =
                URL(url).readText()

            parseRaionGeoJson(
                jsonText,
                oblastName
            )

        } catch (e: Exception) {

            println(
                "ОШИБКА GEOJSON РАЙОНОВ: ${e.message}"
            )

            emptyList()
        }
    }


    // =========================================================
    // РАЗБОР ОБЛАСТЕЙ
    // =========================================================

    private fun parseOblastGeoJson(
        jsonText: String
    ): List<OblastPolygon> {

        val root =
            JSONObject(jsonText)

        val features =
            root.getJSONArray("features")

        val result =
            mutableListOf<OblastPolygon>()


        for (i in 0 until features.length()) {

            val feature =
                features.getJSONObject(i)

            val properties =
                feature.optJSONObject("properties")


            val name =
                properties
                    ?.optString("name")
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: "Невідома область"


            val geometry =
                feature.optJSONObject("geometry")
                    ?: continue


            val polygons =
                parseGeometry(
                    geometry
                )


            if (polygons.isNotEmpty()) {

                result.add(

                    OblastPolygon(

                        name = name,

                        polygons = polygons

                    )

                )

            }

        }

        return result
    }


    // =========================================================
    // РАЗБОР РАЙОНОВ
    // =========================================================

    private fun parseRaionGeoJson(
        jsonText: String,
        oblastName: String
    ): List<RaionPolygon> {

        val root =
            JSONObject(jsonText)

        val features =
            root.getJSONArray("features")

        val result =
            mutableListOf<RaionPolygon>()


        for (i in 0 until features.length()) {

            val feature =
                features.getJSONObject(i)

            val properties =
                feature.optJSONObject("properties")


            val name =
                properties
                    ?.optString("name")
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: continue


            val geometry =
                feature.optJSONObject("geometry")
                    ?: continue


            val polygons =
                parseGeometry(
                    geometry
                )


            if (polygons.isNotEmpty()) {

                result.add(

                    RaionPolygon(

                        name = name,

                        oblastName = oblastName,

                        polygons = polygons

                    )

                )

            }

        }

        return result
    }


    // =========================================================
    // РАЗБОР GEOMETRY
    //
    // Поддерживаем:
    // Polygon
    // MultiPolygon
    // =========================================================

    private fun parseGeometry(
        geometry: JSONObject
    ): List<List<LatLng>> {

        val type =
            geometry.optString("type")

        val coordinates =
            geometry.optJSONArray("coordinates")
                ?: return emptyList()


        val polygons =
            mutableListOf<List<LatLng>>()


        when (type) {

            "Polygon" -> {

                if (coordinates.length() > 0) {

                    val ring =
                        coordinates.getJSONArray(0)

                    val points =
                        parseRing(ring)

                    if (points.size >= 3) {

                        polygons.add(points)

                    }

                }

            }


            "MultiPolygon" -> {

                for (i in 0 until coordinates.length()) {

                    val polygon =
                        coordinates.getJSONArray(i)

                    if (polygon.length() > 0) {

                        val ring =
                            polygon.getJSONArray(0)

                        val points =
                            parseRing(ring)

                        if (points.size >= 3) {

                            polygons.add(points)

                        }

                    }

                }

            }

        }

        return polygons
    }


    // =========================================================
    // РАЗБОР КООРДИНАТ
    // =========================================================

    private fun parseRing(
        ring: org.json.JSONArray
    ): List<LatLng> {

        val points =
            mutableListOf<LatLng>()


        for (i in 0 until ring.length()) {

            val point =
                ring.getJSONArray(i)


            val longitude =
                point.getDouble(0)


            val latitude =
                point.getDouble(1)


            points.add(

                LatLng(
                    latitude,
                    longitude
                )

            )

        }


        return points
    }


    // =========================================================
    // ТРАНСЛИТЕРАЦИЯ УКРАИНСКОГО НАЗВАНИЯ
    //
    // "Харківська область"
    //        ↓
    // "kharkivska_oblast"
    //
    // Именно так называются файлы в репозитории.
    // =========================================================

    private fun toSlug(
        text: String
    ): String {

        if (text.isBlank()) {

            return "unknown"

        }


        val map =
            mapOf(

                'а' to "a",
                'б' to "b",
                'в' to "v",
                'г' to "h",
                'ґ' to "g",
                'д' to "d",
                'е' to "e",
                'є' to "ye",
                'ж' to "zh",
                'з' to "z",
                'и' to "y",
                'і' to "i",
                'ї' to "yi",
                'й' to "y",
                'к' to "k",
                'л' to "l",
                'м' to "m",
                'н' to "n",
                'о' to "o",
                'п' to "p",
                'р' to "r",
                'с' to "s",
                'т' to "t",
                'у' to "u",
                'ф' to "f",
                'х' to "kh",
                'ц' to "ts",
                'ч' to "ch",
                'ш' to "sh",
                'щ' to "shch",
                'ь' to "",
                'ю' to "yu",
                'я' to "ya",
                '\'' to "",
                'ʼ' to ""
            )


        val lower =
            text.lowercase()


        val result =
            buildString {

                for (char in lower) {

                    append(
                        map[char]
                            ?: char.toString()
                    )

                }

            }


        return result
            .replace(
                Regex("[^a-z0-9]+"),
                "_"
            )
            .trim('_')
    }
}