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

// dispatchNotification sends an HTTP POST request to the Cloud Function endpoint.
func (r *PushNotificationReceiver) dispatchNotification(payload PushNotificationPayload) {
	bodyBytes, err := json.Marshal(payload)
	if err != nil {
		slog.Error("failed to marshal push notification payload", "hex", payload.HexIdent, "error", err)
		return
	}

	reqCtx, cancel := context.WithTimeout(r.ctx, 10*time.Second)
	defer cancel()

	req, err := http.NewRequestWithContext(reqCtx, http.MethodPost, r.endpointURL, bytes.NewReader(bodyBytes))
	if err != nil {
		slog.Error("failed to create push notification HTTP request", "hex", payload.HexIdent, "error", err)
		return
	}

	req.Header.Set("Content-Type", "application/json")
	if r.authToken != "" {
		req.Header.Set("Authorization", fmt.Sprintf("Bearer %s", r.authToken))
	}

	slog.Info("sending proximity push notification request",
		"hex", payload.HexIdent,
		"callsign", payload.Callsign,
		"distance_km", fmt.Sprintf("%.2f", payload.DistanceKM),
		"threshold_km", r.proximityThresholdKM,
	)

	resp, err := r.httpClient.Do(req)
	if err != nil {
		slog.Error("failed to send push notification request", "hex", payload.HexIdent, "error", err)
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		slog.Warn("push notification endpoint returned non-2xx status",
			"hex", payload.HexIdent,
			"status", resp.Status,
		)
		return
	}

	slog.Info("push notification dispatched successfully",
		"hex", payload.HexIdent,
		"callsign", payload.Callsign,
	)
}

// IsNotified returns whether a given hex has already been notified in its current flight session.
// Useful for unit testing.
func (r *PushNotificationReceiver) IsNotified(hex string) bool {
	r.mu.Lock()
	defer r.mu.Unlock()
	return r.notified[hex]
}
