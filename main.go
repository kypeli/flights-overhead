// Package main is the entry point for the flights-overhead server. It connects to an
// ADS-B BaseStation TCP stream, tracks aircraft state, and serves a web frontend
// that displays nearby flights in real time via a JSON HTTP API.
package main

import (
	"context"
	_ "embed"
	"encoding/json"
	"flag"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"sort"
	"sync"
	"syscall"
	"text/tabwriter"
	"time"

	"flights-overhead/pkg/sbs"
)

//go:embed dashboard.html
var dashboardHTML []byte

const (
	sseChanBuffer = 10
)

// config holds all runtime configuration parsed from CLI flags.
type config struct {
	trackerAddr string
	httpAddr    string
	expire      time.Duration
	report      time.Duration
	lat, lon    float64
	debug       bool
}

// parseConfig parses all CLI flags and returns a populated config.
func parseConfig() config {
	trackerAddrFlag := flag.String("tracker-addr", "localhost:30003", "ADS-B receiver TCP address (host:port) to read stream from")
	expireFlag := flag.Duration("expire", 60*time.Second, "duration after which an inactive aircraft is expired")
	reportFlag := flag.Duration("report", 5*time.Second, "reporting frequency interval")
	debugFlag := flag.Bool("debug", false, "enable debug logging mode")
	httpFlag := flag.String("http", "localhost:8080", "web dashboard HTTP address (host:port)")
	latFlag := flag.Float64("lat", 0, "receiver latitude coordinate (required)")
	lonFlag := flag.Float64("lon", 0, "receiver longitude coordinate (required)")
	flag.Parse()

	latSet, lonSet := false, false
	flag.Visit(func(f *flag.Flag) {
		switch f.Name {
		case "lat":
			latSet = true
		case "lon":
			lonSet = true
		}
	})
	if !latSet || !lonSet {
		fmt.Fprintln(os.Stderr, "error: -lat and -lon are required")
		flag.Usage()
		os.Exit(2)
	}

	return config{
		trackerAddr: *trackerAddrFlag,
		httpAddr:    *httpFlag,
		expire:      *expireFlag,
		report:      *reportFlag,
		lat:         *latFlag,
		lon:         *lonFlag,
		debug:       *debugFlag,
	}
}

// initLogger sets up the default structured logger at the appropriate level.
func initLogger(debug bool) {
	logLevel := slog.LevelInfo
	if debug {
		logLevel = slog.LevelDebug
	}
	logger := slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{
		Level: logLevel,
	}))
	slog.SetDefault(logger)
}

// FlightJSON defines the web-facing telemetry representation of tracked aircraft.
type FlightJSON struct {
	sbs.Aircraft
	Distance  float64 `json:"distance"`
	Direction string  `json:"direction,omitempty"`
}

// StreamPayload represents the complete live radar broadcast payload.
type StreamPayload struct {
	ReceiverLat  float64      `json:"receiver_lat"`
	ReceiverLon  float64      `json:"receiver_lon"`
	ReceiverAddr string       `json:"receiver_addr"`
	Flights      []FlightJSON `json:"flights"`
}

// Broker coordinates real-time thread-safe Server-Sent Events (SSE) streaming.
type Broker struct {
	mu      sync.Mutex
	clients map[chan string]bool
}

// Broker must implement http.Handler
var _ http.Handler = (*Broker)(nil)

func NewBroker() *Broker {
	return &Broker{
		clients: make(map[chan string]bool),
	}
}

func (b *Broker) Register(ch chan string) {
	b.mu.Lock()
	defer b.mu.Unlock()
	b.clients[ch] = true
}

func (b *Broker) Unregister(ch chan string) {
	b.mu.Lock()
	defer b.mu.Unlock()
	delete(b.clients, ch)
	close(ch)
}

func (b *Broker) Broadcast(msg string) {
	b.mu.Lock()
	defer b.mu.Unlock()
	for ch := range b.clients {
		select {
		case ch <- msg:
		default:
			// Client's channel is blocked; skip to avoid stalling the broadcaster
		}
	}
}

func (b *Broker) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Connection", "keep-alive")
	w.Header().Set("Access-Control-Allow-Origin", "*")

	flusher, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "Streaming unsupported", http.StatusInternalServerError)
		return
	}

	ch := make(chan string, sseChanBuffer)
	b.Register(ch)
	defer b.Unregister(ch)

	ctx := r.Context()
	for {
		select {
		case <-ctx.Done():
			return
		case msg, ok := <-ch:
			if !ok {
				return
			}
			_, err := fmt.Fprintf(w, "data: %s\n\n", msg)
			if err != nil {
				return
			}
			flusher.Flush()
		}
	}
}

// newHTTPHandler creates an explicit ServeMux with routes registered for the dashboard and SSE broker.
func newHTTPHandler(broker *Broker) http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/" {
			http.NotFound(w, r)
			return
		}
		w.Header().Set("Content-Type", "text/html")
		w.Write(dashboardHTML)
	})
	mux.Handle("/events", broker)
	return mux
}

func main() {
	cfg := parseConfig()
	initLogger(cfg.debug)

	slog.Info("starting ADS-B flights overhead tracker backend...")

	// Create context with cancellation for graceful shutdown
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// Set up OS termination signals interceptor
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)
	go func() {
		sig := <-sigChan
		slog.Info("received termination signal, shutting down gracefully...", "signal", sig)
		cancel()
	}()

	// Initialize client and tracker
	client := sbs.NewClient(cfg.trackerAddr)
	tracker := sbs.NewTracker()
	tracker.StartAPIWorker(ctx)


	// Connect to ADS-B stream
	msgChan := client.Start(ctx)

	// Background tickers
	reportTicker := time.NewTicker(cfg.report)
	defer reportTicker.Stop()

	cleanupTicker := time.NewTicker(10 * time.Second)
	defer cleanupTicker.Stop()

	broadcastTicker := time.NewTicker(1 * time.Second)
	defer broadcastTicker.Stop()

	// Launch embedded HTTP web server with the SSE broker
	broker := NewBroker()
	go func() {
		slog.Info("starting web dashboard server...", "addr", cfg.httpAddr)
		if err := http.ListenAndServe(cfg.httpAddr, newHTTPHandler(broker)); err != nil {
			slog.Error("HTTP server failed to start", "error", err)
			cancel()
		}
	}()

	slog.Info("listening for incoming messages...")

	// Main event loop
	for {
		select {
		case <-ctx.Done():
			slog.Info("event loop stopped. Good bye!")
			return

		case msg, ok := <-msgChan:
			if !ok {
				slog.Info("message stream channel closed. Initiating shutdown...")
				return
			}

			// Update aircraft state with the parsed message
			ac, isNew := tracker.UpdateState(msg)
			if isNew {
				slog.Debug("new aircraft spotted!", "hex", ac.HexIdent, "callsign", ac.Callsign)
			}

		case <-reportTicker.C:
			// Print standard status dashboard report
			printOverheadDashboard(tracker)

		case <-cleanupTicker.C:
			// Evict flights that haven't sent reports within the expiration threshold
			evictedCount := tracker.EvictStale(cfg.expire)
			if evictedCount > 0 {
				slog.Info("evicted stale inactive aircraft sessions", "evicted_count", evictedCount)
			}

		case <-broadcastTicker.C:
			// Broadcast latest flight telemetry to connected web clients
			broadcastFlights(tracker, broker, cfg.lat, cfg.lon, cfg.trackerAddr)
		}
	}
}

// broadcastFlights formats all tracked flights and pushes a JSON payload to the SSE broker.
func broadcastFlights(tracker *sbs.Tracker, broker *Broker, receiverLat, receiverLon float64, receiverAddr string) {
	active := tracker.GetAllActive()
	flights := make([]FlightJSON, 0, len(active))

	for _, ac := range active {
		dist := sbs.DistanceNM(receiverLat, receiverLon, ac.Latitude, ac.Longitude)
		var direction string
		if ac.HasTrack {
			direction = sbs.TrackToDirection(ac.Track)
		}

		flights = append(flights, FlightJSON{
			Aircraft:  ac,
			Distance:  dist,
			Direction: direction,
		})
	}

	// Sort by distance ascending; fall back to callsign for flights without position.
	sort.Slice(flights, func(i, j int) bool {
		iPos := flights[i].HasPosition
		jPos := flights[j].HasPosition
		if iPos && jPos {
			return flights[i].Distance < flights[j].Distance
		}
		if iPos != jPos {
			return iPos
		}
		ci := flights[i].Callsign
		if ci == "" {
			ci = flights[i].HexIdent
		}
		cj := flights[j].Callsign
		if cj == "" {
			cj = flights[j].HexIdent
		}
		return ci < cj
	})

	payload := StreamPayload{
		ReceiverLat:  receiverLat,
		ReceiverLon:  receiverLon,
		ReceiverAddr: receiverAddr,
		Flights:      flights,
	}

	data, err := json.Marshal(payload)
	if err != nil {
		slog.Error("failed to marshal broadcast payload", "error", err)
		return
	}
	broker.Broadcast(string(data))
}

// printOverheadDashboard prints the list of currently tracked flights overhead as a formatted table.
func printOverheadDashboard(tracker *sbs.Tracker) {
	active := tracker.GetAllActive()
	if len(active) == 0 {
		fmt.Println("\n--- Flights Overhead Tracker: No active aircraft tracked currently ---")
		return
	}

	fmt.Printf("\n--- Flights Overhead Tracker: %d Active Aircraft Tracked Overhead ---\n", len(active))

	w := tabwriter.NewWriter(os.Stdout, 0, 0, 3, ' ', tabwriter.Debug)
	fmt.Fprintln(w, "ICAO HEX\tCALLSIGN\tALTITUDE (FT)\tSPEED (KT)\tTRACK (°)\tLATITUDE\tLONGITUDE\tSQUAWK\tMSGS\tLAST SEEN")

	now := time.Now()
	for _, ac := range active {
		callsign := ac.Callsign
		if callsign == "" {
			callsign = "------"
		}

		squawk := ac.Squawk
		if squawk == "" {
			squawk = "----"
		}

		coordsFormat := "%.5f"
		latStr := fmt.Sprintf(coordsFormat, ac.Latitude)
		lonStr := fmt.Sprintf(coordsFormat, ac.Longitude)
		if !ac.HasPosition {
			latStr = "------"
			lonStr = "------"
		}

		lastSeenAgo := now.Sub(ac.LastSeen).Truncate(time.Second)

		fmt.Fprintf(w, "%s\t%s\t%d\t%.1f\t%.1f\t%s\t%s\t%s\t%d\t%s ago\n",
			ac.HexIdent,
			callsign,
			ac.Altitude,
			ac.GroundSpeed,
			ac.Track,
			latStr,
			lonStr,
			squawk,
			ac.MessageCount,
			lastSeenAgo,
		)
	}
	w.Flush()
	fmt.Println()
}
