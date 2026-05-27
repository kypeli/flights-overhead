package sbs

import (
	"time"
)

type MessageType string

const (
	MsgTypeMSG MessageType = "MSG" // Transmission message
	MsgTypeSEL MessageType = "SEL" // Selection change
	MsgTypeID  MessageType = "ID"  // Identification change
	MsgTypeAIR MessageType = "AIR" // Aircraft session/transmission started
	MsgTypeSTA MessageType = "STA" // Status change (e.g., lost signal or removed)
	MsgTypeCLK MessageType = "CLK" // Click message
)

type TransmissionType int

const (
	TransIdentAndCategory  TransmissionType = 1 // MSG 1: Callsign
	TransSurfacePosition   TransmissionType = 2 // MSG 2: Surface position
	TransAirbornePosition  TransmissionType = 3 // MSG 3: Airborne position (Lat, Lon, Alt, Ground)
	TransAirborneVelocity  TransmissionType = 4 // MSG 4: Airborne velocity (Speed, Track, VertRate)
	TransSurveillanceAlt   TransmissionType = 5 // MSG 5: Surveillance altitude (Alt, Alert, SPI, Ground)
	TransSurveillanceID    TransmissionType = 6 // MSG 6: Surveillance identification (Squawk, Alert, SPI, Ground)
	TransAirToAir          TransmissionType = 7 // MSG 7: Air to air (Alt, Ground)
	TransAllCallReply      TransmissionType = 8 // MSG 8: All call reply (Ground)
)

type StatusChange string

const (
	StatusSignalLost     StatusChange = "SL" // Signal Lost
	StatusRemove         StatusChange = "RM" // Remove Session
	StatusPositionLost   StatusChange = "PL" // Position Lost
	StatusAircraftDelete StatusChange = "AD" // Delete Aircraft
)

// Message represents a fully parsed SBS-1 message (22 fields).
type Message struct {
	MessageType      MessageType
	TransmissionType *TransmissionType
	SessionID        int64
	AircraftID       int64
	HexIdent         string // 24-bit ICAO address in hex
	FlightID         int64
	GeneratedTime    time.Time
	LoggedTime       time.Time

	// Field 11: Callsign or StatusChange depending on MessageType
	Callsign     string
	StatusChange *StatusChange

	Altitude     *int      // feet
	GroundSpeed  *float64  // knots
	Track        *float64  // degrees (0-359)
	Latitude     *float64  // decimal degrees
	Longitude    *float64  // decimal degrees
	VerticalRate *int      // feet/min
	Squawk       string    // 4-digit octal transponder code
	Alert        *bool
	Emergency    *bool
	SPI          *bool
	IsOnGround   *bool
}
