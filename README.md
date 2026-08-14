# 🚁 DroneTrackerDemo

**Android application for visualizing and monitoring airborne objects on an interactive map.**

> 🚧 **Status: In Development**
>
> This project is currently under active development. New features are being added, existing functionality is being tested and improved.

---

## 📌 About

**DroneTrackerDemo** is a learning and portfolio project that combines an Android application with a Python backend.

The Android application communicates with the backend through a REST API and receives data about airborne objects for visualization on a map.

The project is being developed step by step to gain practical experience in mobile development, backend development, networking and software engineering.

---

## 🏗️ Architecture

The project consists of two main components.

### 📱 Android Client

The Android application is responsible for displaying and visualizing airborne objects.

Current technologies and components include:

* Kotlin
* Jetpack Compose
* Android SDK
* Retrofit
* REST API
* map visualization
* drone movement simulation

The application communicates with the Python backend to receive object data.

### 🐍 Python Backend

The backend is implemented in Python using Flask.

It provides REST API endpoints used by the Android application and processes data before sending it to the client.

Current backend technologies include:

* Python
* Flask
* REST API
* JSON
* HTTP

---

## ✨ Current Features

* 🗺️ Interactive map visualization
* 🚁 Air-object representation
* 📍 Object coordinates
* 🛤️ Movement trails
* 🔄 Android ↔ Python backend communication
* 🌐 REST API
* 🐍 Flask backend
* 🧪 Backend testing scripts
* 🎮 Drone movement simulation

---

## 🛠️ Technologies

| Technology          | Purpose                           |
| ------------------- | --------------------------------- |
| **Kotlin**          | Android application               |
| **Jetpack Compose** | User interface                    |
| **Python**          | Backend development               |
| **Flask**           | REST API server                   |
| **Retrofit**        | HTTP/API communication            |
| **JSON**            | Data exchange                     |
| **Git**             | Version control                   |
| **GitHub**          | Repository and project management |

---

## 📂 Project Structure

```text
DroneTrackerDemo/
│
├── app/
│   └── src/
│       └── main/
│           └── java/
│               └── com/example/dronetrackerdemo/
│                   │
│                   ├── data/
│                   │   ├── AirApi.kt
│                   │   ├── AirDataProvider.kt
│                   │   ├── AirObject.kt
│                   │   ├── RetrofitClient.kt
│                   │   └── TrailPoint.kt
│                   │
│                   ├── simulator/
│                   │   └── DroneSimulator.kt
│                   │
│                   ├── Drone.kt
│                   └── MainActivity.kt
│
├── backend/
│   ├── server.py
│   ├── check_opensky.py
│   └── test_air.py
│
├── .gitignore
└── README.md
```

---

## 🔄 Data Flow

The current architecture follows this general flow:

```text
Air Data
    ↓
Python / Flask Backend
    ↓
REST API
    ↓
Android Application
    ↓
Map Visualization
```

This architecture allows the Android client and backend to be developed and tested separately.

---

## 🚧 Development Roadmap

The project is actively being developed.

Planned improvements include:

* [ ] Improve real-time data updates
* [ ] Improve map visualization
* [ ] Improve movement-trail rendering
* [ ] Add more detailed object information
* [ ] Improve backend error handling
* [ ] Improve API structure
* [ ] Add additional tests
* [ ] Improve logging
* [ ] Improve application UI
* [ ] Improve project documentation

---

## 🎯 Learning Goals

This project is being developed as part of my practical IT education and portfolio.

The main learning areas are:

* Python backend development
* REST API design and integration
* Android development with Kotlin
* Jetpack Compose
* networking fundamentals
* data processing
* Git and GitHub
* cybersecurity fundamentals

The project is intentionally developed incrementally, with new functionality added and existing components refactored as my knowledge grows.

---

## 📚 Project Status

**Development version**

This repository represents an actively developing project and is not intended to be considered a finished production application.

The architecture, implementation and functionality may change as development continues.

---

## 👩‍💻 Author

**Milana**

Student — Information and Communication Systems and Networks

### Areas of interest

* Python
* Backend Development
* Automation
* Networking
* Cybersecurity
* Internal Tools

---

⭐ **This project is under active development.**
