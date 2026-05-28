package sbs

import (
	"bytes"
	"compress/gzip"
	_ "embed"
	"io"
	"log"
	"strings"
	"unicode"
)

//go:embed aircraft_db.csv.gz
var aircraftDBDataGzipped []byte

// aircraftDB maps uppercase ICAO hex → [reg, typeCode, desc]
var aircraftDB map[string][3]string

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

	aircraftDB = make(map[string][3]string)
	for _, rawLine := range strings.Split(string(data), "\n") {
		// Handle \r\n line endings
		line := strings.TrimRight(rawLine, "\r")
		if line == "" {
			continue
		}
		fields := strings.Split(line, ";")
		if len(fields) < 4 {
			continue
		}
		key := strings.ToUpper(fields[0])
		aircraftDB[key] = [3]string{fields[1], fields[2], fields[3]}
	}
}

// Lookup searches the embedded aircraft database for a given ICAO Hex ID.
// If found, it returns the registration, aircraft type code, full description, and a found flag.
func Lookup(hex string) (reg string, typeCode string, desc string, found bool) {
	hex = strings.ToUpper(strings.TrimSpace(hex))
	if len(hex) == 0 || aircraftDB == nil {
		return "", "", "", false
	}
	entry, ok := aircraftDB[hex]
	if !ok {
		return "", "", "", false
	}
	return entry[0], entry[1], entry[2], true
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
	runes := []rune(strings.ToLower(s))
	runes[0] = unicode.ToUpper(runes[0])
	return string(runes)
}
