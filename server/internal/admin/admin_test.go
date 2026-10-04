package admin

import (
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"

	"net.marvinweber.simsli/server/internal/auth"
	"net.marvinweber.simsli/server/internal/config"
	"net.marvinweber.simsli/server/internal/database"
	"net.marvinweber.simsli/server/internal/realtime"
	"net.marvinweber.simsli/server/internal/repository"
	"net.marvinweber.simsli/server/internal/service"
)

func setupTestAdmin(t *testing.T, adminKey string) (http.Handler, *config.Config) {
	db, err := database.Connect("sqlite://:memory:")
	if err != nil {
		t.Fatalf("failed to connect to db: %v", err)
	}

	cfg := &config.Config{
		ServerMode:       "self_hosted",
		RegistrationOpen: true,
		JWTSecret:        []byte("01234567890123456789012345678901"),
		PublicURL:        "http://localhost:8080",
		AdminKey:         adminKey,
		Debug:            true,
	}

	repo := repository.New(db)
	authMgr := auth.NewManager(cfg.JWTSecret)
	hub := realtime.NewHub()
	billing := service.NewUnlimitedBillingService()
	svc := service.NewService(repo, authMgr, hub, cfg, nil, billing)

	h := NewHandler(svc, hub, cfg)
	return h.NewRouter(), cfg
}

func TestUnauthenticatedRedirect(t *testing.T) {
	router, _ := setupTestAdmin(t, "my-secret-key")

	req := httptest.NewRequest(http.MethodGet, "/browser?table=items", nil)
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusSeeOther {
		t.Fatalf("expected status 303, got %d", rec.Code)
	}

	location := rec.Header().Get("Location")
	expected := "/login?next=" + url.QueryEscape("/browser?table=items")
	if location != expected {
		t.Fatalf("expected redirect to %q, got %q", expected, location)
	}
}

func TestLoginRender(t *testing.T) {
	router, _ := setupTestAdmin(t, "my-secret-key")

	req := httptest.NewRequest(http.MethodGet, "/login?next=%2Fbrowser", nil)
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected status 200, got %d", rec.Code)
	}

	body := rec.Body.String()
	if !strings.Contains(body, "Simsli Admin") || !strings.Contains(body, `name="admin_key"`) {
		t.Fatalf("unexpected login page body: %s", body)
	}
}

func TestLoginFailure(t *testing.T) {
	router, _ := setupTestAdmin(t, "my-secret-key")

	form := url.Values{}
	form.Set("admin_key", "wrong-key")
	form.Set("next", "/browser")

	req := httptest.NewRequest(http.MethodPost, "/login", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("expected status 401, got %d", rec.Code)
	}

	body := rec.Body.String()
	if !strings.Contains(body, "Invalid Admin Key") {
		t.Fatalf("expected error message in body: %s", body)
	}
}

func TestLoginSuccessAndSessionCookie(t *testing.T) {
	router, _ := setupTestAdmin(t, "my-secret-key")

	form := url.Values{}
	form.Set("admin_key", "my-secret-key")
	form.Set("next", "/browser")

	req := httptest.NewRequest(http.MethodPost, "/login", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusSeeOther {
		t.Fatalf("expected status 303, got %d", rec.Code)
	}

	if loc := rec.Header().Get("Location"); loc != "/browser" {
		t.Fatalf("expected redirect to /browser, got %s", loc)
	}

	cookies := rec.Result().Cookies()
	var sessionCookie *http.Cookie
	for _, c := range cookies {
		if c.Name == sessionCookieName {
			sessionCookie = c
			break
		}
	}

	if sessionCookie == nil {
		t.Fatal("expected session cookie to be set")
	}

	if sessionCookie.Value == "" {
		t.Fatal("expected non-empty session cookie value")
	}

	// Verify using the session cookie grants access to dashboard
	dashReq := httptest.NewRequest(http.MethodGet, "/", nil)
	dashReq.AddCookie(sessionCookie)
	dashRec := httptest.NewRecorder()

	router.ServeHTTP(dashRec, dashReq)

	if dashRec.Code != http.StatusOK {
		t.Fatalf("expected status 200 with session cookie, got %d", dashRec.Code)
	}
}

func TestQueryParamKeyAutoLogin(t *testing.T) {
	router, _ := setupTestAdmin(t, "my-secret-key")

	req := httptest.NewRequest(http.MethodGet, "/?key=my-secret-key", nil)
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected status 200, got %d", rec.Code)
	}

	// Should have set session cookie
	var sessionCookie *http.Cookie
	for _, c := range rec.Result().Cookies() {
		if c.Name == sessionCookieName {
			sessionCookie = c
			break
		}
	}

	if sessionCookie == nil {
		t.Fatal("expected session cookie to be set when using ?key= query parameter")
	}
}

func TestLogout(t *testing.T) {
	router, _ := setupTestAdmin(t, "my-secret-key")

	req := httptest.NewRequest(http.MethodGet, "/logout", nil)
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusSeeOther {
		t.Fatalf("expected status 303, got %d", rec.Code)
	}

	if loc := rec.Header().Get("Location"); loc != "/login" {
		t.Fatalf("expected redirect to /login, got %s", loc)
	}

	var sessionCookie *http.Cookie
	for _, c := range rec.Result().Cookies() {
		if c.Name == sessionCookieName {
			sessionCookie = c
			break
		}
	}

	if sessionCookie == nil || sessionCookie.MaxAge != -1 {
		t.Fatal("expected expired session cookie on logout")
	}
}
