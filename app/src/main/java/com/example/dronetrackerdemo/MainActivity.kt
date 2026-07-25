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


    // Координаты Харькова
    val kharkiv = LatLng(
        49.9935,
        36.2304
    )


    // Камера карты
    val cameraPositionState = rememberCameraPositionState {

        position = CameraPosition.fromLatLngZoom(
            kharkiv,
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


                    val markerState = remember {
                        MarkerState(
                            position = kharkiv
                        )
                    }


                    Marker(

                        state = markerState,

                        title = "Точка мониторинга",

                        snippet = "Харьков"

                    )


                }


            }


        }


    }


}