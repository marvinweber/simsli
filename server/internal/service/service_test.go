package service

import (
	"context"
	"testing"
	"time"

	"net.marvinweber.simsli/server/internal/auth"
	"net.marvinweber.simsli/server/internal/config"
	"net.marvinweber.simsli/server/internal/database"
	"net.marvinweber.simsli/server/internal/model"
	"net.marvinweber.simsli/server/internal/realtime"
	"net.marvinweber.simsli/server/internal/repository"
)

type mockEmailSender struct {
	lastLink  string
	lastEmail string
}

func (m *mockEmailSender) SendMagicLink(toEmail, link string) error {
	m.lastEmail = toEmail
	m.lastLink = link
	return nil
}

func setupTestService(t *testing.T) (*Service, *repository.Repository) {
	db, err := database.Connect("sqlite://:memory:")
	if err != nil {
		t.Fatalf("failed to connect to in-memory db: %v", err)
	}

	cfg := &config.Config{
		ServerMode:       "self_hosted",
		RegistrationOpen: true,
		JWTSecret:        []byte("01234567890123456789012345678901"),
		PublicURL:        "http://localhost:8080",
	}

	repo := repository.New(db)
	authMgr := auth.NewManager(cfg.JWTSecret)
	hub := realtime.NewHub()
	email := &mockEmailSender{}
	billing := NewUnlimitedBillingService()

	svc := NewService(repo, authMgr, hub, cfg, email, billing)
	return svc, repo
}

func TestMagicLinkFlow(t *testing.T) {
	svc, _ := setupTestService(t)
	ctx := context.Background()

	email := "marvin@simsli.de"
	token, err := svc.RequestMagicLink(ctx, email)
	if err != nil {
		t.Fatalf("request magic link failed: %v", err)
	}

	tokens, user, err := svc.VerifyMagicLink(ctx, token)
	if err != nil {
		t.Fatalf("verify magic link failed: %v", err)
	}

	if user.Email != email {
		t.Errorf("expected email %q, got %q", email, user.Email)
	}
	if tokens.AccessToken == "" || tokens.RefreshToken == "" {
		t.Error("expected non-empty tokens")
	}

	// Verify token rotation
	rotated, err := svc.RefreshTokens(ctx, tokens.RefreshToken)
	if err != nil {
		t.Fatalf("refresh tokens failed: %v", err)
	}
	if rotated.AccessToken == "" {
		t.Error("expected rotated access token")
	}

	// Old refresh token must now be rejected
	_, err = svc.RefreshTokens(ctx, tokens.RefreshToken)
	if err == nil {
		t.Error("expected rejected old refresh token, got nil")
	}
}

func TestHouseholdCreationAndSync(t *testing.T) {
	svc, _ := setupTestService(t)
	ctx := context.Background()

	// 1. Create User
	user, err := svc.AdminCreateUser(ctx, "user1@simsli.de")
	if err != nil {
		t.Fatalf("create user failed: %v", err)
	}

	// 2. Create Household
	hh, err := svc.CreateHousehold(ctx, user.ID, "hh-test-1", "Test Household")
	if err != nil {
		t.Fatalf("create household failed: %v", err)
	}

	if hh.Name != "Test Household" {
		t.Errorf("expected name Test Household, got %q", hh.Name)
	}

	// 3. Flush item mutations
	flushReq := &model.FlushRequest{
		HouseholdID: hh.ID,
		Stores: []model.Store{
			{ID: "s-1", HouseholdID: hh.ID, Name: "Aldi", SortOrder: 1, CreatedAt: time.Now().UTC(), UpdatedAt: time.Now().UTC()},
		},
		Items: []model.Item{
			{ID: "i-1", HouseholdID: hh.ID, Name: "Apples", Notes: "Crisp", Type: "PERMANENT", SortOrder: 1, CreatedAt: time.Now().UTC(), UpdatedAt: time.Now().UTC()},
		},
	}

	if err := svc.Flush(ctx, user.ID, flushReq); err != nil {
		t.Fatalf("flush failed: %v", err)
	}

	// 4. Fetch deltas
	deltas, err := svc.GetDeltas(ctx, user.ID, hh.ID, time.Time{})
	if err != nil {
		t.Fatalf("get deltas failed: %v", err)
	}

	if len(deltas.Stores) != 1 || deltas.Stores[0].Name != "Aldi" {
		t.Errorf("expected 1 store 'Aldi', got %v", deltas.Stores)
	}
	if len(deltas.Items) != 1 || deltas.Items[0].Name != "Apples" {
		t.Errorf("expected 1 item 'Apples', got %v", deltas.Items)
	}
}

func TestInviteFlow(t *testing.T) {
	svc, _ := setupTestService(t)
	ctx := context.Background()

	owner, _ := svc.AdminCreateUser(ctx, "owner@simsli.de")
	member, _ := svc.AdminCreateUser(ctx, "member@simsli.de")

	hh, _ := svc.CreateHousehold(ctx, owner.ID, "hh-invite-test", "Family")

	// Owner creates invite
	invite, err := svc.CreateInvite(ctx, owner.ID, hh.ID)
	if err != nil {
		t.Fatalf("create invite failed: %v", err)
	}

	// Member accepts invite
	joinedHH, err := svc.AcceptInvite(ctx, member.ID, invite.Token)
	if err != nil {
		t.Fatalf("accept invite failed: %v", err)
	}

	if joinedHH != hh.ID {
		t.Errorf("expected household ID %q, got %q", hh.ID, joinedHH)
	}

	// Verify member list has 2 members
	members, err := svc.GetHouseholdMembers(ctx, owner.ID, hh.ID)
	if err != nil {
		t.Fatalf("get members failed: %v", err)
	}
	if len(members) != 2 {
		t.Errorf("expected 2 members, got %d", len(members))
	}
}

func TestSeedDemoData(t *testing.T) {
	svc, _ := setupTestService(t)
	ctx := context.Background()

	if err := svc.SeedDemoData(ctx); err != nil {
		t.Fatalf("seed demo data failed: %v", err)
	}

	// Verify Testhaushalt was created
	householdID := "dd000000-0000-0000-0000-000000000001"
	user, err := svc.repo.GetUserByEmail(ctx, "test1@simsli.de")
	if err != nil {
		t.Fatalf("expected test1@simsli.de user: %v", err)
	}

	hh, err := svc.GetHousehold(ctx, user.ID, householdID)
	if err != nil {
		t.Fatalf("expected Testhaushalt: %v", err)
	}
	if hh.Name != "Testhaushalt" {
		t.Errorf("expected Testhaushalt, got %q", hh.Name)
	}

	deltas, err := svc.GetDeltas(ctx, user.ID, householdID, time.Time{})
	if err != nil {
		t.Fatalf("get deltas failed: %v", err)
	}

	if len(deltas.Stores) != 3 {
		t.Errorf("expected 3 stores, got %d", len(deltas.Stores))
	}
	if len(deltas.Categories) != 6 {
		t.Errorf("expected 6 categories, got %d", len(deltas.Categories))
	}
	if len(deltas.Items) != 15 {
		t.Errorf("expected 15 items, got %d", len(deltas.Items))
	}
	if len(deltas.ListEntries) != 11 {
		t.Errorf("expected 11 entries, got %d", len(deltas.ListEntries))
	}
}
