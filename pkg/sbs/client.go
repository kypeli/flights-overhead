package sbs

import (
	"bufio"
	"context"
	"log/slog"
	"net"
	"time"
)

// Client manages the TCP connection to the ADS-B BaseStation stream.
type Client struct {
	addr string
}

// NewClient initializes a Client with the target address.
func NewClient(addr string) *Client {
	return &Client{
		addr: addr,
	}
}

// Start opens the connection and streams parsed SBS messages to the returned channel.
// It handles socket failures and auto-reconnects with exponential backoff.
func (c *Client) Start(ctx context.Context) <-chan *Message {
	out := make(chan *Message, 100)

	go func() {
		defer close(out)

		baseBackoff := 500 * time.Millisecond
		maxBackoff := 30 * time.Second
		backoff := baseBackoff

		for {
			slog.Info("connecting to ADS-B receiver", "addr", c.addr)
			
			dialer := net.Dialer{}
			conn, err := dialer.DialContext(ctx, "tcp", c.addr)
			if err != nil {
				slog.Error("connection failed", "error", err, "retry_in", backoff)
				
				select {
				case <-ctx.Done():
					return
				case <-time.After(backoff):
					backoff *= 2
					if backoff > maxBackoff {
						backoff = maxBackoff
					}
					continue
				}
			}

			// Connection succeeded, reset backoff
			backoff = baseBackoff
			slog.Info("connected successfully to ADS-B receiver", "addr", c.addr)

			// Handle connection reading
			c.handleConnection(ctx, conn, out)

			// Check if we exited because of context completion
			select {
			case <-ctx.Done():
				slog.Info("stopping TCP client: context completed")
				return
			default:
				slog.Warn("connection lost, reconnecting...")
			}
		}
	}()

	return out
}

// handleConnection scans the connection line-by-line, parses messages, and pushes them to out.
func (c *Client) handleConnection(ctx context.Context, conn net.Conn, out chan<- *Message) {
	defer conn.Close()

	// Close the connection if the context is cancelled while blocked on a read.
	// The done channel ensures the goroutine exits cleanly on normal return too,
	// preventing a leak across reconnects.
	done := make(chan struct{})
	defer close(done)
	go func() {
		select {
		case <-ctx.Done():
			conn.Close()
		case <-done:
		}
	}()

	scanner := bufio.NewScanner(conn)
	for scanner.Scan() {
		line := scanner.Text()
		msg, err := ParseMessage(line)
		if err != nil {
			// Skip malformed/empty rows, log if not simple empty lines
			if line != "" {
				slog.Debug("skipping malformed line", "line", line, "error", err)
			}
			continue
		}

		select {
		case <-ctx.Done():
			return
		case out <- msg:
		}
	}

	if err := scanner.Err(); err != nil {
		select {
		case <-ctx.Done():
			// Silence socket closed error on context cancel
		default:
			slog.Error("error scanning connection stream", "error", err)
		}
	}
}
