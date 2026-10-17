package broadcast

import (
	"context"
	"flights-overhead/data"
	"flights-overhead/pkg/sbs"
	"log/slog"
	"math"
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
	baseLat  float64
	baseLon  float64
}

// NewFirestoreFlightsReceiver creates a new FirestoreFlightsReceiver and starts its background worker.
func NewFirestoreFlightsReceiver(ctx context.Context, client *firestore.Client, baseCoords ...float64) *FirestoreFlightsReceiver {
	var baseLat, baseLon float64
	if len(baseCoords) >= 2 {
		baseLat = baseCoords[0]
		baseLon = baseCoords[1]
	}
	r := &FirestoreFlightsReceiver{
		client:   client,
		ctx:      ctx,
		ch:       make(chan []data.FlightJSON, 1),
		lastSeen: make(map[string]bool),
		prevData: make(map[string]map[string]interface{}),
		baseLat:  baseLat,
		baseLon:  baseLon,
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

// quantizeDistanceKM rounds distance in kilometers to 0.1 km precision so sub-100m
// floating-point coordinate jitter does not trigger redundant Firestore writes every second.
func quantizeDistanceKM(distanceKm float64) float64 {
	return math.Round(distanceKm*10) / 10
}

// process writes the flight snapshot to Firestore using a batched write, deleting stale
// documents and updating changed ones. It is only ever called from the worker goroutine.
func (r *FirestoreFlightsReceiver) process(flights []data.FlightJSON) {
	currentActive := make(map[string]bool, len(flights))
	for _, f := range flights {
		currentActive[f.HexIdent] = true
	}

	// If there are no active flights, delete all documents in the collection in a batch
	if len(currentActive) == 0 {
		docsIter := r.client.Collection("active_flights").Documents(r.ctx)
		docs, err := docsIter.GetAll()
		if err != nil {
			slog.Error("failed to list Firestore documents for cleanup", "error", err)
		} else if len(docs) > 0 {
			batch := r.client.Batch()
			for _, doc := range docs {
				batch.Delete(doc.Ref)
			}
			if _, delErr := batch.Commit(r.ctx); delErr != nil {
				slog.Error("failed to batch delete stale Firestore documents", "error", delErr)
			}
		}
		// Reset tracking maps
		r.lastSeen = make(map[string]bool)
		r.prevData = make(map[string]map[string]any)
		return
	}

	batch := r.client.Batch()
	batchOps := 0
	var deletedHexes []string
	stagedData := make(map[string]map[string]any, len(flights))

	// 1. Stage deletes from Firestore for flights that are no longer active
	for hex := range r.lastSeen {
		if !currentActive[hex] {
			batch.Delete(r.client.Collection("active_flights").Doc(hex))
			batchOps++
			deletedHexes = append(deletedHexes, hex)
		}
	}

	// 2. Stage uploads/updates for active flights whose telemetry changed
	for _, f := range flights {
		docRef := r.client.Collection("active_flights").Doc(f.HexIdent)

		var distanceKm float64
		if f.HasPosition && (r.baseLat != 0 || r.baseLon != 0) {
			distanceKm = quantizeDistanceKM(sbs.DistanceKM(r.baseLat, r.baseLon, f.Latitude, f.Longitude))
		}

		// Create a map representation of the flight data to upload
		dataMap := map[string]any{
			"hex":             f.HexIdent,
			"callsign":        f.Callsign,
			"latitude":        f.Latitude,
			"longitude":       f.Longitude,
			"altitude":        f.Altitude,
			"verticalRate":    f.VerticalRate,
			"distanceKm":      distanceKm,
			"distance":        distanceKm,
			"squawk":          f.Squawk,
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
		batch.Set(docRef, dataMap)
		batchOps++
		stagedData[f.HexIdent] = dataMap
	}

	if batchOps > 0 {
		if _, err := batch.Commit(r.ctx); err != nil {
			slog.Error("failed to commit active flights batch to Firestore", "ops", batchOps, "error", err)
		} else {
			for _, hex := range deletedHexes {
				slog.Info("deleted inactive flight from Firestore", "hex", hex)
			}
			for hex, dataMap := range stagedData {
				r.prevData[hex] = dataMap
			}
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

