package frontend

import (
	_ "embed"
	"net/http"
)

//go:embed dashboard.html
var dashboardHTML []byte

// NewHTTPHandler creates an explicit ServeMux with routes registered for the dashboard and SSE broker.
func NewHTTPHandler(broker *SSEBroker) http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/" {
			http.NotFound(w, r)
			return
		}
		w.Header().Set("Content-Type", "text/html")
		w.Write(dashboardHTML)
	})
	mux.Handle("/events", broker)
	return mux
}
