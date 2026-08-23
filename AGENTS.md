# Agent Onboarding Guide - flights-overhead

Welcome! This document is designed for AI coding agents to quickly understand the architecture, data formats, directory structures, and design decisions of this repository.

---

## 📌 Project Overview
The `flights-overhead` project is an end-to-end flight tracking system. It connects to local ADS-B receivers over raw TCP (no HTTP), reads raw comma-separated **BaseStation (SBS-1)** stream lines, parses them into type-safe Go structs, aggregates real-time telemetry of aircraft flying overhead, enriches the data via adsbdb.com, and synchronizes state to an embedded web UI, Google Cloud Firestore, and a companion Android mobile app with live telemetry streaming and proximity push notification alerts.

---

## ⚙️ Technology Stack
* **Go Backend**: Go 1.26.3 (tested with local toolchain 1.26.4)
  * Uses standard library packages along with Google Cloud & Cloud Firestore integrations:
    * `cloud.google.com/go/firestore` for Firebase real-time document synchronization.
    * `google.golang.org/api/idtoken` & `google.golang.org/api/option` for Google Service Account OIDC ID token generation and credential management.
    * `log/slog` for structured logging.
    * `bufio.Scanner` for optimized line-by-line TCP socket reading.
    * `sync.RWMutex` & `sync.Mutex` for thread-safe concurrent registry maps and deduplication caches.
    * `text/tabwriter` for clean console dashboards.
    * `net/http` for the embedded web dashboard, SSE broker, test push endpoint, and outbound API calls to adsbdb.com / Cloud Functions.
* **Firebase Functions**: Node.js 22, TypeScript, [firebase-functions](https://www.npmjs.com/package/firebase-functions) v2 API, `google-auth-library` (Service Account OIDC validation), `firebase-admin/messaging` (multicast FCM push notifications).
* **Android Mobile App**: Kotlin, Jetpack Compose Material 3, Navigation3, Metro DI, Ktor Client (OkHttp engine), Firebase Auth, Cloud Firestore SDK (`callbackFlow` real-time streaming), Firebase Messaging (`FlightsNotificationManager`), Firebase Installations, Coil.
* **External HTTP API**: [adsbdb.com](https://api.adsbdb.com/v0) — queried at runtime for aircraft metadata (manufacturer, registration, owner) and flight route (origin/destination airports) via `/aircraft/{hex}` and `/callsign/{callsign}` endpoints. Calls are rate-limited (max 2 req/s) and in-memory cached.

---

## 📁 Repository Directory Structure

The repository is organized into three subprojects: **`backend/`** (the Go ADS-B tracker), **`cloud-functions/`** (the Firebase project and TypeScript Cloud Functions), and **`android/`** (the native Android mobile client). The `Taskfile.yml` at the repo root drives automation across subprojects.

```
flights-overhead/
├── AGENTS.md                                # This onboarding document
├── CLAUDE.md                                # Symlink → AGENTS.md (used by Claude Code)
├── Taskfile.yml                             # Task runner automation (build/run in backend, deploy in cloud-functions)
├── service-account-key.json                 # Firestore service account key (gitignored, repo root)
├── backend/                                 # Go ADS-B tracker (module: flights-overhead)
│   ├── go.mod                               # Go module definition
│   ├── main.go                              # Application orchestrator, CLI entrypoint, and receiver creation
│   ├── broadcast/
│   │   ├── broadcaster.go                   # FlightsReceiver interface; Broadcast() snapshot fan-out
│   │   ├── SSEFlightsReceiver.go            # Computes distance/direction, sorts flights, serialises SSE payload
│   │   ├── firestore.go                     # FirestoreFlightsReceiver (syncs active flight snapshots to Firestore)
│   │   ├── firestore_test.go                # Unit tests for the Firestore snapshot receiver
│   │   ├── push_receiver.go                 # PushNotificationReceiver (proximity calculation & FCM trigger)
│   │   └── push_receiver_test.go            # Unit tests for push notification receiver & test dispatch
│   ├── data/
│   │   └── data.go                          # Shared wire types: FlightJSON, StreamPayload, PushNotificationPayload
│   ├── frontend/
│   │   ├── broker.go                        # SSEBroker — thread-safe SSE client registry and http.Handler
│   │   ├── console.go                       # PrintOverheadDashboard() — terminal tabwriter flight table
│   │   ├── dashboard.html                   # Embedded web dashboard (SSE-driven live radar UI)
│   │   ├── http_handler.go                  # NewHTTPHandler() — registers /, /events, and /test-push routes
│   │   └── http_handler_test.go             # Unit tests for HTTP routes and test push dispatching
│   ├── sbsfirestore/
│   │   └── firestore.go                     # Firestore client initialization and connection wrapper
│   └── pkg/
│       └── sbs/
│           ├── adsbdb.go                    # adsbdb.com API types and JSON response parsers
│           ├── adsbdb_test.go               # Unit tests for adsbdb.com response parsing
│           ├── aircraft.go                  # Aggregated state struct for tracked flights (with route/owner fields)
│           ├── client.go                    # TCP connection manager with exponential backoff reconnect
│           ├── geo.go                       # Haversine distance (NM & KM) and track-to-direction utilities
│           ├── geo_test.go                  # Unit tests for geo calculations
│           ├── message.go                   # Raw BaseStation Message struct and enums
│           ├── parser.go                    # CSV field extractor and time parsing logic
│           ├── parser_test.go               # Resiliency unit tests for the parser
│           ├── tracker.go                   # Thread-safe flight state registrar, orphan cleaner, adsbdb.com API worker
│           └── tracker_test.go              # Unit tests for tracker state consolidation
├── cloud-functions/                         # Firebase project (deploy from this directory)
│   ├── firebase.json                        # Firebase/Firestore project setup configuration
│   ├── .firebaserc                          # Firebase project alias (default: flights-overhead)
│   ├── firestore.rules                      # Firestore security rules (read allowed for authenticated clients)
│   ├── firestore.indexes.json               # Firestore index definitions
│   ├── flights-overhead.service.template    # Systemd service unit template for Raspberry Pi deployment
│   └── functions/                           # Firebase Cloud Functions (TypeScript)
│       ├── package.json                     # Node.js dependencies and lifecycle scripts
│       ├── tsconfig.json                    # TypeScript compilation configuration
│       ├── src/
│       │   ├── index.ts                     # Functions entrypoint and triggers
│       │   ├── firebase.ts                  # Admin SDK initialization & config (maxInstances: 10)
│       │   ├── http.ts                      # Auth helpers: onGet, onPost (Firebase ID tokens), onServicePost (OIDC)
│       │   ├── flights.ts                   # GET /overheadFlights endpoint handler
│       │   ├── token.ts                     # POST /token: FID & device metadata registration endpoint
│       │   ├── validation.ts                # Document ID & platform validation rules
│       │   └── push-notification.ts          # POST /pushNotification: FCM proximity multicast trigger
│       └── test/                            # Unit tests (http, flights, token, push-notification, validation)
└── android/                                 # Native Android client (Jetpack Compose & Kotlin)
    ├── build.gradle.kts                     # Root Gradle build configuration
    ├── settings.gradle.kts                  # Android project settings & module configuration
    └── app/
        ├── build.gradle.kts                 # App module dependencies & SDK configuration
        └── src/
            ├── main/java/com/kypeli/flightsoverhead/
            │   ├── FlightsOverheadApplication.kt # Application class & DI initialization
            │   ├── MainActivity.kt          # Single activity hosting Compose Navigation3 & notification handling
            │   ├── api/                     # Ktor HTTP client & DTO definitions (TokenApi, TokenDto)
            │   ├── data/                    # Data models (Flight) and airline resolver
            │   ├── di/                      # Metro dependency injection graphs and scopes
            │   ├── entity/                  # Domain entities (e.g. FlightPath)
            │   ├── navigation/              # Navigation3 route entries
            │   ├── repository/              # FlightsRepository (Firestore Flow streaming), TokenRepository
            │   ├── service/                 # FlightsFirebaseMessagingService & FlightsNotificationManager
            │   ├── ui/                      # Jetpack Compose UI (screens, theme, components)
            │   └── viewmodel/               # FlightsViewModel and AuthViewModel
            └── test/java/com/kypeli/flightsoverhead/
                ├── TokenDtoTest.kt          # Unit tests for Token API serialization
                ├── repository/
                │   └── FlightsRepositoryTest.kt # Unit tests for Firestore snapshot listener & mapper
                ├── service/
                │   └── FlightsNotificationManagerTest.kt # Unit tests for notification titles, bodies & extras
                └── viewmodel/
                    └── FlightsViewModelTest.kt # Unit tests for ViewModel state flows & auth lifecycle
```

---

## 📡 The BaseStation (SBS-1) Protocol Format
BaseStation data streams line-by-line over raw TCP (commonly on port `30003`). Each line is a comma-separated list of exactly or at least 22 fields.

### Field Definitions (0-Indexed)
1. **MessageType** (Field 0): `MSG`, `SEL`, `ID`, `AIR`, `STA`, `CLK`.
2. **TransmissionType** (Field 1): `1` to `8` (Only applies to `MSG` message types).
   * `1`: ES Identification and Category (Callsign).
   * `2`: ES Surface Position.
   * `3`: ES Airborne Position.
   * `4`: ES Airborne Velocity.
   * `5`: Surveillance Alt.
   * `6`: Surveillance ID.
   * `7`: Air To Air.
   * `8`: All Call Reply.
3. **SessionID** (Field 2): Database session ID.
4. **AircraftID** (Field 3): Database aircraft ID.
5. **HexIdent** (Field 4): Unique 24-bit ICAO aircraft address in hex format (e.g. `4601F6`).
6. **FlightID** (Field 5): Database flight ID.
7. **DateMessageGenerated** (Field 6): Format `YYYY/MM/DD`.
8. **TimeMessageGenerated** (Field 7): Format `HH:MM:SS.mmm` (fractional seconds optional).
9. **DateMessageLogged** (Field 8): Logged date `YYYY/MM/DD`.
10. **TimeMessageLogged** (Field 9): Logged time `HH:MM:SS.mmm`.
11. **Callsign / StatusChange** (Field 10): 
    * Aircraft callsign (e.g. `KLM123`) for `MSG` type.
    * Status change code (`SL` = Signal Lost, `RM` = Remove, `PL` = Position Lost, `AD` = Aircraft Delete) for `STA` type.
12. **Altitude** (Field 11): Altitude in feet.
13. **GroundSpeed** (Field 12): Speed in knots.
14. **Track** (Field 13): Ground heading/track in degrees (0-359).
15. **Latitude** (Field 14): Latitude in decimal degrees.
16. **Longitude** (Field 15): Longitude in decimal degrees.
17. **VerticalRate** (Field 16): Vertical climb/descend rate in feet/min.
18. **Squawk** (Field 17): 4-digit octal transponder code.
19. **Alert** (Field 18): Squawk change flag (`0`, `1`, `-1`, or blank).
20. **Emergency** (Field 19): Emergency squawk flag.
21. **SPI** (Field 20): Ident/Special Position Indicator flag.
22. **IsOnGround** (Field 21): Ground status flag.

---

## 🧩 Core Software Components

### 1. Raw Message Parsing (`pkg/sbs/parser.go`)
Converts the comma-separated strings into a structured type-safe Go struct.
* **Resiliency**: Pads rows dynamically to 22 fields. Empty string fields are parsed as `nil` pointers inside `sbs.Message` so consumers can distinguish between a value of `0` and a field being empty.
* **Time parsing**: Date and Time string columns are unified and parsed together using layouts that safely handle dynamic decimal millisecond places.

### 2. State Aggregation (`pkg/sbs/tracker.go`)
Since SBS-1 messages transmit updates incrementally (e.g. MSG,3 updates coordinates, MSG,4 updates speed), the thread-safe `sbs.Tracker` accumulates these fields.
* Uses two separate mutexes: `mu` (`sync.RWMutex`) for the live aircraft registry and `cacheMu` for the adsbdb.com API caches. Never acquire `mu` while holding `cacheMu`, and vice versa — both `triggerAircraftLookup` and `triggerRouteLookup` are called from `UpdateState` (which holds `mu.Lock()`) and must not re-acquire `mu`.
* **Garbage-Collection**: Exposes an `EvictStale(maxAge time.Duration)` routine that purges stale flights that haven't sent updates within a configured interval.
* **Explicit Dropping**: Instantly evicts aircraft from the state database when receiving an explicit `STA` status change message with code `RM` (Remove) or `AD` (Aircraft Delete).
* **API Enrichment**: On first sight of a new hex ID, queues an aircraft metadata lookup; on first sight of a callsign, queues a route lookup. Both paths check the cache first (cache-hit is synchronous, cache-miss queues an async fetch).

### 3. Network Connection Manager (`pkg/sbs/client.go`)
* Opens a TCP stream socket connection and scans lines continuously.
* Integrates an **exponential backoff auto-reconnect loop** (starting at 500ms, doubling up to a maximum of 30s) if the receiver goes offline or network drops out.
* Context-aware cancellation enables immediate shutdown response.

### 4. Geo Utilities (`pkg/sbs/geo.go`)
* `DistanceNM(lat1, lon1, lat2, lon2)` — calculates great-circle distance in nautical miles using the Haversine formula. Returns `0` when either coordinate pair is `0,0` (unknown position).
* `DistanceKM(lat1, lon1, lat2, lon2)` — calculates great-circle distance in kilometers using the Haversine formula (Earth radius 6371.0 km). Used for Firestore telemetry and proximity push notification triggering.
* `TrackToDirection(track)` — converts a heading in degrees to an 8-point cardinal/ordinal string (`N`, `NE`, `E`, …, `NW`). Handles negative and >360° inputs via normalization.

### 5. adsbdb.com API Integration (`pkg/sbs/adsbdb.go` & `pkg/sbs/tracker.go`)
* **Aircraft Metadata**: When a new aircraft hex ID is first seen, a lookup to `https://api.adsbdb.com/v0/aircraft/{hex}` is queued. On success, the `Aircraft` struct is enriched with `Manufacturer`, `Model` (ICAO type description), `ICAOType`, `Registration`, and `RegisteredOwner`/`Operator`.
* **Flight Route**: When a callsign is first observed for an aircraft, a lookup to `https://api.adsbdb.com/v0/callsign/{callsign}` is queued. On success, origin and destination airport fields (`OriginICAO`, `OriginIATA`, `OriginName`, `OriginCity`, `DestICAO`, `DestIATA`, `DestName`, `DestCity`) are populated.
* **Background Worker**: `Tracker.StartAPIWorker(ctx)` runs a single goroutine that drains an internal buffered channel (`apiQueue`, capacity 200) at a rate-limited 500ms tick (≤2 req/s) to avoid hitting API rate limits.
* **In-Memory Caching**: Results (including "not found") are cached in `aircraftCache` and `routeCache` (`map[string]*cached*`) under a dedicated `cacheMu` mutex, so no hex or callsign is ever fetched twice.
* **Response Parsing**: `ParseAircraftResponse` and `ParseRouteResponse` in `adsbdb.go` handle the API's unusual envelope where `response` may be either a JSON object or a plain string like `"unknown aircraft"`.

### 6. Broadcast Layer (`broadcast/`)
* `Broadcast(receiver, tracker)` in `broadcaster.go` snapshots `tracker.GetAllActive()` and forwards a `[]data.FlightJSON` slice to all registered `FlightsReceiver` implementations.
* **`SSEFlightsReceiver`** (`SSEFlightsReceiver.go`): Computes per-flight distance and direction from receiver coordinates, sorts flights by distance (unknown positions last), marshals `data.StreamPayload` JSON, and calls `SSEBroker.Broadcast`.
* **`FirestoreFlightsReceiver`** (`firestore.go`): Syncs flight snapshots to Cloud Firestore on a 1-second ticker (see Section 9).
* **`PushNotificationReceiver`** (`push_receiver.go`):
  * Checks flight distance against the configured `ProximityThresholdKM` (default `15.0 km`).
  * Deduplicates per flight session using an in-memory `notified` map, ensuring each aircraft only triggers **one** push alert per pass. Purges entries when flights leave active tracking.
  * Uses Google Cloud Service Account OIDC tokens via `google.golang.org/api/idtoken` to securely call the Firebase Cloud Function `/pushNotification` endpoint.
  * Implements `TestPushSender` to support on-demand test push dispatches.

### 7. Shared Data Types (`data/data.go`)
* `FlightJSON` — wire representation of a tracked aircraft: embeds `sbs.Aircraft` and adds computed `Distance` (NM) and `Direction` fields.
* `StreamPayload` — top-level SSE envelope: receiver address and the full `[]FlightJSON` slice.
* `PushNotificationPayload` — payload structure transmitted to the Cloud Function push notification endpoint (`hex`, `callsign`, `distanceKm`, `altitude`, `groundSpeed`, `track`, coordinates, aircraft metadata, and route airports).

### 8. Web Frontend & Test API (`frontend/`)
* `SSEBroker` (`broker.go`) manages connected HTTP clients and fans out JSON strings over **Server-Sent Events**. Implements `http.Handler` for the `/events` route.
* `NewHTTPHandler(broker, pushSender)` (`http_handler.go`) wires up an explicit `http.ServeMux` with:
  * `/` — serves the compile-time embedded `dashboard.html`.
  * `/events` — SSE broker stream.
  * `/test-push` & `/api/test-push` — accepts GET / POST requests to dispatch test push notifications to all registered client devices using the injected `TestPushSender`.
* `PrintOverheadDashboard(tracker)` (`console.go`) renders active aircraft as a formatted `tabwriter` table to stdout.

### 9. Cloud Firestore Sync Component (`sbsfirestore/` & `broadcast/firestore.go`)
* **Client Initializer**: `NewFirestoreClient(ctx, config)` initializes the connection to Cloud Firestore using provided credentials JSON files and project IDs.
* **Sync Receiver**: `FirestoreFlightsReceiver` implements `FlightsReceiver` and processes flight snapshots on a 1-second interval:
  * Uses a buffered channel `ch` of capacity 1 with non-blocking drain logic.
  * Computes `distanceKm` and maps fields (`hex`, `callsign`, `latitude`, `longitude`, `altitude`, `verticalRate`, `distanceKm`, `squawk`, route, aircraft details).
  * Compares snapshot data against `prevData` using `reflect.DeepEqual` to filter out unchanged flights, minimizing database writes.
  * Automatically identifies and deletes inactive flights from the `active_flights` Firestore collection.
* **Security Rules** (`firestore.rules`): Permits read access on `/active_flights/{flightId}` for authenticated users (`request.auth != null`), allowing mobile clients to attach direct snapshot listeners.

### 10. Firebase Cloud Functions (`cloud-functions/functions/`)
* Written in TypeScript, compiled to Node.js 22, using Firebase Functions v2 API.
* Lives under `cloud-functions/`, alongside the Firebase project config (`firebase.json`, `.firebaserc`, `firestore.rules`, `firestore.indexes.json`).
* Main entry point is [cloud-functions/functions/src/index.ts](file:///Users/kypeli/src/own/flights-overhead/cloud-functions/functions/src/index.ts). Global configuration (e.g. `maxInstances: 10`) is initialized in [cloud-functions/functions/src/firebase.ts](file:///Users/kypeli/src/own/flights-overhead/cloud-functions/functions/src/firebase.ts).
* **Endpoints**:
  * **`overheadFlights`** ([flights.ts](file:///Users/kypeli/src/own/flights-overhead/cloud-functions/functions/src/flights.ts)): Authenticated `GET` endpoint returning active overhead flights from the `active_flights` Firestore collection.
  * **`token`** ([token.ts](file:///Users/kypeli/src/own/flights-overhead/cloud-functions/functions/src/token.ts)): Authenticated `POST` endpoint registering client Firebase Installation IDs (FIDs) and optional device metadata (`manufacturer`, `model`, `osVersion`, `sdkInt`, `appVersion`, `appBuild`) into `fcm_tokens`. Keyed by `installationId` for multi-device support.
  * **`pushNotification`** ([push-notification.ts](file:///Users/kypeli/src/own/flights-overhead/cloud-functions/functions/src/push-notification.ts)): Service-authenticated `POST` endpoint invoked by the Go backend to multicast FCM push notifications to registered devices when an aircraft is within proximity.
* **HTTP Authentication Helpers** ([http.ts](file:///Users/kypeli/src/own/flights-overhead/cloud-functions/functions/src/http.ts)):
  * `onGet` / `onPost`: Wraps handlers with CORS and Firebase ID Token verification (`getAuth().verifyIdToken`).
  * `onServicePost`: Wraps service-to-service endpoints with CORS and Google Cloud Service Account OIDC ID token verification (`oauth2Client.verifyIdToken`).

### 11. Android Mobile Client (`android/`)
* Native Android client developed in Kotlin with Jetpack Compose Material 3 and Navigation3.
* Architecture: MVVM with reactive UI state and repository abstractions.
  * **`FlightsRepository` & Real-Time Firestore Streaming**: Uses Firestore's `addSnapshotListener` on `active_flights` exposed as a Kotlin coroutines `Flow<Result<List<Flight>>>` via `callbackFlow`. Automatically streams live flight movements and deletions directly to the UI without polling.
  * **`FlightsNotificationManager`**: Handles notification channel creation (`flights_overhead_channel`), rich notification layout formatting (`Operator • Callsign`, `From Origin • X.X km away`), `BigTextStyle` multi-line expansion, and automatic dismissal on tap.
  * **`FlightsFirebaseMessagingService`**: Receives incoming FCM messages, extracts telemetry, and delegates display to `FlightsNotificationManager`.
  * **`TokenRepository` & `TokenService`**: Collects hardware device metadata (`DeviceInfoDto`) and registers client Firebase Installation IDs with the backend.
  * **`FlightRow` Component**: Renders live telemetry cards with airline avatar / fallback initials, flight number, origin/destination airport code badges, flight path status chips (Climbing, Descending, Cruising based on vertical rate), altitude in meters, distance in km, heading, model description, registration, squawk, and ICAO hex code.
  * **`LoginScreen`**: Firebase Authentication interface with Jetpack Compose Previews.

---

## 🛠️ Operational Commands

### Go Backend

#### Run Automated Tests
```bash
cd backend
go test -v ./...
```

#### Start the Application (connecting to an ADS-B receiver)

Using `task` automation (run from repo root):
```bash
task run TRACKER=<tracker_ip> LAT=<latitude> LON=<longitude> PROJECT=<project_id> CREDENTIALS=<credentials_path> PROXIMITY_KM=15.0
```

Or using `go run` directly (from the `backend/` directory):
```bash
cd backend
go run main.go \
  -tracker-addr "localhost:30003" \
  -expire 60s \
  -report 5s \
  -lat <lat> \
  -lon <lon> \
  -proximity-km 15.0 \
  -push-notification-url "https://pushnotification-g5q7shkmca-lz.a.run.app" \
  -firestore-project <project-id> \
  -firestore-credentials <credentials-path>
```

| Flag | Default | Description |
|---|---|---|
| `-tracker-addr` | `localhost:30003` | ADS-B receiver TCP address `host:port`. |
| `-expire` | `60s` | Duration after which a silent aircraft is evicted from state. |
| `-report` | `5s` | Interval for printing the terminal flight dashboard. |
| `-http` | `localhost:8080` | Address for the embedded web dashboard and test push HTTP server. |
| `-lat` | *(required)* | Receiver latitude (used for distance calculations). |
| `-lon` | *(required)* | Receiver longitude (used for distance calculations). |
| `-proximity-km` | `15.0` | Proximity distance threshold in kilometers for push alerts. |
| `-push-notification-url` | `https://pushnotification-g5q7shkmca-lz.a.run.app` | URL for the `/pushNotification` Cloud Function. |
| `-debug` | `false` | Enables verbose per-line parser logging. |
| `-firestore-project` | *(required)* | Firebase Project ID for Firestore integration. |
| `-firestore-credentials` | *(required)* | Path to the service account credentials JSON key file. |

### Firebase Cloud Functions

Manage, build, and deploy the functions and rules from `cloud-functions/`:

| Command | Action |
|---|---|
| `npm run --prefix cloud-functions/functions test` | Run unit tests for Firebase Functions. |
| `npm run --prefix cloud-functions/functions lint` | Lint Firebase Functions source files. |
| `npm run --prefix cloud-functions/functions build` | Compile TypeScript functions code to JavaScript. |
| `npm run --prefix cloud-functions/functions serve` | Compile and start the local Firebase emulator for Functions. |
| `task test-functions` | Built-in Taskfile command to run Cloud Functions unit tests. |
| `task deploy-functions` | Built-in Taskfile command to build and deploy Cloud Functions to production. |
| `task deploy-rules` | Built-in Taskfile command to deploy Firestore security rules to production. |

### Raspberry Pi Deployment (Headless)

Tasks for building and running `flights-overhead` on a Raspberry Pi:

| Command | Action |
|---|---|
| `task build-pi` | Cross-compile Linux/arm64 binary (`backend/bin/flights-overhead-pi`). |
| `task deploy-pi PI_HOST="<host>"` | SCP binary to Raspberry Pi and restart systemd service. |
| `task deploy-service-pi PI_HOST="<host>"` | Deploy systemd unit file (`flights-overhead.service`), reload daemon, enable and restart service. |
| `task restart-pi PI_HOST="<host>"` | Restart the systemd service on the Raspberry Pi. |
| `task logs-pi PI_HOST="<host>"` | Stream live `journalctl` service logs from the Raspberry Pi. |

### Android Mobile App

The Android subproject lives in `android/`:

| Command | Action |
|---|---|
| `cd android && ./gradlew assembleDebug` | Build the debug APK. |
| `cd android && ./gradlew test` | Run unit tests across modules. |
| `cd android && ./gradlew connectedAndroidTest` | Run instrumented tests on an attached device or emulator. |

---

## 🔮 Roadmap & Expansion Guidelines for Future Agents
When extending this codebase, adhere to the following principles:

1. **Adding REST or WebSockets APIs**:
   * Wrap the thread-safe `sbs.Tracker` via interface abstractions.
   * Inject `Tracker` into your HTTP handler packages. Do not introduce direct global variables.
2. **Database Persistence**:
   * If saving history is requested, write a database writer worker that consumes messages from a separate channel out of `sbs.Client`, keeping DB writes independent from the real-time memory tracker.
3. **No External Dependencies**:
   * Keep external dependencies minimal. Use standard library interfaces unless doing so requires rebuilding extensive framework logic (like WebSockets).
