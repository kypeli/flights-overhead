package broadcast

import (
	"context"
	"flights-overhead/data"
	"flights-overhead/pkg/sbs"
	"log/slog"
	"reflect"

	"cloud.google.com/go/firestore"
)

// FirestoreFlightsReceiver implements broadcast.FlightsReceiver to push active flight snapshots to Firestore.
type FirestoreFlightsReceiver struct {
	client   *firestore.Client
	ctx      context.Context
	lastSeen map[string]bool
	prevData map[string]map[string]interface{}
}

// NewFirestoreFlightsReceiver creates a new FirestoreFlightsReceiver.
func NewFirestoreFlightsReceiver(ctx context.Context, client *firestore.Client) *FirestoreFlightsReceiver {
	return &FirestoreFlightsReceiver{
		client:   client,
		ctx:      ctx,
		lastSeen: make(map[string]bool),
		prevData: make(map[string]map[string]interface{}),
	}
}

// Send updates active flights in Firestore and deletes stale ones.
func (r *FirestoreFlightsReceiver) Send(flights []data.FlightJSON) {
	currentActive := make(map[string]bool)
	for _, f := range flights {
		currentActive[f.Aircraft.HexIdent] = true
	}

	// If there are no active flights, delete all documents in the collection
	if len(currentActive) == 0 {
		docsIter := r.client.Collection("active_flights").Documents(r.ctx)
		docs, err := docsIter.GetAll()
		if err != nil {
			slog.Error("failed to list Firestore documents for cleanup", "error", err)
		} else {
			for _, doc := range docs {
				_, delErr := doc.Ref.Delete(r.ctx)
				if delErr != nil {
					slog.Error("failed to delete stale Firestore document", "doc", doc.Ref.ID, "error", delErr)
				}
			}
		}
		// Reset tracking maps
		r.lastSeen = make(map[string]bool)
		r.prevData = make(map[string]map[string]interface{})
		return
	}

	// 1. Delete documents from Firestore for flights that are no longer active
	for hex := range r.lastSeen {
		if !currentActive[hex] {
			_, err := r.client.Collection("active_flights").Doc(hex).Delete(r.ctx)
			if err != nil {
				slog.Error("failed to delete inactive flight from Firestore", "hex", hex, "error", err)
			} else {
				slog.Info("deleted inactive flight from Firestore", "hex", hex)
			}
		}
	}

	// 2. Upload/Update active flights
	for _, f := range flights {
		docRef := r.client.Collection("active_flights").Doc(f.Aircraft.HexIdent)

		var direction string
		if f.Aircraft.HasTrack {
			direction = sbs.TrackToDirection(f.Aircraft.Track)
		}

		// Create a map representation of the flight data to upload
		dataMap := map[string]interface{}{
			"hex":             f.Aircraft.HexIdent,
			"callsign":        f.Aircraft.Callsign,
			"latitude":        f.Aircraft.Latitude,
			"longitude":       f.Aircraft.Longitude,
			"altitude":        f.Aircraft.Altitude,
			"speed":           f.Aircraft.GroundSpeed,
			"track":           f.Aircraft.Track,
			"direction":       direction,
			"hasPosition":     f.Aircraft.HasPosition,
			"hasTrack":        f.Aircraft.HasTrack,
			"verticalRate":    f.Aircraft.VerticalRate,
			"squawk":          f.Aircraft.Squawk,
			"isOnGround":      f.Aircraft.IsOnGround,
			"manufacturer":    f.Aircraft.Manufacturer,
			"model":           f.Aircraft.Model,
			"registration":    f.Aircraft.Registration,
			"icaoType":        f.Aircraft.ICAOType,
			"registeredOwner": f.Aircraft.RegisteredOwner,
			"operator":        f.Aircraft.Operator,
			"originICAO":      f.Aircraft.OriginICAO,
			"originIATA":      f.Aircraft.OriginIATA,
			"originName":      f.Aircraft.OriginName,
			"originCity":      f.Aircraft.OriginCity,
			"destICAO":        f.Aircraft.DestICAO,
			"destIATA":        f.Aircraft.DestIATA,
			"destName":        f.Aircraft.DestName,
			"destCity":        f.Aircraft.DestCity,
			"lastSeen":        f.Aircraft.LastSeen,
		}

		// Only write if data has changed
		if prev, ok := r.prevData[f.Aircraft.HexIdent]; ok && reflect.DeepEqual(prev, dataMap) {
			// No changes, skip Firestore update
			continue
		}
		_, err := docRef.Set(r.ctx, dataMap)
		if err != nil {
			slog.Error("failed to write active flight to Firestore", "hex", f.Aircraft.HexIdent, "error", err)
		} else {
			// Store current snapshot for future comparison
			r.prevData[f.Aircraft.HexIdent] = dataMap
		}
	}

	// Clean up prevData for flights no longer active
	for hex := range r.lastSeen {
		if !currentActive[hex] {
			delete(r.prevData, hex)
		}
	}
	r.lastSeen = currentActive
}
