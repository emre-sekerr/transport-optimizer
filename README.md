# 🚚 Transport Optimizer
### Vehicle Routing & Geospatial Optimization System
A Spring Boot REST API that solves the **Vehicle Routing Problem (VRP)** using real road network data.  
Integrates with [OpenRouteService (ORS)](https://openrouteservice.org) for accurate distance and duration calculations.

---

## 📋 Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [API Endpoints](#api-endpoints)
- [Algorithms](#algorithms)
- [ORS Profiles](#ors-profiles)
- [Self-Hosted ORS](#self-hosted-ors)
- [Project Structure](#project-structure)

---

## ✨ Features

- **VRP Solver** — Nearest Neighbor, 2-opt, and Or-opt algorithms
- **ORS Integration** — Real road network distance and duration calculation
- **Haversine Fallback** — Automatically falls back to straight-line distance if ORS is unreachable
- **Capacity Constraints** — Per-vehicle load capacity management
- **Time Windows** — Customer-level delivery time constraint support
- **Geocoding** — Address → coordinate conversion
- **Caching** — Caffeine cache for ORS API calls (500 entries, 10-minute TTL)
- **Swagger UI** — Available at `http://localhost:8080/api/swagger-ui.html`

---

## ⚙️ Requirements

| Component | Version |
|-----------|---------|
| Java | 17+ |
| Maven | 3.8+ |
| ORS API Key | Free at [openrouteservice.org](https://openrouteservice.org) |

---

## 🚀 Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/your-username/transport-optimizer.git
cd transport-optimizer
```

### 2. Get an ORS API Key

Create a free account at [https://openrouteservice.org](https://openrouteservice.org) and generate an API key.

### 3. Configure the API key

In `src/main/resources/application.yml`:

```yaml
ors:
  api:
    key: YOUR_API_KEY_HERE
```

> ⚠️ **Security:** Never commit your API key directly to the repository. Use an environment variable instead:
> ```yaml
> ors:
>   api:
>     key: ${ORS_API_KEY}
> ```
> ```bash
> export ORS_API_KEY=your_key_here
> ```

### 4. Run the application

```bash
mvn spring-boot:run
```

The application will start at `http://localhost:8080/api`.

---

## 🔧 Configuration

Configurable parameters in `application.yml`:

```yaml
optimizer:
  algorithm: nearest-neighbor   # nearest-neighbor | two-opt | or-opt
  max-locations: 50             # Maximum number of locations per request
  max-vehicles: 20              # Maximum number of vehicles
  two-opt-iterations: 100       # Number of 2-opt improvement iterations
  time-window-tolerance: 300    # Time window tolerance in seconds

ors:
  api:
    timeout-seconds: 30
    max-retries: 3
```

---

## 📡 API Endpoints

### `POST /api/v1/optimize` — Route Optimization

Calculates optimized routes for the given vehicles and locations.

**Request:**
```json
{
  "locations": [
    {
      "id": "depot",
      "name": "Istanbul Depot",
      "latitude": 41.0082,
      "longitude": 28.9784,
      "type": "DEPOT"
    },
    {
      "id": "c1",
      "name": "Kadikoy Customer",
      "latitude": 40.9906,
      "longitude": 29.0264,
      "demand": 100,
      "type": "DELIVERY"
    },
    {
      "id": "c2",
      "name": "Besiktas Customer",
      "latitude": 41.0438,
      "longitude": 29.0047,
      "demand": 150,
      "type": "DELIVERY"
    }
  ],
  "vehicles": [
    {
      "id": "v1",
      "name": "Truck 1",
      "capacity": 500,
      "costPerKm": 2.5,
      "fixedCost": 50,
      "orsProfile": "driving-hgv"
    }
  ],
  "profile": "driving-car",
  "useCapacityConstraints": true
}
```

---

### `POST /api/v1/route` — Single Route Calculation

Returns the route, duration, and turn-by-turn directions between two points.

```json
{
  "origin": {
    "name": "Taksim",
    "latitude": 41.0370,
    "longitude": 28.9850
  },
  "destination": {
    "name": "Kadikoy",
    "latitude": 40.9906,
    "longitude": 29.0264
  },
  "profile": "driving-car",
  "includeGeometry": true,
  "includeInstructions": true
}
```

---

### `POST /api/v1/matrix` — Distance/Duration Matrix

Calculates all pairwise distances and durations between N locations.

```json
{
  "locations": [
    {"name": "Taksim",   "latitude": 41.0370, "longitude": 28.9850},
    {"name": "Kadikoy",  "latitude": 40.9906, "longitude": 29.0264},
    {"name": "Besiktas", "latitude": 41.0438, "longitude": 29.0047}
  ],
  "profile": "driving-car"
}
```

---

### `GET /api/v1/geocode/search?query=Kadikoy&countryCode=TR` — Geocoding

Converts an address or place name into coordinates.

---

### `GET /api/v1/health` — Health Check

Returns the status of the API and ORS connectivity.

---

## 🧠 Algorithms

| Algorithm | Description |
|-----------|-------------|
| `nearest-neighbor` | Fast initial solution. At each step, selects the nearest unvisited location. |
| `two-opt` | Improves the route by reversing segments between two edges. Automatically applied after Nearest Neighbor. |
| `or-opt` | Relocates small segments to different positions in the route for further improvement. |

---

## 🗺️ ORS Profiles

| Profile | Vehicle Type |
|---------|-------------|
| `driving-car` | Car |
| `driving-hgv` | Heavy goods vehicle (truck) |
| `cycling-regular` | Bicycle |
| `foot-walking` | Walking |

---

## 🐳 Self-Hosted ORS

To run your own ORS server:

```bash
docker run -d -p 8082:8082 \
  -v /path/to/osm:/home/ors/files \
  openrouteservice/openrouteservice:latest
```

Then update `application.yml`:

```yaml
ors:
  api:
    base-url: http://localhost:8082/ors
    key: ""   # No key required for self-hosted
```

---

## 📁 Project Structure

```
src/main/java/com/transport/optimizer/
├── algorithm/
│   └── VrpAlgorithm.java          # Nearest Neighbor, 2-opt, Or-opt
├── config/
│   ├── JacksonConfig.java
│   ├── OptimizerConfig.java       # Algorithm parameters
│   ├── OrsConfig.java             # ORS connection settings
│   └── WebConfig.java             # CORS
├── controller/
│   ├── GeocodingController.java
│   ├── HealthController.java
│   ├── MatrixController.java
│   ├── OptimizationController.java
│   └── RouteController.java
├── dto/
│   └── TransportDtos.java         # Request/Response models
├── exception/
│   └── GlobalExceptionHandler.java
├── model/
│   ├── Location.java
│   ├── Route.java
│   └── Vehicle.java
└── service/
    ├── OptimizationService.java   # VRP workflow
    └── OrsApiService.java         # ORS API client
