package main

import (
	"context"
	"flag"
	"fmt"
	"log/slog"
	"os"
	"os/signal"
	"syscall"
	"text/tabwriter"
	"time"

	"flights-overhead/pkg/sbs"
)

func main() {
	// 1. Define CLI Parameters
	addrFlag := flag.String("addr", "localhost:30003", "ADS-B receiver TCP address (host:port)")
	expireFlag := flag.Duration("expire", 60*time.Second, "duration after which an inactive aircraft is expired")
	reportFlag := flag.Duration("report", 5*time.Second, "reporting frequency interval")
	debugFlag := flag.Bool("debug", false, "enable debug logging mode")
	flag.Parse()

	// 2. Initialize Structured Logger
	logLevel := slog.LevelInfo
	if *debugFlag {
		logLevel = slog.LevelDebug
	}
	logger := slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{
		Level: logLevel,
	}))
	slog.SetDefault(logger)

	slog.Info("starting ADS-B flights overhead tracker backend...")

	// 3. Create context with cancellation for graceful shutdown
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// 4. Set up OS termination signals interceptor
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)
	go func() {
		sig := <-sigChan
		slog.Info("received termination signal, shutting down gracefully...", "signal", sig)
		cancel()
	}()

	// 5. Initialize client and tracker
	client := sbs.NewClient(*addrFlag)
	tracker := sbs.NewTracker()

	// 6. Connect to ADS-B stream
	msgChan := client.Start(ctx)

	// 7. Background ticker to report active flights overhead
	reportTicker := time.NewTicker(*reportFlag)
	defer reportTicker.Stop()

	// 8. Background ticker to clean up orphan (expired) aircraft sessions
	cleanupTicker := time.NewTicker(10 * time.Second)
	defer cleanupTicker.Stop()

	slog.Info("listening for incoming messages...")

	// 9. Main event loop
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
			// Clean up flights that haven't sent reports within the expiration threshold
			evictedCount := tracker.CleanupOrphans(*expireFlag)
			if evictedCount > 0 {
				slog.Info("cleaned up inactive aircraft sessions", "evicted_count", evictedCount)
			}
		}
	}
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
		if ac.Latitude == 0.0 && ac.Longitude == 0.0 {
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
