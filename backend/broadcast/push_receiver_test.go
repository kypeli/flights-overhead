package broadcast

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"net/http"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"flights-overhead/data"
	"flights-overhead/pkg/sbs"
)

type roundTripFunc func(req *http.Request) *http.Response

func (f roundTripFunc) RoundTrip(req *http.Request) (*http.Response, error) {
	return f(req), nil
}

func TestPushNotificationReceiver_DeduplicationAndProximity(t *testing.T) {
	var requestCount int32
	var receivedPayloads []PushNotificationPayload
	var mu sync.Mutex

	mockClient := &http.Client{
		Transport: roundTripFunc(func(r *http.Request) *http.Response {
			atomic.AddInt32(&requestCount, 1)

			body, err := io.ReadAll(r.Body)
			if err != nil {
				return &http.Response{
					StatusCode: http.StatusBadRequest,
					Body:       io.NopCloser(bytes.NewBufferString("read error")),
				}
			}

			var payload PushNotificationPayload
			if err := json.Unmarshal(body, &payload); err != nil {
				return &http.Response{
					StatusCode: http.StatusBadRequest,
					Body:       io.NopCloser(bytes.NewBufferString("json error")),
				}
			}

			mu.Lock()
			receivedPayloads = append(receivedPayloads, payload)
			mu.Unlock()

			return &http.Response{
				StatusCode: http.StatusOK,
				Body:       io.NopCloser(bytes.NewBufferString(`{"success": true}`)),
				Header:     make(http.Header),
			}
		}),
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// Base station at Helsinki Center: 60.1699, 24.9384
	// Vantaa Airport is at 60.3172, 24.9633 (~16.4 km away)
	// Closer point in Pasila: 60.1990, 24.9340 (~3.2 km away)
	// Far point in Tampere: 61.4978, 23.7610 (~160 km away)
	receiver := NewPushNotificationReceiver(ctx, PushReceiverConfig{
		BaseLat:              60.1699,
		BaseLon:              24.9384,
		ProximityThresholdKM: 10.0, // 10 km threshold
		EndpointURL:          "http://mock-cloud-function/pushNotification",
		HTTPClient:           mockClient,
	})

	flightPasila := data.FlightJSON{
		Aircraft: sbs.Aircraft{
			HexIdent:    "4601F6",
			Callsign:    "FIN123",
			Latitude:    60.1990,
			Longitude:   24.9340,
			HasPosition: true,
		},
	}

	flightFar := data.FlightJSON{
		Aircraft: sbs.Aircraft{
			HexIdent:    "4006EA",
			Callsign:    "BAW456",
			Latitude:    61.4978,
			Longitude:   23.7610,
			HasPosition: true,
		},
	}

	flightNoPos := data.FlightJSON{
		Aircraft: sbs.Aircraft{
			HexIdent:    "123456",
			Callsign:    "NOPOS",
			HasPosition: false,
		},
	}

	// 1. Initial snapshot with 1 close, 1 far, 1 no position
	receiver.Send([]data.FlightJSON{flightPasila, flightFar, flightNoPos})

	// Wait briefly for async dispatch
	time.Sleep(50 * time.Millisecond)

	if atomic.LoadInt32(&requestCount) != 1 {
		t.Fatalf("expected 1 push notification request, got %d", atomic.LoadInt32(&requestCount))
	}
	if !receiver.IsNotified("4601F6") {
		t.Errorf("expected 4601F6 to be marked as notified")
	}
	if receiver.IsNotified("4006EA") {
		t.Errorf("did not expect 4006EA (far) to be marked as notified")
	}

	// 2. Second snapshot: same flights with updated position still close. Should NOT send duplicate notification!
	flightPasilaMoved := flightPasila
	flightPasilaMoved.Latitude = 60.1980
	flightPasilaMoved.Longitude = 24.9330
	receiver.Send([]data.FlightJSON{flightPasilaMoved, flightFar})

	time.Sleep(50 * time.Millisecond)

	if atomic.LoadInt32(&requestCount) != 1 {
		t.Fatalf("expected request count to remain 1 after duplicate snapshot, got %d", atomic.LoadInt32(&requestCount))
	}

	// 3. Third snapshot: aircraft leaves coverage (evicted from active flights)
	receiver.Send([]data.FlightJSON{flightFar})

	if receiver.IsNotified("4601F6") {
		t.Errorf("expected 4601F6 to be cleaned up after leaving active snapshot")
	}

	// 4. Fourth snapshot: aircraft returns later in a new flight session and gets notified again
	receiver.Send([]data.FlightJSON{flightPasila})

	time.Sleep(50 * time.Millisecond)

	if atomic.LoadInt32(&requestCount) != 2 {
		t.Fatalf("expected request count to be 2 after new flight session, got %d", atomic.LoadInt32(&requestCount))
	}
}

func TestPushNotificationReceiver_DispatchTestNotification(t *testing.T) {
	var lastReceivedPayload PushNotificationPayload
	var mu sync.Mutex

	mockClient := &http.Client{
		Transport: roundTripFunc(func(r *http.Request) *http.Response {
			body, _ := io.ReadAll(r.Body)
			var payload PushNotificationPayload
			_ = json.Unmarshal(body, &payload)

			mu.Lock()
			lastReceivedPayload = payload
			mu.Unlock()

			return &http.Response{
				StatusCode: http.StatusOK,
				Body:       io.NopCloser(bytes.NewBufferString(`{"success": true, "sentCount": 1}`)),
				Header:     make(http.Header),
			}
		}),
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	receiver := NewPushNotificationReceiver(ctx, PushReceiverConfig{
		BaseLat:              60.1699,
		BaseLon:              24.9384,
		ProximityThresholdKM: 10.0,
		EndpointURL:          "http://mock-cloud-function/pushNotification",
		HTTPClient:           mockClient,
	})

	// 1. Dispatch test notification with default dummy payload
	status, payload, err := receiver.DispatchTestNotification(ctx, nil)
	if err != nil {
		t.Fatalf("unexpected error on default test notification: %v", err)
	}
	if status != http.StatusOK {
		t.Errorf("expected status 200, got %d", status)
	}
	if payload.HexIdent != "TEST01" || payload.Callsign != "TESTFLT" {
		t.Errorf("expected default hex TEST01 and callsign TESTFLT, got %+v", payload)
	}

	mu.Lock()
	received := lastReceivedPayload
	mu.Unlock()
	if received.HexIdent != "TEST01" || received.Model != "A350-900" {
		t.Errorf("expected received mock payload to have hex TEST01, got %+v", received)
	}

	// 2. Dispatch test notification with custom payload
	custom := &PushNotificationPayload{
		HexIdent:   "CUSTOM99",
		Callsign:   "CUST99",
		DistanceKM: 2.5,
		Altitude:   1200,
	}
	status, payload, err = receiver.DispatchTestNotification(ctx, custom)
	if err != nil {
		t.Fatalf("unexpected error on custom test notification: %v", err)
	}
	if status != http.StatusOK {
		t.Errorf("expected status 200, got %d", status)
	}
	if payload.HexIdent != "CUSTOM99" || payload.Callsign != "CUST99" {
		t.Errorf("expected custom hex CUSTOM99 and callsign CUST99, got %+v", payload)
	}

	mu.Lock()
	received = lastReceivedPayload
	mu.Unlock()
	if received.HexIdent != "CUSTOM99" || received.DistanceKM != 2.5 {
		t.Errorf("expected received mock payload to have hex CUSTOM99 and distance 2.5, got %+v", received)
	}
}

func TestPushNotificationReceiver_EnrichmentGracePeriod(t *testing.T) {
	var receivedPayloads []PushNotificationPayload
	var mu sync.Mutex

	mockClient := &http.Client{
		Transport: roundTripFunc(func(r *http.Request) *http.Response {
			body, _ := io.ReadAll(r.Body)
			var payload PushNotificationPayload
			_ = json.Unmarshal(body, &payload)

			mu.Lock()
			receivedPayloads = append(receivedPayloads, payload)
			mu.Unlock()

			return &http.Response{
				StatusCode: http.StatusOK,
				Body:       io.NopCloser(bytes.NewBufferString(`{"success": true}`)),
				Header:     make(http.Header),
			}
		}),
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	receiver := NewPushNotificationReceiver(ctx, PushReceiverConfig{
		BaseLat:               60.1699,
		BaseLon:               24.9384,
		ProximityThresholdKM:  10.0,
		EndpointURL:           "http://mock-cloud-function/pushNotification",
		HTTPClient:            mockClient,
		EnrichmentGracePeriod: 120 * time.Millisecond,
	})

	// 1. Flight spotted with position (MSG,3), but callsign/route not yet enriched
	unEnriched := data.FlightJSON{
		Aircraft: sbs.Aircraft{
			HexIdent:    "4601F6",
			Latitude:    60.1990,
			Longitude:   24.9340,
			HasPosition: true,
			FirstSeen:   time.Now(),
		},
	}

	receiver.Send([]data.FlightJSON{unEnriched})
	time.Sleep(30 * time.Millisecond)

	if receiver.IsNotified("4601F6") {
		t.Fatalf("expected 4601F6 to wait for enrichment during grace period")
	}

	// 2. Next tick: callsign and route arrive before grace period expires -> dispatches immediately
	enriched := unEnriched
	enriched.Callsign = "FIN123"
	enriched.Operator = "Finnair"
	enriched.OriginIATA = "HEL"
	enriched.DestIATA = "OUL"

	receiver.Send([]data.FlightJSON{enriched})
	time.Sleep(30 * time.Millisecond)

	if !receiver.IsNotified("4601F6") {
		t.Fatalf("expected 4601F6 to be notified immediately once enriched")
	}

	mu.Lock()
	if len(receivedPayloads) != 1 || receivedPayloads[0].Operator != "Finnair" || receivedPayloads[0].OriginIATA != "HEL" || receivedPayloads[0].DestIATA != "OUL" {
		t.Errorf("expected enriched payload with Finnair/HEL/OUL, got %+v", receivedPayloads)
	}
	mu.Unlock()

	// 3. Another flight never receives enrichment -> still notifies after grace period expires
	unEnriched2 := data.FlightJSON{
		Aircraft: sbs.Aircraft{
			HexIdent:    "ABCDEF",
			Latitude:    60.1990,
			Longitude:   24.9340,
			HasPosition: true,
			FirstSeen:   time.Now(),
		},
	}
	receiver.Send([]data.FlightJSON{enriched, unEnriched2})
	if receiver.IsNotified("ABCDEF") {
		t.Fatalf("expected ABCDEF not to be notified before grace period expires")
	}

	time.Sleep(150 * time.Millisecond)
	receiver.Send([]data.FlightJSON{enriched, unEnriched2})
	time.Sleep(30 * time.Millisecond)

	if !receiver.IsNotified("ABCDEF") {
		t.Fatalf("expected ABCDEF to be notified after grace period expired")
	}
}


