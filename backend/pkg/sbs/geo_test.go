// geo_test.go contains tests for the geographic helper functions in geo.go.
package sbs

import (
	"math"
	"testing"
)

func TestTrackToDirection(t *testing.T) {
	tests := []struct {
		track    float64
		expected string
	}{
		{0, "N"},
		{22, "N"},
		{23, "NE"},
		{45, "NE"},
		{67, "NE"},
		{68, "E"},
		{90, "E"},
		{135, "SE"},
		{180, "S"},
		{225, "SW"},
		{270, "W"},
		{315, "NW"},
		{337, "NW"},
		{338, "N"},
		{360, "N"},
		{720, "N"},
		{-45, "NW"},
	}

	for _, tc := range tests {
		got := TrackToDirection(tc.track)
		if got != tc.expected {
			t.Errorf("TrackToDirection(%f) = %q; expected %q", tc.track, got, tc.expected)
		}
	}
}

func TestDistanceNM(t *testing.T) {
	// Helsinki coordinates: 60.1699, 24.9384
	// Vantaa Airport coordinates: 60.3172, 24.9633
	// Direct distance is approx 8.8 Nautical Miles (16.4 km)
	dist := DistanceNM(60.1699, 24.9384, 60.3172, 24.9633)
	if math.Abs(dist-8.8) > 0.5 {
		t.Errorf("DistanceNM(Helsinki, Vantaa) = %f; expected approx 8.8", dist)
	}

	// Distance with zero coordinates should return 0
	if DistanceNM(0, 0, 60.1699, 24.9384) != 0 {
		t.Errorf("Expected 0 distance when one coordinate is 0,0")
	}
}

func TestDistanceKM(t *testing.T) {
	// Helsinki coordinates: 60.1699, 24.9384
	// Vantaa Airport coordinates: 60.3172, 24.9633
	// Direct distance is approx 16.4 km
	dist := DistanceKM(60.1699, 24.9384, 60.3172, 24.9633)
	if math.Abs(dist-16.4) > 0.5 {
		t.Errorf("DistanceKM(Helsinki, Vantaa) = %f; expected approx 16.4", dist)
	}

	// Distance with zero coordinates should return 0
	if DistanceKM(0, 0, 60.1699, 24.9384) != 0 {
		t.Errorf("Expected 0 distance when one coordinate is 0,0")
	}
}

