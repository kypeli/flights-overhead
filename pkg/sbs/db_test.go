// db_test.go contains tests for the aircraft database Lookup function in db.go.
package sbs

import (
	"testing"
)

func TestLookup(t *testing.T) {
	// Test lookup of a known hex address (004002, which is Boeing 737-200)
	hex := "004002"
	reg, typeCode, _, desc, raw, found := Lookup(hex)
	if !found {
		t.Fatalf("Expected to find hex %s, but got found = false", hex)
	}

	if reg != "Z-WPA" {
		t.Errorf("Expected registration Z-WPA, got %s", reg)
	}

	if typeCode != "B732" {
		t.Errorf("Expected typeCode B732, got %s", typeCode)
	}

	if desc != "BOEING 737-200" {
		t.Errorf("Expected desc BOEING 737-200, got %s", desc)
	}

	if raw == "" {
		t.Errorf("Expected non-empty raw entry for hex %s", hex)
	}

	// Test case-insensitivity and spacing trim
	_, _, _, _, _, foundUpper := Lookup(" 004002 ")
	_, _, _, _, _, foundLower := Lookup(" 004002 ")
	if !foundUpper || !foundLower {
		t.Errorf("Expected case-insensitive and trimmed Lookup to succeed")
	}

	// Test non-existent hex
	_, _, _, _, _, foundMissing := Lookup("FFFFFF")
	if foundMissing {
		t.Errorf("Expected Lookup of FFFFFF to fail (found = false)")
	}
}

func TestParseManufacturerAndModel(t *testing.T) {
	mfg, model := ParseManufacturerAndModel("BOEING 737-200", "B732")
	if mfg != "Boeing" || model != "737-200" {
		t.Errorf("Expected Boeing and 737-200, got '%s' and '%s'", mfg, model)
	}

	mfg, model = ParseManufacturerAndModel("AIRBUS A320-200", "A320")
	if mfg != "Airbus" || model != "A320-200" {
		t.Errorf("Expected Airbus and A320-200, got '%s' and '%s'", mfg, model)
	}

	mfg, model = ParseManufacturerAndModel("", "B732")
	if mfg != "" || model != "B732" {
		t.Errorf("Expected empty mfg and 'B732' model, got '%s' and '%s'", mfg, model)
	}
}
