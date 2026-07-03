# Agent Onboarding Guide - flights-overhead

Welcome! This document is designed for AI coding agents to quickly understand the architecture, data formats, directory structures, and design decisions of this repository.

---

## 📌 Project Overview
The `flights-overhead` project is a Go-based backend designed to connect to local ADS-B receivers over raw TCP (no HTTP), read raw comma-separated **BaseStation (SBS-1)** stream lines, parse them into type-safe Go structs, and track the real-time aggregated telemetry state of aircraft currently flying overhead.

---

## ⚙️ Technology Stack
* **Language**: Go 1.26.3
* **Dependencies**: Zero Go module dependencies. Uses the standard library exclusively:
  * `log/slog` for structured logging.
  * `bufio.Scanner` for optimized line-by-line TCP socket reading.
  * `sync.RWMutex` for thread-safe concurrent registry maps.
  * `text/tabwriter` for clean console dashboards.
  * `net/http` for the embedded web dashboard, SSE broker, and outbound API calls to adsbdb.com.
* **External HTTP API**: [adsbdb.com](https://api.adsbdb.com/v0) — queried at runtime for aircraft metadata (manufacturer, registration, owner) and flight route (origin/destination airports) via `/aircraft/{hex}` and `/callsign/{callsign}` endpoints. Calls are rate-limited (max 2 req/s) and in-memory cached.

---

## 📁 Repository Directory Structure

```
flights-overhead/
├── AGENT.md                   # This onboarding document
├── CLAUDE.md                  # Symlink → AGENT.md (used by Claude Code)
├── go.mod                     # Go module definition
├── main.go                    # Application orchestrator and CLI entrypoint
├── broadcast/
│   ├── broadcaster.go         # FlightsReceiver interface; Broadcast() snapshot fan-out
│   └── SSEFlightsReceiver.go  # Computes distance/direction, sorts flights, serialises SSE payload
├── data/
│   └── data.go                # Shared wire types: FlightJSON, StreamPayload
├── frontend/
│   ├── broker.go              # SSEBroker — thread-safe SSE client registry and http.Handler
│   ├── console.go             # PrintOverheadDashboard() — terminal tabwriter flight table
│   ├── dashboard.html         # Embedded web dashboard (SSE-driven live radar UI)
│   └── http_handler.go        # NewHTTPHandler() — registers / and /events routes
└── pkg/
    └── sbs/
        ├── adsbdb.go          # adsbdb.com API types and JSON response parsers
        ├── adsbdb_test.go     # Unit tests for adsbdb.com response parsing
        ├── aircraft.go        # Aggregated state struct for tracked flights (with route/owner fields)
        ├── client.go          # TCP connection manager with exponential backoff reconnect
        ├── geo.go             # Haversine distance (NM) and track-to-direction utilities
        ├── geo_test.go        # Unit tests for geo calculations
        ├── message.go         # Raw BaseStation Message struct and enums
        ├── parser.go          # CSV field extractor and time parsing logic
        ├── parser_test.go     # Resiliency unit tests for the parser
        ├── tracker.go         # Thread-safe flight state registrar, orphan cleaner, adsbdb.com API worker
        └── tracker_test.go    # Unit tests for tracker state consolidation
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
* `TrackToDirection(track)` — converts a heading in degrees to an 8-point cardinal/ordinal string (`N`, `NE`, `E`, …, `NW`). Handles negative and >360° inputs via normalization.

### 5. adsbdb.com API Integration (`pkg/sbs/adsbdb.go` & `pkg/sbs/tracker.go`)
* **Aircraft Metadata**: When a new aircraft hex ID is first seen, a lookup to `https://api.adsbdb.com/v0/aircraft/{hex}` is queued. On success, the `Aircraft` struct is enriched with `Manufacturer`, `Model` (ICAO type description), `ICAOType`, `Registration`, and `RegisteredOwner`/`Operator`.
* **Flight Route**: When a callsign is first observed for an aircraft, a lookup to `https://api.adsbdb.com/v0/callsign/{callsign}` is queued. On success, origin and destination airport fields (`OriginICAO`, `OriginIATA`, `OriginName`, `OriginCity`, `DestICAO`, `DestIATA`, `DestName`, `DestCity`) are populated.
* **Background Worker**: `Tracker.StartAPIWorker(ctx)` runs a single goroutine that drains an internal buffered channel (`apiQueue`, capacity 200) at a rate-limited 500ms tick (≤2 req/s) to avoid hitting API rate limits.
* **In-Memory Caching**: Results (including "not found") are cached in `aircraftCache` and `routeCache` (`map[string]*cached*`) under a dedicated `cacheMu` mutex, so no hex or callsign is ever fetched twice.
* **Response Parsing**: `ParseAircraftResponse` and `ParseRouteResponse` in `adsbdb.go` handle the API's unusual envelope where `response` may be either a JSON object or a plain string like `"unknown aircraft"`.

### 6. Broadcast Layer (`broadcast/`)
* `Broadcast(receiver, tracker)` in `broadcaster.go` snapshots `tracker.GetAllActive()` and forwards a `[]data.FlightJSON` slice to any `FlightsReceiver` implementation.
* `SSEFlightsReceiver` in `SSEFlightsReceiver.go` is the concrete SSE implementation: it computes per-flight distance and direction from the receiver coordinates, sorts flights by distance (unknown-position flights sort last by callsign/hex), marshals the result into a `data.StreamPayload` JSON string, and calls `SSEBroker.Broadcast`.

### 7. Shared Data Types (`data/data.go`)
* `FlightJSON` — wire representation of a tracked aircraft: embeds `sbs.Aircraft` and adds computed `Distance` (NM) and `Direction` fields.
* `StreamPayload` — top-level SSE envelope: receiver address, lat/lon, and the full `[]FlightJSON` slice.

### 8. Web Frontend (`frontend/`)
* `SSEBroker` (`broker.go`) manages a set of connected HTTP clients and fans out JSON strings over **Server-Sent Events**. Implements `http.Handler` for the `/events` route.
* `NewHTTPHandler(broker)` (`http_handler.go`) wires up an explicit `http.ServeMux` with routes for `/` (dashboard) and `/events` (SSE broker). Returns 404 for unknown paths.
* `dashboard.html` (`dashboard.html`) is embedded at compile time via `//go:embed` and served at `/`.
* `PrintOverheadDashboard(tracker)` (`console.go`) renders all active aircraft as a formatted `tabwriter` table to stdout.

---

## 🛠️ Operational Commands

### Run Automated Tests
```bash
go test -v ./...
```

### Start the Application (connecting to an ADS-B receiver)
```bash
go run main.go -tracker-addr "localhost:30003" -expire 60s -report 5s -lat <lat> -lon <lon>
```

| Flag | Default | Description |
|---|---|---|
| `-tracker-addr` | `localhost:30003` | ADS-B receiver TCP address `host:port`. |
| `-expire` | `60s` | Duration after which a silent aircraft is evicted from state. |
| `-report` | `5s` | Interval for printing the terminal flight dashboard. |
| `-http` | `localhost:8080` | Address for the embedded web dashboard HTTP server. |
| `-lat` | *(required)* | Receiver latitude (used for distance calculations). |
| `-lon` | *(required)* | Receiver longitude (used for distance calculations). |
| `-debug` | `false` | Enables verbose per-line parser logging. |

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
