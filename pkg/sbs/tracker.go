// tracker.go implements the Tracker, which maintains a thread-safe registry of
// active aircraft indexed by ICAO hex address. It merges incremental SBS-1 messages
// into per-aircraft state and evicts entries that have not been updated recently.
package sbs

import (
	"context"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"strings"
	"sync"
	"time"
)

var adsbDBBaseURL = "https://api.adsbdb.com/v0"

type apiRequest struct {
	hexIdent string
	callsign string
	reqType  string // "aircraft" or "route"
}

type cachedAircraft struct {
	pending  bool
	notFound bool
	data     *ADSBDBAircraft
}

type cachedRoute struct {
	pending  bool
	notFound bool
	data     *ADSBDBRoute
}

// Tracker coordinates state tracking for a registry of active aircraft.
type Tracker struct {
	mu    sync.RWMutex
	byHex map[string]*Aircraft

	// In-memory caches to prevent redundant lookups and respect API rate limits
	cacheMu       sync.RWMutex
	aircraftCache map[string]*cachedAircraft
	routeCache    map[string]*cachedRoute

	apiQueue   chan apiRequest
	httpClient *http.Client
}

// NewTracker creates a new thread-safe Aircraft state tracker.
func NewTracker() *Tracker {
	return &Tracker{
		byHex:         make(map[string]*Aircraft),
		aircraftCache: make(map[string]*cachedAircraft),
		routeCache:    make(map[string]*cachedRoute),
		apiQueue:      make(chan apiRequest, 200),
		httpClient:    &http.Client{Timeout: 10 * time.Second},
	}
}

// StartAPIWorker starts the background worker goroutine that processes API lookups
// at a rate-limited speed to avoid hitting adsbdb.com API rate limits.
func (t *Tracker) StartAPIWorker(ctx context.Context) {
	slog.Info("starting adsbdb.com API lookup queue worker...")
	go func() {
		ticker := time.NewTicker(500 * time.Millisecond) // Safe rate-limit (max 2 requests per second)
		defer ticker.Stop()

		for {
			select {
			case <-ctx.Done():
				return
			case req := <-t.apiQueue:
				// Wait for rate-limiter tick
				select {
				case <-ctx.Done():
					return
				case <-ticker.C:
				}

				if req.reqType == "aircraft" {
					t.fetchAircraftDetails(req.hexIdent)
				} else if req.reqType == "route" {
					t.fetchRouteDetails(req.hexIdent, req.callsign)
				}
			}
		}
	}()
}

// UpdateState applies a raw parsed SBS-1 message to update or create an aircraft state.
// Returns the updated aircraft state (as a copy) and a bool indicating if this is a newly tracked aircraft.
func (t *Tracker) UpdateState(msg *Message) (Aircraft, bool) {
	if msg.HexIdent == "" {
		return Aircraft{}, false
	}

	t.mu.Lock()
	defer t.mu.Unlock()

	// Handle explicit delete/remove status message from standard SBS-1
	if msg.MessageType == MsgTypeSTA && msg.StatusChange != nil {
		sc := *msg.StatusChange
		if sc == StatusRemove || sc == StatusAircraftDelete {
			delete(t.byHex, msg.HexIdent)
			return Aircraft{}, false
		}
	}

	now := time.Now()
	timestamp := msg.GeneratedTime
	if timestamp.IsZero() {
		timestamp = now
	}

	ac, exists := t.byHex[msg.HexIdent]
	isNew := !exists

	if isNew {
		ac = &Aircraft{
			HexIdent:  msg.HexIdent,
			FirstSeen: timestamp,
		}
		t.byHex[msg.HexIdent] = ac
		// Try to resolve aircraft details (synchronously from cache or queue async fetch)
		t.triggerAircraftLookup(ac)
	}

	// Apply updates incrementally
	ac.LastSeen = timestamp
	ac.MessageCount++

	if msg.Callsign != "" {
		cleanedCallsign := strings.TrimSpace(msg.Callsign)
		if cleanedCallsign != "" && cleanedCallsign != ac.Callsign {
			ac.Callsign = cleanedCallsign
			// Trigger route details lookup (from cache or queue async fetch)
			t.triggerRouteLookup(ac, cleanedCallsign)
		}
	}
	if msg.Altitude != nil {
		ac.Altitude = *msg.Altitude
	}
	if msg.GroundSpeed != nil {
		ac.GroundSpeed = *msg.GroundSpeed
	}
	if msg.Track != nil {
		ac.Track = *msg.Track
		ac.HasTrack = true
	}
	if msg.Latitude != nil {
		ac.Latitude = *msg.Latitude
		ac.HasPosition = true
	}
	if msg.Longitude != nil {
		ac.Longitude = *msg.Longitude
		ac.HasPosition = true
	}
	if msg.VerticalRate != nil {
		ac.VerticalRate = *msg.VerticalRate
	}
	if msg.Squawk != "" {
		ac.Squawk = msg.Squawk
	}
	if msg.IsOnGround != nil {
		ac.IsOnGround = *msg.IsOnGround
	}

	// Return a copy to avoid caller mutation race conditions
	return *ac, isNew
}

// Get retrieves the current state of a tracked aircraft by its 24-bit ICAO Hex ID.
// Returns a copy of the state and a boolean indicating if it exists.
func (t *Tracker) Get(hexIdent string) (Aircraft, bool) {
	t.mu.RLock()
	defer t.mu.RUnlock()

	ac, exists := t.byHex[hexIdent]
	if !exists {
		return Aircraft{}, false
	}
	return *ac, true
}

// GetAllActive returns a list of all currently tracked active aircraft.
func (t *Tracker) GetAllActive() []Aircraft {
	t.mu.RLock()
	defer t.mu.RUnlock()

	list := make([]Aircraft, 0, len(t.byHex))
	for _, ac := range t.byHex {
		list = append(list, *ac)
	}
	return list
}

// Remove explicitly deletes an aircraft from the tracker.
func (t *Tracker) Remove(hexIdent string) {
	t.mu.Lock()
	defer t.mu.Unlock()
	delete(t.byHex, hexIdent)
}

// EvictStale evicts aircraft that haven't been heard from in longer than the specified maxAge.
// Returns the number of evicted aircraft.
func (t *Tracker) EvictStale(maxAge time.Duration) int {
	t.mu.Lock()
	defer t.mu.Unlock()

	now := time.Now()
	evicted := 0

	for hex, ac := range t.byHex {
		if now.Sub(ac.LastSeen) > maxAge {
			delete(t.byHex, hex)
			evicted++
		}
	}

	return evicted
}

func (t *Tracker) triggerAircraftLookup(ac *Aircraft) {
	hex := ac.HexIdent
	t.cacheMu.RLock()
	cached, found := t.aircraftCache[hex]
	t.cacheMu.RUnlock()

	if found {
		if !cached.pending && !cached.notFound && cached.data != nil {
			ac.Manufacturer = cached.data.Manufacturer
			ac.Model = cached.data.Type
			ac.Registration = cached.data.Registration
			ac.ICAOType = cached.data.ICAOType
			ac.RegisteredOwner = cached.data.RegisteredOwner
			ac.Operator = cached.data.RegisteredOwner
		}
		return
	}

	t.cacheMu.Lock()
	if _, exists := t.aircraftCache[hex]; exists {
		t.cacheMu.Unlock()
		return
	}
	t.aircraftCache[hex] = &cachedAircraft{pending: true}
	t.cacheMu.Unlock()

	select {
	case t.apiQueue <- apiRequest{hexIdent: hex, reqType: "aircraft"}:
	default:
		t.cacheMu.Lock()
		delete(t.aircraftCache, hex)
		t.cacheMu.Unlock()
		slog.Warn("api queue full, dropping aircraft lookup request", "hex", hex)
	}
}

// triggerRouteLookup is always called from UpdateState, which holds t.mu.Lock().
// The cache-hit path must NOT re-acquire t.mu — update ac directly instead.
func (t *Tracker) triggerRouteLookup(ac *Aircraft, callsign string) {
	hex := ac.HexIdent

	t.cacheMu.RLock()
	cached, found := t.routeCache[callsign]
	t.cacheMu.RUnlock()

	if found {
		if !cached.pending && !cached.notFound && cached.data != nil && cached.data.FlightRoute != nil {
			fr := cached.data.FlightRoute
			if fr.Origin != nil {
				ac.OriginICAO = fr.Origin.ICAOCode
				ac.OriginIATA = fr.Origin.IATACode
				ac.OriginName = fr.Origin.Name
				ac.OriginCity = fr.Origin.Municipality
			}
			if fr.Destination != nil {
				ac.DestICAO = fr.Destination.ICAOCode
				ac.DestIATA = fr.Destination.IATACode
				ac.DestName = fr.Destination.Name
				ac.DestCity = fr.Destination.Municipality
			}
		}
		return
	}

	t.cacheMu.Lock()
	if _, exists := t.routeCache[callsign]; exists {
		t.cacheMu.Unlock()
		return
	}
	t.routeCache[callsign] = &cachedRoute{pending: true}
	t.cacheMu.Unlock()

	select {
	case t.apiQueue <- apiRequest{hexIdent: hex, callsign: callsign, reqType: "route"}:
	default:
		t.cacheMu.Lock()
		delete(t.routeCache, callsign)
		t.cacheMu.Unlock()
		slog.Warn("api queue full, dropping route lookup request", "callsign", callsign)
	}
}

func (t *Tracker) fetchAircraftDetails(hex string) {
	slog.Debug("fetching aircraft details from adsbdb.com API", "hex", hex)
	url := fmt.Sprintf("%s/aircraft/%s", adsbDBBaseURL, hex)
	resp, err := t.httpClient.Get(url)

	if err != nil {
		slog.Warn("failed to fetch aircraft details from adsbdb.com", "hex", hex, "error", err)
		t.cacheMu.Lock()
		delete(t.aircraftCache, hex)
		t.cacheMu.Unlock()
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusNotFound {
		slog.Debug("aircraft not found on adsbdb.com API", "hex", hex)
		t.cacheMu.Lock()
		t.aircraftCache[hex] = &cachedAircraft{notFound: true}
		t.cacheMu.Unlock()
		return
	}

	if resp.StatusCode != http.StatusOK {
		slog.Warn("adsbdb.com API returned non-OK status for aircraft", "hex", hex, "status", resp.Status)
		t.cacheMu.Lock()
		delete(t.aircraftCache, hex)
		t.cacheMu.Unlock()
		return
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		slog.Warn("failed to read aircraft details response body", "hex", hex, "error", err)
		t.cacheMu.Lock()
		delete(t.aircraftCache, hex)
		t.cacheMu.Unlock()
		return
	}

	acData, err := ParseAircraftResponse(body)
	if err != nil {
		slog.Warn("failed to parse aircraft details response", "hex", hex, "error", err)
		t.cacheMu.Lock()
		delete(t.aircraftCache, hex)
		t.cacheMu.Unlock()
		return
	}

	t.cacheMu.Lock()
	if acData == nil {
		t.aircraftCache[hex] = &cachedAircraft{notFound: true}
	} else {
		t.aircraftCache[hex] = &cachedAircraft{data: acData}
	}
	t.cacheMu.Unlock()

	if acData != nil {
		t.mu.Lock()
		if ac, exists := t.byHex[hex]; exists {
			ac.Manufacturer = acData.Manufacturer
			ac.Model = acData.Type
			ac.Registration = acData.Registration
			ac.ICAOType = acData.ICAOType
			ac.RegisteredOwner = acData.RegisteredOwner
			ac.Operator = acData.RegisteredOwner
		}
		t.mu.Unlock()
	}
}

func (t *Tracker) fetchRouteDetails(hex string, callsign string) {
	slog.Debug("fetching route details from adsbdb.com API", "callsign", callsign)
	url := fmt.Sprintf("%s/callsign/%s", adsbDBBaseURL, callsign)
	resp, err := t.httpClient.Get(url)

	if err != nil {
		slog.Warn("failed to fetch route details from adsbdb.com", "callsign", callsign, "error", err)
		t.cacheMu.Lock()
		delete(t.routeCache, callsign)
		t.cacheMu.Unlock()
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusNotFound {
		slog.Debug("route not found on adsbdb.com API", "callsign", callsign)
		t.cacheMu.Lock()
		t.routeCache[callsign] = &cachedRoute{notFound: true}
		t.cacheMu.Unlock()
		return
	}

	if resp.StatusCode != http.StatusOK {
		slog.Warn("adsbdb.com API returned non-OK status for route", "callsign", callsign, "status", resp.Status)
		t.cacheMu.Lock()
		delete(t.routeCache, callsign)
		t.cacheMu.Unlock()
		return
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		slog.Warn("failed to read route details response body", "callsign", callsign, "error", err)
		t.cacheMu.Lock()
		delete(t.routeCache, callsign)
		t.cacheMu.Unlock()
		return
	}

	routeData, err := ParseRouteResponse(body)
	if err != nil {
		slog.Warn("failed to parse route details response", "callsign", callsign, "error", err)
		t.cacheMu.Lock()
		delete(t.routeCache, callsign)
		t.cacheMu.Unlock()
		return
	}

	t.cacheMu.Lock()
	if routeData == nil || routeData.FlightRoute == nil {
		t.routeCache[callsign] = &cachedRoute{notFound: true}
	} else {
		t.routeCache[callsign] = &cachedRoute{data: routeData}
	}
	t.cacheMu.Unlock()

	if routeData != nil && routeData.FlightRoute != nil {
		fr := routeData.FlightRoute
		t.mu.Lock()
		if ac, exists := t.byHex[hex]; exists {
			if fr.Origin != nil {
				ac.OriginICAO = fr.Origin.ICAOCode
				ac.OriginIATA = fr.Origin.IATACode
				ac.OriginName = fr.Origin.Name
				ac.OriginCity = fr.Origin.Municipality
			}
			if fr.Destination != nil {
				ac.DestICAO = fr.Destination.ICAOCode
				ac.DestIATA = fr.Destination.IATACode
				ac.DestName = fr.Destination.Name
				ac.DestCity = fr.Destination.Municipality
			}
		}
		t.mu.Unlock()
	}
}
