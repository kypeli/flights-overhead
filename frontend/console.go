package frontend

import (
	"fmt"
	"os"
	"text/tabwriter"
	"time"

	"flights-overhead/pkg/sbs"
)

// printOverheadDashboard prints the list of currently tracked flights overhead as a formatted table.
func PrintOverheadDashboard(tracker *sbs.Tracker) {
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
