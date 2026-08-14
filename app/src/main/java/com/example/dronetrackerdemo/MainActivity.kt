package com.example.dronetrackerdemo

import com.google.android.gms.maps.model.BitmapDescriptorFactory
import android.Manifest
import android.content.pm.PackageManager

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.CircleOptions
import androidx.compose.ui.graphics.Color

import com.google.maps.android.compose.*

import com.example.dronetrackerdemo.data.AirObject
import com.example.dronetrackerdemo.data.RetrofitClient

import kotlinx.coroutines.delay


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContent {

            MaterialTheme {

                DroneTrackerScreen()

            }

        }

    }

}

@Composable
fun DroneTrackerScreen() {

    var monitoring by remember {
        mutableStateOf(false)
    }

    // реальные объекты с Flask

    var airObjects by remember {

        mutableStateOf<List<AirObject>>(emptyList())

    }
    val context = LocalContext.current

    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        locationPermissionGranted = granted
    }


    // запрос к серверу
    LaunchedEffect(monitoring) {

        while (monitoring) {

            try {

                airObjects = RetrofitClient.api.getObjects()

            } catch (e: Exception) {

                println("ОШИБКА API: ${e.message}")

            }

            delay(3000)

        }
    }

// ВОТ СЮДА ВСТАВЛЯЕМ

    LaunchedEffect(Unit) {

        if (!locationPermissionGranted) {

            permissionLauncher.launch(
                Manifest.permission.ACCESS_FINE_LOCATION
            )

        }

    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(51.8, 32.2), 8f
        )
    }

    Scaffold(

        topBar = {

            Text(

                text = "🚁 Real Air Monitor",

                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),

                style = MaterialTheme.typography.titleLarge

            )

        }

    ) { padding ->

        Column(

            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),

            horizontalAlignment = Alignment.CenterHorizontally

        ) {

            Button(

                onClick = {

                    monitoring = !monitoring

                }

            ) {


                Text(

                    if (monitoring)

                        "Остановить мониторинг"
                    else

                        "Запустить мониторинг"
                )

            }

            Spacer(
                Modifier.height(20.dp)
            )

            Text(

                text = "Объектов: ${airObjects.size}"

            )

            Spacer(
                Modifier.height(20.dp)
            )

            Card(

                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)

            ) {

                GoogleMap(

                    modifier = Modifier.fillMaxSize(),

                    cameraPositionState = cameraPositionState,

                    properties = MapProperties(
                        mapType = MapType.HYBRID
                    )

                ) {

                    val droneIcon = remember {
                        BitmapDescriptorFactory.defaultMarker(
                            BitmapDescriptorFactory.HUE_RED
                        )
                    }

                    airObjects.forEach { obj ->


                        val position = LatLng(
                            obj.lat, obj.lon
                        )

                        val place = when {

                            !obj.locality.isNullOrBlank() && !obj.region.isNullOrBlank() -> "${obj.locality}, ${obj.region}"

                            !obj.locality.isNullOrBlank() -> obj.locality

                            !obj.region.isNullOrBlank() -> obj.region

                            else -> "Место неизвестно"
                        }

                        Marker(

                            state = MarkerState(
                                position = position
                            ),

                            icon = droneIcon,

                            title = obj.name,

                            snippet = """
📍 $place

🚀 ${obj.speed} км/ч

🧭 ${obj.heading}°

🟢 ${obj.confidence.uppercase()}

👥 Подтверждений: ${obj.sources}

Статус: ${obj.status}
                """.trimIndent()

                        )

                        if (obj.trail.size > 1) {

                            Polyline(

                                points = obj.trail.map {

                                    LatLng(
                                        it.lat, it.lon
                                    )

                                }

                            )

                        }

                        Polyline(

                            points = listOf(

                                position,

                                calculateDirectionPoint(
                                    position, obj.heading.toDouble()
                                )
                            )
                        )
                    }
                }
            }
        }
    }

}
fun calculateDirectionPoint(

    position: LatLng,

    heading: Double,

    distance: Double = 0.05

): LatLng {

    val radians = Math.toRadians(heading)

    return LatLng(

        position.latitude + distance * kotlin.math.cos(radians),

        position.longitude + distance * kotlin.math.sin(radians)

    )

}