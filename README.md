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

## 📦 Requirements

- Go 1.21+
- An ADS-B receiver with `dump1090` (or equivalent) running and exposing a BaseStation TCP stream, typically on port `30003`

No external Go dependencies — standard library only.

## 🚀 Quick start

```bash
git clone https://github.com/yourname/flights-overhead
cd flights-overhead
go run main.go -tracker-addr "localhost:30003"
```

Then open `http://localhost:8080` in your browser.

## ⚙️ Configuration

| Flag | Default | Description |
|---|---|---|
| `-tracker-addr` | — | ADS-B receiver TCP address `host:port` (preferred) |
| `-addr` | `localhost:30003` | Deprecated alias for `-tracker-addr` |
| `-lat` | `60.1699` | Receiver latitude — used for distance calculations |
| `-lon` | `24.9384` | Receiver longitude — used for distance calculations |
| `-http` | `localhost:8080` | Address for the web dashboard |
| `-expire` | `60s` | How long before a silent aircraft is dropped from state |
| `-report` | `5s` | How often to print the terminal flight table |
| `-debug` | `false` | Verbose per-line parser logging |

Example with custom receiver location:

```bash
go run main.go \
  -tracker-addr "192.168.1.100:30003" \
  -lat 51.5074 \
  -lon -0.1278 \
  -expire 90s
```

## 🖥️ Dashboard

The web UI shows a live table of all tracked aircraft sorted by distance from your receiver, including:

- **Callsign** and ICAO hex address
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
      ├── parser     CSV line → typed Message struct
      ├── tracker    incremental state aggregation per ICAO hex ID
      ├── broker     Server-Sent Events fan-out to browser clients
      └── dashboard  embedded HTML/JS served at :8080
```

SBS-1 messages are incremental — one message updates the callsign, another the position, another the speed. The tracker merges them into a single up-to-date `Aircraft` record per ICAO address. Aircraft that stop transmitting are expired after the configured `-expire` duration; aircraft that send an explicit `STA RM` or `STA AD` message are removed immediately.

The TCP client reconnects automatically with exponential backoff (500 ms → 30 s) if the receiver goes offline.

### ✈️ Offline Aircraft Lookup Database
To display accurate aircraft manufacturers and model types (e.g. `Boeing • 737-800`), `flights-overhead` incorporates an embedded, local database of over 500,000 aircraft:
* **Map-Based Lookup**: On startup, `aircraft_db.csv.gz` (only **4.0 MB** compressed) is decompressed once and loaded into a `map[string][3]string` keyed by ICAO hex address, giving O(1) lookups with minimal latency.
* **Database Compiler Script**: The helper script `scripts/build_db.go` can be run at any time to download and compile the latest crowdsourced registry from `wiedehopf/tar1090-db`. It filters out records that do not contain model details and strips unused columns to optimize storage.

## 🧪 Running the tests

```bash
go test -v ./...
```

## 📁 Project layout

```
flights-overhead/
├── main.go              # CLI entrypoint, HTTP server, SSE broker, event loop
├── dashboard.html       # Embedded web UI (compiled into the binary)
├── scripts/
│   └── build_db.go      # Resilient build-time builder/compiler for aircraft database
└── pkg/sbs/
    ├── message.go       # SBS-1 message types and field definitions
    ├── aircraft.go      # Aggregated aircraft state struct (with manufacturer/model)
    ├── parser.go        # CSV → Message parser
    ├── tracker.go       # Thread-safe aircraft state registry (with DB query)
    ├── client.go        # TCP connection manager with auto-reconnect
    ├── geo.go           # Haversine distance and heading utilities
    ├── db.go            # Embedded database engine & map-based ICAO hex lookup
    ├── db_test.go       # Unit tests for database lookup and parsing
    └── aircraft_db.csv.gz # Gzipped lookup database (embedded in the Go binary, ~4.0 MB)
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
