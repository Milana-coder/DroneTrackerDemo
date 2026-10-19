
from flask import Flask, jsonify
import json
import os
import threading
import time

import requests
import websocket


app = Flask(__name__)


# =========================================================
# NEPTUN WEBSOCKET
# =========================================================

NEPTUN_WS_URL = "wss://neptun.in.ua/api/v1/stream"

# Последние полученные объекты NEPTUN.
neptun_objects = {}
neptun_lock = threading.Lock()

# Защита от повторного запуска потока внутри одного процесса.
neptun_thread = None
neptun_thread_lock = threading.Lock()


# =========================================================
# ALERTS.IN.UA
# =========================================================

ALERTS_URL = "https://api.alerts.in.ua/v1/alerts/active.json"

# Токен задаётся в Railway → Variables.
# Не записывай секретный токен в исходный код.
ALERTS_API_TOKEN = os.getenv("ALERTS_API_TOKEN")

alerts_cache = {
    "data": None,
    "time": 0
}

ALERTS_CACHE_SECONDS = 30


# =========================================================
# NEPTUN DATA PROCESSING
# =========================================================

def process_neptun_message(message):
    """Обрабатывает одно сообщение WebSocket NEPTUN."""

    try:
        envelope = json.loads(message)
    except (json.JSONDecodeError, TypeError):
        print("NEPTUN: получено некорректное JSON-сообщение")
        return

    event_type = envelope.get("type")
    data = envelope.get("data")

    # -----------------------------------------------------
    # SNAPSHOT: полное состояние объектов
    # -----------------------------------------------------

    if event_type == "snapshot":
        threats = []

        if isinstance(data, dict):
            threats = data.get("threats", [])

        if not isinstance(threats, list):
            threats = []

        new_objects = {}

        for obj in threats:
            if not isinstance(obj, dict):
                continue

            obj_id = obj.get("id")

            if obj_id:
                new_objects[obj_id] = obj

        with neptun_lock:
            neptun_objects.clear()
            neptun_objects.update(new_objects)

        print(
            "NEPTUN: получен snapshot — "
            f"{len(new_objects)} объектов"
        )

    # -----------------------------------------------------
    # UPSERT: добавление или обновление объекта
    # -----------------------------------------------------

    elif event_type == "upsert":
        if not isinstance(data, dict):
            return

        obj_id = data.get("id")

        if obj_id:
            with neptun_lock:
                neptun_objects[obj_id] = data

            print(f"NEPTUN: обновлён объект {obj_id}")

    # -----------------------------------------------------
    # REMOVE: удаление объекта
    # -----------------------------------------------------

    elif event_type == "remove":
        obj_id = None

        if isinstance(data, dict):
            obj_id = data.get("id")

        if obj_id:
            with neptun_lock:
                neptun_objects.pop(obj_id, None)

            print(f"NEPTUN: удалён объект {obj_id}")

    # -----------------------------------------------------
    # HEARTBEAT
    # -----------------------------------------------------

    elif event_type == "heartbeat":
        print("NEPTUN: heartbeat")

    # -----------------------------------------------------
    # ALERTS EVENT
    # -----------------------------------------------------

    elif event_type == "alerts":
        print("NEPTUN: получено событие alerts")

    else:
        print(f"NEPTUN: неизвестный тип события {event_type}")


# =========================================================
# NEPTUN BACKGROUND WORKER
# =========================================================

def neptun_websocket_worker():
    """Подключается к NEPTUN и переподключается при ошибках."""

    print("NEPTUN WebSocket: фоновый поток запущен")

    while True:
        ws = None

        try:
            print("NEPTUN WebSocket: подключение...")

            ws = websocket.create_connection(
                NEPTUN_WS_URL,
                timeout=30,
                enable_multithread=True
            )

            # Ожидаем сообщения без тайм-аута простоя.
            ws.settimeout(None)

            print("NEPTUN WebSocket: соединение установлено")

            while True:
                message = ws.recv()

                if not message:
                    print("NEPTUN WebSocket: соединение закрыто")
                    break

                process_neptun_message(message)

        except Exception as exc:
            print(f"NEPTUN WebSocket: ошибка соединения: {exc}")

        finally:
            if ws is not None:
                try:
                    ws.close()
                except Exception:
                    pass

        print("NEPTUN WebSocket: повторное подключение через 5 секунд")
        time.sleep(5)


def start_neptun_worker():
    """Запускает один фоновый поток в текущем процессе."""

    global neptun_thread

    with neptun_thread_lock:
        if neptun_thread is not None and neptun_thread.is_alive():
            print("NEPTUN WebSocket: поток уже работает")
            return

        neptun_thread = threading.Thread(
            target=neptun_websocket_worker,
            name="neptun-websocket",
            daemon=True
        )

        neptun_thread.start()


# =========================================================
# OBJECTS API
# =========================================================

@app.route("/objects", methods=["GET"])
def get_objects():
    with neptun_lock:
        objects_snapshot = list(neptun_objects.values())

    objects = []

    for obj in objects_snapshot:
        obj_id = obj.get("id")
        lat = obj.get("lat")
        lon = obj.get("lon")

        # Без идентификатора и координат объект не отдаём.
        if not obj_id or lat is None or lon is None:
            continue

        velocity = obj.get("velocity") or {}
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

        objects.append({
            "id": obj_id,
            "name": obj.get("title") or "Unknown",
            "lat": lat,
            "lon": lon,
            "speed": speed,
            "heading": heading,
            "type": obj.get("type"),
            "status": obj.get("status") or "unknown",
            "confidence": obj.get("confidenceLevel") or "unknown",
            "sources": obj.get("sourceCount") or 0,
            "region": obj.get("region") or "",
            "locality": obj.get("locality") or "",
            "trail": trail,
            "speedKnown": speed_known,
            "updatedAt": obj.get("updatedAt"),
            "uncertaintyKm": obj.get("uncertaintyKm"),
            "positionQuality": obj.get("positionQuality"),
            "areaOnly": obj.get("areaOnly", False)
        })

    print(f"Отдано Android: {len(objects)} объектов")

    return jsonify(objects)


# =========================================================
# AIR ALERTS API
# =========================================================

@app.route("/alerts", methods=["GET"])
def get_alerts():
    global alerts_cache

    if not ALERTS_API_TOKEN:
        print("ОШИБКА: не задан ALERTS_API_TOKEN")

        return jsonify({
            "success": False,
            "error": "ALERTS_API_TOKEN is not configured",
            "alerts": []
        }), 500

    now = time.time()

    # Используем кэш, если он ещё актуален.
    if (
        alerts_cache["data"] is not None
        and now - alerts_cache["time"] < ALERTS_CACHE_SECONDS
    ):
        return jsonify({
            "success": True,
            "cached": True,
            "alerts": alerts_cache["data"]
        })

    try:
        response = requests.get(
            ALERTS_URL,
            headers={
                "Authorization": f"Bearer {ALERTS_API_TOKEN}"
            },
            timeout=10
        )

        if response.status_code == 401:
            print("alerts.in.ua: токен неверный или неактивный")

            return jsonify({
                "success": False,
                "error": "Unauthorized",
                "alerts": []
            }), 401

        if response.status_code == 429:
            print("alerts.in.ua: превышен лимит запросов")

            return jsonify({
                "success": False,
                "error": "Too many requests",
                "alerts": []
            }), 429

        response.raise_for_status()
        data = response.json()

        if isinstance(data, dict):
            alerts = data.get("alerts", [])
        else:
            alerts = []

        alerts_cache["data"] = alerts
        alerts_cache["time"] = time.time()

        print(f"Получено {len(alerts)} активных тревог")

        return jsonify({
            "success": True,
            "cached": False,
            "alerts": alerts
        })

    except Exception as exc:
        print(f"Ошибка alerts.in.ua: {exc}")

        if alerts_cache["data"] is not None:
            return jsonify({
                "success": False,
                "cached": True,
                "error": str(exc),
                "alerts": alerts_cache["data"]
            })

        return jsonify({
            "success": False,
            "error": str(exc),
            "alerts": []
        }), 500


# =========================================================
# STARTUP
# =========================================================

# Важно: Gunicorn импортирует server.py, а не запускает его
# как __main__. Поэтому поток запускается при импорте модуля.
start_neptun_worker()


if __name__ == "__main__":
    # При обычном запуске Python повторно поток не создастся:
    # start_neptun_worker() проверяет, работает ли он уже.
    app.run(
        host="0.0.0.0",
        port=int(os.getenv("PORT", "8000")),
        threaded=True
    )
