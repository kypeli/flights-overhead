// aircraft.go defines the Aircraft struct that holds the aggregated state of a single
// aircraft as built up from one or more incremental SBS-1 messages.
package sbs

import (
	"time"
)

type Aircraft struct {
	HexIdent        string    `json:"hex_ident"`
	Callsign        string    `json:"callsign,omitempty"`
	Altitude        int       `json:"altitude,omitempty"`
	GroundSpeed     float64   `json:"ground_speed,omitempty"`
	Track           float64   `json:"track,omitempty"`
	HasTrack        bool      `json:"-"`
	HasPosition     bool      `json:"-"`
	Latitude        float64   `json:"latitude,omitempty"`
	Longitude       float64   `json:"longitude,omitempty"`
	VerticalRate    int       `json:"vertical_rate,omitempty"`
	Squawk          string    `json:"squawk,omitempty"`
	IsOnGround      bool      `json:"is_on_ground"`
	LastSeen        time.Time `json:"last_seen"`
	MessageCount    int64     `json:"message_count"`
	FirstSeen       time.Time `json:"first_seen"`
	Manufacturer    string    `json:"manufacturer,omitempty"`
	Model           string    `json:"model,omitempty"`
	Operator        string    `json:"operator,omitempty"`
	Registration    string    `json:"registration,omitempty"`
	ICAOType        string    `json:"icao_type,omitempty"`
	RegisteredOwner string    `json:"registered_owner,omitempty"`
	OriginICAO      string    `json:"origin_icao,omitempty"`
	OriginIATA      string    `json:"origin_iata,omitempty"`
	OriginName      string    `json:"origin_name,omitempty"`
	OriginCity      string    `json:"origin_city,omitempty"`
	DestICAO        string    `json:"dest_icao,omitempty"`
	DestIATA        string    `json:"dest_iata,omitempty"`
	DestName        string    `json:"dest_name,omitempty"`
	DestCity        string    `json:"dest_city,omitempty"`
}
