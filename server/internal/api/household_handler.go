package api

import (
	"encoding/json"
	"net/http"

	"github.com/go-chi/chi/v5"
	"net.marvinweber.simsli/server/internal/service"
)

type HouseholdHandler struct {
	svc *service.Service
}

func NewHouseholdHandler(svc *service.Service) *HouseholdHandler {
	return &HouseholdHandler{svc: svc}
}

type CreateHouseholdRequest struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}

type AcceptInviteRequest struct {
	Token string `json:"token"`
}

func (h *HouseholdHandler) CreateHousehold(w http.ResponseWriter, r *http.Request) {
	user := GetAuthUser(r)
	if user == nil {
		writeJSONError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	var req CreateHouseholdRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSONError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	hh, err := h.svc.CreateHousehold(r.Context(), user.ID, req.ID, req.Name)
	if err != nil {
		writeJSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	writeJSON(w, http.StatusCreated, hh)
}

func (h *HouseholdHandler) GetHousehold(w http.ResponseWriter, r *http.Request) {
	user := GetAuthUser(r)
	if user == nil {
		writeJSONError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	householdID := chi.URLParam(r, "id")
	hh, err := h.svc.GetHousehold(r.Context(), user.ID, householdID)
	if err != nil {
		if err == service.ErrForbidden {
			writeJSONError(w, http.StatusForbidden, "forbidden")
			return
		}
		writeJSONError(w, http.StatusNotFound, "household not found")
		return
	}

	writeJSON(w, http.StatusOK, hh)
}

func (h *HouseholdHandler) GetMembers(w http.ResponseWriter, r *http.Request) {
	user := GetAuthUser(r)
	if user == nil {
		writeJSONError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	householdID := chi.URLParam(r, "id")
	members, err := h.svc.GetHouseholdMembers(r.Context(), user.ID, householdID)
	if err != nil {
		if err == service.ErrForbidden {
			writeJSONError(w, http.StatusForbidden, "forbidden")
			return
		}
		writeJSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, members)
}

func (h *HouseholdHandler) RemoveMember(w http.ResponseWriter, r *http.Request) {
	user := GetAuthUser(r)
	if user == nil {
		writeJSONError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	householdID := chi.URLParam(r, "id")
	targetUserID := chi.URLParam(r, "userId")

	err := h.svc.RemoveHouseholdMember(r.Context(), user.ID, householdID, targetUserID)
	if err != nil {
		if err == service.ErrForbidden {
			writeJSONError(w, http.StatusForbidden, "forbidden")
			return
		}
		writeJSONError(w, http.StatusBadRequest, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, map[string]string{"message": "member removed successfully"})
}

func (h *HouseholdHandler) CreateInvite(w http.ResponseWriter, r *http.Request) {
	user := GetAuthUser(r)
	if user == nil {
		writeJSONError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	householdID := chi.URLParam(r, "id")
	invite, err := h.svc.CreateInvite(r.Context(), user.ID, householdID)
	if err != nil {
		if err == service.ErrForbidden {
			writeJSONError(w, http.StatusForbidden, "only household owner can create invite tokens")
			return
		}
		writeJSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	writeJSON(w, http.StatusCreated, invite)
}

func (h *HouseholdHandler) AcceptInvite(w http.ResponseWriter, r *http.Request) {
	user := GetAuthUser(r)
	if user == nil {
		writeJSONError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	var req AcceptInviteRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSONError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	newHouseholdID, err := h.svc.AcceptInvite(r.Context(), user.ID, req.Token)
	if err != nil {
		writeJSONError(w, http.StatusBadRequest, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, map[string]string{
		"household_id": newHouseholdID,
		"message":      "invite accepted successfully",
	})
}
