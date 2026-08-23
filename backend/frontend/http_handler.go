package frontend

import (
	_ "embed"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"

	"flights-overhead/data"
)

//go:embed dashboard.html
var dashboardHTML []byte

// TestPushSender defines the interface for triggering an on-demand test push notification.
type TestPushSender interface {
	DispatchTestNotification(ctx context.Context, customPayload *data.PushNotificationPayload) (int, *data.PushNotificationPayload, error)
}

// TestPushResponse represents the JSON response structure for the test push endpoint.
type TestPushResponse struct {
	Success bool                         `json:"success"`
	Status  int                          `json:"status,omitempty"`
	Message string                       `json:"message,omitempty"`
	Error   string                       `json:"error,omitempty"`
	Payload *data.PushNotificationPayload `json:"payload,omitempty"`
}

// NewHTTPHandler creates an explicit ServeMux with routes registered for the dashboard, SSE broker,
// and test push notification endpoint.
func NewHTTPHandler(broker *SSEBroker, pushSender TestPushSender) http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/" {
			http.NotFound(w, r)
			return
		}
		w.Header().Set("Content-Type", "text/html")
		w.Write(dashboardHTML)
	})
	mux.Handle("/events", broker)

	testPushHandler := func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost && r.Method != http.MethodGet {
			http.Error(w, "Method Not Allowed", http.StatusMethodNotAllowed)
			return
		}

		w.Header().Set("Content-Type", "application/json")

		if pushSender == nil {
			w.WriteHeader(http.StatusServiceUnavailable)
			_ = json.NewEncoder(w).Encode(TestPushResponse{
				Success: false,
				Error:   "push notification sender is not configured",
			})
			return
		}

		var customPayload *data.PushNotificationPayload
		if r.Method == http.MethodPost && r.Body != nil {
			bodyBytes, err := io.ReadAll(http.MaxBytesReader(w, r.Body, 65536))
			if err != nil {
				w.WriteHeader(http.StatusBadRequest)
				_ = json.NewEncoder(w).Encode(TestPushResponse{
					Success: false,
					Error:   fmt.Sprintf("failed to read request body: %v", err),
				})
				return
			}

			if len(bodyBytes) > 0 {
				var p data.PushNotificationPayload
				if err := json.Unmarshal(bodyBytes, &p); err != nil {
					w.WriteHeader(http.StatusBadRequest)
					_ = json.NewEncoder(w).Encode(TestPushResponse{
						Success: false,
						Error:   fmt.Sprintf("invalid JSON payload: %v", err),
					})
					return
				}
				customPayload = &p
			}
		}

		statusCode, dispatchedPayload, err := pushSender.DispatchTestNotification(r.Context(), customPayload)
		if err != nil {
			respStatus := http.StatusBadGateway
			if statusCode >= 400 {
				respStatus = statusCode
			}
			w.WriteHeader(respStatus)
			_ = json.NewEncoder(w).Encode(TestPushResponse{
				Success: false,
				Status:  statusCode,
				Error:   err.Error(),
				Payload: dispatchedPayload,
			})
			return
		}

		if statusCode < 200 || statusCode >= 300 {
			w.WriteHeader(http.StatusBadGateway)
			_ = json.NewEncoder(w).Encode(TestPushResponse{
				Success: false,
				Status:  statusCode,
				Message: fmt.Sprintf("push endpoint returned HTTP status %d", statusCode),
				Payload: dispatchedPayload,
			})
			return
		}

		w.WriteHeader(http.StatusOK)
		_ = json.NewEncoder(w).Encode(TestPushResponse{
			Success: true,
			Status:  statusCode,
			Message: "Test push notification dispatched successfully",
			Payload: dispatchedPayload,
		})
	}

	mux.HandleFunc("/test-push", testPushHandler)
	mux.HandleFunc("/api/test-push", testPushHandler)

	return mux
}

