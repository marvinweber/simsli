package api

import (
	"log"
	"net/http"

	"github.com/coder/websocket"
	"net.marvinweber.simsli/server/internal/auth"
	"net.marvinweber.simsli/server/internal/realtime"
	"net.marvinweber.simsli/server/internal/service"
)

type RealtimeHandler struct {
	hub     *realtime.Hub
	authMgr *auth.Manager
	svc     *service.Service
}

func NewRealtimeHandler(hub *realtime.Hub, authMgr *auth.Manager, svc *service.Service) *RealtimeHandler {
	return &RealtimeHandler{
		hub:     hub,
		authMgr: authMgr,
		svc:     svc,
	}
}

func (h *RealtimeHandler) HandleWS(w http.ResponseWriter, r *http.Request) {
	token := r.URL.Query().Get("token")
	householdID := r.URL.Query().Get("household_id")

	if token == "" || householdID == "" {
		http.Error(w, "missing token or household_id", http.StatusBadRequest)
		return
	}

	claims, err := h.authMgr.ValidateAccessToken(token)
	if err != nil {
		http.Error(w, "invalid token", http.StatusUnauthorized)
		return
	}

	// Verify membership
	_, err = h.svc.GetHousehold(r.Context(), claims.UserID, householdID)
	if err != nil {
		http.Error(w, "access to household forbidden", http.StatusForbidden)
		return
	}

	// Upgrade to WebSocket
	conn, err := websocket.Accept(w, r, &websocket.AcceptOptions{
		InsecureSkipVerify: true, // Allow all origins for mobile/local clients
	})
	if err != nil {
		log.Printf("[WebSocket] Upgrade failed: %v", err)
		return
	}

	h.hub.ServeWS(r.Context(), conn, householdID)
}
