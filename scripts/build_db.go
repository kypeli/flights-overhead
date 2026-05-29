package main

import (
	"bufio"
	"compress/gzip"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strings"
)

const dbURL = "https://github.com/wiedehopf/tar1090-db/raw/csv/aircraft.csv.gz"

func main() {
	log.Println("Downloading aircraft database from tar1090-db...")
	resp, err := http.Get(dbURL)
	if err != nil {
		log.Fatalf("failed to download database: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		log.Fatalf("bad HTTP status: %d %s", resp.StatusCode, resp.Status)
	}

	gzReader, err := gzip.NewReader(resp.Body)
	if err != nil {
		log.Fatalf("failed to create gzip reader: %v", err)
	}
	defer gzReader.Close()

	// Prepare the output file path: pkg/sbs/aircraft_db.csv.gz
	outPath := filepath.Join("pkg", "sbs", "aircraft_db.csv.gz")
	
	// Create parent directories if they don't exist
	if err := os.MkdirAll(filepath.Dir(outPath), 0755); err != nil {
		log.Fatalf("failed to create output directories: %v", err)
	}

	outFile, err := os.Create(outPath)
	if err != nil {
		log.Fatalf("failed to create output file: %v", err)
	}
	defer outFile.Close()

	gzWriter := gzip.NewWriter(outFile)
	defer gzWriter.Close()

	log.Println("Filtering and optimizing aircraft database entries (using robust Split parser)...")

	recordsRead := 0
	recordsWritten := 0

	scanner := bufio.NewScanner(gzReader)
	for scanner.Scan() {
		line := scanner.Text()
		recordsRead++

		record := strings.Split(line, ";")
		if len(record) < 5 {
			continue
		}

		// Source format: hex;reg;typeCode;flag;desc;year;operator[;...]
		hex := strings.TrimSpace(record[0])
		reg := strings.TrimSpace(record[1])
		typeCode := strings.TrimSpace(record[2])
		desc := strings.TrimSpace(record[4])
		operator := ""
		if len(record) > 6 {
			operator = strings.TrimSpace(record[6])
		}

		// Filter out records where both TypeCode and Description are empty.
		if typeCode == "" && desc == "" {
			continue
		}

		// Write optimized record: hex;registration;typecode;operator;description
		_, err = gzWriter.Write([]byte(hex + ";" + reg + ";" + typeCode + ";" + operator + ";" + desc + "\n"))
		if err != nil {
			log.Fatalf("error writing output: %v", err)
		}
		recordsWritten++
	}

	if err := scanner.Err(); err != nil {
		log.Fatalf("scanner error: %v", err)
	}

	log.Printf("Successfully compiled database! Read %d, wrote %d optimized entries.", recordsRead, recordsWritten)
	log.Printf("Saved database to %s", outPath)
}
