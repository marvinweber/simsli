package api

import (
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/cors"
	"net.marvinweber.simsli/server/internal/auth"
	"net.marvinweber.simsli/server/internal/realtime"
	"net.marvinweber.simsli/server/internal/service"
)

func NewRouter(
	svc *service.Service,
	authMgr *auth.Manager,
	hub *realtime.Hub,
) http.Handler {
	r := chi.NewRouter()

	// Global middleware
	r.Use(middleware.RequestID)
	r.Use(middleware.RealIP)
	r.Use(middleware.Logger)
	r.Use(middleware.Recoverer)

	// Permissive CORS for mobile apps & web admin
	r.Use(cors.Handler(cors.Options{
		AllowedOrigins:   []string{"*"},
		AllowedMethods:   []string{"GET", "POST", "PUT", "DELETE", "OPTIONS"},
		AllowedHeaders:   []string{"Accept", "Authorization", "Content-Type", "X-CSRF-Token"},
		ExposedHeaders:   []string{"Link"},
		AllowCredentials: true,
		MaxAge:           300,
	}))

	authH := NewAuthHandler(svc)
	hhH := NewHouseholdHandler(svc)
	syncH := NewSyncHandler(svc)
	realtimeH := NewRealtimeHandler(hub, authMgr, svc)

	r.Route("/api/v1", func(r chi.Router) {
		// Server Info
		r.Get("/server-info", func(w http.ResponseWriter, r *http.Request) {
			writeJSON(w, http.StatusOK, svc.GetServerInfo())
		})

		// Realtime WebSocket
		r.Get("/realtime", realtimeH.HandleWS)

		// Public Auth
		r.Route("/auth", func(r chi.Router) {
			r.Post("/magic-link", authH.RequestMagicLink)
			r.Get("/verify", authH.Verify)
			r.Post("/verify", authH.Verify)
			r.Post("/refresh", authH.Refresh)
			r.Post("/logout", authH.Logout)
		})

		// Protected Routes
		r.Group(func(r chi.Router) {
			r.Use(AuthMiddleware(authMgr))

			// Households
			r.Route("/households", func(r chi.Router) {
				r.Post("/", hhH.CreateHousehold)
				r.Post("/invites/accept", hhH.AcceptInvite)
				r.Get("/{id}", hhH.GetHousehold)
				r.Get("/{id}/members", hhH.GetMembers)
				r.Delete("/{id}/members/{userId}", hhH.RemoveMember)
				r.Post("/{id}/invites", hhH.CreateInvite)
			})

			// Sync
			r.Route("/sync", func(r chi.Router) {
				r.Get("/deltas", syncH.GetDeltas)
				r.Post("/flush", syncH.Flush)
			})
		})
	})

	return r
}
