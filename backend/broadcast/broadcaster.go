package broadcast

import (
	"flights-overhead/data"
	"flights-overhead/pkg/sbs"
)

type FlightsReceiver interface {
	Send(flights []data.FlightJSON)
}

func Broadcast(flightsReceiver FlightsReceiver, tracker *sbs.Tracker) {
	active := tracker.GetAllActive()
	flights := make([]data.FlightJSON, 0, len(active))
	for _, ac := range active {
		flights = append(flights, data.FlightJSON{
			Aircraft: ac,
		})
	}
	flightsReceiver.Send(flights)
}
