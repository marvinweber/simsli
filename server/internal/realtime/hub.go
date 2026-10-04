package realtime

import (
	"context"
	"encoding/json"
	"log"
	"sync"
	"time"

	"github.com/coder/websocket"
)

type Event struct {
	Type        string `json:"event"`
	HouseholdID string `json:"household_id"`
}

type Client struct {
	HouseholdID string
	Conn        *websocket.Conn
	send        chan []byte
}

type Hub struct {
	mu         sync.RWMutex
	households map[string]map[*Client]bool
	broadcast  chan Event
	register   chan *Client
	unregister chan *Client

	// Optional external notifier (for PostgreSQL LISTEN/NOTIFY)
	externalNotifier func(householdID string)
}

func NewHub() *Hub {
	return &Hub{
		households: make(map[string]map[*Client]bool),
		broadcast:  make(chan Event, 256),
		register:   make(chan *Client),
		unregister: make(chan *Client),
	}
}

func (h *Hub) SetExternalNotifier(fn func(householdID string)) {
	h.mu.Lock()
	defer h.mu.Unlock()
	h.externalNotifier = fn
}

func (h *Hub) Run(ctx context.Context) {
	for {
		select {
		case <-ctx.Done():
			return

		case client := <-h.register:
			h.mu.Lock()
			clients := h.households[client.HouseholdID]
			if clients == nil {
				clients = make(map[*Client]bool)
				h.households[client.HouseholdID] = clients
			}
			clients[client] = true
			h.mu.Unlock()

		case client := <-h.unregister:
			h.mu.Lock()
			if clients, ok := h.households[client.HouseholdID]; ok {
				if _, exists := clients[client]; exists {
					delete(clients, client)
					close(client.send)
					if len(clients) == 0 {
						delete(h.households, client.HouseholdID)
					}
				}
			}
			h.mu.Unlock()

		case event := <-h.broadcast:
			payload, err := json.Marshal(event)
			if err != nil {
				continue
			}

			h.mu.RLock()
			clients := h.households[event.HouseholdID]
			for client := range clients {
				select {
				case client.send <- payload:
				default:
					// Slow client, drop or disconnect
				}
			}
			h.mu.RUnlock()
		}
	}
}

// BroadcastLocal triggers a sync invalidation for local websocket clients
func (h *Hub) BroadcastLocal(householdID string) {
	h.broadcast <- Event{
		Type:        "sync",
		HouseholdID: householdID,
	}
}

// Broadcast sends the invalidation locally and via external notifier (Postgres LISTEN/NOTIFY) if configured
func (h *Hub) Broadcast(householdID string) {
	h.BroadcastLocal(householdID)

	h.mu.RLock()
	notifier := h.externalNotifier
	h.mu.RUnlock()

	if notifier != nil {
		notifier(householdID)
	}
}

// ActiveConnectionsCount returns the total number of connected clients
func (h *Hub) ActiveConnectionsCount() int {
	h.mu.RLock()
	defer h.mu.RUnlock()

	count := 0
	for _, clients := range h.households {
		count += len(clients)
	}
	return count
}

// ServeWS handles WebSocket connection lifecycle
func (h *Hub) ServeWS(ctx context.Context, conn *websocket.Conn, householdID string) {
	client := &Client{
		HouseholdID: householdID,
		Conn:        conn,
		send:        make(chan []byte, 32),
	}

	h.register <- client
	defer func() {
		h.unregister <- client
		conn.Close(websocket.StatusNormalClosure, "")
	}()

	// Write loop
	go func() {
		for {
			select {
			case <-ctx.Done():
				return
			case message, ok := <-client.send:
				if !ok {
					return
				}
				writeCtx, cancel := context.WithTimeout(ctx, 5*time.Second)
				err := conn.Write(writeCtx, websocket.MessageText, message)
				cancel()
				if err != nil {
					return
				}
			}
		}
	}()

	// Read loop (to detect disconnects / ping-pong)
	for {
		_, _, err := conn.Read(ctx)
		if err != nil {
			log.Printf("[WebSocket] client disconnected: %v", err)
			break
		}
	}
}
