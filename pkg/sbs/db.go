// db.go embeds and parses a gzip-compressed aircraft database CSV (sourced from
// tar1090-db) and exposes a Lookup function that maps a 24-bit ICAO hex address to
// registration, type code, operator, description, and raw CSV fields.
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

// aircraftDB maps uppercase ICAO hex → raw CSV value (everything after the hex field)
var aircraftDB map[string]string

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

	aircraftDB = make(map[string]string)
	for _, rawLine := range strings.Split(string(data), "\n") {
		// Handle \r\n line endings
		line := strings.TrimRight(rawLine, "\r")
		if line == "" {
			continue
		}
		sep := strings.IndexByte(line, ';')
		if sep == -1 {
			continue
		}
		key := strings.ToUpper(line[:sep])
		aircraftDB[key] = line[sep+1:]
	}
}

// Lookup searches the embedded aircraft database for a given ICAO Hex ID.
// If found, it returns the registration, aircraft type code, operator, full description,
// the raw DB entry string, and a found flag.
func Lookup(hex string) (reg string, typeCode string, operator string, desc string, raw string, found bool) {
	hex = strings.ToUpper(strings.TrimSpace(hex))
	if len(hex) == 0 || aircraftDB == nil {
		return "", "", "", "", "", false
	}
	raw, found = aircraftDB[hex]
	if !found {
		return "", "", "", "", "", false
	}
	fields := strings.SplitN(raw, ";", 4)
	if len(fields) > 0 {
		reg = fields[0]
	}
	if len(fields) > 1 {
		typeCode = fields[1]
	}
	if len(fields) > 2 {
		operator = fields[2]
	}
	if len(fields) > 3 {
		desc = fields[3]
	}
	return reg, typeCode, operator, desc, raw, true
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
