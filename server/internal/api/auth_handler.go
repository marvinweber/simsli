package api

import (
	"encoding/json"
	"net/http"

	"net.marvinweber.simsli/server/internal/service"
)

type AuthHandler struct {
	svc *service.Service
}

func NewAuthHandler(svc *service.Service) *AuthHandler {
	return &AuthHandler{svc: svc}
}

type MagicLinkRequest struct {
	Email string `json:"email"`
}

type MagicLinkResponse struct {
	Message string `json:"message"`
	Token   string `json:"token,omitempty"` // Included in dev/response for easy automation
}

type VerifyRequest struct {
	Token string `json:"token"`
}

type RefreshRequest struct {
	RefreshToken string `json:"refresh_token"`
}

func (h *AuthHandler) RequestMagicLink(w http.ResponseWriter, r *http.Request) {
	var req MagicLinkRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSONError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	token, err := h.svc.RequestMagicLink(r.Context(), req.Email)
	if err != nil {
		if err == service.ErrRegistrationClosed {
			writeJSONError(w, http.StatusForbidden, "registration is closed on this server")
			return
		}
		if err == service.ErrEmailNotAllowed {
			writeJSONError(w, http.StatusForbidden, "email address is not allowed on this server")
			return
		}
		writeJSONError(w, http.StatusBadRequest, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, MagicLinkResponse{
		Message: "Magic link sent. Check your email or admin dashboard.",
		Token:   token,
	})
}

func (h *AuthHandler) Verify(w http.ResponseWriter, r *http.Request) {
	var req VerifyRequest
	// Support both JSON body and query parameter ?token=...
	if r.Method == http.MethodGet {
		req.Token = r.URL.Query().Get("token")
	} else {
		_ = json.NewDecoder(r.Body).Decode(&req)
	}

	if req.Token == "" {
		writeJSONError(w, http.StatusBadRequest, "missing verification token")
		return
	}

	tokens, user, err := h.svc.VerifyMagicLink(r.Context(), req.Token)
	if err != nil {
		writeJSONError(w, http.StatusUnauthorized, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"access_token":  tokens.AccessToken,
		"refresh_token": tokens.RefreshToken,
		"expires_at":    tokens.ExpiresAt,
		"user":          user,
	})
}

func (h *AuthHandler) Refresh(w http.ResponseWriter, r *http.Request) {
	var req RefreshRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSONError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	if req.RefreshToken == "" {
		writeJSONError(w, http.StatusBadRequest, "missing refresh token")
		return
	}

	tokens, err := h.svc.RefreshTokens(r.Context(), req.RefreshToken)
	if err != nil {
		writeJSONError(w, http.StatusUnauthorized, "invalid or expired refresh token")
		return
	}

	writeJSON(w, http.StatusOK, tokens)
}

func (h *AuthHandler) Logout(w http.ResponseWriter, r *http.Request) {
	var req RefreshRequest
	_ = json.NewDecoder(r.Body).Decode(&req)
	if req.RefreshToken != "" {
		_ = h.svc.RevokeRefreshToken(r.Context(), req.RefreshToken)
	}

	writeJSON(w, http.StatusOK, map[string]string{"message": "logged out successfully"})
}

func writeJSON(w http.ResponseWriter, status int, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(data)
}

func writeJSONError(w http.ResponseWriter, status int, message string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(map[string]string{"error": message})
}
