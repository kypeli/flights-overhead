package sbs

import (
	"math"
)

// DistanceNM calculates the distance between two coordinates in Nautical Miles using the Haversine formula.
func DistanceNM(lat1, lon1, lat2, lon2 float64) float64 {
	if lat1 == 0 && lon1 == 0 || lat2 == 0 && lon2 == 0 {
		return 0
	}
	
	// Earth radius in nautical miles
	const earthRadiusNM = 3440.065

	// Convert to radians
	radLat1 := lat1 * math.Pi / 180
	radLon1 := lon1 * math.Pi / 180
	radLat2 := lat2 * math.Pi / 180
	radLon2 := lon2 * math.Pi / 180

	dLat := radLat2 - radLat1
	dLon := radLon2 - radLon1

	a := math.Sin(dLat/2)*math.Sin(dLat/2) +
		math.Cos(radLat1)*math.Cos(radLat2)*
			math.Sin(dLon/2)*math.Sin(dLon/2)

	c := 2 * math.Atan2(math.Sqrt(a), math.Sqrt(1-a))

	return earthRadiusNM * c
}

// TrackToDirection converts a track angle in degrees to a cardinal/ordinal direction string (e.g. N, NE, E, etc.).
func TrackToDirection(track float64) string {
	// Normalize to [0, 360)
	t := math.Mod(track, 360)
	if t < 0 {
		t += 360
	}

	// 8 cardinal directions
	directions := []string{"N", "NE", "E", "SE", "S", "SW", "W", "NW"}
	index := int(math.Round(t/45)) % 8
	return directions[index]
}
