package frontend

import (
	"fmt"
	"net/http"
	"sync"
)

// sseChanBuffer is the size of the channel used to buffer SSE messages.
const (
	sseChanBuffer = 10
)

// Broker coordinates real-time thread-safe Server-Sent Events (SSE) streaming.
type SSEBroker struct {
	mu      sync.Mutex
	clients map[chan string]bool
}

func NewSSEBroker() *SSEBroker {
	return &SSEBroker{
		clients: make(map[chan string]bool),
	}
}

func (b *SSEBroker) Register(ch chan string) {
	b.mu.Lock()
	defer b.mu.Unlock()
	b.clients[ch] = true
}

func (b *SSEBroker) Unregister(ch chan string) {
	b.mu.Lock()
	defer b.mu.Unlock()
	delete(b.clients, ch)
	close(ch)
}

func (b *SSEBroker) Broadcast(msg string) {
	b.mu.Lock()
	defer b.mu.Unlock()
	for ch := range b.clients {
		select {
		case ch <- msg:
		default:
			// Client's channel is blocked; skip to avoid stalling the broadcaster
		}
	}
}

// SSEBroker must implement http.Handler
var _ http.Handler = (*SSEBroker)(nil)

func (b *SSEBroker) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Connection", "keep-alive")
	w.Header().Set("Access-Control-Allow-Origin", "*")

	flusher, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "Streaming unsupported", http.StatusInternalServerError)
		return
	}

	ch := make(chan string, sseChanBuffer)
	b.Register(ch)
	defer b.Unregister(ch)

	ctx := r.Context()
	for {
		select {
		case <-ctx.Done():
			return
		case msg, ok := <-ch:
			if !ok {
				return
			}
			_, err := fmt.Fprintf(w, "data: %s\n\n", msg)
			if err != nil {
				return
			}
			flusher.Flush()
		}
	}
}
