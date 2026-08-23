package data

import (
	"flights-overhead/pkg/sbs"
)

// FlightJSON defines the telemetry representation of tracked aircraft.
type FlightJSON struct {
	sbs.Aircraft
	Distance  float64 `json:"distance"`
	Direction string  `json:"direction,omitempty"`
}

// StreamPayload represents the complete live radar broadcast payload.
type StreamPayload struct {
	ReceiverAddr string       `json:"receiver_addr"`
	Flights      []FlightJSON `json:"flights"`
}

// PushNotificationPayload is the JSON request body sent to the push notification endpoint.
type PushNotificationPayload struct {
	HexIdent        string  `json:"hex"`
	Callsign        string  `json:"callsign,omitempty"`
	DistanceKM      float64 `json:"distanceKm"`
	Altitude        int     `json:"altitude,omitempty"`
	GroundSpeed     float64 `json:"groundSpeed,omitempty"`
	Track           float64 `json:"track,omitempty"`
	Latitude        float64 `json:"latitude,omitempty"`
	Longitude       float64 `json:"longitude,omitempty"`
	Manufacturer    string  `json:"manufacturer,omitempty"`
	Model           string  `json:"model,omitempty"`
	Registration    string  `json:"registration,omitempty"`
	ICAOType        string  `json:"icaoType,omitempty"`
	RegisteredOwner string  `json:"registeredOwner,omitempty"`
	Operator        string  `json:"operator,omitempty"`
	OriginICAO      string  `json:"originICAO,omitempty"`
	OriginIATA      string  `json:"originIATA,omitempty"`
	OriginName      string  `json:"originName,omitempty"`
	OriginCity      string  `json:"originCity,omitempty"`
	DestICAO        string  `json:"destICAO,omitempty"`
	DestIATA        string  `json:"destIATA,omitempty"`
	DestName        string  `json:"destName,omitempty"`
	DestCity        string  `json:"destCity,omitempty"`
}

