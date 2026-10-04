package admin

import (
	"embed"
	"fmt"
	"html/template"
	"net/http"
	"runtime"
	"time"

	"github.com/go-chi/chi/v5"
	"net.marvinweber.simsli/server/internal/config"
	"net.marvinweber.simsli/server/internal/model"
	"net.marvinweber.simsli/server/internal/realtime"
	"net.marvinweber.simsli/server/internal/service"
)

//go:embed templates/*
var templatesFS embed.FS

type DashboardData struct {
	Config           *config.Config
	ServerInfo       model.ServerInfo
	PendingLinks     []model.MagicLink
	Users            []model.User
	UserCount        int
	Households       []model.Household
	HouseholdCount   int
	AllocMB          float64
	NumGoroutine     int
	ActiveWebSockets int
	Message          string
	Error            string
}

type Handler struct {
	svc  *service.Service
	hub  *realtime.Hub
	cfg  *config.Config
	tmpl *template.Template
}

func NewHandler(svc *service.Service, hub *realtime.Hub, cfg *config.Config) *Handler {
	funcMap := template.FuncMap{
		"formatTime": func(t time.Time) string {
			return t.Format("2006-01-02 15:04:05")
		},
		"formatExpiry": func(t time.Time) string {
			remaining := time.Until(t)
			if remaining < 0 {
				return "expired"
			}
			return fmt.Sprintf("%dm %ds", int(remaining.Minutes()), int(remaining.Seconds())%60)
		},
		"magicLinkURL": func(token string) string {
			// deep link for mobile or web link
			return fmt.Sprintf("simsli://auth#access_token=%s", token)
		},
		"webVerifyURL": func(publicURL, token string) string {
			return fmt.Sprintf("%s/api/v1/auth/verify?token=%s", publicURL, token)
		},
	}

	tmpl := template.Must(template.New("dashboard.html").Funcs(funcMap).ParseFS(templatesFS, "templates/dashboard.html"))

	return &Handler{
		svc:  svc,
		hub:  hub,
		cfg:  cfg,
		tmpl: tmpl,
	}
}

func (h *Handler) NewRouter() http.Handler {
	r := chi.NewRouter()

	r.Use(func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if h.cfg.AdminKey != "" {
				key := r.Header.Get("X-Admin-Key")
				if key == "" {
					key = r.URL.Query().Get("key")
				}
				if key != h.cfg.AdminKey {
					http.Error(w, "Unauthorized: Invalid Admin Key", http.StatusUnauthorized)
					return
				}
			}
			next.ServeHTTP(w, r)
		})
	})

	r.Get("/", h.RenderDashboard)
	r.Post("/users", h.CreateUser)
	r.Post("/magic-link", h.GenerateMagicLink)
	r.Post("/seed", h.SeedDemoData)

	return r
}

func (h *Handler) RenderDashboard(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()

	var m runtime.MemStats
	runtime.ReadMemStats(&m)

	pendingLinks, _ := h.svc.ListPendingMagicLinks(ctx)
	users, userCount, _ := h.svc.ListUsers(ctx, 50, 0)
	hhs, hhCount, _ := h.svc.ListHouseholds(ctx, 50, 0)

	data := DashboardData{
		Config:           h.cfg,
		ServerInfo:       h.svc.GetServerInfo(),
		PendingLinks:     pendingLinks,
		Users:            users,
		UserCount:        userCount,
		Households:       hhs,
		HouseholdCount:   hhCount,
		AllocMB:          float64(m.Alloc) / 1024 / 1024,
		NumGoroutine:     runtime.NumGoroutine(),
		ActiveWebSockets: h.hub.ActiveConnectionsCount(),
		Message:          r.URL.Query().Get("msg"),
		Error:            r.URL.Query().Get("err"),
	}

	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	_ = h.tmpl.Execute(w, data)
}

func (h *Handler) CreateUser(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		http.Redirect(w, r, "/?err=Invalid+form+data", http.StatusSeeOther)
		return
	}

	email := r.FormValue("email")
	if email == "" {
		http.Redirect(w, r, "/?err=Email+cannot+be+empty", http.StatusSeeOther)
		return
	}

	_, err := h.svc.AdminCreateUser(r.Context(), email)
	if err != nil {
		http.Redirect(w, r, "/?err="+err.Error(), http.StatusSeeOther)
		return
	}

	http.Redirect(w, r, "/?msg=User+created+successfully", http.StatusSeeOther)
}

func (h *Handler) GenerateMagicLink(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		http.Redirect(w, r, "/?err=Invalid+form+data", http.StatusSeeOther)
		return
	}

	email := r.FormValue("email")
	token, err := h.svc.RequestMagicLink(r.Context(), email)
	if err != nil {
		http.Redirect(w, r, "/?err="+err.Error(), http.StatusSeeOther)
		return
	}

	http.Redirect(w, r, fmt.Sprintf("/?msg=Magic+link+generated+for+%s:+token=%s", email, token), http.StatusSeeOther)
}

func (h *Handler) SeedDemoData(w http.ResponseWriter, r *http.Request) {
	if !h.cfg.Debug {
		http.Redirect(w, r, "/?err=Seeding+is+only+allowed+in+debug+mode+(SIMSLI_DEBUG=true)", http.StatusForbidden)
		return
	}

	if err := h.svc.SeedDemoData(r.Context()); err != nil {
		http.Redirect(w, r, "/?err="+err.Error(), http.StatusSeeOther)
		return
	}

	http.Redirect(w, r, "/?msg=Demo+data+(Testhaushalt)+seeded+successfully!", http.StatusSeeOther)
}
