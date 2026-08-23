package frontend

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"

	"flights-overhead/data"
)

type mockPushSender struct {
	dispatchFunc func(ctx context.Context, customPayload *data.PushNotificationPayload) (int, *data.PushNotificationPayload, error)
}

func (m *mockPushSender) DispatchTestNotification(ctx context.Context, customPayload *data.PushNotificationPayload) (int, *data.PushNotificationPayload, error) {
	if m.dispatchFunc != nil {
		return m.dispatchFunc(ctx, customPayload)
	}
	return http.StatusOK, customPayload, nil
}

func TestHTTPHandler_Dashboard(t *testing.T) {
	broker := NewSSEBroker()
	handler := NewHTTPHandler(broker, nil)

	req := httptest.NewRequest(http.MethodGet, "/", nil)
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected status 200 for /, got %d", rec.Code)
	}
	if ct := rec.Header().Get("Content-Type"); ct != "text/html" {
		t.Errorf("expected Content-Type text/html, got %s", ct)
	}
}

func TestHTTPHandler_NotFound(t *testing.T) {
	broker := NewSSEBroker()
	handler := NewHTTPHandler(broker, nil)

	req := httptest.NewRequest(http.MethodGet, "/unknown-path", nil)
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusNotFound {
		t.Fatalf("expected status 404 for /unknown-path, got %d", rec.Code)
	}
}

func TestHTTPHandler_TestPush_NilSender(t *testing.T) {
	broker := NewSSEBroker()
	handler := NewHTTPHandler(broker, nil)

	req := httptest.NewRequest(http.MethodPost, "/test-push", nil)
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusServiceUnavailable {
		t.Fatalf("expected status 503 when sender is nil, got %d", rec.Code)
	}

	var resp TestPushResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("failed to unmarshal JSON response: %v", err)
	}
	if resp.Success {
		t.Errorf("expected success to be false")
	}
}

func TestHTTPHandler_TestPush_MethodNotAllowed(t *testing.T) {
	broker := NewSSEBroker()
	mock := &mockPushSender{}
	handler := NewHTTPHandler(broker, mock)

	req := httptest.NewRequest(http.MethodDelete, "/test-push", nil)
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusMethodNotAllowed {
		t.Fatalf("expected status 405 for DELETE /test-push, got %d", rec.Code)
	}
}

func TestHTTPHandler_TestPush_DefaultPayload(t *testing.T) {
	broker := NewSSEBroker()
	var capturedPayload *data.PushNotificationPayload
	mock := &mockPushSender{
		dispatchFunc: func(ctx context.Context, customPayload *data.PushNotificationPayload) (int, *data.PushNotificationPayload, error) {
			capturedPayload = customPayload
			return http.StatusOK, &data.PushNotificationPayload{
				HexIdent: "TEST01",
				Callsign: "TESTFLT",
			}, nil
		},
	}
	handler := NewHTTPHandler(broker, mock)

	// Test POST with empty body
	req := httptest.NewRequest(http.MethodPost, "/test-push", nil)
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected status 200, got %d", rec.Code)
	}
	if capturedPayload != nil {
		t.Errorf("expected capturedPayload to be nil for empty body, got %+v", capturedPayload)
	}

	var resp TestPushResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("failed to unmarshal JSON response: %v", err)
	}
	if !resp.Success {
		t.Errorf("expected success to be true")
	}
	if resp.Payload == nil || resp.Payload.HexIdent != "TEST01" {
		t.Errorf("expected payload with hex TEST01, got %+v", resp.Payload)
	}
}

func TestHTTPHandler_TestPush_CustomPayload(t *testing.T) {
	broker := NewSSEBroker()
	var capturedPayload *data.PushNotificationPayload
	mock := &mockPushSender{
		dispatchFunc: func(ctx context.Context, customPayload *data.PushNotificationPayload) (int, *data.PushNotificationPayload, error) {
			capturedPayload = customPayload
			return http.StatusOK, customPayload, nil
		},
	}
	handler := NewHTTPHandler(broker, mock)

	custom := data.PushNotificationPayload{
		HexIdent:   "4601F6",
		Callsign:   "FIN123",
		DistanceKM: 4.8,
		Altitude:   5000,
	}
	bodyBytes, _ := json.Marshal(custom)

	req := httptest.NewRequest(http.MethodPost, "/api/test-push", bytes.NewReader(bodyBytes))
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected status 200, got %d", rec.Code)
	}
	if capturedPayload == nil || capturedPayload.Callsign != "FIN123" {
		t.Fatalf("expected custom payload to be received by sender, got %+v", capturedPayload)
	}

	var resp TestPushResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("failed to unmarshal JSON response: %v", err)
	}
	if !resp.Success {
		t.Errorf("expected success to be true")
	}
	if resp.Payload == nil || resp.Payload.Callsign != "FIN123" {
		t.Errorf("expected payload callsign FIN123, got %+v", resp.Payload)
	}
}

func TestHTTPHandler_TestPush_InvalidJSON(t *testing.T) {
	broker := NewSSEBroker()
	mock := &mockPushSender{}
	handler := NewHTTPHandler(broker, mock)

	req := httptest.NewRequest(http.MethodPost, "/test-push", bytes.NewReader([]byte("{invalid-json")))
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("expected status 400 for invalid JSON, got %d", rec.Code)
	}

	var resp TestPushResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("failed to unmarshal JSON response: %v", err)
	}
	if resp.Success {
		t.Errorf("expected success to be false")
	}
}

func TestHTTPHandler_TestPush_DispatchError(t *testing.T) {
	broker := NewSSEBroker()
	mock := &mockPushSender{
		dispatchFunc: func(ctx context.Context, customPayload *data.PushNotificationPayload) (int, *data.PushNotificationPayload, error) {
			return 500, nil, errors.New("connection refused")
		},
	}
	handler := NewHTTPHandler(broker, mock)

	req := httptest.NewRequest(http.MethodPost, "/test-push", nil)
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusInternalServerError {
		t.Fatalf("expected status 500 when sender errors, got %d", rec.Code)
	}

	var resp TestPushResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("failed to unmarshal JSON response: %v", err)
	}
	if resp.Success {
		t.Errorf("expected success to be false")
	}
	if resp.Error != "connection refused" {
		t.Errorf("expected error message 'connection refused', got '%s'", resp.Error)
	}
}
