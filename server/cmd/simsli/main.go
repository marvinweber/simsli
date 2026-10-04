package main

import (
	"context"
	"errors"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/jackc/pgx/v5"
	"net.marvinweber.simsli/server/internal/admin"
	"net.marvinweber.simsli/server/internal/api"
	"net.marvinweber.simsli/server/internal/auth"
	"net.marvinweber.simsli/server/internal/config"
	"net.marvinweber.simsli/server/internal/database"
	"net.marvinweber.simsli/server/internal/realtime"
	"net.marvinweber.simsli/server/internal/repository"
	"net.marvinweber.simsli/server/internal/service"
)

func main() {
	log.Println("Starting Simsli Server...")
	cfg := config.Load()

	// 1. Database connection & migrations
	db, err := database.Connect(cfg.DatabaseURL)
	if err != nil {
		log.Fatalf("Database connection failed: %v", err)
	}
	defer db.Close()

	// 2. Realtime Hub
	hub := realtime.NewHub()
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go hub.Run(ctx)

	// 3. PostgreSQL LISTEN / NOTIFY setup (if running in PostgreSQL mode)
	if db.Driver == "pgx" {
		setupPostgresPubSub(ctx, cfg.DatabaseURL, hub)
	}

	// 4. Auth & Services
	authMgr := auth.NewManager(cfg.JWTSecret)
	emailSender := service.NewEmailSender(cfg)

	var billingSvc service.BillingService
	if cfg.ServerMode == "cloud" {
		billingSvc = service.NewCloudBillingService()
	} else {
		billingSvc = service.NewUnlimitedBillingService()
	}

	repo := repository.New(db)
	svc := service.NewService(repo, authMgr, hub, cfg, emailSender, billingSvc)

	// 5. Public API Server (Port 8080)
	apiRouter := api.NewRouter(svc, authMgr, hub)
	apiServer := &http.Server{
		Addr:         ":" + cfg.Port,
		Handler:      apiRouter,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	// 6. Admin Dashboard Server (Port 8081)
	adminHandler := admin.NewHandler(svc, hub, cfg)
	adminServer := &http.Server{
		Addr:         ":" + cfg.AdminPort,
		Handler:      adminHandler.NewRouter(),
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	// Start servers
	go func() {
		log.Printf("[Public API] Listening on http://0.0.0.0:%s", cfg.Port)
		if err := apiServer.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatalf("Public API server failed: %v", err)
		}
	}()

	go func() {
		log.Printf("[Admin Dashboard] Listening on http://0.0.0.0:%s", cfg.AdminPort)
		if err := adminServer.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatalf("Admin Dashboard server failed: %v", err)
		}
	}()

	log.Printf("==================================================")
	log.Printf("Simsli Server running!")
	log.Printf("• Mode:         %s", cfg.ServerMode)
	log.Printf("• Database:     %s", db.Driver)
	log.Printf("• Public API:   http://localhost:%s", cfg.Port)
	log.Printf("• Admin Portal: http://localhost:%s", cfg.AdminPort)
	log.Printf("• Registration: %v", cfg.RegistrationOpen)
	log.Printf("==================================================")

	// 7. Graceful Shutdown
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, os.Interrupt, syscall.SIGTERM)
	<-quit

	log.Println("Shutting down Simsli Server gracefully...")
	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer shutdownCancel()

	_ = apiServer.Shutdown(shutdownCtx)
	_ = adminServer.Shutdown(shutdownCtx)
	log.Println("Simsli Server stopped.")
}

func setupPostgresPubSub(ctx context.Context, dbURL string, hub *realtime.Hub) {
	conn, err := pgx.Connect(ctx, dbURL)
	if err != nil {
		log.Printf("[Postgres PubSub] Warning: failed to connect dedicated listener: %v", err)
		return
	}

	_, err = conn.Exec(ctx, "LISTEN simsli_events")
	if err != nil {
		log.Printf("[Postgres PubSub] Warning: LISTEN simsli_events failed: %v", err)
		conn.Close(ctx)
		return
	}

	log.Println("[Postgres PubSub] Listening for cross-instance invalidation notifications")

	// Goroutine to receive notifications from other instances
	go func() {
		defer conn.Close(context.Background())
		for {
			notification, err := conn.WaitForNotification(ctx)
			if err != nil {
				if ctx.Err() != nil {
					return
				}
				log.Printf("[Postgres PubSub] Listener error: %v", err)
				time.Sleep(2 * time.Second)
				continue
			}

			// Broadcast received event to local websocket clients
			householdID := notification.Payload
			hub.BroadcastLocal(householdID)
		}
	}()

	// Wire outbound notifier to NOTIFY simsli_events
	hub.SetExternalNotifier(func(householdID string) {
		notifyCtx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		defer cancel()
		_, _ = conn.Exec(notifyCtx, fmt.Sprintf("NOTIFY simsli_events, '%s'", householdID))
	})
}
