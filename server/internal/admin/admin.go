package admin

import (
	"crypto/hmac"
	"crypto/sha256"
	"embed"
	"encoding/hex"
	"fmt"
	"html/template"
	"net/http"
	"net/url"
	"runtime"
	"strconv"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"
	"net.marvinweber.simsli/server/internal/config"
	"net.marvinweber.simsli/server/internal/model"
	"net.marvinweber.simsli/server/internal/realtime"
	"net.marvinweber.simsli/server/internal/service"
)

//go:embed templates/*
var templatesFS embed.FS

const sessionCookieName = "simsli_admin_session"

type LoginData struct {
	Error string
	Next  string
}

type DashboardData struct {
	Config           *config.Config
	ServerInfo       model.ServerInfo
	PendingLinks     []model.MagicLink
	Users            []model.User
	UserCount        int
	Households       []model.Household
	HouseholdCount   int
	Usage            service.UsageOverview
	AllocMB          float64
	NumGoroutine     int
	ActiveWebSockets int
	Message          string
	Error            string
}

type TableBrowserData struct {
	Config           *config.Config
	ServerInfo       model.ServerInfo
	Tables           []string
	CurrentTable     string
	Households       []model.Household
	CurrentHousehold string
	Columns          []string
	Rows             [][]string
	TotalCount       int
	Limit            int
	Offset           int
	PrevOffset       int
	NextOffset       int
	HasPrev          bool
	HasNext          bool
	Error            string
	Message          string
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
		"formatMonth": func(year, month int) string {
			return time.Date(year, time.Month(month), 1, 0, 0, 0, 0, time.UTC).Format("2006-01")
		},
		"magicLinkURL": func(token string) string {
			// deep link for mobile or web link
			return fmt.Sprintf("simsli://auth#access_token=%s", token)
		},
		"webVerifyURL": func(publicURL, token string) string {
			return fmt.Sprintf("%s/api/v1/auth/verify?token=%s", publicURL, token)
		},
	}

	tmpl := template.Must(template.New("root").Funcs(funcMap).ParseFS(templatesFS, "templates/*.html"))

	return &Handler{
		svc:  svc,
		hub:  hub,
		cfg:  cfg,
		tmpl: tmpl,
	}
}

func (h *Handler) sessionToken() string {
	secret := h.cfg.JWTSecret
	if len(secret) == 0 {
		secret = []byte("simsli-admin-default-secret")
	}
	mac := hmac.New(sha256.New, secret)
	mac.Write([]byte("simsli-admin:" + h.cfg.AdminKey))
	return hex.EncodeToString(mac.Sum(nil))
}

func (h *Handler) isAuthenticated(r *http.Request) bool {
	if h.cfg.AdminKey == "" {
		return true
	}
	if r.Header.Get("X-Admin-Key") == h.cfg.AdminKey {
		return true
	}
	if cookie, err := r.Cookie(sessionCookieName); err == nil && cookie.Value == h.sessionToken() {
		return true
	}
	if qKey := r.URL.Query().Get("key"); qKey != "" && qKey == h.cfg.AdminKey {
		return true
	}
	return false
}

func (h *Handler) NewRouter() http.Handler {
	r := chi.NewRouter()

	r.Get("/login", h.RenderLogin)
	r.Post("/login", h.HandleLogin)
	r.Get("/logout", h.HandleLogout)

	r.Group(func(r chi.Router) {
		r.Use(h.authMiddleware)

		r.Get("/", h.RenderDashboard)
		r.Get("/browser", h.RenderBrowser)
		r.Post("/users", h.CreateUser)
		r.Post("/magic-link", h.GenerateMagicLink)
		r.Post("/seed", h.SeedDemoData)
	})

	return r
}

func (h *Handler) authMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if h.cfg.AdminKey == "" {
			next.ServeHTTP(w, r)
			return
		}

		if r.Header.Get("X-Admin-Key") == h.cfg.AdminKey {
			next.ServeHTTP(w, r)
			return
		}

		if cookie, err := r.Cookie(sessionCookieName); err == nil && cookie.Value == h.sessionToken() {
			next.ServeHTTP(w, r)
			return
		}

		if qKey := r.URL.Query().Get("key"); qKey != "" && qKey == h.cfg.AdminKey {
			http.SetCookie(w, &http.Cookie{
				Name:     sessionCookieName,
				Value:    h.sessionToken(),
				Path:     "/",
				HttpOnly: true,
				SameSite: http.SameSiteLaxMode,
				MaxAge:   86400 * 30,
			})
			next.ServeHTTP(w, r)
			return
		}

		if r.Method == http.MethodGet {
			nextURL := r.URL.RequestURI()
			http.Redirect(w, r, "/login?next="+url.QueryEscape(nextURL), http.StatusSeeOther)
			return
		}

		http.Error(w, "Unauthorized: Invalid Admin Key", http.StatusUnauthorized)
	})
}

func (h *Handler) RenderLogin(w http.ResponseWriter, r *http.Request) {
	if h.cfg.AdminKey == "" || h.isAuthenticated(r) {
		http.Redirect(w, r, "/", http.StatusSeeOther)
		return
	}
	next := r.URL.Query().Get("next")
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	_ = h.tmpl.ExecuteTemplate(w, "login.html", LoginData{Next: next})
}

func (h *Handler) HandleLogin(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		h.renderLoginWithError(w, r, "Invalid form data")
		return
	}

	key := r.FormValue("admin_key")
	if key == "" || key != h.cfg.AdminKey {
		h.renderLoginWithError(w, r, "Invalid Admin Key")
		return
	}

	http.SetCookie(w, &http.Cookie{
		Name:     sessionCookieName,
		Value:    h.sessionToken(),
		Path:     "/",
		HttpOnly: true,
		SameSite: http.SameSiteLaxMode,
		MaxAge:   86400 * 30,
	})

	next := r.FormValue("next")
	if next == "" {
		next = r.URL.Query().Get("next")
	}
	if next == "" || !strings.HasPrefix(next, "/") || strings.HasPrefix(next, "//") {
		next = "/"
	}

	http.Redirect(w, r, next, http.StatusSeeOther)
}

func (h *Handler) renderLoginWithError(w http.ResponseWriter, r *http.Request, errMsg string) {
	next := r.FormValue("next")
	if next == "" {
		next = r.URL.Query().Get("next")
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(http.StatusUnauthorized)
	_ = h.tmpl.ExecuteTemplate(w, "login.html", LoginData{
		Error: errMsg,
		Next:  next,
	})
}

func (h *Handler) HandleLogout(w http.ResponseWriter, r *http.Request) {
	http.SetCookie(w, &http.Cookie{
		Name:     sessionCookieName,
		Value:    "",
		Path:     "/",
		HttpOnly: true,
		MaxAge:   -1,
		Expires:  time.Unix(0, 0),
	})
	http.Redirect(w, r, "/login", http.StatusSeeOther)
}

func (h *Handler) RenderDashboard(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()

	var m runtime.MemStats
	runtime.ReadMemStats(&m)

	pendingLinks, _ := h.svc.ListPendingMagicLinks(ctx)
	users, userCount, _ := h.svc.ListUsers(ctx, 50, 0)
	hhs, hhCount, _ := h.svc.ListHouseholds(ctx, 50, 0)
	usage := h.svc.GetUsageOverview(ctx)

	data := DashboardData{
		Config:           h.cfg,
		ServerInfo:       h.svc.GetServerInfo(),
		PendingLinks:     pendingLinks,
		Users:            users,
		UserCount:        userCount,
		Households:       hhs,
		HouseholdCount:   hhCount,
		Usage:            usage,
		AllocMB:          float64(m.Alloc) / 1024 / 1024,
		NumGoroutine:     runtime.NumGoroutine(),
		ActiveWebSockets: h.hub.ActiveConnectionsCount(),
		Message:          r.URL.Query().Get("msg"),
		Error:            r.URL.Query().Get("err"),
	}

	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	_ = h.tmpl.ExecuteTemplate(w, "dashboard.html", data)
}

func (h *Handler) RenderBrowser(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	table := r.URL.Query().Get("table")
	if table == "" {
		table = "households"
	}
	householdID := r.URL.Query().Get("household_id")
	offset, _ := strconv.Atoi(r.URL.Query().Get("offset"))
	limit := 50

	hhs, _, _ := h.svc.ListHouseholds(ctx, 100, 0)
	cols, rows, total, err := h.svc.BrowseTable(ctx, table, householdID, limit, offset)

	var errMsg string
	if err != nil {
		errMsg = err.Error()
	}

	tables := []string{
		"households",
		"household_members",
		"items",
		"stores",
		"categories",
		"list_entries",
		"household_monthly_stats",
		"item_stores",
		"store_categories",
		"invite_tokens",
		"users",
		"magic_links",
		"refresh_tokens",
	}

	prevOffset := offset - limit
	if prevOffset < 0 {
		prevOffset = 0
	}

	data := TableBrowserData{
		Config:           h.cfg,
		ServerInfo:       h.svc.GetServerInfo(),
		Tables:           tables,
		CurrentTable:     table,
		Households:       hhs,
		CurrentHousehold: householdID,
		Columns:          cols,
		Rows:             rows,
		TotalCount:       total,
		Limit:            limit,
		Offset:           offset,
		PrevOffset:       prevOffset,
		NextOffset:       offset + limit,
		HasPrev:          offset > 0,
		HasNext:          offset+limit < total,
		Error:            errMsg,
	}

	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	_ = h.tmpl.ExecuteTemplate(w, "browser.html", data)
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
