package api

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strings"

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

	// If accessed from a web browser via GET, redirect or provide deep link to launch the app
	if r.Method == http.MethodGet && strings.Contains(r.Header.Get("Accept"), "text/html") {
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		w.WriteHeader(http.StatusOK)
		deepLink := fmt.Sprintf("simsli://auth/verify?token=%s", req.Token)
		html := fmt.Sprintf(`<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Opening Simsli...</title>
    <meta http-equiv="refresh" content="0;url=%s">
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; display: flex; align-items: center; justify-content: center; height: 80vh; margin: 0; background: #f8fafc; color: #0f172a; text-align: center; }
        .card { background: white; padding: 2rem; border-radius: 16px; box-shadow: 0 4px 20px rgba(0,0,0,0.08); max-width: 320px; }
        h1 { margin-top: 0; font-size: 1.4rem; }
        p { color: #64748b; font-size: 0.95rem; margin-bottom: 1.5rem; }
        .btn { display: inline-block; padding: 0.75rem 1.5rem; background: #006C4C; color: white; text-decoration: none; border-radius: 9999px; font-weight: 600; }
    </style>
</head>
<body>
    <div class="card">
        <h1>Simsli</h1>
        <p>Opening the Simsli app to complete sign in...</p>
        <a class="btn" href="%s">Open App</a>
    </div>
</body>
</html>`, deepLink, deepLink)
		_, _ = w.Write([]byte(html))
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
