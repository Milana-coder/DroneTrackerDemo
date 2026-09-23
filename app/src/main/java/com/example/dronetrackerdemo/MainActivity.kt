package com.example.dronetrackerdemo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.core.content.ContextCompat

import com.example.dronetrackerdemo.data.Alert
import com.example.dronetrackerdemo.data.AirObject
import com.example.dronetrackerdemo.data.GeoJsonLoader
import com.example.dronetrackerdemo.data.OblastPolygon
import com.example.dronetrackerdemo.data.RaionPolygon
import com.example.dronetrackerdemo.data.RetrofitClient

import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng

import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext


// =============================================================
// MAIN ACTIVITY
// =============================================================

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


// =============================================================
// ОСНОВНОЙ ЭКРАН
// =============================================================

@Composable
fun DroneTrackerScreen() {

    // =========================================================
    // СОСТОЯНИЕ
    // =========================================================

    var monitoring by remember {
        mutableStateOf(false)
    }

    var airObjects by remember {
        mutableStateOf<List<AirObject>>(emptyList())
    }

    var alerts by remember {
        mutableStateOf<List<Alert>>(emptyList())
    }

    var oblastPolygons by remember {
        mutableStateOf<List<OblastPolygon>>(emptyList())
    }

    var raionPolygons by remember {
        mutableStateOf<List<RaionPolygon>>(emptyList())
    }

    var raionCache by remember {
        mutableStateOf<Map<String, List<RaionPolygon>>>(emptyMap())
    }

    // Выбранная область
    var selectedOblastName by remember {
        mutableStateOf<String?>(null)
    }

    // Выбранный район
    var selectedRaionName by remember {
        mutableStateOf<String?>(null)
    }

    val context = LocalContext.current


    // =========================================================
    // ГЕОЛОКАЦИЯ
    // =========================================================

    var locationPermissionGranted by remember {

        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )

    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            locationPermissionGranted = granted

        }

    LaunchedEffect(Unit) {

        if (!locationPermissionGranted) {

            permissionLauncher.launch(
                Manifest.permission.ACCESS_FINE_LOCATION
            )

        }

    }


    // =========================================================
    // ЗАГРУЗКА GEOJSON ОБЛАСТЕЙ
    // =========================================================

    LaunchedEffect(Unit) {

        try {

            val loaded = withContext(Dispatchers.IO) {

                GeoJsonLoader.loadOblasts(context)

            }

            oblastPolygons = loaded

            println(
                "GEOJSON: областей = ${loaded.size}"
            )

        } catch (e: Exception) {

            println(
                "ОШИБКА GEOJSON ОБЛАСТЕЙ: ${e.message}"
            )

        }

    }


    // =========================================================
    // ПОЛУЧЕНИЕ ДАННЫХ ОТ FLASK
    // =========================================================

    LaunchedEffect(monitoring) {

        while (monitoring) {

            try {

                // -------------------------------------------------
                // ОБЪЕКТЫ
                // -------------------------------------------------

                val newObjects =
                    RetrofitClient.api.getObjects()

                airObjects = newObjects


                // -------------------------------------------------
                // ТРЕВОГИ
                // -------------------------------------------------

                val alertResponse =
                    RetrofitClient.api.getAlerts()

                if (alertResponse.success) {

                    alerts = alertResponse.alerts

                }

                println(
                    "АЛЕРТЫ: ${alerts.size}"
                )

            } catch (e: Exception) {

                println(
                    "ОШИБКА API: ${e.message}"
                )

            }

            delay(3000)

        }

    }


    // =========================================================
    // ЗАГРУЗКА РАЙОНОВ
    // =========================================================

    LaunchedEffect(alerts) {

        val oblastsWithRaionAlerts =

            alerts
                .filter {

                    val alertType =
                        it.alert_type
                            ?.trim()
                            ?.lowercase()

                    val locationType =
                        it.location_type
                            ?.trim()
                            ?.lowercase()

                    alertType == "air_raid" &&
                            locationType == "raion"

                }
                .mapNotNull {

                    it.location_oblast
                        ?.trim()

                }
                .filter {

                    it.isNotBlank()

                }
                .distinct()


        for (oblastName in oblastsWithRaionAlerts) {

            if (!raionCache.containsKey(oblastName)) {

                try {

                    val loadedRaions =
                        withContext(Dispatchers.IO) {

                            GeoJsonLoader.loadRaions(
                                oblastName
                            )

                        }

                    raionCache =
                        raionCache +
                                (oblastName to loadedRaions)

                    println(
                        "GEOJSON: $oblastName — районов ${loadedRaions.size}"
                    )

                } catch (e: Exception) {

                    println(
                        "ОШИБКА РАЙОНОВ $oblastName: ${e.message}"
                    )

                }

            }

        }

    }


    // =========================================================
    // ОБЪЕДИНЯЕМ РАЙОНЫ ИЗ КЭША
    // =========================================================

    LaunchedEffect(raionCache) {

        raionPolygons =
            raionCache.values.flatten()

    }


    // =========================================================
    // КАМЕРА
    // =========================================================

    val cameraPositionState =
        rememberCameraPositionState {

            position =
                CameraPosition.fromLatLngZoom(
                    LatLng(
                        51.8,
                        32.2
                    ),
                    8f
                )

        }


    // =========================================================
    // СТАТИСТИКА ТРЕВОГ
    // =========================================================

    val airRaidAlerts =
        alerts.filter {

            it.alert_type
                ?.trim()
                ?.equals(
                    "air_raid",
                    ignoreCase = true
                ) == true

        }


    val artilleryAlerts =
        alerts.filter {

            it.alert_type
                ?.trim()
                ?.equals(
                    "artillery_shelling",
                    ignoreCase = true
                ) == true

        }


    val urbanFightAlerts =
        alerts.filter {

            it.alert_type
                ?.trim()
                ?.equals(
                    "urban_fights",
                    ignoreCase = true
                ) == true

        }


    val chemicalAlerts =
        alerts.filter {

            it.alert_type
                ?.trim()
                ?.equals(
                    "chemical",
                    ignoreCase = true
                ) == true

        }


    val nuclearAlerts =
        alerts.filter {

            it.alert_type
                ?.trim()
                ?.equals(
                    "nuclear",
                    ignoreCase = true
                ) == true

        }


    // =========================================================
    // ТИПЫ ОБЪЕКТОВ
    // =========================================================

    val uavObjects =
        airObjects.filter {

            val type =
                it.type
                    .trim()
                    .lowercase()

            type == "uav" ||
                    type == "drone" ||
                    type == "бпла"

        }


    val fpvObjects =
        airObjects.filter {

            it.type
                .trim()
                .lowercase() == "fpv"

        }


    val missileObjects =
        airObjects.filter {

            when (
                it.type
                    .trim()
                    .lowercase()
            ) {

                "missile",
                "rocket",
                "ракета" -> true

                else -> false

            }

        }


    val kabObjects =
        airObjects.filter {

            when (
                it.type
                    .trim()
                    .lowercase()
            ) {

                "kab",
                "каб",
                "guided_bomb",
                "guided_bombs",
                "guided_aerial_bomb",
                "guided_aerial_bombs" -> true

                else -> false

            }

        }


    // =========================================================
    // ТИПЫ УГРОЗ
    // =========================================================

    val allThreats =
        airRaidAlerts.flatMap {
            it.threats ?: emptyList()
        }


    val droneThreats =
        allThreats.filter {

            it.threat_type
                ?.equals(
                    "drones",
                    ignoreCase = true
                ) == true

        }


    val cruiseMissileThreats =
        allThreats.filter {

            it.threat_type
                ?.equals(
                    "cruise_missiles",
                    ignoreCase = true
                ) == true

        }


    val ballisticMissileThreats =
        allThreats.filter {

            it.threat_type
                ?.equals(
                    "ballistic_missiles",
                    ignoreCase = true
                ) == true

        }


    val guidedBombThreats =
        allThreats.filter {

            it.threat_type
                ?.equals(
                    "guided_aerial_bombs",
                    ignoreCase = true
                ) == true

        }


    val tacticalAircraftThreats =
        allThreats.filter {

            it.threat_type
                ?.equals(
                    "tactic_aircraft_activity",
                    ignoreCase = true
                ) == true

        }


    val strategicAircraftThreats =
        allThreats.filter {

            it.threat_type
                ?.equals(
                    "strategic_aircraft_activity",
                    ignoreCase = true
                ) == true

        }


    val mig31kThreats =
        allThreats.filter {

            it.threat_type
                ?.equals(
                    "mig31k_departure",
                    ignoreCase = true
                ) == true

        }


    val unspecifiedMissileThreats =
        allThreats.filter {

            it.threat_type
                ?.equals(
                    "unspecified_missiles",
                    ignoreCase = true
                ) == true

        }


    val knownThreatTypes = setOf(

        "drones",
        "cruise_missiles",
        "ballistic_missiles",
        "guided_aerial_bombs",
        "tactic_aircraft_activity",
        "strategic_aircraft_activity",
        "mig31k_departure",
        "unspecified_missiles",
        "air_defense",
        "unknown"

    )


    val unknownThreats =
        allThreats.filter {

            val type =
                it.threat_type
                    ?.lowercase()

            !type.isNullOrBlank() &&
                    type !in knownThreatTypes

        }


    // =========================================================
    // ДРУГИЕ ТИПЫ ТРЕВОГ
    // =========================================================

    val otherThreats =
        alerts.filter {

            val type =
                it.alert_type
                    ?.trim()
                    ?.lowercase()

            !type.isNullOrBlank() &&
                    type != "air_raid"

        }


    // =========================================================
    // ОБЛАСТИ С ТРЕВОГОЙ
    // =========================================================

    val wholeOblastNames =

        airRaidAlerts
            .filter {

                it.location_type
                    ?.trim()
                    ?.lowercase() == "oblast"

            }
            .mapNotNull {

                it.location_oblast
                    ?.trim()

            }
            .filter {

                it.isNotBlank()

            }
            .distinct()


    val partialOblastNames =

        airRaidAlerts
            .filter {

                val type =
                    it.location_type
                        ?.trim()
                        ?.lowercase()

                type == "raion" ||
                        type == "hromada" ||
                        type == "city" ||
                        type == "community"

            }
            .mapNotNull {

                it.location_oblast
                    ?.trim()

            }
            .filter {

                it.isNotBlank()

            }
            .distinct()
            .filterNot { name ->

                wholeOblastNames.any { whole ->

                    whole.equals(
                        name,
                        ignoreCase = true
                    )

                }

            }


    val allAlertOblasts =
        (
                wholeOblastNames +
                        partialOblastNames
                )
            .distinct()


    // =========================================================
    // ДАННЫЕ ДЛЯ ВЫБРАННОЙ ОБЛАСТИ / РАЙОНА
    // =========================================================

    val selectedAlerts =

        when {

            selectedRaionName != null -> {

                alerts.filter { alert ->

                    val alertRaion =
                        alert.location_raion
                            ?.trim()

                    val alertOblast =
                        alert.location_oblast
                            ?.trim()

                    alertRaion?.equals(
                        selectedRaionName!!.trim(),
                        ignoreCase = true
                    ) == true &&

                            alertOblast?.equals(
                                selectedOblastName?.trim(),
                                ignoreCase = true
                            ) == true

                }

            }

            selectedOblastName != null -> {

                alerts.filter { alert ->

                    alert.location_oblast
                        ?.trim()
                        ?.equals(
                            selectedOblastName!!.trim(),
                            ignoreCase = true
                        ) == true

                }

            }

            else -> emptyList()

        }


    val selectedAirRaidAlerts =
        selectedAlerts.filter {

            it.alert_type
                ?.trim()
                ?.equals(
                    "air_raid",
                    ignoreCase = true
                ) == true

        }


    val selectedRaionAlerts =
        selectedAlerts.filter {

            it.location_type
                ?.trim()
                ?.equals(
                    "raion",
                    ignoreCase = true
                ) == true

        }


    val selectedThreatTypes =

        selectedAirRaidAlerts
            .flatMap {
                it.threats ?: emptyList()
            }
            .mapNotNull {
                it.threat_type
            }
            .distinct()


    val selectedOtherThreatTypes =

        selectedAlerts
            .filter {

                it.alert_type
                    ?.trim()
                    ?.lowercase() != "air_raid"

            }
            .mapNotNull {

                it.alert_type

            }
            .distinct()


    // =========================================================
    // ИНТЕРФЕЙС
    // =========================================================

    Scaffold(

        topBar = {

            Surface(
                shadowElevation = 4.dp
            ) {

                Text(

                    text = "🚁 Real Air Monitor",

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),

                    style =
                        MaterialTheme
                            .typography
                            .titleLarge,

                    fontWeight =
                        FontWeight.Bold

                )

            }

        }

    ) { padding ->

        Column(

            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp),

            horizontalAlignment =
                Alignment.CenterHorizontally

        ) {

            // =================================================
            // СТАТУС
            // =================================================

            Text(

                text =
                    if (monitoring)
                        "🟢 Мониторинг активен"
                    else
                        "⚪ Мониторинг остановлен",

                style =
                    MaterialTheme
                        .typography
                        .titleMedium

            )


            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )


            // =================================================
            // КНОПКА
            // =================================================

            Button(

                onClick = {
                    monitoring = !monitoring
                }

            ) {

                Text(

                    text =
                        if (monitoring)
                            "Остановить мониторинг"
                        else
                            "Запустить мониторинг"

                )

            }


            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )


// =================================================
// КОМПАКТНАЯ СТАТИСТИКА
// =================================================

// -------------------------------------------------
// ТИПЫ ОБЪЕКТОВ
// -------------------------------------------------

            val uavObjects =
                airObjects.filter {

                    val type =
                        it.type
                            .trim()
                            .lowercase()

                    type == "uav" ||
                            type == "drone" ||
                            type == "бпла"
                }


            val fpvObjects =
                airObjects.filter {

                    it.type
                        .trim()
                        .lowercase() == "fpv"

                }


            val missileObjects =
                airObjects.filter {

                    when (
                        it.type
                            .trim()
                            .lowercase()
                    ) {

                        "missile",
                        "rocket",
                        "ракета" -> true

                        else -> false
                    }
                }


            val kabObjects =
                airObjects.filter {

                    when (
                        it.type
                            .trim()
                            .lowercase()
                    ) {

                        "kab",
                        "каб",
                        "guided_bomb",
                        "guided_bombs",
                        "guided_aerial_bomb",
                        "guided_aerial_bombs" -> true

                        else -> false
                    }
                }


// -------------------------------------------------
// СТРОКА ТИПОВ
// -------------------------------------------------

            if (
                uavObjects.isNotEmpty() ||
                missileObjects.isNotEmpty() ||
                kabObjects.isNotEmpty() ||
                fpvObjects.isNotEmpty()
            ) {

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.SpaceEvenly,

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    // БПЛА

                    if (uavObjects.isNotEmpty()) {

                        Text(
                            text =
                                "🛸 БПЛА: ${uavObjects.size}",

                            fontSize =
                                11.sp,

                            fontWeight =
                                FontWeight.Bold
                        )
                    }


                    // РАКЕТЫ

                    if (missileObjects.isNotEmpty()) {

                        Text(
                            text =
                                "🚀 Ракеты: ${missileObjects.size}",

                            fontSize =
                                11.sp,

                            fontWeight =
                                FontWeight.Bold
                        )
                    }


                    // КАБ

                    if (kabObjects.isNotEmpty()) {

                        Text(
                            text =
                                "💣 КАБ: ${kabObjects.size}",

                            fontSize =
                                11.sp,

                            fontWeight =
                                FontWeight.Bold
                        )
                    }


                    // FPV

                    if (fpvObjects.isNotEmpty()) {

                        Text(
                            text =
                                "🎯 FPV: ${fpvObjects.size}",

                            fontSize =
                                11.sp,

                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }
            }


// -------------------------------------------------
// ОТСТУП
// -------------------------------------------------

            Spacer(
                modifier =
                    Modifier.height(3.dp)
            )


// -------------------------------------------------
// СТАТИСТИКА ТРЕВОГ
// -------------------------------------------------

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.SpaceEvenly,

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    text =
                        "🚨 Тревог: ${airRaidAlerts.size}",

                    fontSize =
                        11.sp,

                    fontWeight =
                        FontWeight.Bold
                )


                Text(
                    text =
                        "🗺 Областей: ${allAlertOblasts.size}",

                    fontSize =
                        11.sp,

                    fontWeight =
                        FontWeight.Medium
                )


                Text(
                    text =
                        "🔴 ${wholeOblastNames.size} полностью",

                    fontSize =
                        11.sp
                )


                Text(
                    text =
                        "🟠 ${partialOblastNames.size} частично",

                    fontSize =
                        11.sp
                )
            }


// -------------------------------------------------
// ОБЛАСТИ: ПОЛНОСТЬЮ
// -------------------------------------------------

            if (wholeOblastNames.isNotEmpty()) {

                Text(
                    text =
                        "🔴 ${wholeOblastNames.joinToString(", ")}",

                    fontSize =
                        9.sp,

                    textAlign =
                        TextAlign.Center,

                    maxLines =
                        1,

                    modifier =
                        Modifier.fillMaxWidth()
                )
            }


// -------------------------------------------------
// ОБЛАСТИ: ЧАСТИЧНО
// -------------------------------------------------

            if (partialOblastNames.isNotEmpty()) {

                Text(
                    text =
                        "🟠 ${partialOblastNames.joinToString(", ")}",

                    fontSize =
                        9.sp,

                    textAlign =
                        TextAlign.Center,

                    maxLines =
                        1,

                    modifier =
                        Modifier.fillMaxWidth()
                )
            }


            Spacer(
                modifier =
                    Modifier.height(5.dp)
            )


// =================================================
// КАРТА
// =================================================


            // =================================================
            // КАРТА
            // =================================================

            Card(

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(500.dp)

            ) {

                Box(
                    modifier =
                        Modifier.fillMaxSize()
                ) {

                    // =================================================
                    // GOOGLE MAP
                    // =================================================

                    GoogleMap(

                        modifier =
                            Modifier.fillMaxSize(),

                        cameraPositionState =
                            cameraPositionState,

                        properties =
                            MapProperties(
                                mapType =
                                    MapType.HYBRID
                            )

                    ) {

                        // =================================================
                        // ОБЛАСТИ
                        // =================================================

                        oblastPolygons.forEach { oblast ->

                            val oblastName =
                                oblast.name.trim()


                            val wholeOblastAlerts =
                                airRaidAlerts.filter { alert ->

                                    val alertOblast =
                                        alert.location_oblast
                                            ?.trim()

                                    val type =
                                        alert.location_type
                                            ?.trim()
                                            ?.lowercase()

                                    alertOblast?.equals(
                                        oblastName,
                                        ignoreCase = true
                                    ) == true &&
                                            type == "oblast"

                                }


                            val partialOblastAlerts =
                                airRaidAlerts.filter { alert ->

                                    val alertOblast =
                                        alert.location_oblast
                                            ?.trim()

                                    val type =
                                        alert.location_type
                                            ?.trim()
                                            ?.lowercase()

                                    alertOblast?.equals(
                                        oblastName,
                                        ignoreCase = true
                                    ) == true &&

                                            (
                                                    type == "raion" ||
                                                            type == "hromada" ||
                                                            type == "city" ||
                                                            type == "community"
                                                    )

                                }


                            val hasWholeRed =
                                wholeOblastAlerts.any {

                                    it.alert_level
                                        ?.equals(
                                            "red",
                                            ignoreCase = true
                                        ) == true

                                }


                            val hasWholeYellow =
                                wholeOblastAlerts.any {

                                    it.alert_level
                                        ?.equals(
                                            "yellow",
                                            ignoreCase = true
                                        ) == true

                                }


                            val hasPartialRed =
                                partialOblastAlerts.any {

                                    it.alert_level
                                        ?.equals(
                                            "red",
                                            ignoreCase = true
                                        ) == true

                                }


                            val hasPartialYellow =
                                partialOblastAlerts.any {

                                    it.alert_level
                                        ?.equals(
                                            "yellow",
                                            ignoreCase = true
                                        ) == true

                                }


                            val fillColor = when {

                                hasWholeRed ->
                                    Color.Red.copy(
                                        alpha = 0.22f
                                    )

                                hasWholeYellow ->
                                    Color.Yellow.copy(
                                        alpha = 0.20f
                                    )

                                hasPartialRed ->
                                    Color.Red.copy(
                                        alpha = 0.08f
                                    )

                                hasPartialYellow ->
                                    Color.Yellow.copy(
                                        alpha = 0.08f
                                    )

                                else ->
                                    Color.Transparent

                            }


                            val strokeColor = when {

                                hasWholeRed ->
                                    Color.Red.copy(
                                        alpha = 0.70f
                                    )

                                hasWholeYellow ->
                                    Color.Yellow.copy(
                                        alpha = 0.70f
                                    )

                                hasPartialRed ->
                                    Color.Red.copy(
                                        alpha = 0.40f
                                    )

                                hasPartialYellow ->
                                    Color.Yellow.copy(
                                        alpha = 0.40f
                                    )

                                else ->
                                    Color.Transparent

                            }


                            if (
                                hasWholeRed ||
                                hasWholeYellow ||
                                hasPartialRed ||
                                hasPartialYellow
                            ) {

                                oblast.polygons.forEach { points ->

                                    Polygon(

                                        points = points,

                                        clickable = true,

                                        fillColor = fillColor,

                                        strokeColor = strokeColor,

                                        strokeWidth = 1.5f,

                                        onClick = {

                                            selectedOblastName =
                                                oblastName

                                            selectedRaionName =
                                                null

                                        }

                                    )

                                }

                            }

                        }


                        // =================================================
                        // РАЙОНЫ
                        // =================================================

                        raionPolygons.forEach { raion ->

                            val raionAlerts =
                                airRaidAlerts.filter { alert ->

                                    val alertRaion =
                                        alert.location_raion
                                            ?.trim()

                                    val alertOblast =
                                        alert.location_oblast
                                            ?.trim()

                                    val alertType =
                                        alert.alert_type
                                            ?.trim()
                                            ?.lowercase()

                                    val locationType =
                                        alert.location_type
                                            ?.trim()
                                            ?.lowercase()

                                    alertRaion?.equals(
                                        raion.name.trim(),
                                        ignoreCase = true
                                    ) == true &&

                                            alertOblast?.equals(
                                                raion.oblastName.trim(),
                                                ignoreCase = true
                                            ) == true &&

                                            locationType == "raion" &&

                                            alertType == "air_raid"

                                }


                            val hasRed =
                                raionAlerts.any {

                                    it.alert_level
                                        ?.equals(
                                            "red",
                                            ignoreCase = true
                                        ) == true

                                }


                            val hasYellow =
                                raionAlerts.any {

                                    it.alert_level
                                        ?.equals(
                                            "yellow",
                                            ignoreCase = true
                                        ) == true

                                }


                            if (
                                hasRed ||
                                hasYellow
                            ) {

                                val fillColor =

                                    if (hasRed) {

                                        Color.Red.copy(
                                            alpha = 0.25f
                                        )

                                    } else {

                                        Color.Yellow.copy(
                                            alpha = 0.22f
                                        )

                                    }


                                val strokeColor =

                                    if (hasRed) {

                                        Color.Red.copy(
                                            alpha = 0.70f
                                        )

                                    } else {

                                        Color.Yellow.copy(
                                            alpha = 0.70f
                                        )

                                    }


                                raion.polygons.forEach { points ->

                                    Polygon(

                                        points = points,

                                        clickable = true,

                                        fillColor = fillColor,

                                        strokeColor = strokeColor,

                                        strokeWidth = 1.5f,

                                        onClick = {

                                            selectedOblastName =
                                                raion.oblastName

                                            selectedRaionName =
                                                raion.name

                                        }

                                    )

                                }

                            }

                        }


                        // =================================================
                        // МАРКЕРЫ ОБЪЕКТОВ
                        // =================================================

                        val redMarkerIcon =
                            remember {

                                BitmapDescriptorFactory
                                    .defaultMarker(
                                        BitmapDescriptorFactory
                                            .HUE_RED
                                    )

                            }


                        airObjects.forEach { obj ->

                            // =================================================
                            // ПЛАВНОЕ ДВИЖЕНИЕ
                            // =================================================

                            val animatedLatitude by
                            animateFloatAsState(

                                targetValue =
                                    obj.lat.toFloat(),

                                animationSpec =
                                    tween(
                                        durationMillis = 2800,
                                        easing = LinearEasing
                                    ),

                                label =
                                    "latitude_${obj.id}"

                            )


                            val animatedLongitude by
                            animateFloatAsState(

                                targetValue =
                                    obj.lon.toFloat(),

                                animationSpec =
                                    tween(
                                        durationMillis = 2800,
                                        easing = LinearEasing
                                    ),

                                label =
                                    "longitude_${obj.id}"

                            )


                            val position =
                                LatLng(

                                    animatedLatitude.toDouble(),

                                    animatedLongitude.toDouble()

                                )


                            val place = when {

                                !obj.locality.isNullOrBlank() &&
                                        !obj.region.isNullOrBlank() ->

                                    "${obj.locality}, ${obj.region}"


                                !obj.locality.isNullOrBlank() ->

                                    obj.locality


                                !obj.region.isNullOrBlank() ->

                                    obj.region


                                else ->

                                    "Место неизвестно"

                            }


                            val direction =
                                headingToDirection(
                                    obj.heading
                                )


                            val typeName =
                                objectTypeName(
                                    obj.type
                                )


                            val speedText =
                                if (obj.speedKnown) {

                                    "${obj.speed.toInt()} км/ч"

                                } else {

                                    "—"

                                }


                            // ---------------------------------------------
                            // ОСНОВНОЙ МАРКЕР
                            // ---------------------------------------------

                            Marker(

                                state =
                                    rememberUpdatedMarkerState(
                                        position
                                    ),

                                icon =
                                    redMarkerIcon,

                                title =
                                    typeName,

                                snippet =
                                    """
                                    📍 $place
                                    
                                    ⚡ Скорость: $speedText
                                    🧭 Направление: $direction
                                    📐 Курс: ${obj.heading.toInt()}°
                                    """.trimIndent(),

                                zIndex = 10f

                            )


                            // ---------------------------------------------
                            // НАЗВАНИЕ ОБЪЕКТА
                            // ---------------------------------------------

                            MarkerComposable(

                                keys =
                                    arrayOf(
                                        "label",
                                        obj.id
                                    ),

                                state =
                                    rememberUpdatedMarkerState(
                                        position
                                    ),

                                anchor =
                                    Offset(
                                        0.5f,
                                        1.8f
                                    ),

                                zIndex = 5f

                            ) {

                                Text(

                                    text =
                                        obj.name,

                                    fontSize =
                                        9.sp,

                                    fontWeight =
                                        FontWeight.Bold,

                                    color =
                                        Color.White,

                                    textAlign =
                                        TextAlign.Center,

                                    maxLines = 1

                                )

                            }


                            // ---------------------------------------------
                            // СЛЕД
                            // ---------------------------------------------

                            if (obj.trail.size > 1) {

                                Polyline(

                                    points =
                                        obj.trail.map {

                                            LatLng(
                                                it.lat,
                                                it.lon
                                            )

                                        },

                                    color =
                                        Color.Red,

                                    width = 4f

                                )

                            }


                            // ---------------------------------------------
                            // НАПРАВЛЕНИЕ
                            // ---------------------------------------------

                            Polyline(

                                points =
                                    listOf(

                                        position,

                                        calculateDirectionPoint(
                                            position,
                                            obj.heading
                                        )

                                    ),

                                color =
                                    Color.Red,

                                width = 4f

                            )

                        }

                    }


                    // =================================================
                    // КАРТОЧКА ВЫБРАННОЙ ОБЛАСТИ / РАЙОНА
                    // =================================================

                    if (selectedOblastName != null) {

                        Surface(

                            modifier =
                                Modifier
                                    .align(
                                        Alignment.BottomCenter
                                    )
                                    .padding(8.dp),

                            shape =
                                MaterialTheme
                                    .shapes
                                    .medium,

                            shadowElevation =
                                6.dp,

                            tonalElevation =
                                6.dp

                        ) {

                            Column(

                                modifier =
                                    Modifier
                                        .padding(12.dp)
                                        .widthIn(
                                            max = 340.dp
                                        )

                            ) {

                                Text(

                                    text =
                                        if (
                                            selectedRaionName != null
                                        ) {

                                            "📍 $selectedRaionName"

                                        } else {

                                            "📍 $selectedOblastName"

                                        },

                                    fontWeight =
                                        FontWeight.Bold,

                                    fontSize =
                                        15.sp

                                )


                                if (
                                    selectedRaionName != null
                                ) {

                                    Text(

                                        text =
                                            "Область: $selectedOblastName",

                                        fontSize =
                                            11.sp

                                    )

                                }


                                Spacer(
                                    modifier =
                                        Modifier.height(4.dp)
                                )


                                Text(

                                    text =
                                        "🚨 Воздушных тревог: ${selectedAirRaidAlerts.size}",

                                    fontSize =
                                        12.sp

                                )


                                Text(

                                    text =
                                        "🗺 Районных записей: ${selectedRaionAlerts.size}",

                                    fontSize =
                                        12.sp

                                )


                                // -----------------------------------------
                                // УГРОЗЫ
                                // -----------------------------------------

                                if (
                                    selectedThreatTypes.isNotEmpty()
                                ) {

                                    Spacer(
                                        modifier =
                                            Modifier.height(4.dp)
                                    )

                                    Text(

                                        text =
                                            "⚠️ Угрозы:",

                                        fontWeight =
                                            FontWeight.Bold,

                                        fontSize =
                                            12.sp

                                    )


                                    selectedThreatTypes
                                        .forEach { threat ->

                                            Text(

                                                text =
                                                    threatDisplayName(
                                                        threat
                                                    ),

                                                fontSize =
                                                    11.sp

                                            )

                                        }

                                }


                                // -----------------------------------------
                                // ДРУГИЕ ТИПЫ ТРЕВОГ
                                // -----------------------------------------

                                if (
                                    selectedOtherThreatTypes
                                        .isNotEmpty()
                                ) {

                                    Spacer(
                                        modifier =
                                            Modifier.height(4.dp)
                                    )

                                    selectedOtherThreatTypes
                                        .forEach { type ->

                                            Text(

                                                text =
                                                    alertTypeDisplayName(
                                                        type
                                                    ),

                                                fontSize =
                                                    11.sp

                                            )

                                        }

                                }


                                Spacer(
                                    modifier =
                                        Modifier.height(4.dp)
                                )


                                TextButton(

                                    onClick = {

                                        selectedOblastName =
                                            null

                                        selectedRaionName =
                                            null

                                    }

                                ) {

                                    Text(
                                        "Закрыть"
                                    )

                                }

                            }

                        }

                    }

                }

            }

        }

    }

}


// =============================================================
// НАЗВАНИЕ ТИПА ОБЪЕКТА
// =============================================================

fun objectTypeName(
    type: String
): String {

    return when (
        type.trim().lowercase()
    ) {

        "uav" ->
            "БПЛА"

        "drone" ->
            "БПЛА"

        "бпла" ->
            "БПЛА"

        "fpv" ->
            "FPV"

        "missile" ->
            "РАКЕТА"

        "rocket" ->
            "РАКЕТА"

        "ракета" ->
            "РАКЕТА"

        else ->
            type
                .trim()
                .uppercase()

    }

}


// =============================================================
// КУРС → СТОРОНА СВЕТА
// =============================================================

fun headingToDirection(
    heading: Double
): String {

    val h =
        ((heading % 360) + 360) % 360

    return when {

        h >= 337.5 ||
                h < 22.5 ->

            "С"

        h < 67.5 ->

            "СВ"

        h < 112.5 ->

            "В"

        h < 157.5 ->

            "ЮВ"

        h < 202.5 ->

            "Ю"

        h < 247.5 ->

            "ЮЗ"

        h < 292.5 ->

            "З"

        else ->

            "СЗ"

    }

}


// =============================================================
// ТОЧКА ДЛЯ ЛИНИИ НАПРАВЛЕНИЯ
// =============================================================

fun calculateDirectionPoint(
    position: LatLng,
    heading: Double,
    distance: Double = 0.05
): LatLng {

    val radians =
        Math.toRadians(
            heading
        )

    return LatLng(

        position.latitude +
                distance *
                kotlin.math.cos(
                    radians
                ),

        position.longitude +
                distance *
                kotlin.math.sin(
                    radians
                )

    )

}


// =============================================================
// НАЗВАНИЕ УГРОЗЫ
// =============================================================

fun threatDisplayName(
    type: String
): String {

    return when (
        type.trim().lowercase()
    ) {

        "drones" ->
            "🛸 Дроны"

        "cruise_missiles" ->
            "🚀 Крылатые ракеты"

        "ballistic_missiles" ->
            "☄️ Баллистическая угроза"

        "guided_aerial_bombs" ->
            "💣 КАБы"

        "tactic_aircraft_activity" ->
            "✈️ Тактическая авиация"

        "strategic_aircraft_activity" ->
            "✈️ Стратегическая авиация"

        "mig31k_departure" ->
            "⚠️ Вылет МиГ-31К"

        "unspecified_missiles" ->
            "🚀 Неуточнённая ракетная угроза"

        "air_defense" ->
            "🛡 Работа ПВО"

        "unknown" ->
            "❓ Неизвестная угроза"

        else ->
            "❓ $type"

    }

}


// =============================================================
// НАЗВАНИЕ ТИПА ТРЕВОГИ
// =============================================================

fun alertTypeDisplayName(
    type: String
): String {

    return when (
        type.trim().lowercase()
    ) {

        "artillery_shelling" ->
            "🟠 Артиллерийский обстрел"

        "urban_fights" ->
            "🟣 Уличные бои"

        "chemical" ->
            "🟢 Химическая угроза"

        "nuclear" ->
            "🟡 Ядерная угроза"

        else ->
            "⚠️ $type"

    }

}