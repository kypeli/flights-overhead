package sbs

import (
	"bytes"
	"encoding/json"
)

// ADSBDBAircraft contains metadata for a specific airframe.
type ADSBDBAircraft struct {
	Type            string `json:"type"`
	ICAOType        string `json:"icao_type"`
	Manufacturer    string `json:"manufacturer"`
	Registration    string `json:"registration"`
	RegisteredOwner string `json:"registered_owner"`
}

// ADSBDBAircraftResponse wraps the response from /aircraft/{hex} API endpoint.
type ADSBDBAircraftResponse struct {
	Response json.RawMessage `json:"response"`
}

// ADSBDBAircraftWrapper wraps the aircraft details inside the response object.
type ADSBDBAircraftWrapper struct {
	Aircraft *ADSBDBAircraft `json:"aircraft"`
}

// ParseAircraftResponse decodes the JSON body returned by adsbdb.com /aircraft/{hex} endpoint.
// It returns nil, nil if the aircraft was not found (e.g. "unknown aircraft" string response).
func ParseAircraftResponse(body []byte) (*ADSBDBAircraft, error) {
	var resp ADSBDBAircraftResponse
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, err
	}
	trimmed := bytes.TrimSpace(resp.Response)
	if len(trimmed) > 0 && trimmed[0] == '{' {
		var wrapper ADSBDBAircraftWrapper
		if err := json.Unmarshal(trimmed, &wrapper); err != nil {
			return nil, err
		}
		return wrapper.Aircraft, nil
	}
	return nil, nil
}


// AirportInfo contains name and location details of an airport.
type AirportInfo struct {
	IATACode     string `json:"iata_code"`
	ICAOCode     string `json:"icao_code"`
	Municipality string `json:"municipality"`
	Name         string `json:"name"`
}

// FlightRoute contains origin and destination information for a flight route.
type FlightRoute struct {
	Origin      *AirportInfo `json:"origin"`
	Destination *AirportInfo `json:"destination"`
}

// ADSBDBRoute wraps the FlightRoute.
type ADSBDBRoute struct {
	FlightRoute *FlightRoute `json:"flightroute"`
}

// ADSBDBRouteResponse wraps the response from /callsign/{callsign} API endpoint.
type ADSBDBRouteResponse struct {
	Response json.RawMessage `json:"response"`
}

// ParseRouteResponse decodes the JSON body returned by adsbdb.com /callsign/{callsign} endpoint.
// It returns nil, nil if the route was not found (e.g. "unknown route" string response).
func ParseRouteResponse(body []byte) (*ADSBDBRoute, error) {
	var resp ADSBDBRouteResponse
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, err
	}
	trimmed := bytes.TrimSpace(resp.Response)
	if len(trimmed) > 0 && trimmed[0] == '{' {
		var route ADSBDBRoute
		if err := json.Unmarshal(trimmed, &route); err != nil {
			return nil, err
		}
		return &route, nil
	}
	return nil, nil
}
