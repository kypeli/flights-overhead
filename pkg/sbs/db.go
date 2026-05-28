package sbs

import (
	"bytes"
	"compress/gzip"
	_ "embed"
	"io"
	"log"
	"strings"
)

//go:embed aircraft_db.csv.gz
var aircraftDBDataGzipped []byte

var aircraftDBData []byte

func init() {
	// Decompress the database at startup
	if len(aircraftDBDataGzipped) == 0 {
		return
	}
	reader, err := gzip.NewReader(bytes.NewReader(aircraftDBDataGzipped))
	if err != nil {
		log.Fatalf("failed to initialize embedded aircraft database: %v", err)
	}
	defer reader.Close()

	data, err := io.ReadAll(reader)
	if err != nil {
		log.Fatalf("failed to read decompressed aircraft database: %v", err)
	}
	aircraftDBData = data
}

// Lookup searches the embedded aircraft database for a given ICAO Hex ID.
// If found, it returns the registration, aircraft type code, full description, and a found flag.
func Lookup(hex string) (reg string, typeCode string, desc string, found bool) {
	hex = strings.ToUpper(strings.TrimSpace(hex))
	if len(hex) == 0 || len(aircraftDBData) == 0 {
		return "", "", "", false
	}

	low := 0
	high := len(aircraftDBData)

	for low < high {
		mid := low + (high-low)/2

		// Backtrack to the start of the current line
		start := mid
		for start > 0 && aircraftDBData[start-1] != '\n' {
			start--
		}

		// Find the end of the current line
		end := start
		for end < len(aircraftDBData) && aircraftDBData[end] != '\n' {
			end++
		}

		line := aircraftDBData[start:end]
		if len(line) == 0 {
			break
		}

		// Read the hex code at the start of the line (up to the first ';')
		semiIdx := -1
		for i, b := range line {
			if b == ';' {
				semiIdx = i
				break
			}
		}

		if semiIdx == -1 {
			// Malformed line
			break
		}

		currHex := string(line[:semiIdx])

		if currHex == hex {
			// Found it! Parse the fields.
			// Format is: hex;registration;typecode;description
			fields := strings.Split(string(line), ";")
			if len(fields) >= 4 {
				return fields[1], fields[2], fields[3], true
			}
			return "", "", "", false
		}

		// Since ICAO Hex addresses are alphabetically sorted in the database:
		if currHex < hex {
			low = end + 1
		} else {
			high = start
		}
	}

	return "", "", "", false
}

// ParseManufacturerAndModel extracts a user-friendly manufacturer name and model
// from the raw description string.
func ParseManufacturerAndModel(desc string, typeCode string) (string, string) {
	desc = strings.TrimSpace(desc)
	if desc == "" {
		if typeCode != "" {
			return "", typeCode
		}
		return "", ""
	}

	// Find the first space to separate manufacturer from model
	spaceIdx := strings.Index(desc, " ")
	if spaceIdx == -1 {
		// Just one word, treat it as the model
		return "", toTitle(desc)
	}

	mfg := strings.TrimSpace(desc[:spaceIdx])
	model := strings.TrimSpace(desc[spaceIdx+1:])

	// Title case the manufacturer (e.g. BOEING -> Boeing)
	mfg = toTitle(mfg)

	return mfg, model
}

// toTitle capitalizes only the first letter of a word (e.g. "BOEING" -> "Boeing").
func toTitle(s string) string {
	if s == "" {
		return ""
	}
	r := []rune(strings.ToLower(s))
	if len(r) > 0 {
		// Safely capitalize the first character
		r[0] = []rune(strings.ToUpper(string(r[0])))[0]
	}
	return string(r)
}
