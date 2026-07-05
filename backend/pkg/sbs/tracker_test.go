// tracker_test.go contains tests for the aircraft state tracker in tracker.go.
package sbs

import (
	"testing"
	"time"
)

func TestTracker_IncrementalUpdate(t *testing.T) {
	tracker := NewTracker()

	// 1. Send first message (Callsign KLM123)
	msg1 := &Message{
		MessageType: MsgTypeMSG,
		HexIdent:    "4601F6",
		Callsign:    "KLM123",
	}
	ac, isNew := tracker.UpdateState(msg1)
	if !isNew {
		t.Errorf("expected aircraft to be new")
	}
	if ac.Callsign != "KLM123" {
		t.Errorf("expected callsign KLM123, got %s", ac.Callsign)
	}
	if ac.MessageCount != 1 {
		t.Errorf("expected MessageCount 1, got %d", ac.MessageCount)
	}

	// 2. Send second message (Altitude and Coordinates)
	alt := 12000
	lat := 52.1
	lon := 4.9
	msg2 := &Message{
		MessageType: MsgTypeMSG,
		HexIdent:    "4601F6",
		Altitude:    &alt,
		Latitude:    &lat,
		Longitude:   &lon,
	}
	ac, isNew = tracker.UpdateState(msg2)
	if isNew {
		t.Errorf("expected aircraft to be already tracked")
	}
	if ac.Callsign != "KLM123" {
		t.Errorf("expected callsign KLM123 to be preserved, got %s", ac.Callsign)
	}
	if ac.Altitude != 12000 {
		t.Errorf("expected altitude 12000, got %d", ac.Altitude)
	}
	if ac.Latitude != 52.1 {
		t.Errorf("expected latitude 52.1, got %f", ac.Latitude)
	}
	if ac.Longitude != 4.9 {
		t.Errorf("expected longitude 4.9, got %f", ac.Longitude)
	}
	if ac.MessageCount != 2 {
		t.Errorf("expected MessageCount 2, got %d", ac.MessageCount)
	}
}

func TestTracker_STARemove(t *testing.T) {
	tracker := NewTracker()

	// Add aircraft
	tracker.UpdateState(&Message{
		MessageType: MsgTypeMSG,
		HexIdent:    "4601F6",
		Callsign:    "KLM123",
	})

	// Verify it exists
	_, exists := tracker.Get("4601F6")
	if !exists {
		t.Fatalf("expected aircraft to exist in tracker")
	}

	// Update with STA RM
	status := StatusRemove
	tracker.UpdateState(&Message{
		MessageType:  MsgTypeSTA,
		HexIdent:     "4601F6",
		StatusChange: &status,
	})

	// Verify it got removed
	_, exists = tracker.Get("4601F6")
	if exists {
		t.Errorf("expected aircraft to be removed from tracker")
	}
}

func TestTracker_CleanupOrphans(t *testing.T) {
	tracker := NewTracker()

	// Add first aircraft (recently seen)
	tracker.UpdateState(&Message{
		MessageType: MsgTypeMSG,
		HexIdent:    "RECENT",
	})

	// Add second aircraft (simulated old last seen using GeneratedTime)
	tracker.UpdateState(&Message{
		MessageType:   MsgTypeMSG,
		HexIdent:      "EXPIRED",
		GeneratedTime: time.Now().Add(-10 * time.Minute),
	})

	// Manually tweak "EXPIRED" to be old (UpdateState defaults to GeneratedTime)
	ac, exists := tracker.byHex["EXPIRED"]
	if exists {
		ac.LastSeen = time.Now().Add(-10 * time.Minute)
	}

	// Run cleanup with max age of 5 minutes
	evicted := tracker.EvictStale(5 * time.Minute)
	if evicted != 1 {
		t.Errorf("expected 1 aircraft to be cleaned up, got %d", evicted)
	}

	// Verify only RECENT remains
	_, existsRecent := tracker.Get("RECENT")
	if !existsRecent {
		t.Errorf("expected RECENT aircraft to be kept")
	}

	_, existsExpired := tracker.Get("EXPIRED")
	if existsExpired {
		t.Errorf("expected EXPIRED aircraft to be evicted")
	}
}
