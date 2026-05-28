# Agent Onboarding Guide - flights-overhead

Welcome! This document is designed for AI coding agents to quickly understand the architecture, data formats, directory structures, and design decisions of this repository.

---

## 📌 Project Overview
The `flights-overhead` project is a Go-based backend designed to connect to local ADS-B receivers over raw TCP (no HTTP), read raw comma-separated **BaseStation (SBS-1)** stream lines, parse them into type-safe Go structs, and track the real-time aggregated telemetry state of aircraft currently flying overhead.

---

## ⚙️ Technology Stack
* **Language**: Go 1.26.3
* **Dependencies**: Zero external dependencies. Uses the standard library exclusively:
  * `log/slog` for structured logging.
  * `bufio.Scanner` for optimized line-by-line TCP socket reading.
  * `sync.RWMutex` for thread-safe concurrent registry maps.
  * `text/tabwriter` for clean console dashboards.
  * `net/http` for the embedded web dashboard and SSE broker.

---

## 📁 Repository Directory Structure

```
flights-overhead/
├── AGENT.md               # This onboarding document
├── dashboard.html         # Embedded web dashboard (SSE-driven live radar UI)
├── go.mod                 # Go module definition
├── main.go                # Application orchestrator, CLI entrypoint, HTTP server & SSE broker
├── scripts/
│   └── build_db.go        # Build-time tool to pull, filter and optimize static metadata
└── pkg/
    └── sbs/
        ├── aircraft.go    # Aggregated state struct for tracked flights (with manufacturer/model)
        ├── aircraft_db.csv.gz # Gzipped lookup database (embedded in binary, ~4.0 MB)
        ├── client.go      # TCP connection manager with exponential backoff reconnect
        ├── db.go          # Embedded database engine & binary search lookup
        ├── db_test.go     # Unit tests for database lookup and parsing
        ├── geo.go         # Haversine distance (NM) and track-to-direction utilities
        ├── geo_test.go    # Unit tests for geo calculations
        ├── message.go     # Raw BaseStation Message struct and enums
        ├── parser.go      # CSV field extractor and time parsing logic
        ├── parser_test.go # Resiliency unit tests for the parser
        ├── tracker.go     # Thread-safe flight state registrar and orphan cleaner (queries DB)
        └── tracker_test.go# Unit tests for tracker state consolidation
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
* Uses standard `sync.RWMutex` to guarantee that reads (e.g., periodic reports or futures API calls) and writes (updates from TCP stream) do not lock or collide.
* **Garbage-Collection**: Exposes an `EvictStale(maxAge time.Duration)` routine that purges stale flights that haven't sent updates within a configured interval.
* **Explicit Dropping**: Instantly evicts aircraft from the state database when receiving an explicit `STA` status change message with code `RM` (Remove) or `AD` (Aircraft Delete).

### 3. Network Connection Manager (`pkg/sbs/client.go`)
* Opens a TCP stream socket connection and scans lines continuously.
* Integrates an **exponential backoff auto-reconnect loop** (starting at 500ms, doubling up to a maximum of 30s) if the receiver goes offline or network drops out.
* Context-aware cancellation enables immediate shutdown response.

### 4. Geo Utilities (`pkg/sbs/geo.go`)
* `DistanceNM(lat1, lon1, lat2, lon2)` — calculates great-circle distance in nautical miles using the Haversine formula. Returns `0` when either coordinate pair is `0,0` (unknown position).
* `TrackToDirection(track)` — converts a heading in degrees to an 8-point cardinal/ordinal string (`N`, `NE`, `E`, …, `NW`). Handles negative and >360° inputs via normalization.

### 5. Embedded Aircraft Database (`pkg/sbs/db.go` & `scripts/build_db.go`)
* **Embedding**: Leverages standard `//go:embed` to package the compressed database `aircraft_db.csv.gz` directly into the binary.
* **Map-Based Lookup**: Decompresses exactly once at package startup (`init()`) into a `map[string][3]string` keyed by uppercase ICAO hex address, giving O(1) lookups.
* **Builder Script**: `scripts/build_db.go` downloads, filters, and optimizes the static database branch of `wiedehopf/tar1090-db` at build time.

### 6. Web Dashboard & SSE Broker (`main.go`)
* `dashboard.html` is embedded at compile time via `//go:embed` and served at `/`.
* The `Broker` type manages a set of connected HTTP clients and pushes JSON payloads over **Server-Sent Events** at `/events` once per second.
* Each broadcast includes the receiver's coordinates, its TCP address, and a sorted-by-distance list of all active `FlightJSON` records.
* Flights without known coordinates (lat/lon both `0`) sort to the bottom of the list.

---

## 🛠️ Operational Commands

### Run Automated Tests
```bash
go test -v ./...
```

### Start the Application (connecting to an ADS-B receiver)
```bash
go run main.go -tracker-addr "localhost:30003" -expire 60s -report 5s -lat 60.1699 -lon 24.9384
```

| Flag | Default | Description |
|---|---|---|
| `-tracker-addr` | `localhost:30003` | ADS-B receiver TCP address `host:port`. |
| `-expire` | `60s` | Duration after which a silent aircraft is evicted from state. |
| `-report` | `5s` | Interval for printing the terminal flight dashboard. |
| `-http` | `localhost:8080` | Address for the embedded web dashboard HTTP server. |
| `-lat` | `60.1699` | Receiver latitude (used for distance calculations). |
| `-lon` | `24.9384` | Receiver longitude (used for distance calculations). |
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
