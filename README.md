

<div align="center">

# 🚆 Railway ETA prediction project

### Intelligent | Real-Time Ready | Scalable

**A smart backend system for calculating and dynamically forecasting the Estimated Time of Arrival (ETA) of railway trains.**

<br>

![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-Backend-brightgreen?style=for-the-badge&logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Database-blue?style=for-the-badge&logo=postgresql)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-Streaming-black?style=for-the-badge&logo=apachekafka)
![Python](https://img.shields.io/badge/Python-Data%20Processing-blue?style=for-the-badge&logo=python)

<br>

**Built for Smart Railway ETA Forecasting 🚄**

</div>

---

# 📌 Problem Statement

Traditional train arrival estimates may not always reflect changing operational conditions. A train's arrival time can be affected by:

- Speed variations
- Operational delays
- Distance remaining
- Historical movement patterns
- Railway congestion
- Unexpected disruptions

The objective of this project is to build a system capable of processing railway and live train-state information to generate a **dynamic Estimated Time of Arrival (ETA)**.

---

# 💡 Our Solution

The **Dynamic Railway ETA Forecasting System** provides a backend architecture that processes train state and railway data to estimate:

> 🚉 **ETA to the Next Station**

> 🏁 **ETA to the Final Destination**

> ⏳ **Estimated Delay**

The architecture is designed to be scalable and extensible, allowing future integration of **Machine Learning models, real-time GPS streams, and additional railway intelligence systems**.

---

# ✨ Key Features

| Feature | Description |
|---|---|
| 🚄 Live Train State | Tracks current train movement information |
| 📍 Current Location | Identifies the current and next station |
| ⏱️ Dynamic ETA | Calculates ETA using speed and remaining distance |
| 🏁 Destination ETA | Estimates time remaining to the final destination |
| ⏳ Delay Analysis | Calculates estimated train delays |
| 📊 Railway Data | Uses train, station, and schedule datasets |
| 📨 Kafka Ready | Supports event-driven data processing |
| 🗄️ PostgreSQL | Persistent railway data storage |
| 🔌 REST APIs | Enables frontend and external system integration |
| 🔮 ML Ready | Architecture can be extended with prediction models |

---

# 🏗️ System Architecture

```text
                         ┌─────────────────────────┐
                         │     Railway Datasets     │
                         │                         │
                         │  • Trains               │
                         │  • Stations             │
                         │  • Schedules            │
                         └────────────┬────────────┘
                                      │
                                      ▼
                         ┌─────────────────────────┐
                         │   Data Import Pipeline   │
                         │        Python           │
                         └────────────┬────────────┘
                                      │
                                      ▼
                         ┌─────────────────────────┐
                         │       PostgreSQL        │
                         │                         │
                         │ Railway Data Storage    │
                         └────────────┬────────────┘
                                      │
                                      ▼
                ┌──────────────────────────────────────┐
                │        Spring Boot Backend           │
                │                                      │
                │   ┌──────────────────────────────┐   │
                │   │    Train State Service       │   │
                │   └──────────────┬───────────────┘   │
                │                  │                   │
                │                  ▼                   │
                │   ┌──────────────────────────────┐   │
                │   │   ETA Calculation Service    │   │
                │   └──────────────┬───────────────┘   │
                │                  │                   │
                │                  ▼                   │
                │   ┌──────────────────────────────┐   │
                │   │ Delay Calculation Service    │   │
                │   └──────────────────────────────┘   │
                └──────────────────┬───────────────────┘
                                   │
                                   ▼
                         ┌─────────────────────────┐
                         │       REST APIs         │
                         └────────────┬────────────┘
                                      │
                                      ▼
                         ┌─────────────────────────┐
                         │   Frontend Dashboard    │
                         │                         │
                         │ • Train Search          │
                         │ • Live Status           │
                         │ • ETA Visualization     │
                         │ • Delay Information     │
                         └─────────────────────────┘
```

---

# 🔄 Data Flow

The project follows the following flow:

```text
Railway Data
     │
     ▼
Data Processing
     │
     ▼
Database Storage
     │
     ▼
Live Train State
     │
     ▼
ETA Calculation
     │
     ▼
Delay Calculation
     │
     ▼
REST API Response
     │
     ▼
Frontend Dashboard
```

---

# 🧠 ETA Calculation

The ETA calculation is based on the current speed and remaining distance of the train.

## ETA Formula

```text
ETA (minutes) = (Remaining Distance / Current Speed) × 60
```

### ETA to Next Station

```text
Distance to Next Station
            ÷
Current Train Speed
            ×
           60
            │
            ▼
ETA to Next Station
```

### ETA to Destination

```text
Distance to Destination
            ÷
Current Train Speed
            ×
           60
            │
            ▼
ETA to Final Destination
```

The delay calculation service further contributes delay information to the final ETA response.

---

# 🛠️ Technology Stack

## Backend

- ☕ Java 21
- 🌱 Spring Boot
- 🌐 Spring Web MVC
- 🗄️ Spring Data JPA
- ✔️ Spring Validation
- 📊 Spring Actuator

## Database

- 🐘 PostgreSQL
- 🔄 Flyway Database Migration

## Data Processing

- 🐍 Python
- 📄 JSON Railway Datasets

## Streaming Infrastructure

- 📨 Apache Kafka

## Build Tool

- 🔧 Maven

---

# 📂 Project Structure

```text
eta
│
├── 📁 data
│   ├── schedules.json
│   ├── stations.json
│   └── trains.json
│
├── 📁 scripts
│   └── import_railway_data.py
│
├── 📁 src
│   │
│   └── main
│       │
│       └── java
│           │
│           └── com.railway.eta
│
│               ├── EtaApplication.java
│               │
│               ├── 📁 config
│               │   └── SchedulingConfig.java
│               │
│               ├── 📁 eta
│               │   ├── DelayCalculationService.java
│               │   ├── EtaCalculationService.java
│               │   ├── EtaController.java
│               │   ├── EtaResponse.java
│               │   ├── TrainState.java
│               │   ├── TrainStateController.java
│               │   └── TrainStateService.java
│               │
│               ├── 📁 history
│               │   ├── GpsHistory.java
│               │   └── GpsHistoryRepository.java
│               │
│               └── 📁 infrastructure
│                   └── kafka
│                       ├── KafkaConfig.java
│                       ├── KafkaConsumerConfig.java
│                       └── KafkaProducerConfig.java
│
├── 📄 pom.xml
├── 📄 mvnw
├── 📄 mvnw.cmd
├── 🖼️ flow.png
└── 📄 README.md
```

---

# 🔌 API Endpoints

## 🚄 Get ETA for a Train

```http
GET /api/eta/{trainNo}
```

### Example

```http
GET /api/eta/12345
```

The response provides information related to:

```json
{
  "trainNo": "12345",
  "currentStation": "Current Station",
  "nextStation": "Next Station",
  "distanceToNextStationKm": 25.0,
  "distanceToDestinationKm": 180.0,
  "speedKmh": 60.0,
  "etaToNextStationMinutes": 25.0,
  "etaToDestinationMinutes": 180.0,
  "delayMinutes": 10.0
}
```

---

## 📡 Get All Live Trains

```http
GET /api/live
```

Returns the currently available live states of trains.

---

## 🚆 Get Live State of a Specific Train

```http
GET /api/live/{trainNo}
```

Example:

```http
GET /api/live/12345
```

---

# 🚀 Getting Started

## Prerequisites

Make sure you have the following installed:

- Java 21
- PostgreSQL
- Python 3.x
- Apache Kafka *(if Kafka streaming is enabled)*
- Maven *(optional when using Maven Wrapper)*

---

## 1️⃣ Clone the Repository

```bash
git clone https://github.com/Ashit-Mishra/eta.git
cd eta
```

---

## 2️⃣ Configure PostgreSQL

Create a PostgreSQL database.

Example configuration:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/railway_eta
spring.datasource.username=postgres
spring.datasource.password=your_password
```

> Configure the application properties according to your local environment.

---

## 3️⃣ Import Railway Data

The repository contains railway datasets inside the `data` directory.

Run:

```bash
python scripts/import_railway_data.py
```

---

## 4️⃣ Run the Application

### Windows

```bash
mvnw.cmd spring-boot:run
```

### Linux / macOS

```bash
./mvnw spring-boot:run
```

Or using Maven:

```bash
mvn spring-boot:run
```

---

# 📊 Railway Datasets

The system uses structured railway information.

| Dataset | Purpose |
|---|---|
| 🚄 `trains.json` | Train information |
| 🚉 `stations.json` | Railway station information |
| 📅 `schedules.json` | Train schedule information |

These datasets provide the foundation for railway data processing and ETA calculations.

---

# 🖥️ Frontend Integration

The backend is designed to integrate with a modern frontend dashboard.

The frontend can provide:

- 🔍 Train search
- 📍 Current train location
- 🚉 Next station information
- ⏱️ Dynamic ETA
- ⏳ Delay status
- 📊 Railway analytics
- 🗺️ Route visualization

```text
Frontend
    │
    │ REST API Request
    ▼
Spring Boot Backend
    │
    │ Process Train Data
    ▼
ETA Calculation Engine
    │
    ▼
JSON Response
    │
    ▼
Interactive User Dashboard
```

---

# 🔮 Future Roadmap

The architecture is designed to support future enhancements.

### 🤖 Machine Learning ETA Forecasting

Potential features:

- Historical delay analysis
- Route-based prediction
- Train-specific behavior analysis
- Time-based traffic patterns
- Predictive ETA models

### 📍 Real-Time GPS Integration

- Continuous location updates
- Automatic speed updates
- Dynamic ETA recalculation

### 🌦️ External Factors

Potential integration of:

- Weather conditions
- Railway congestion
- Operational disruptions
- Route conditions

### 🗺️ Interactive Railway Map

Visualize:

- Train location
- Route progress
- Upcoming stations
- Predicted arrival times

---

# 🎯 Vision

> **To build a scalable and intelligent railway ETA forecasting platform that can evolve from rule-based calculations into an advanced real-time predictive system.**

The long-term goal is to combine:

```text
Real-Time Data
      +
Historical Railway Data
      +
Machine Learning
      +
Operational Intelligence
      =
More Accurate Train ETA Forecasting
```

---

# 👥 Team

Developed as part of a railway ETA forecasting project.

| Role | Responsibility |
|---|---|
| 🎨 Frontend | User Interface & Visualization |
| ⚙️ Backend | APIs & System Architecture |
| 🤖 ML | ETA Prediction & Forecasting |
| 🔗 Integration | Connecting ML, Backend & Frontend |

> Team member names can be added here.

---

# 🤝 Contributing

Contributions and improvements are welcome.

1. Fork the repository.
2. Create a feature branch.
3. Make your changes.
4. Commit your changes.
5. Submit a Pull Request.

---

# 📄 License

This project is currently developed for educational, research, prototype, and hackathon purposes.

---

<div align="center">

## 🚆 Building Smarter Railways with Intelligent ETA Forecasting

**If you find this project interesting, consider giving the repository a ⭐**

</div>
