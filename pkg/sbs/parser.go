package sbs

import (
	"errors"
	"fmt"
	"strconv"
	"strings"
	"time"
)

var (
	ErrLineTooShort = errors.New("line contains too few fields to be a valid SBS-1 message")
)

// parseOpt is a generic helper that converts a non-empty string using conv, returning nil on empty or error.
func parseOpt[T any](s string, conv func(string) (T, error)) *T {
	if s == "" {
		return nil
	}
	v, err := conv(s)
	if err != nil {
		return nil
	}
	return &v
}

// parseBool parses binary/boolean flags (usually 0, 1, -1, or empty).
func parseBool(s string) (*bool, error) {
	if s == "" {
		return nil, nil
	}
	var b bool
	if s == "1" || strings.ToLower(s) == "true" {
		b = true
	} else if s == "0" || strings.ToLower(s) == "false" {
		b = false
	} else {
		return nil, fmt.Errorf("invalid bool value: %s", s)
	}
	return &b, nil
}

// ParseMessage parses a raw comma-separated SBS-1 line into a structured Message.
func ParseMessage(line string) (*Message, error) {
	// Clean up line endings
	line = strings.TrimSpace(line)
	if line == "" {
		return nil, errors.New("empty line")
	}

	fields := strings.Split(line, ",")
	if len(fields) < 10 {
		return nil, fmt.Errorf("%w (found %d fields)", ErrLineTooShort, len(fields))
	}

	// Pad fields to 22 to avoid out-of-bounds panic if the receiver didn't output all trailing columns
	if len(fields) < 22 {
		padded := make([]string, 22)
		copy(padded, fields)
		fields = padded
	}

	// Trim spaces from all fields
	for i := range fields {
		fields[i] = strings.TrimSpace(fields[i])
	}

	msgType := MessageType(fields[0])

	msg := &Message{
		MessageType: msgType,
		HexIdent:    fields[4],
	}

	// 1. Parse TransmissionType (Field 1 - optional, only for MSG)
	msg.TransmissionType = parseOpt(fields[1], func(s string) (TransmissionType, error) {
		tt, err := strconv.Atoi(s)
		if err != nil {
			return 0, err
		}
		return TransmissionType(tt), nil
	})

	// 2. Parse SessionID (Field 2)
	if fields[2] != "" {
		val, _ := strconv.ParseInt(fields[2], 10, 64)
		msg.SessionID = val
	}

	// 3. Parse AircraftID (Field 3)
	if fields[3] != "" {
		val, _ := strconv.ParseInt(fields[3], 10, 64)
		msg.AircraftID = val
	}

	// 4. Parse FlightID (Field 5)
	if fields[5] != "" {
		val, _ := strconv.ParseInt(fields[5], 10, 64)
		msg.FlightID = val
	}

	// 5. Parse GeneratedTime (Fields 6 & 7)
	if fields[6] != "" && fields[7] != "" {
		t, err := parseDateTime(fields[6], fields[7])
		if err == nil {
			msg.GeneratedTime = t
		}
	}

	// 6. Parse LoggedTime (Fields 8 & 9)
	if fields[8] != "" && fields[9] != "" {
		t, err := parseDateTime(fields[8], fields[9])
		if err == nil {
			msg.LoggedTime = t
		}
	}

	// 7. Parse field 10: Callsign or StatusChange depending on MessageType
	switch msgType {
	case MsgTypeSTA:
		if fields[10] != "" {
			sc := StatusChange(fields[10])
			msg.StatusChange = &sc
		}
	default:
		msg.Callsign = fields[10]
	}

	// 8. Parse Altitude (Field 11)
	msg.Altitude = parseOpt(fields[11], func(s string) (int, error) {
		return strconv.Atoi(s)
	})

	// 9. Parse GroundSpeed (Field 12)
	msg.GroundSpeed = parseOpt(fields[12], func(s string) (float64, error) {
		return strconv.ParseFloat(s, 64)
	})

	// 10. Parse Track (Field 13)
	msg.Track = parseOpt(fields[13], func(s string) (float64, error) {
		return strconv.ParseFloat(s, 64)
	})

	// 11. Parse Latitude (Field 14)
	msg.Latitude = parseOpt(fields[14], func(s string) (float64, error) {
		return strconv.ParseFloat(s, 64)
	})

	// 12. Parse Longitude (Field 15)
	msg.Longitude = parseOpt(fields[15], func(s string) (float64, error) {
		return strconv.ParseFloat(s, 64)
	})

	// 13. Parse VerticalRate (Field 16)
	msg.VerticalRate = parseOpt(fields[16], func(s string) (int, error) {
		return strconv.Atoi(s)
	})

	// 14. Parse Squawk (Field 17)
	msg.Squawk = fields[17]

	// 15. Parse Alert (Field 18)
	msg.Alert, _ = parseBool(fields[18])

	// 16. Parse Emergency (Field 19)
	msg.Emergency, _ = parseBool(fields[19])

	// 17. Parse SPI (Field 20)
	msg.SPI, _ = parseBool(fields[20])

	// 18. Parse IsOnGround (Field 21)
	msg.IsOnGround, _ = parseBool(fields[21])

	return msg, nil
}

// parseDateTime parses a date string "YYYY/MM/DD" or "YYYY-MM-DD" and a time string "HH:MM:SS.mmm"
// into a consolidated time.Time object.
func parseDateTime(dateStr, timeStr string) (time.Time, error) {
	// Normalize date separators from / to -
	dateStr = strings.ReplaceAll(dateStr, "/", "-")
	combined := dateStr + " " + timeStr

	// Try layouts depending on presence of fractional seconds
	var layout string
	if strings.Contains(timeStr, ".") {
		layout = "2006-01-02 15:04:05.999999999"
	} else {
		layout = "2006-01-02 15:04:05"
	}

	return time.Parse(layout, combined)
}
