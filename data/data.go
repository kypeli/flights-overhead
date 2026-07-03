package data

import (
	"flights-overhead/pkg/sbs"
)

// FlightJSON defines the telemetry representation of tracked aircraft.
type FlightJSON struct {
	Aircraft  sbs.Aircraft
	Distance  float64 `json:"distance"`
	Direction string  `json:"direction,omitempty"`
}

// StreamPayload represents the complete live radar broadcast payload.
type StreamPayload struct {
	ReceiverAddr string       `json:"receiver_addr"`
	Flights      []FlightJSON `json:"flights"`
}
