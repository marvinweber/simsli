package realtime

import (
	"context"
	"encoding/json"
	"testing"
	"time"
)

func TestHubSenderExclusion(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	hub := NewHub()
	go hub.Run(ctx)

	client1 := &Client{
		HouseholdID: "hh-1",
		DeviceID:    "device-sender",
		send:        make(chan []byte, 10),
	}
	client2 := &Client{
		HouseholdID: "hh-1",
		DeviceID:    "device-other",
		send:        make(chan []byte, 10),
	}

	hub.register <- client1
	hub.register <- client2
	time.Sleep(50 * time.Millisecond)

	// Broadcast with exclusion of "device-sender"
	hub.Broadcast("hh-1", "device-sender")

	// client2 should receive the event
	select {
	case msg := <-client2.send:
		var ev Event
		if err := json.Unmarshal(msg, &ev); err != nil {
			t.Fatalf("failed to unmarshal event: %v", err)
		}
		if ev.HouseholdID != "hh-1" || ev.Type != "sync" {
			t.Errorf("unexpected event payload: %+v", ev)
		}
		if ev.SenderDeviceID != "device-sender" {
			t.Errorf("expected sender device ID 'device-sender', got '%s'", ev.SenderDeviceID)
		}
	case <-time.After(500 * time.Millisecond):
		t.Fatal("client2 timed out waiting for event")
	}

	// client1 should NOT receive the event
	select {
	case msg := <-client1.send:
		t.Fatalf("client1 (sender) should have been excluded, but received: %s", string(msg))
	case <-time.After(100 * time.Millisecond):
		// Expected
	}

	// Broadcast without exclusion
	hub.Broadcast("hh-1")

	// Both should receive the event
	select {
	case <-client1.send:
	case <-time.After(500 * time.Millisecond):
		t.Fatal("client1 timed out waiting for non-excluded broadcast")
	}

	select {
	case <-client2.send:
	case <-time.After(500 * time.Millisecond):
		t.Fatal("client2 timed out waiting for non-excluded broadcast")
	}
}
