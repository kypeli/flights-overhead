// Package main is the entry point for the flights-overhead server. It connects to an
// ADS-B BaseStation TCP stream, tracks aircraft state, and serves a web frontend
// that displays nearby flights in real time via a JSON HTTP API.
package main

import (
	"context"
	_ "embed"
	"flag"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"flights-overhead/broadcast"
	"flights-overhead/frontend"
	"flights-overhead/pkg/sbs"
	"flights-overhead/sbsfirestore"

	"cloud.google.com/go/firestore"
)

// config holds all runtime configuration parsed from CLI flags.
type config struct {
	trackerAddr      string
	httpAddr         string
	expire           time.Duration
	report           time.Duration
	lat, lon         float64
	debug            bool
	firestoreProject string

	firestoreCreds string

	proximityKM         float64
	pushNotificationURL string
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

	firestoreProjectFlag := flag.String("firestore-project", "", "Firebase Project ID for Firestore integration")
	firestoreCredsFlag := flag.String("firestore-credentials", "", "path to the service account credentials JSON key file")

	proximityKMFlag := flag.Float64("proximity-km", 15.0, "proximity distance threshold in kilometers for push notification alerts")
	pushNotificationURLFlag := flag.String("push-notification-url", "https://pushnotification-g5q7shkmca-lz.a.run.app", "URL for the /pushNotification Cloud Function endpoint")

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

	// Ensure both Firestore flags are provided
	if *firestoreProjectFlag == "" || *firestoreCredsFlag == "" {
		fmt.Fprintln(os.Stderr, "error: both -firestore-project and -firestore-credentials must be provided")
		flag.Usage()
		os.Exit(2)
	}

	return config{
		trackerAddr:         *trackerAddrFlag,
		httpAddr:            *httpFlag,
		expire:              *expireFlag,
		report:              *reportFlag,
		lat:                 *latFlag,
		lon:                 *lonFlag,
		debug:               *debugFlag,
		firestoreProject:    *firestoreProjectFlag,
		firestoreCreds:      *firestoreCredsFlag,
		proximityKM:         *proximityKMFlag,
		pushNotificationURL: *pushNotificationURLFlag,
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

	// Initialize Firestore client if project ID is provided
	var firestoreClient *firestore.Client
	if cfg.firestoreProject != "" {
		slog.Info("initializing Cloud Firestore client...", "project", cfg.firestoreProject)

		var err error
		firestoreClient, err = sbsfirestore.NewFirestoreClient(ctx, sbsfirestore.Config{
			FirestoreProject: cfg.firestoreProject,
			FirestoreCreds:   cfg.firestoreCreds,
		})
		if err != nil {
			slog.Error("failed to initialize Firestore client", "error", err)
			cancel()
			return
		}

		defer func() {
			slog.Info("closing Firestore client...")
			if err := firestoreClient.Close(); err != nil {
				slog.Error("error closing Firestore client", "error", err)
			}
		}()
	}

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
	broker := frontend.NewSSEBroker()
	httpHandler := frontend.NewHTTPHandler(broker)
	go func() {
		slog.Info("starting web dashboard server...", "addr", cfg.httpAddr)
		if err := http.ListenAndServe(cfg.httpAddr, httpHandler); err != nil {
			slog.Error("HTTP server failed to start", "error", err)
			cancel()
		}
	}()

	// Create receivers
	sseReceiver := createSSEReceivers(broker, cfg.lat, cfg.lon, cfg.trackerAddr)
	firestoreReceiver := createFirestoreReceiver(ctx, firestoreClient)
	pushReceiver := createPushNotificationReceiver(ctx, cfg.lat, cfg.lon, cfg.proximityKM, cfg.pushNotificationURL)

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
			frontend.PrintOverheadDashboard(tracker)

		case <-cleanupTicker.C:
			// Evict flights that haven't sent reports within the expiration threshold
			evictedCount := tracker.EvictStale(cfg.expire)
			if evictedCount > 0 {
				slog.Info("evicted stale inactive aircraft sessions", "evicted_count", evictedCount)
			}

		case <-broadcastTicker.C:
			// Broadcast latest flight telemetry to connected web clients
			flightsReceivers := []broadcast.FlightsReceiver{
				sseReceiver,
				firestoreReceiver,
				pushReceiver,
			}

			for _, r := range flightsReceivers {
				broadcast.Broadcast(r, tracker)
			}
		}
	}
}

func createSSEReceivers(broker *frontend.SSEBroker, lat, lon float64, trackerAddr string) *broadcast.SSEFlightsReceiver {
	return broadcast.NewSSEFlightsReceiver(broker, lat, lon, trackerAddr)
}

func createFirestoreReceiver(ctx context.Context, client *firestore.Client) *broadcast.FirestoreFlightsReceiver {
	return broadcast.NewFirestoreFlightsReceiver(ctx, client)
}

func createPushNotificationReceiver(ctx context.Context, lat, lon, proximityKM float64, endpointURL string) *broadcast.PushNotificationReceiver {
	return broadcast.NewPushNotificationReceiver(ctx, broadcast.PushReceiverConfig{
		BaseLat:              lat,
		BaseLon:              lon,
		ProximityThresholdKM: proximityKM,
		EndpointURL:          endpointURL,
	})
}
