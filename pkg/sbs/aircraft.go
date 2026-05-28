package sbs

import (
	"time"
)

type Aircraft struct {
	HexIdent     string    `json:"hex_ident"`
	Callsign     string    `json:"callsign,omitempty"`
	Altitude     int       `json:"altitude,omitempty"`
	GroundSpeed  float64   `json:"ground_speed,omitempty"`
	Track        float64   `json:"track,omitempty"`
	HasTrack     bool      `json:"-"`
	Latitude     float64   `json:"latitude,omitempty"`
	Longitude    float64   `json:"longitude,omitempty"`
	VerticalRate int       `json:"vertical_rate,omitempty"`
	Squawk       string    `json:"squawk,omitempty"`
	IsOnGround   bool      `json:"is_on_ground"`
	LastSeen     time.Time `json:"last_seen"`
	MessageCount int64     `json:"message_count"`
	FirstSeen    time.Time `json:"first_seen"`
}
