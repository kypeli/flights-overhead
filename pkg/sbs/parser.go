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

	msg := &Message{
		MessageType: MessageType(fields[0]),
		HexIdent:    fields[4],
		Callsign:    fields[10],
	}

	// 1. Parse TransmissionType (Field 1 - optional, only for MSG)
	if fields[1] != "" {
		tt, err := strconv.Atoi(fields[1])
		if err == nil {
			val := TransmissionType(tt)
			msg.TransmissionType = &val
		}
	}

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

	// 7. Parse StatusChange if MessageType is STA (Field 10/11 depending on parsing interpretation)
	// Standard SBS-1 outputs SL/RM/PL/AD status in field 10 (callsign slot) for STA type.
	if msg.MessageType == MsgTypeSTA && fields[10] != "" {
		sc := StatusChange(fields[10])
		msg.StatusChange = &sc
		msg.Callsign = "" // Don't interpret status code as callsign
	}

	// 7b. Parse Callsign if MessageType is ID
	if msg.MessageType == MsgTypeID && fields[10] != "" {
		msg.Callsign = fields[10]
	}

	// 8. Parse Altitude (Field 11)
	if fields[11] != "" {
		val, err := strconv.Atoi(fields[11])
		if err == nil {
			msg.Altitude = &val
		}
	}

	// 9. Parse GroundSpeed (Field 12)
	if fields[12] != "" {
		val, err := strconv.ParseFloat(fields[12], 64)
		if err == nil {
			msg.GroundSpeed = &val
		}
	}

	// 10. Parse Track (Field 13)
	if fields[13] != "" {
		val, err := strconv.ParseFloat(fields[13], 64)
		if err == nil {
			msg.Track = &val
		}
	}

	// 11. Parse Latitude (Field 14)
	if fields[14] != "" {
		val, err := strconv.ParseFloat(fields[14], 64)
		if err == nil {
			msg.Latitude = &val
		}
	}

	// 12. Parse Longitude (Field 15)
	if fields[15] != "" {
		val, err := strconv.ParseFloat(fields[15], 64)
		if err == nil {
			msg.Longitude = &val
		}
	}

	// 13. Parse VerticalRate (Field 16)
	if fields[16] != "" {
		val, err := strconv.Atoi(fields[16])
		if err == nil {
			msg.VerticalRate = &val
		}
	}

	// 14. Parse Squawk (Field 17)
	msg.Squawk = fields[17]

	// Helper for parsing binary/boolean flags (usually 0, 1, -1, or empty)
	parseBoolFlag := func(s string) *bool {
		if s == "" {
			return nil
		}
		var b bool
		if s == "1" || strings.ToLower(s) == "true" {
			b = true
		} else if s == "0" || strings.ToLower(s) == "false" {
			b = false
		} else {
			return nil
		}
		return &b
	}

	// 15. Parse Alert (Field 18)
	msg.Alert = parseBoolFlag(fields[18])

	// 16. Parse Emergency (Field 19)
	msg.Emergency = parseBoolFlag(fields[19])

	// 17. Parse SPI (Field 20)
	msg.SPI = parseBoolFlag(fields[20])

	// 18. Parse IsOnGround (Field 21)
	msg.IsOnGround = parseBoolFlag(fields[21])

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
