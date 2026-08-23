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
