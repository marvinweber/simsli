package api

import (
	"context"
	"net/http"
	"strings"

	"net.marvinweber.simsli/server/internal/auth"
)

type contextKey string

const (
	UserContextKey contextKey = "simsli_user"
)

type AuthUser struct {
	ID    string
	Email string
}

func AuthMiddleware(authMgr *auth.Manager) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			authHeader := r.Header.Get("Authorization")
			if authHeader == "" {
				http.Error(w, `{"error":"missing authorization header"}`, http.StatusUnauthorized)
				return
			}

			parts := strings.SplitN(authHeader, " ", 2)
			if len(parts) != 2 || !strings.EqualFold(parts[0], "Bearer") {
				http.Error(w, `{"error":"invalid authorization header format"}`, http.StatusUnauthorized)
				return
			}

			claims, err := authMgr.ValidateAccessToken(parts[1])
			if err != nil {
				http.Error(w, `{"error":"invalid or expired token"}`, http.StatusUnauthorized)
				return
			}

			ctx := context.WithValue(r.Context(), UserContextKey, &AuthUser{
				ID:    claims.UserID,
				Email: claims.Email,
			})

			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

func GetAuthUser(r *http.Request) *AuthUser {
	if val, ok := r.Context().Value(UserContextKey).(*AuthUser); ok {
		return val
	}
	return nil
}
