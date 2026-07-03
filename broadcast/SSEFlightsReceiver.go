package broadcast

import (
	"encoding/json"
	"flights-overhead/data"
	"flights-overhead/frontend"
	"flights-overhead/pkg/sbs"
	"log/slog"
	"sort"
)

type SSEFlightsReceiver struct {
	broker       *frontend.SSEBroker
	receiverLat  float64
	receiverLon  float64
	receiverAddr string
}

func NewSSEFlightsReceiver(broker *frontend.SSEBroker, receiverLat, receiverLon float64, receiverAddr string) *SSEFlightsReceiver {
	return &SSEFlightsReceiver{
		broker:       broker,
		receiverLat:  receiverLat,
		receiverLon:  receiverLon,
		receiverAddr: receiverAddr,
	}
}

func (f *SSEFlightsReceiver) Send(flights []data.FlightJSON) {
	for i := range flights {
		dist := sbs.DistanceNM(f.receiverLat, f.receiverLon, flights[i].Aircraft.Latitude, flights[i].Aircraft.Longitude)

		var direction string
		if flights[i].Aircraft.HasTrack {
			direction = sbs.TrackToDirection(flights[i].Aircraft.Track)
		}

		flights[i].Distance = dist
		flights[i].Direction = direction
	}

	// Sort by distance ascending; fall back to callsign for flights without position.
	sort.Slice(flights, func(i, j int) bool {
		iPos := flights[i].Aircraft.HasPosition
		jPos := flights[j].Aircraft.HasPosition
		if iPos && jPos {
			return flights[i].Distance < flights[j].Distance
		}
		if iPos != jPos {
			return iPos
		}
		ci := flights[i].Aircraft.Callsign
		if ci == "" {
			ci = flights[i].Aircraft.HexIdent
		}
		cj := flights[j].Aircraft.Callsign
		if cj == "" {
			cj = flights[j].Aircraft.HexIdent
		}
		return ci < cj
	})

	payload := data.StreamPayload{
		ReceiverAddr: f.receiverAddr,
		Flights:      flights,
	}

	payloadBytes, err := json.Marshal(payload)
	if err != nil {
		slog.Error("failed to marshal broadcast payload", "error", err)
		return
	}
	f.broker.Broadcast(string(payloadBytes))
}
