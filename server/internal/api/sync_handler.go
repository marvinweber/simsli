package api

import (
	"encoding/json"
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

	writeJSON(w, http.StatusOK, map[string]string{"message": "flush applied successfully"})
}
