package sbs

import (
	"sync"
	"time"
)

// Tracker coordinates state tracking for a registry of active aircraft.
type Tracker struct {
	mu        sync.RWMutex
	aircrafts map[string]*Aircraft
}

// NewTracker creates a new thread-safe Aircraft state tracker.
func NewTracker() *Tracker {
	return &Tracker{
		aircrafts: make(map[string]*Aircraft),
	}
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
			delete(t.aircrafts, msg.HexIdent)
			return Aircraft{}, false
		}
	}

	now := time.Now()
	timestamp := msg.GeneratedTime
	if timestamp.IsZero() {
		timestamp = now
	}

	ac, exists := t.aircrafts[msg.HexIdent]
	isNew := !exists

	if isNew {
		ac = &Aircraft{
			HexIdent:  msg.HexIdent,
			FirstSeen: timestamp,
		}
		t.aircrafts[msg.HexIdent] = ac
	}

	// Apply updates incrementally
	ac.LastSeen = timestamp
	ac.MessageCount++

	if msg.Callsign != "" {
		ac.Callsign = msg.Callsign
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
	}
	if msg.Longitude != nil {
		ac.Longitude = *msg.Longitude
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

	ac, exists := t.aircrafts[hexIdent]
	if !exists {
		return Aircraft{}, false
	}
	return *ac, true
}

// GetAllActive returns a list of all currently tracked active aircraft.
func (t *Tracker) GetAllActive() []Aircraft {
	t.mu.RLock()
	defer t.mu.RUnlock()

	list := make([]Aircraft, 0, len(t.aircrafts))
	for _, ac := range t.aircrafts {
		list = append(list, *ac)
	}
	return list
}

// Remove explicitly deletes an aircraft from the tracker.
func (t *Tracker) Remove(hexIdent string) {
	t.mu.Lock()
	defer t.mu.Unlock()
	delete(t.aircrafts, hexIdent)
}

// CleanupOrphans evicts aircraft that haven't been heard from in longer than the specified maxAge.
// Returns the number of evicted aircraft.
func (t *Tracker) CleanupOrphans(maxAge time.Duration) int {
	t.mu.Lock()
	defer t.mu.Unlock()

	now := time.Now()
	evicted := 0

	for hex, ac := range t.aircrafts {
		if now.Sub(ac.LastSeen) > maxAge {
			delete(t.aircrafts, hex)
			evicted++
		}
	}

	return evicted
}
