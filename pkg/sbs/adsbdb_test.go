package sbs

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestParseAircraftResponse(t *testing.T) {
	validJSON := `{
		"response": {
			"aircraft": {
				"type": "737-800",
				"icao_type": "B738",
				"manufacturer": "BOEING",
				"registration": "G-TAAW",
				"registered_owner": "TUI Airways"
			}
		}
	}`
	ac, err := ParseAircraftResponse([]byte(validJSON))
	if err != nil {
		t.Fatalf("unexpected error parsing valid JSON: %v", err)
	}
	if ac == nil {
		t.Fatal("expected non-nil aircraft details")
	}
	if ac.Registration != "G-TAAW" || ac.Manufacturer != "BOEING" || ac.Type != "737-800" {
		t.Errorf("unexpected aircraft data parsed: %+v", ac)
	}

	unknownJSON := `{"response": "unknown aircraft"}`
	acUnknown, err := ParseAircraftResponse([]byte(unknownJSON))
	if err != nil {
		t.Fatalf("unexpected error parsing unknown response: %v", err)
	}
	if acUnknown != nil {
		t.Errorf("expected nil aircraft details for unknown response, got: %+v", acUnknown)
	}
}

func TestParseRouteResponse(t *testing.T) {
	validJSON := `{
		"response": {
			"flightroute": {
				"callsign": "TOM123",
				"origin": {
					"iata_code": "HEL",
					"icao_code": "EFHK",
					"municipality": "Helsinki",
					"name": "Helsinki Vantaa Airport"
				},
				"destination": {
					"iata_code": "OSL",
					"icao_code": "ENGM",
					"municipality": "Oslo",
					"name": "Oslo Airport, Gardermoen"
				}
			}
		}
	}`
	route, err := ParseRouteResponse([]byte(validJSON))
	if err != nil {
		t.Fatalf("unexpected error parsing valid JSON: %v", err)
	}
	if route == nil || route.FlightRoute == nil {
		t.Fatal("expected non-nil route details")
	}
	fr := route.FlightRoute
	if fr.Origin.IATACode != "HEL" || fr.Destination.Municipality != "Oslo" {
		t.Errorf("unexpected route data parsed: %+v", fr)
	}

	unknownJSON := `{"response": "unknown route"}`
	routeUnknown, err := ParseRouteResponse([]byte(unknownJSON))
	if err != nil {
		t.Fatalf("unexpected error parsing unknown response: %v", err)
	}
	if routeUnknown != nil {
		t.Errorf("expected nil route details for unknown response, got: %+v", routeUnknown)
	}
}

func TestTracker_APIIntegration(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		if r.URL.Path == "/aircraft/4006EA" {
			w.Write([]byte(`{
				"response": {
					"aircraft": {
						"type": "Boeing 777-200",
						"icao_type": "B772",
						"manufacturer": "BOEING",
						"registration": "G-VIIA",
						"registered_owner": "British Airways"
					}
				}
			}`))
			return
		}
		if r.URL.Path == "/callsign/BAW227" {
			w.Write([]byte(`{
				"response": {
					"flightroute": {
						"origin": {
							"iata_code": "LHR",
							"icao_code": "EGLL",
							"municipality": "London",
							"name": "London Heathrow"
						},
						"destination": {
							"iata_code": "HEL",
							"icao_code": "EFHK",
							"municipality": "Helsinki",
							"name": "Helsinki Vantaa"
						}
					}
				}
			}`))
			return
		}
		w.WriteHeader(http.StatusNotFound)
		w.Write([]byte(`{"response": "not found"}`))
	}))
	defer server.Close()

	// Override API base URL to point to mock server
	oldBaseURL := adsbDBBaseURL
	adsbDBBaseURL = server.URL
	defer func() { adsbDBBaseURL = oldBaseURL }()

	// Initialize tracker
	tracker := NewTracker()
	// Use mock server's client
	tracker.httpClient = server.Client()

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	tracker.StartAPIWorker(ctx)

	// Send message for a new flight
	msg := &Message{
		MessageType: MsgTypeMSG,
		HexIdent:    "4006EA",
		Callsign:    "BAW227",
	}

	tracker.UpdateState(msg)

	// Wait up to 2 seconds for worker to process lookups
	var ac Aircraft
	var found bool
	deadline := time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		ac, found = tracker.Get("4006EA")
		if found && ac.Registration == "G-VIIA" && ac.OriginCity == "London" {
			break
		}
		time.Sleep(50 * time.Millisecond)
	}

	if !found {
		t.Fatalf("expected aircraft 4006EA to be tracked")
	}

	if ac.Registration != "G-VIIA" {
		t.Errorf("expected aircraft registration G-VIIA, got %s", ac.Registration)
	}
	if ac.Manufacturer != "BOEING" {
		t.Errorf("expected manufacturer BOEING, got %s", ac.Manufacturer)
	}
	if ac.OriginCity != "London" {
		t.Errorf("expected origin city London, got %s", ac.OriginCity)
	}
	if ac.DestCity != "Helsinki" {
		t.Errorf("expected dest city Helsinki, got %s", ac.DestCity)
	}
}
