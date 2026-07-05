# ✈️ flights-overhead

A lightweight ADS-B flight tracker that reads live aircraft transponder data from a local receiver and displays it on a real-time web dashboard.

![Dashboard screenshot placeholder](https://placehold.co/900x400/0b1326/dae2fd?text=AeroTrack+Live+Dashboard)

## 🛰️ What it does

Connect it to an ADS-B receiver (like a RTL-SDR dongle running `dump1090`) and it will:

- 📡 Parse the raw **SBS-1 / BaseStation** TCP stream coming off your receiver
- 🗂️ Track the state of every aircraft overhead — callsign, altitude, speed, heading, position, squawk
- 🌐 Serve a live **web dashboard** at `http://localhost:8080` updated every second via Server-Sent Events
- 🖥️ Print a terminal table of all active flights at a configurable interval
- 🔁 Automatically expire aircraft that go silent and reconnect to the receiver if the connection drops
- ✈️ Enrich each flight with aircraft type, registration, operator, and route (origin → destination) via live lookups against [adsbdb.com](https://www.adsbdb.com/)

## 📦 Requirements

- Go 1.26+
- An ADS-B receiver with `dump1090` (or equivalent) running and exposing a BaseStation TCP stream, typically on port `30003`
- A Firebase / Cloud Firestore project with service account credentials configured.

Uses standard library components combined with the Cloud Firestore Go SDK to synchronize live flight snapshots to Firestore. Aircraft and route data is fetched live from [adsbdb.com](https://www.adsbdb.com/); an internet connection is required for those enrichments but not for core tracking.

## 🚀 Quick start

Using `task` (Taskfile.yml):

```bash
git clone https://github.com/yourname/flights-overhead
cd flights-overhead
# Start the application with your receiver IP, coordinates, and Firestore parameters
task run TRACKER=<tracker_ip> LAT=<latitude> LON=<longitude> PROJECT=<firestore_project_id> CREDENTIALS=<credentials_json_path>
```

Or using `go run` directly:

```bash
go run main.go \
  -tracker-addr "<tracker_ip>:30003" \
  -lat <latitude> \
  -lon <longitude> \
  -firestore-project <firestore_project_id> \
  -firestore-credentials <credentials_json_path>
```

Then open `http://localhost:8080` in your browser to view the local dashboard, or inspect your Firestore database under the `active_flights` collection.

## ⚙️ Configuration

| Flag | Default | Description |
|---|---|---|
| `-tracker-addr` | `localhost:30003` | ADS-B receiver TCP address `host:port` |
| `-lat` | *(required)* | Receiver latitude — used for distance calculations |
| `-lon` | *(required)* | Receiver longitude — used for distance calculations |
| `-http` | `localhost:8080` | Address for the web dashboard |
| `-expire` | `60s` | How long before a silent aircraft is dropped from state |
| `-report` | `5s` | How often to print the terminal flight table |
| `-debug` | `false` | Verbose per-line parser logging |
| `-firestore-project` | *(required)* | Firebase Project ID for Firestore integration |
| `-firestore-credentials` | *(required)* | Path to the service account credentials JSON key file |

Example with custom receiver location and Firestore:

```bash
go run main.go \
  -tracker-addr "<IP address of the ADS-B receiver>:30003" \
  -lat <latitude of ADS-B receiver> \
  -lon <longitude of ADS-B receiver> \
  -expire 90s \
  -firestore-project "my-firebase-project" \
  -firestore-credentials "service-account-key.json"
```

## 🖥️ Dashboard

The web UI shows a live table of all tracked aircraft sorted by distance from your receiver, including:

- **Callsign**, aircraft model, registration, and operator — enriched via adsbdb.com
- **Route** — origin and destination city (e.g. `Helsinki → Oslo`) when available
- **Altitude** (ft) and **vertical rate** (fpm) with climb/descent colour coding
- **Distance** (nautical miles) and **heading** direction
- 🚨 **Emergency squawk watch** — highlights any aircraft squawking 7500, 7600, or 7700

The dashboard reconnects automatically if the backend is restarted.

## 🔧 How it works

```
RTL-SDR dongle
      │
  dump1090
      │ TCP :30003 (SBS-1 / BaseStation format)
      │
 flights-overhead
      ├── pkg/sbs/parser     CSV line → typed Message struct
      ├── pkg/sbs/tracker    incremental state aggregation per ICAO hex ID
      ├── pkg/sbs/adsbdb     live aircraft & route lookups (adsbdb.com API)
      ├── broadcast          snapshot fan-out to FlightsReceiver implementations:
      │     ├── SSE           broadcasts to SSEBroker -> web clients
      │     └── Firestore     syncs active flights collection in Cloud Firestore
      ├── sbsfirestore       manages Firestore client connection
      ├── frontend/broker    Server-Sent Events broker
      └── frontend/dashboard embedded HTML/JS served at :8080
```

SBS-1 messages are incremental — one message updates the callsign, another the position, another the speed. The tracker merges them into a single up-to-date `Aircraft` record per ICAO address. Aircraft that stop transmitting are expired after the configured `-expire` duration; aircraft that send an explicit `STA RM` or `STA AD` message are removed immediately.

The TCP client reconnects automatically with exponential backoff (500 ms → 30 s) if the receiver goes offline.

### ✈️ Live Aircraft & Route Enrichment

When a new aircraft or callsign is first seen, `flights-overhead` queues an asynchronous lookup against the [adsbdb.com](https://www.adsbdb.com/) API (rate-limited to 2 requests/second):

- **`/aircraft/{hex}`** — returns manufacturer, model, ICAO type, registration, and registered owner
- **`/callsign/{callsign}`** — returns the flight route with origin and destination airport details

Results are cached in memory for the lifetime of the process. Lookups are deduplicated so the same ICAO address or callsign is only fetched once regardless of how many messages arrive for it. If the API queue fills up or a lookup fails, the aircraft is still tracked — it simply won't have enrichment data.

## 🧪 Running the tests

```bash
go test -v ./...
```

## 📁 Project layout

```
flights-overhead/
├── main.go                    # CLI entrypoint and event loop
├── Taskfile.yml               # Task automation runner configuration
├── firebase.json              # Firebase project configuration
├── firestore.rules            # Firestore security rules
├── firestore.indexes.json     # Firestore index definitions
├── flights-overhead.service   # Systemd service unit configuration file
├── broadcast/
│   ├── broadcaster.go         # FlightsReceiver interface and snapshot fan-out
│   ├── SSEFlightsReceiver.go  # Distance/sort enrichment, SSE payload serialiser
│   ├── firestore.go           # FirestoreFlightsReceiver (syncs snapshots to collection)
│   └── firestore_test.go      # Tests for the Firestore synchronization receiver
├── data/
│   └── data.go                # Shared types: FlightJSON, StreamPayload
├── frontend/
│   ├── broker.go              # SSEBroker — thread-safe SSE client registry
│   ├── console.go             # Terminal flight table printer
│   ├── dashboard.html         # Embedded web UI (compiled into the binary)
│   └── http_handler.go        # HTTP routes (/ dashboard, /events SSE)
├── sbsfirestore/
│   └── firestore.go           # Cloud Firestore client initialization and wrapper
└── pkg/sbs/
    ├── message.go             # SBS-1 message types and field definitions
    ├── aircraft.go            # Aggregated aircraft state struct
    ├── parser.go              # CSV → Message parser
    ├── tracker.go             # Thread-safe aircraft state registry & enrichment cache
    ├── adsbdb.go              # adsbdb.com API client (aircraft & route structs + JSON parsing)
    ├── client.go              # TCP connection manager with auto-reconnect
    └── geo.go                 # Haversine distance and heading utilities
```

## 📻 Setting up an ADS-B receiver

You'll need an RTL-SDR-compatible USB dongle (e.g. RTL2832U) and an antenna. Install `dump1090` or `readsb` and start it with BaseStation output enabled:

```bash
# dump1090-fa
dump1090-fa --net --net-sbs-port 30003

# readsb
readsb --net --net-sbs-port 30003
```

Then point `flights-overhead` at it with `-tracker-addr "localhost:30003"`.

## 📄 License

MIT
