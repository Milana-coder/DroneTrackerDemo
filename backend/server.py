from flask import Flask, jsonify
import json
import os
import threading
import time
from pathlib import Path

import requests
import websocket


app = Flask(__name__)


# =========================================================
# NEPTUN WEBSOCKET
# =========================================================

NEPTUN_WS_URL = "wss://neptun.in.ua/api/v1/stream"

neptun_objects = {}
neptun_lock = threading.Lock()


# =========================================================
# ALERTS.IN.UA
# =========================================================

ALERTS_URL = "https://api.alerts.in.ua/v1/alerts/active.json"

ALERT_STATUS_URL = (
    "https://api.alerts.in.ua/v1/iot/"
    "active_air_raid_alerts.json"
)

# Токен берётся из переменной окружения.
# Не вставляйте секретный токен в код.
ALERTS_API_TOKEN = os.getenv("ALERTS_API_TOKEN")

ALERTS_CACHE_SECONDS = 30
ALERT_STATUS_CACHE_SECONDS = 30

alerts_cache = {
    "data": None,
    "time": 0,
}

alert_status_cache = {
    "data": None,
    "time": 0,
}


# =========================================================
# NEPTUN WEBSOCKET WORKER
# =========================================================

def neptun_websocket_worker():
    print("NEPTUN WebSocket: запуск фонового потока")

    while True:
        try:
            print("NEPTUN WebSocket: подключение...")

            ws = websocket.create_connection(
                NEPTUN_WS_URL,
                timeout=30,
                enable_multithread=True,
            )

            print("NEPTUN WebSocket: соединение установлено")

            try:
                while True:
                    message = ws.recv()

                    if not message:
                        print(
                            "NEPTUN WebSocket: "
                            "получено пустое сообщение"
                        )
                        break

                    try:
                        envelope = json.loads(message)
                    except json.JSONDecodeError:
                        print(
                            "NEPTUN WebSocket: "
                            "сообщение не в формате JSON"
                        )
                        continue

                    event_type = envelope.get("type")
                    data = envelope.get("data")

                    # SNAPSHOT
                    if event_type == "snapshot":
                        threats = []

                        if isinstance(data, dict):
                            threats = data.get("threats", [])
                        elif isinstance(data, list):
                            threats = data

                        if not isinstance(threats, list):
                            threats = []

                        with neptun_lock:
                            neptun_objects.clear()

                            for obj in threats:
                                if not isinstance(obj, dict):
                                    continue

                                obj_id = obj.get("id")

                                if obj_id:
                                    neptun_objects[obj_id] = obj

                        print(
                            "NEPTUN WebSocket: получен snapshot — "
                            f"{len(threats)} объектов"
                        )

                    # UPSERT
                    elif event_type == "upsert":
                        if isinstance(data, dict):
                            obj_id = data.get("id")

                            if obj_id:
                                with neptun_lock:
                                    neptun_objects[obj_id] = data

                                print(
                                    "NEPTUN WebSocket: "
                                    "объект обновлён"
                                )

                    # REMOVE
                    elif event_type == "remove":
                        obj_id = None

                        if isinstance(data, dict):
                            obj_id = data.get("id")

                        if obj_id:
                            with neptun_lock:
                                neptun_objects.pop(obj_id, None)

                            print(
                                "NEPTUN WebSocket: объект удалён"
                            )

                    # HEARTBEAT
                    elif event_type == "heartbeat":
                        pass

                    # ALERTS
                    elif event_type == "alerts":
                        print(
                            "NEPTUN ALERTS DATA:",
                            json.dumps(data, ensure_ascii=False)
                        )

                    # UNKNOWN EVENT
                    else:
                        print(
                            "NEPTUN WebSocket: неизвестный тип события "
                            f"{event_type}"
                        )

            finally:
                try:
                    ws.close()
                except Exception:
                    pass

        except Exception as error:
            print(
                "NEPTUN WebSocket: ошибка соединения "
                f"({type(error).__name__})"
            )

        print("NEPTUN WebSocket: соединение закрыто")
        print(
            "NEPTUN WebSocket: "
            "повторное подключение через 5 секунд..."
        )

        time.sleep(5)


# =========================================================
# OBJECTS API
# =========================================================

@app.route("/objects")
def get_objects():
    with neptun_lock:
        objects_snapshot = list(neptun_objects.values())

    objects = []

    for obj in objects_snapshot:
        if not isinstance(obj, dict):
            continue

        obj_id = obj.get("id")
        lat = obj.get("lat")
        lon = obj.get("lon")

        if not obj_id or lat is None or lon is None:
            continue

        velocity = obj.get("velocity") or {}

        if not isinstance(velocity, dict):
            velocity = {}

        source_speed = velocity.get("speedKmh")

        if source_speed is None:
            speed = 0
            speed_known = False
        else:
            speed = source_speed
            speed_known = True

        heading = obj.get("heading")

        if heading is None:
            heading = velocity.get("bearingDeg")

        if heading is None:
            heading = 0

        trail = obj.get("trail") or []

        objects.append(
            {
                "id": obj_id,
                "name": obj.get("title") or "Unknown",
                "lat": lat,
                "lon": lon,
                "speed": speed,
                "heading": heading,
                "type": obj.get("type"),
                "status": obj.get("status") or "unknown",
                "confidence": (
                    obj.get("confidenceLevel") or "unknown"
                ),
                "sources": obj.get("sourceCount") or 0,
                "region": obj.get("region") or "",
                "locality": obj.get("locality") or "",
                "trail": trail,
                "speedKnown": speed_known,
                "updatedAt": obj.get("updatedAt"),
                "uncertaintyKm": obj.get("uncertaintyKm"),
                "positionQuality": obj.get("positionQuality"),
                "areaOnly": obj.get("areaOnly", False),
            }
        )

    print(f"Отдано Android объектов: {len(objects)}")

    return jsonify(objects)


# =========================================================
# EVENTS API
# =========================================================

@app.route("/events", methods=["GET"])
def get_events():
    events_file = Path(__file__).with_name("events.json")

    try:
        with events_file.open("r", encoding="utf-8") as file:
            events = json.load(file)

        return jsonify(events)

    except (OSError, json.JSONDecodeError) as error:
        print(
            "Ошибка чтения events.json: "
            f"{type(error).__name__}"
        )

        return jsonify(
            {"error": "Не удалось загрузить события"}
        ), 500


# =========================================================
# ALERTS.IN.UA API
# =========================================================

@app.route("/alerts")
def get_alerts():
    global alerts_cache

    if not ALERTS_API_TOKEN:
        print("ОШИБКА: не задан ALERTS_API_TOKEN")

        return jsonify(
            {
                "success": False,
                "error": "ALERTS_API_TOKEN is not configured",
                "alerts": [],
            }
        ), 500

    now = time.time()

    if (
        alerts_cache["data"] is not None
        and now - alerts_cache["time"] < ALERTS_CACHE_SECONDS
    ):
        return jsonify(
            {
                "success": True,
                "cached": True,
                "alerts": alerts_cache["data"],
            }
        )

    try:
        response = requests.get(
            ALERTS_URL,
            headers={
                "Authorization": f"Bearer {ALERTS_API_TOKEN}"
            },
            timeout=10,
        )

        if response.status_code == 401:
            print(
                "Ошибка alerts.in.ua: "
                "неверный или неактивный токен"
            )

            return jsonify(
                {
                    "success": False,
                    "error": "Unauthorized",
                    "alerts": [],
                }
            ), 401

        if response.status_code == 429:
            print(
                "Ошибка alerts.in.ua: "
                "слишком много запросов"
            )

            return jsonify(
                {
                    "success": False,
                    "error": "Too many requests",
                    "alerts": [],
                }
            ), 429

        response.raise_for_status()

        data = response.json()

        if isinstance(data, dict):
            alerts = data.get("alerts", [])
        else:
            alerts = []

        alerts_cache["data"] = alerts
        alerts_cache["time"] = now

        print(f"Получено активных тревог: {len(alerts)}")

        return jsonify(
            {
                "success": True,
                "cached": False,
                "alerts": alerts,
            }
        )

    except Exception as error:
        print(
            "Ошибка alerts.in.ua: "
            f"{type(error).__name__}"
        )

        if alerts_cache["data"] is not None:
            return jsonify(
                {
                    "success": False,
                    "cached": True,
                    "error": "Не удалось обновить данные",
                    "alerts": alerts_cache["data"],
                }
            )

        return jsonify(
            {
                "success": False,
                "error": "Не удалось получить данные тревог",
                "alerts": [],
            }
        ), 500


# =========================================================
# CURRENT ALERT STATUSES API
# =========================================================

@app.route("/alert-statuses")
def get_alert_statuses():
    global alert_status_cache

    if not ALERTS_API_TOKEN:
        print("ОШИБКА: не задан ALERTS_API_TOKEN")

        return jsonify(
            {
                "success": False,
                "error": "ALERTS_API_TOKEN is not configured",
                "statuses": "",
            }
        ), 500

    now = time.time()

    if (
        alert_status_cache["data"] is not None
        and now - alert_status_cache["time"]
        < ALERT_STATUS_CACHE_SECONDS
    ):
        return jsonify(
            {
                "success": True,
                "cached": True,
                "statuses": alert_status_cache["data"],
            }
        )

    try:
        response = requests.get(
            ALERT_STATUS_URL,
            headers={
                "Authorization": f"Bearer {ALERTS_API_TOKEN}"
            },
            timeout=10,
        )

        if response.status_code == 401:
            print(
                "Ошибка актуальных статусов: "
                "неверный или неактивный токен"
            )

            return jsonify(
                {
                    "success": False,
                    "error": "Unauthorized",
                    "statuses": "",
                }
            ), 401

        if response.status_code == 429:
            print(
                "Ошибка актуальных статусов: "
                "слишком много запросов"
            )

            return jsonify(
                {
                    "success": False,
                    "error": "Too many requests",
                    "statuses": "",
                }
            ), 429

        response.raise_for_status()

        data = response.json()

        if isinstance(data, str):
            statuses = data
        elif isinstance(data, list):
            statuses = "".join(str(item) for item in data)
        else:
            raise ValueError(
                "Неожиданный формат ответа статусов тревог"
            )

        alert_status_cache["data"] = statuses
        alert_status_cache["time"] = now

        print(
            "Получены актуальные статусы тревог: "
            f"{len(statuses)} UID"
        )

        return jsonify(
            {
                "success": True,
                "cached": False,
                "statuses": statuses,
            }
        )

    except Exception as error:
        print(
            "Ошибка актуальных статусов: "
            f"{type(error).__name__}"
        )

        if alert_status_cache["data"] is not None:
            return jsonify(
                {
                    "success": False,
                    "cached": True,
                    "error": "Не удалось обновить статусы",
                    "statuses": alert_status_cache["data"],
                }
            )

        return jsonify(
            {
                "success": False,
                "error": "Не удалось получить статусы тревог",
                "statuses": "",
            }
        ), 500


# =========================================================
# START NEPTUN WORKER
# =========================================================

def start_neptun_worker():
    thread = threading.Thread(
        target=neptun_websocket_worker,
        daemon=True,
        name="neptun-websocket",
    )

    thread.start()



# =========================================================
# START NEPTUN WORKER
# =========================================================

_neptun_worker_started = False
_neptun_worker_start_lock = threading.Lock()


def ensure_neptun_worker_started():
    global _neptun_worker_started

    with _neptun_worker_start_lock:
        if _neptun_worker_started:
            return

        start_neptun_worker()
        _neptun_worker_started = True


# =========================================================
# START LOCAL SERVER
# =========================================================

if __name__ == "__main__":
    ensure_neptun_worker_started()

    app.run(
        host="0.0.0.0",
        port=8000,
        threaded=True,
    )
else:
    ensure_neptun_worker_started()
