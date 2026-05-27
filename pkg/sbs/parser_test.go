package sbs

import (
	"testing"
)

func TestParseMessage_AIR(t *testing.T) {
	line := "AIR,,333,2,4601F6,102,2026/05/27,14:12:00.284,2026/05/27,14:12:00.284"
	msg, err := ParseMessage(line)
	if err != nil {
		t.Fatalf("unexpected error parsing AIR message: %v", err)
	}

	if msg.MessageType != MsgTypeAIR {
		t.Errorf("expected MessageType 'AIR', got '%s'", msg.MessageType)
	}
	if msg.HexIdent != "4601F6" {
		t.Errorf("expected HexIdent '4601F6', got '%s'", msg.HexIdent)
	}
	if msg.SessionID != 333 {
		t.Errorf("expected SessionID 333, got %d", msg.SessionID)
	}
	if msg.AircraftID != 2 {
		t.Errorf("expected AircraftID 2, got %d", msg.AircraftID)
	}
	if msg.FlightID != 102 {
		t.Errorf("expected FlightID 102, got %d", msg.FlightID)
	}
	
	expectedTime := "2026-05-27 14:12:00.284"
	if msg.GeneratedTime.Format("2006-01-02 15:04:05.000") != expectedTime {
		t.Errorf("expected GeneratedTime %s, got %v", expectedTime, msg.GeneratedTime)
	}
}

func TestParseMessage_MSG_AirbornePosition(t *testing.T) {
	// MSG,3 Airborne Position containing Altitude, Lat, Lon, Alert, Emergency, SPI, Ground
	line := "MSG,3,111,1,4601F6,101,2026/05/27,14:12:00.000,2026/05/27,14:12:00.000,,5250,,,52.34567,4.89012,,,0,0,0,0"
	msg, err := ParseMessage(line)
	if err != nil {
		t.Fatalf("unexpected error parsing MSG,3 message: %v", err)
	}

	if msg.MessageType != MsgTypeMSG {
		t.Errorf("expected MessageType 'MSG', got '%s'", msg.MessageType)
	}
	if msg.TransmissionType == nil || *msg.TransmissionType != TransAirbornePosition {
		t.Errorf("expected TransmissionType 3, got %v", msg.TransmissionType)
	}
	
	// Test Altitude
	if msg.Altitude == nil || *msg.Altitude != 5250 {
		t.Errorf("expected Altitude 5250, got %v", msg.Altitude)
	}
	// Test Lat/Lon
	if msg.Latitude == nil || *msg.Latitude != 52.34567 {
		t.Errorf("expected Latitude 52.34567, got %v", msg.Latitude)
	}
	if msg.Longitude == nil || *msg.Longitude != 4.89012 {
		t.Errorf("expected Longitude 4.89012, got %v", msg.Longitude)
	}
	// Test Alerts/SPI/Ground flags (0 should be parsed as false pointer)
	if msg.Alert == nil || *msg.Alert != false {
		t.Errorf("expected Alert false, got %v", msg.Alert)
	}
	if msg.Emergency == nil || *msg.Emergency != false {
		t.Errorf("expected Emergency false, got %v", msg.Emergency)
	}
	if msg.SPI == nil || *msg.SPI != false {
		t.Errorf("expected SPI false, got %v", msg.SPI)
	}
	if msg.IsOnGround == nil || *msg.IsOnGround != false {
		t.Errorf("expected IsOnGround false, got %v", msg.IsOnGround)
	}
}

func TestParseMessage_MSG_AirborneVelocity(t *testing.T) {
	// MSG,4 Airborne Velocity containing Speed, Track, VertRate
	line := "MSG,4,333,2,4601F6,102,2026/05/27,14:12:01.870,2026/05/27,14:12:01.870,,,200.0,189.2,,,1024,,,,,"
	msg, err := ParseMessage(line)
	if err != nil {
		t.Fatalf("unexpected error parsing MSG,4 message: %v", err)
	}

	if msg.GroundSpeed == nil || *msg.GroundSpeed != 200.0 {
		t.Errorf("expected Speed 200.0, got %v", msg.GroundSpeed)
	}
	if msg.Track == nil || *msg.Track != 189.2 {
		t.Errorf("expected Track 189.2, got %v", msg.Track)
	}
	if msg.VerticalRate == nil || *msg.VerticalRate != 1024 {
		t.Errorf("expected VerticalRate 1024, got %v", msg.VerticalRate)
	}
}

func TestParseMessage_STA(t *testing.T) {
	line := "STA,,333,1,3C55C9,101,2026/05/27,14:07:05.098,2026/05/27,14:07:05.098,RM"
	msg, err := ParseMessage(line)
	if err != nil {
		t.Fatalf("unexpected error parsing STA message: %v", err)
	}

	if msg.MessageType != MsgTypeSTA {
		t.Errorf("expected MessageType 'STA', got '%s'", msg.MessageType)
	}
	if msg.StatusChange == nil || *msg.StatusChange != StatusRemove {
		t.Errorf("expected StatusChange 'RM', got %v", msg.StatusChange)
	}
	if msg.Callsign != "" {
		t.Errorf("expected empty Callsign for STA status change, got '%s'", msg.Callsign)
	}
}

func TestParseMessage_ShortResiliency(t *testing.T) {
	// Fewer than 22 fields but at least 10 fields should pad and parse gracefully without panic
	line := "MSG,1,333,2,4601F6,102,2026/05/27,14:12:00.284,2026/05/27,14:12:00.284,KLM123"
	msg, err := ParseMessage(line)
	if err != nil {
		t.Fatalf("unexpected error parsing short line: %v", err)
	}

	if msg.Callsign != "KLM123" {
		t.Errorf("expected Callsign 'KLM123', got '%s'", msg.Callsign)
	}
	if msg.Altitude != nil {
		t.Errorf("expected nil Altitude, got %v", msg.Altitude)
	}
	if msg.IsOnGround != nil {
		t.Errorf("expected nil IsOnGround, got %v", msg.IsOnGround)
	}
}

func TestParseMessage_InvalidLine(t *testing.T) {
	// Too few fields (less than 10)
	line := "MSG,1,2,3"
	_, err := ParseMessage(line)
	if err == nil {
		t.Errorf("expected error parsing invalid line, got nil")
	}
}
