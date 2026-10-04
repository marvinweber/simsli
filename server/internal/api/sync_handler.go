package api

import (
	"encoding/json"
	"log"
	"net/http"
	"time"

	"net.marvinweber.simsli/server/internal/model"
	"net.marvinweber.simsli/server/internal/service"
)

type SyncHandler struct {
	svc *service.Service
}

func NewSyncHandler(svc *service.Service) *SyncHandler {
	return &SyncHandler{svc: svc}
}

func (h *SyncHandler) GetDeltas(w http.ResponseWriter, r *http.Request) {
	user := GetAuthUser(r)
	if user == nil {
		writeJSONError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	householdID := r.URL.Query().Get("household_id")
	if householdID == "" {
		writeJSONError(w, http.StatusBadRequest, "missing household_id query parameter")
		return
	}

	sinceStr := r.URL.Query().Get("since")
	var since time.Time
	if sinceStr != "" {
		parsed, err := time.Parse(time.RFC3339Nano, sinceStr)
		if err == nil {
			since = parsed
		} else {
			parsed2, err2 := time.Parse(time.RFC3339, sinceStr)
			if err2 == nil {
				since = parsed2
			}
		}
	}

	deltas, err := h.svc.GetDeltas(r.Context(), user.ID, householdID, since)
	if err != nil {
		if err == service.ErrForbidden {
			writeJSONError(w, http.StatusForbidden, "forbidden")
			return
		}
		writeJSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	// Diagnostics for sync-loop analysis, logged only when the pull actually
	// returned changes — empty pulls (healthy steady state) and the always-fully-
	//-returned item_stores/store_categories stay silent.
	if deltas.Household != nil || len(deltas.Stores) > 0 || len(deltas.Categories) > 0 ||
		len(deltas.Items) > 0 || len(deltas.ListEntries) > 0 {
		log.Printf("[Sync] deltas household=%s device=%s since=%s household=%t stores=%d categories=%d items=%d entries=%d item_stores=%d store_categories=%d",
			householdID, DeviceID(r), sinceStr,
			deltas.Household != nil,
			len(deltas.Stores), len(deltas.Categories), len(deltas.Items), len(deltas.ListEntries),
			len(deltas.ItemStores), len(deltas.StoreCategories))
	}

	writeJSON(w, http.StatusOK, deltas)
}

func (h *SyncHandler) Flush(w http.ResponseWriter, r *http.Request) {
	user := GetAuthUser(r)
	if user == nil {
		writeJSONError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	var req model.FlushRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSONError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	if req.HouseholdID == "" {
		writeJSONError(w, http.StatusBadRequest, "missing household_id")
		return
	}

	if err := h.svc.Flush(r.Context(), user.ID, &req); err != nil {
		if err == service.ErrForbidden {
			writeJSONError(w, http.StatusForbidden, "forbidden")
			return
		}
		writeJSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	// Diagnostics: what the client pushed, logged only for non-empty flushes —
	// empty ones are pure realtime echo pokes and happen constantly.
	if len(req.Stores)+len(req.Categories)+len(req.Items)+len(req.ListEntries)+
		len(req.ItemStores)+len(req.StoreCategories)+
		len(req.DeletedStores)+len(req.DeletedCategories)+len(req.DeletedItems)+len(req.DeletedEntries) > 0 ||
		req.Household != nil {
		log.Printf("[Sync] flush household=%s device=%s household_row=%t stores=%d categories=%d items=%d entries=%d item_stores=%d store_categories=%d deletes=%d",
			req.HouseholdID, DeviceID(r), req.Household != nil,
			len(req.Stores), len(req.Categories), len(req.Items), len(req.ListEntries),
			len(req.ItemStores), len(req.StoreCategories),
			len(req.DeletedStores)+len(req.DeletedCategories)+len(req.DeletedItems)+len(req.DeletedEntries))
	}

	writeJSON(w, http.StatusOK, map[string]string{"message": "flush applied successfully"})
}
