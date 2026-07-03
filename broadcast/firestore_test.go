package broadcast

import (
	"context"
	"os"
	"testing"
	"time"

	"flights-overhead/data"
	"flights-overhead/pkg/sbs"

	"cloud.google.com/go/firestore"
)

func TestFirestoreFlightsReceiver_StateTracking(t *testing.T) {
	// Use the Firestore Emulator environment variable to prevent it from making real network calls to GCP.
	os.Setenv("FIRESTORE_EMULATOR_HOST", "localhost:8989")
	defer os.Unsetenv("FIRESTORE_EMULATOR_HOST")

	ctx := context.Background()
	// Initialize client using a dummy project
	client, err := firestore.NewClient(ctx, "test-project")
	if err != nil {
		t.Fatalf("failed to create firestore client: %v", err)
	}
	defer client.Close()

	// Use a context with a generous timeout. With FIRESTORE_EMULATOR_HOST pointing at a
	// non-running server, all Firestore calls fail immediately with "connection refused",
	// so the worker completes each batch in milliseconds.
	recvCtx, cancel := context.WithTimeout(ctx, 50*time.Millisecond)
	defer cancel()

	receiver := NewFirestoreFlightsReceiver(recvCtx, client)

	// Step 1: Send two active flights
	flights1 := []data.FlightJSON{
		{
			Aircraft: sbs.Aircraft{
				HexIdent:    "4601F6",
				Callsign:    "FIN123",
				Latitude:    60.1,
				Longitude:   24.9,
				Altitude:    10000,
				LastSeen:    time.Now(),
				HasPosition: true,
			},
		},
		{
			Aircraft: sbs.Aircraft{
				HexIdent:    "300001",
				Callsign:    "SAS456",
				Latitude:    59.9,
				Longitude:   20.1,
				Altitude:    35000,
				LastSeen:    time.Now(),
				HasPosition: true,
			},
		},
	}

	// Call process() directly — it's the internal method under test.
	// Send() is the async dispatcher; calling process() synchronously avoids
	// relying on timing of the background worker goroutine.
	receiver.process(flights1)

	// Verify both are added to lastSeen
	if !receiver.lastSeen["4601F6"] || !receiver.lastSeen["300001"] {
		t.Errorf("expected both flights to be tracked in lastSeen, got: %v", receiver.lastSeen)
	}
	if len(receiver.lastSeen) != 2 {
		t.Errorf("expected lastSeen to have length 2, got: %d", len(receiver.lastSeen))
	}

	// Step 2: Send flights where one has disappeared (SAS456 is gone, only FIN123 is present)
	flights2 := []data.FlightJSON{
		{
			Aircraft: sbs.Aircraft{
				HexIdent:    "4601F6",
				Callsign:    "FIN123",
				Latitude:    60.2,
				Longitude:   25.0,
				Altitude:    11000,
				LastSeen:    time.Now(),
				HasPosition: true,
			},
		},
	}

	receiver.process(flights2)

	// Verify only FIN123 remains in lastSeen
	if !receiver.lastSeen["4601F6"] {
		t.Errorf("expected FIN123 to still be tracked")
	}
	if receiver.lastSeen["300001"] {
		t.Errorf("expected SAS456 to be evicted from lastSeen")
	}
	if len(receiver.lastSeen) != 1 {
		t.Errorf("expected lastSeen to have length 1, got: %d", len(receiver.lastSeen))
	}
}
