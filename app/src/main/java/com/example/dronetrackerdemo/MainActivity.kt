package com.example.dronetrackerdemo

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

import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.rememberCameraPositionState

import com.example.dronetrackerdemo.simulator.DroneSimulator
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


    // Список дронов
    val drones = remember {

        mutableStateListOf(

            Drone(
                id = 1,
                name = "Drone 1",
                LatLng(49.9935, 36.2304)
            ),

            Drone(
                id = 2,
                name = "Drone 2",
                LatLng(50.0150, 36.2700)
            ),

            Drone(
                id = 3,
                name = "Drone 3",
               LatLng(49.9750, 36.1800)
            )

        )

    }
    LaunchedEffect(monitoring) {

        while (monitoring) {

            DroneSimulator.move(drones)

            delay(500)

        }

    }

    // Камера карты
    val cameraPositionState = rememberCameraPositionState {

        position = CameraPosition.fromLatLngZoom(
            drones.first().position,
            12f
        )

    }



    Scaffold(

        topBar = {

            Surface(
                shadowElevation = 4.dp
            ) {

                Text(

                    text = "🚁 Drone Tracker Demo",

                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),

                    style = MaterialTheme.typography.titleLarge

                )

            }

        }


    ) { padding ->


        Column(

            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(20.dp),

            horizontalAlignment = Alignment.CenterHorizontally

        ) {


            Text(

                text = if (monitoring)

                    "🟢 Мониторинг активен"
                else

                    "⚪ Мониторинг остановлен",


                style = MaterialTheme.typography.titleMedium

            )



            Spacer(
                modifier = Modifier.height(20.dp)
            )



            Button(

                onClick = {

                    monitoring = !monitoring

                }

            ) {


                Text(

                    text = if (monitoring)

                        "Остановить мониторинг"
                    else

                        "Запустить мониторинг"

                )

            }



            Spacer(
                modifier = Modifier.height(20.dp)
            )



            Card(

                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)

            ) {


                GoogleMap(

                    modifier = Modifier.fillMaxSize(),

                    cameraPositionState = cameraPositionState,


                    properties = MapProperties(

                        mapType = MapType.HYBRID

                    )


                ) {
                    drones.forEach { drone ->

                        Marker(

                            state = MarkerState(
                                position = drone.position
                            ),

                            title = drone.name,

                            snippet = "Обнаружен"

                        )

                    }


                }


            }


        }


    }


}