from flask import Flask, jsonify
import requests
import time
import os
import json
import threading
import websocket

app = Flask(__name__)


# =========================================================
# NEPTUN WEBSOCKET
# =========================================================

NEPTUN_WS_URL = "wss://neptun.in.ua/api/v1/stream"

# Здесь будем хранить последние данные NEPTUN
neptun_objects = {}

# Защита словаря от одновременного доступа
neptun_lock = threading.Lock()


# =========================================================
# ALERTS.IN.UA
# =========================================================

ALERTS_URL = "https://api.alerts.in.ua/v1/alerts/active.json"

# Токен берём из переменной окружения Windows.
# НЕ записываем токен в код.
ALERTS_API_TOKEN = os.getenv("ALERTS_API_TOKEN")


# Кэш тревог
alerts_cache = {
    "data": None,
    "time": 0
}

# 30 секунд
ALERTS_CACHE_SECONDS = 30


# =========================================================
# NEPTUN WEBSOCKET
# =========================================================

def neptun_websocket_worker():

    print(
        "NEPTUN WebSocket: "
        "запуск фонового потока"
    )

    while True:

        try:

            print(
                "NEPTUN WebSocket: "
                "подключение..."
            )

            ws = websocket.create_connection(
                NEPTUN_WS_URL,
                timeout=30,
                enable_multithread=True
            )

            print(
                "NEPTUN WebSocket: "
                "СОЕДИНЕНИЕ УСТАНОВЛЕНО"
            )

            while True:

                message = ws.recv()

                if not message:

                    print(
                        "NEPTUN WebSocket: "
                        "получено пустое сообщение"
                    )

                    break

                try:

                    envelope = json.loads(
                        message
                    )

                except json.JSONDecodeError:

                    print(
                        "NEPTUN WebSocket: "
                        "получено сообщение "
                        "не в формате JSON"
                    )

                    continue


                event_type = envelope.get(
                    "type"
                )

                data = envelope.get(
                    "data"
                )


                # =================================================
                # SNAPSHOT
                # =================================================

                if event_type == "snapshot":

                    threats = []

                    if isinstance(
                        data,
                        dict
                    ):

                        threats = data.get(
                            "threats",
                            []
                        )


                    with neptun_lock:

                        neptun_objects.clear()

                        for obj in threats:

                            obj_id = obj.get(
                                "id"
                            )

                            if obj_id:

                                neptun_objects[
                                    obj_id
                                ] = obj


                    print(
                        "NEPTUN WebSocket: "
                        f"получен snapshot — "
                        f"{len(threats)} объектов"
                    )


                # =================================================
                # UPSERT
                # =================================================

                elif event_type == "upsert":

                    if isinstance(
                        data,
                        dict
                    ):

                        obj_id = data.get(
                            "id"
                        )

                        if obj_id:

                            with neptun_lock:

                                neptun_objects[
                                    obj_id
                                ] = data


                            print(
                                "NEPTUN WebSocket: "
                                f"обновлён объект "
                                f"{obj_id}"
                            )


                # =================================================
                # REMOVE
                # =================================================

                elif event_type == "remove":

                    obj_id = None

                    if isinstance(
                        data,
                        dict
                    ):

                        obj_id = data.get(
                            "id"
                        )

                    if obj_id:

                        with neptun_lock:

                            neptun_objects.pop(
                                obj_id,
                                None
                            )


                        print(
                            "NEPTUN WebSocket: "
                            f"удалён объект "
                            f"{obj_id}"
                        )


                # =================================================
                # HEARTBEAT
                # =================================================

                elif event_type == "heartbeat":

                    print(
                        "NEPTUN WebSocket: "
                        "heartbeat"
                    )


                # =================================================
                # ALERTS
                # =================================================

                elif event_type == "alerts":

                    print(
                        "NEPTUN WebSocket: "
                        "получено событие alerts"
                    )


                # =================================================
                # НЕИЗВЕСТНОЕ СОБЫТИЕ
                # =================================================

                else:

                    print(
                        "NEPTUN WebSocket: "
                        f"неизвестный тип: "
                        f"{event_type}"
                    )


        except Exception as e:

            print(
                "NEPTUN WebSocket: "
                f"ошибка: {e}"
            )


        print(
            "NEPTUN WebSocket: "
            "соединение закрыто"
        )

        print(
            "NEPTUN WebSocket: "
            "повторное подключение "
            "через 5 секунд..."
        )

        time.sleep(5)


# =========================================================
# OBJECTS
# =========================================================

@app.route("/objects")
def get_objects():

    with neptun_lock:

        objects_snapshot = list(
            neptun_objects.values()
        )


    objects = []


    for obj in objects_snapshot:

        obj_id = obj.get("id")

        lat = obj.get("lat")
        lon = obj.get("lon")


        if not obj_id:
            continue

        if lat is None or lon is None:
            continue


        # -------------------------------------------------
        # Скорость NEPTUN
        # -------------------------------------------------

        velocity = (
            obj.get("velocity")
            or {}
        )

        source_speed = (
            velocity.get("speedKmh")
        )

        print(
            "NEPTUN SPEED:",
            obj_id,
            "speedKmh =",
            source_speed
        )

        if source_speed is None:

            speed = 0

            speed_known = False

        else:

            speed = source_speed

            speed_known = True


        # -------------------------------------------------
        # Курс NEPTUN
        # -------------------------------------------------

        heading = obj.get(
            "heading"
        )


        if heading is None:

            heading = (
                velocity.get(
                    "bearingDeg"
                )
            )


        if heading is None:

            heading = 0


        # -------------------------------------------------
        # TRAIL
        # -------------------------------------------------

        trail = (
            obj.get("trail")
            or []
        )


        # -------------------------------------------------
        # Объект для Android
        # -------------------------------------------------

        objects.append(
            {
                "id":
                    obj_id,

                "name":
                    obj.get(
                        "title"
                    )
                    or "Unknown",

                "lat":
                    lat,

                "lon":
                    lon,

                "speed":
                    speed,

                "heading":
                    heading,

                "type":
                    obj.get(
                        "type"
                    ),

                "status":
                    obj.get(
                        "status"
                    )
                    or "unknown",

                "confidence":
                    obj.get(
                        "confidenceLevel"
                    )
                    or "unknown",

                "sources":
                    obj.get(
                        "sourceCount"
                    )
                    or 0,

                "region":
                    obj.get(
                        "region"
                    )
                    or "",

                "locality":
                    obj.get(
                        "locality"
                    )
                    or "",

                "trail":
                    trail,

                "speedKnown":
                    speed_known,

                "updatedAt":
                    obj.get(
                        "updatedAt"
                    ),

                "uncertaintyKm":
                    obj.get(
                        "uncertaintyKm"
                    ),

                "positionQuality":
                    obj.get(
                        "positionQuality"
                    ),

                "areaOnly":
                    obj.get(
                        "areaOnly",
                        False
                    )
            }
        )


    print(
        "Отдано Android:",
        len(objects),
        "объектов"
    )


    return jsonify(objects)


# =========================================================
# ALERTS.IN.UA
# =========================================================

@app.route("/alerts")
def get_alerts():

    global alerts_cache


    # -----------------------------------------------------
    # Проверяем токен
    # -----------------------------------------------------

    if not ALERTS_API_TOKEN:

        print(
            "ОШИБКА: "
            "не задан ALERTS_API_TOKEN"
        )

        return jsonify({
            "success": False,
            "error":
                "ALERTS_API_TOKEN is not configured",
            "alerts": []
        }), 500


    # -----------------------------------------------------
    # Проверяем кэш
    # -----------------------------------------------------

    now = time.time()


    if (
        alerts_cache["data"] is not None
        and
        now - alerts_cache["time"]
        <
        ALERTS_CACHE_SECONDS
    ):

        return jsonify({
            "success": True,
            "cached": True,
            "alerts":
                alerts_cache["data"]
        })


    # -----------------------------------------------------
    # Запрос alerts.in.ua
    # -----------------------------------------------------

    try:

        response = requests.get(
            ALERTS_URL,
            headers={
                "Authorization":
                    f"Bearer {ALERTS_API_TOKEN}"
            },
            timeout=10
        )


        if response.status_code == 401:

            print(
                "Ошибка alerts.in.ua: "
                "неверный или неактивный токен"
            )

            return jsonify({
                "success": False,
                "error": "Unauthorized",
                "alerts": []
            }), 401


        if response.status_code == 429:

            print(
                "Ошибка alerts.in.ua: "
                "слишком много запросов"
            )

            return jsonify({
                "success": False,
                "error":
                    "Too many requests",
                "alerts": []
            }), 429


        response.raise_for_status()


        data = response.json()


        alerts = data.get(
            "alerts",
            []
        )


        alerts_cache["data"] = alerts
        alerts_cache["time"] = now


        print(
            f"Получено "
            f"{len(alerts)} активных тревог"
        )


        return jsonify({
            "success": True,
            "cached": False,
            "alerts": alerts
        })


    except Exception as e:

        print(
            "Ошибка alerts.in.ua:",
            e
        )


        if alerts_cache["data"] is not None:

            return jsonify({
                "success": False,
                "cached": True,
                "error": str(e),
                "alerts":
                    alerts_cache["data"]
            })


        return jsonify({
            "success": False,
            "error": str(e),
            "alerts": []
        }), 500


# =========================================================
# START NEPTUN THREAD
# =========================================================

def start_neptun_worker():

    thread = threading.Thread(
        target=neptun_websocket_worker,
        daemon=True
    )

    thread.start()


# =========================================================
# START SERVER
# =========================================================

if __name__ == "__main__":

    start_neptun_worker()

    app.run(
        host="0.0.0.0",
        port=8000,
        threaded=True
    )