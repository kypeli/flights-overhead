package broadcast

import (
	"context"
	"flights-overhead/data"
	"log/slog"
	"reflect"

	"cloud.google.com/go/firestore"
)

// FirestoreFlightsReceiver implements broadcast.FlightsReceiver to push active flight snapshots to Firestore.
type FirestoreFlightsReceiver struct {
	client   *firestore.Client
	ctx      context.Context
	ch       chan []data.FlightJSON
	lastSeen map[string]bool
	prevData map[string]map[string]interface{}
}

// NewFirestoreFlightsReceiver creates a new FirestoreFlightsReceiver and starts its background worker.
func NewFirestoreFlightsReceiver(ctx context.Context, client *firestore.Client) *FirestoreFlightsReceiver {
	r := &FirestoreFlightsReceiver{
		client:   client,
		ctx:      ctx,
		ch:       make(chan []data.FlightJSON, 1),
		lastSeen: make(map[string]bool),
		prevData: make(map[string]map[string]interface{}),
	}
	go r.worker()
	return r
}

// Send enqueues a flight snapshot for the background worker to write to Firestore.
// It is non-blocking: if the worker is still processing the previous snapshot, the
// stale entry is replaced so the worker always processes the most recent state.
func (r *FirestoreFlightsReceiver) Send(flights []data.FlightJSON) {
	// Drain any pending snapshot that hasn't been processed yet.
	select {
	case <-r.ch:
	default:
	}
	r.ch <- flights
}

// worker runs in its own goroutine and processes Firestore writes sequentially.
func (r *FirestoreFlightsReceiver) worker() {
	for {
		select {
		case <-r.ctx.Done():
			return
		case flights := <-r.ch:
			r.process(flights)
		}
	}
}

// process writes the flight snapshot to Firestore, deleting stale documents and
// updating changed ones. It is only ever called from the worker goroutine.
func (r *FirestoreFlightsReceiver) process(flights []data.FlightJSON) {
	currentActive := make(map[string]bool)
	for _, f := range flights {
		currentActive[f.HexIdent] = true
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
		r.prevData = make(map[string]map[string]any)
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
		docRef := r.client.Collection("active_flights").Doc(f.HexIdent)

		// Create a map representation of the flight data to upload
		dataMap := map[string]any{
			"hex":             f.HexIdent,
			"callsign":        f.Callsign,
			"latitude":        f.Latitude,
			"longitude":       f.Longitude,
			"altitude":        f.Altitude,
			"manufacturer":    f.Manufacturer,
			"model":           f.Model,
			"registration":    f.Registration,
			"icaoType":        f.ICAOType,
			"registeredOwner": f.RegisteredOwner,
			"operator":        f.Operator,
			"originICAO":      f.OriginICAO,
			"originIATA":      f.OriginIATA,
			"originName":      f.OriginName,
			"originCity":      f.OriginCity,
			"destICAO":        f.DestICAO,
			"destIATA":        f.DestIATA,
			"destName":        f.DestName,
			"destCity":        f.DestCity,
		}

		// Only write if data has changed
		if prev, ok := r.prevData[f.HexIdent]; ok && reflect.DeepEqual(prev, dataMap) {
			continue
		}
		_, err := docRef.Set(r.ctx, dataMap)
		if err != nil {
			slog.Error("failed to write active flight to Firestore", "hex", f.HexIdent, "error", err)
		} else {
			r.prevData[f.HexIdent] = dataMap
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
