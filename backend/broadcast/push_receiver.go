package broadcast

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"sync"
	"time"

	"flights-overhead/data"
	"flights-overhead/pkg/sbs"
)

// PushReceiverConfig holds configuration for the PushNotificationReceiver.
type PushReceiverConfig struct {
	BaseLat              float64
	BaseLon              float64
	ProximityThresholdKM float64
	EndpointURL          string
	HTTPClient           *http.Client
	AuthToken            string
}

// PushNotificationPayload is the JSON request body sent to the push notification endpoint.
type PushNotificationPayload = data.PushNotificationPayload

// TestPushSender defines an interface for sending on-demand test push notifications.
type TestPushSender interface {
	DispatchTestNotification(ctx context.Context, customPayload *PushNotificationPayload) (int, *PushNotificationPayload, error)
}

// PushNotificationReceiver implements FlightsReceiver to dispatch push notifications
// when an aircraft enters the defined proximity radius around the SBS-1 base station.
type PushNotificationReceiver struct {
	ctx                  context.Context
	baseLat              float64
	baseLon              float64
	proximityThresholdKM float64
	endpointURL          string
	httpClient           *http.Client
	authToken            string

	mu       sync.Mutex
	notified map[string]bool
}

// Ensure PushNotificationReceiver implements TestPushSender.
var _ TestPushSender = (*PushNotificationReceiver)(nil)

// NewPushNotificationReceiver creates a new PushNotificationReceiver.
func NewPushNotificationReceiver(ctx context.Context, cfg PushReceiverConfig) *PushNotificationReceiver {
	client := cfg.HTTPClient
	if client == nil {
		client = &http.Client{Timeout: 10 * time.Second}
	}

	return &PushNotificationReceiver{
		ctx:                  ctx,
		baseLat:              cfg.BaseLat,
		baseLon:              cfg.BaseLon,
		proximityThresholdKM: cfg.ProximityThresholdKM,
		endpointURL:          cfg.EndpointURL,
		httpClient:           client,
		authToken:            cfg.AuthToken,
		notified:             make(map[string]bool),
	}
}

// Send processes active flight snapshots, checks distance against the proximity threshold,
// deduplicates per flight session, and triggers push notifications.
func (r *PushNotificationReceiver) Send(flights []data.FlightJSON) {
	if r.endpointURL == "" || r.proximityThresholdKM <= 0 {
		return
	}

	r.mu.Lock()
	defer r.mu.Unlock()

	currentActive := make(map[string]bool, len(flights))

	for _, f := range flights {
		currentActive[f.HexIdent] = true

		if !f.HasPosition {
			continue
		}

		distKM := sbs.DistanceKM(r.baseLat, r.baseLon, f.Latitude, f.Longitude)
		if distKM <= 0 || distKM > r.proximityThresholdKM {
			continue
		}

		// If this flight session has already been notified, do not notify again
		if r.notified[f.HexIdent] {
			continue
		}

		// Mark as notified for this flight session
		r.notified[f.HexIdent] = true

		// Build payload and trigger notification dispatch asynchronously
		payload := PushNotificationPayload{
			HexIdent:        f.HexIdent,
			Callsign:        f.Callsign,
			DistanceKM:      distKM,
			Altitude:        f.Altitude,
			GroundSpeed:     f.GroundSpeed,
			Track:           f.Track,
			Latitude:        f.Latitude,
			Longitude:       f.Longitude,
			Manufacturer:    f.Manufacturer,
			Model:           f.Model,
			Registration:    f.Registration,
			ICAOType:        f.ICAOType,
			RegisteredOwner: f.RegisteredOwner,
			Operator:        f.Operator,
			OriginICAO:      f.OriginICAO,
			OriginIATA:      f.OriginIATA,
			OriginName:      f.OriginName,
			OriginCity:      f.OriginCity,
			DestICAO:        f.DestICAO,
			DestIATA:        f.DestIATA,
			DestName:        f.DestName,
			DestCity:        f.DestCity,
		}

		go r.dispatchNotification(payload)
	}

	// Clean up notified state for aircraft that are no longer active
	for hex := range r.notified {
		if !currentActive[hex] {
			delete(r.notified, hex)
		}
	}
}

// sendPayload sends an HTTP POST request with the given payload to the configured endpoint.
func (r *PushNotificationReceiver) sendPayload(ctx context.Context, payload PushNotificationPayload) (int, error) {
	if r.endpointURL == "" {
		return 0, fmt.Errorf("push notification endpoint URL is empty")
	}

	bodyBytes, err := json.Marshal(payload)
	if err != nil {
		return 0, fmt.Errorf("failed to marshal push notification payload: %w", err)
	}

	reqCtx, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()

	req, err := http.NewRequestWithContext(reqCtx, http.MethodPost, r.endpointURL, bytes.NewReader(bodyBytes))
	if err != nil {
		return 0, fmt.Errorf("failed to create push notification HTTP request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	if r.authToken != "" {
		req.Header.Set("Authorization", fmt.Sprintf("Bearer %s", r.authToken))
	}

	resp, err := r.httpClient.Do(req)
	if err != nil {
		return 0, fmt.Errorf("failed to execute push notification request: %w", err)
	}
	defer resp.Body.Close()

	return resp.StatusCode, nil
}

// dispatchNotification sends an HTTP POST request to the Cloud Function endpoint asynchronously.
func (r *PushNotificationReceiver) dispatchNotification(payload PushNotificationPayload) {
	slog.Info("sending proximity push notification request",
		"hex", payload.HexIdent,
		"callsign", payload.Callsign,
		"distance_km", fmt.Sprintf("%.2f", payload.DistanceKM),
		"threshold_km", r.proximityThresholdKM,
	)

	statusCode, err := r.sendPayload(r.ctx, payload)
	if err != nil {
		slog.Error("failed to send push notification request", "hex", payload.HexIdent, "error", err)
		return
	}

	if statusCode < 200 || statusCode >= 300 {
		slog.Warn("push notification endpoint returned non-2xx status",
			"hex", payload.HexIdent,
			"status_code", statusCode,
		)
		return
	}

	slog.Info("push notification dispatched successfully",
		"hex", payload.HexIdent,
		"callsign", payload.Callsign,
		"status_code", statusCode,
	)
}

// DispatchTestNotification sends a test push notification to the configured endpoint immediately.
// If customPayload is nil or contains empty fields, default dummy values are populated.
func (r *PushNotificationReceiver) DispatchTestNotification(ctx context.Context, customPayload *PushNotificationPayload) (int, *PushNotificationPayload, error) {
	var payload PushNotificationPayload
	if customPayload != nil {
		payload = *customPayload
	}

	// Populate defaults if fields are empty
	if payload.HexIdent == "" {
		payload.HexIdent = "TEST01"
	}
	if payload.Callsign == "" {
		payload.Callsign = "TESTFLT"
	}
	if payload.DistanceKM <= 0 {
		payload.DistanceKM = 3.5
	}
	if payload.Altitude == 0 {
		payload.Altitude = 3500
	}
	if payload.GroundSpeed == 0 {
		payload.GroundSpeed = 280
	}
	if payload.Track == 0 {
		payload.Track = 180
	}
	if payload.Latitude == 0 && payload.Longitude == 0 {
		if r.baseLat != 0 && r.baseLon != 0 {
			payload.Latitude = r.baseLat + 0.02
			payload.Longitude = r.baseLon + 0.02
		} else {
			payload.Latitude = 60.1990
			payload.Longitude = 24.9340
		}
	}
	if payload.Manufacturer == "" {
		payload.Manufacturer = "Airbus"
	}
	if payload.Model == "" {
		payload.Model = "A350-900"
	}
	if payload.Registration == "" {
		payload.Registration = "OH-LWA"
	}
	if payload.ICAOType == "" {
		payload.ICAOType = "A359"
	}
	if payload.RegisteredOwner == "" {
		payload.RegisteredOwner = "Test Airlines"
	}
	if payload.Operator == "" {
		payload.Operator = "Test Airlines"
	}
	if payload.OriginICAO == "" && payload.OriginIATA == "" {
		payload.OriginICAO = "EFHK"
		payload.OriginIATA = "HEL"
		payload.OriginName = "Helsinki Airport"
		payload.OriginCity = "Helsinki"
	}
	if payload.DestICAO == "" && payload.DestIATA == "" {
		payload.DestICAO = "RJAA"
		payload.DestIATA = "NRT"
		payload.DestName = "Narita International Airport"
		payload.DestCity = "Tokyo"
	}

	slog.Info("dispatching on-demand test push notification",
		"hex", payload.HexIdent,
		"callsign", payload.Callsign,
		"endpoint", r.endpointURL,
	)

	statusCode, err := r.sendPayload(ctx, payload)
	if err != nil {
		slog.Error("failed to dispatch test push notification", "hex", payload.HexIdent, "error", err)
		return statusCode, &payload, err
	}

	if statusCode < 200 || statusCode >= 300 {
		slog.Warn("test push notification endpoint returned non-2xx status",
			"hex", payload.HexIdent,
			"status_code", statusCode,
		)
	} else {
		slog.Info("test push notification dispatched successfully",
			"hex", payload.HexIdent,
			"callsign", payload.Callsign,
			"status_code", statusCode,
		)
	}

	return statusCode, &payload, nil
}

// IsNotified returns whether a given hex has already been notified in its current flight session.
// Useful for unit testing.
func (r *PushNotificationReceiver) IsNotified(hex string) bool {
	r.mu.Lock()
	defer r.mu.Unlock()
	return r.notified[hex]
}
