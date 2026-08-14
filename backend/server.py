from flask import Flask, jsonify
import requests
import time

app = Flask(__name__)

NEPTUN_URL = "https://neptun.in.ua/api/v1/threats"

# память движения объектов
history = {}

from math import radians, sin, cos, sqrt, atan2


def calculate_speed(points):

    if len(points) < 2:
        return 0


    p1 = points[-2]
    p2 = points[-1]


    R = 6371  # км


    lat1 = radians(p1["lat"])
    lat2 = radians(p2["lat"])

    dlat = radians(
        p2["lat"] - p1["lat"]
    )

    dlon = radians(
        p2["lon"] - p1["lon"]
    )


    a = (
        sin(dlat / 2)**2 +
        cos(lat1) *
        cos(lat2) *
        sin(dlon / 2)**2
    )


    distance = R * 2 * atan2(
        sqrt(a),
        sqrt(1-a)
    )


    seconds = p2["time"] - p1["time"]


    if seconds <= 0:
        return 0


    speed = distance / seconds * 3600


    return round(speed, 1)
@app.route("/objects")
def get_objects():

    try:

        response = requests.get(
            NEPTUN_URL,
            timeout=10
        )

        data = response.json()

    except Exception as e:

        print("Ошибка API:", e)
        return jsonify([])


    objects = []

    threats = data.get("threats", [])


    for obj in threats:
     ##   print(obj)
        obj_id = obj.get("id")

        lat = obj.get("lat")
        lon = obj.get("lon")


        if not obj_id or lat is None or lon is None:
            continue

        # сохраняем историю движения
        now = time.time()

        old_history = history.get(obj_id, [])

        if not old_history or (
                old_history[-1]["lat"] != lat or
                old_history[-1]["lon"] != lon
        ):
            old_history.append(
                {
                    "lat": lat,
                    "lon": lon,
                    "time": now
                }
            )

        # оставляем последние 10 точек
        old_history = old_history[-50:]

        history[obj_id] = old_history

        trail = old_history

        objects.append(
            {

                "id": obj_id,

                "name": obj.get("title") or "Unknown",

                "lat": lat,

                "lon": lon,

                "speed": calculate_speed(trail),

                "heading": obj.get("heading") or 0,

                "type": obj.get("type"),

                "status": obj.get("status") or "unknown",

                "confidence": obj.get("confidenceLevel") or "unknown",

                "sources": obj.get("sourceCount") or 0,

                "region": obj.get("region") or "",

                "locality": obj.get("locality") or "",

                "trail": [
                    {
                        "lat": p["lat"],
                        "lon": p["lon"],
                        "t": str(p["time"])
                    }
                    for p in trail
                ]

            }
        )


    print(f"Получено {len(objects)} объектов")


    return jsonify(objects)



if __name__ == "__main__":

    app.run(
        host="0.0.0.0",
        port=8000
    )