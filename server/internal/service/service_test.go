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

	tokens, user, err := svc.VerifyMagicLink(ctx, token, ClientInfo{})
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
	rotated, err := svc.RefreshTokens(ctx, tokens.RefreshToken, ClientInfo{})
	if err != nil {
		t.Fatalf("refresh tokens failed: %v", err)
	}
	if rotated.AccessToken == "" {
		t.Error("expected rotated access token")
	}

	// Old refresh token must now be rejected
	_, err = svc.RefreshTokens(ctx, tokens.RefreshToken, ClientInfo{})
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
			{
				ID:          "i-1",
				HouseholdID: hh.ID,
				Name:        "Apples",
				Notes:       "Crisp",
				Type:        "PERMANENT",
				SortOrder:   1,
				Links: []model.ItemLink{
					{URL: "https://example.com/apple", Title: &[]string{"Apple info"}[0]},
				},
				CreatedAt: time.Now().UTC(),
				UpdatedAt: time.Now().UTC(),
			},
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
	if len(deltas.Items[0].Links) != 1 || deltas.Items[0].Links[0].URL != "https://example.com/apple" || deltas.Items[0].Links[0].Title == nil || *deltas.Items[0].Links[0].Title != "Apple info" {
		t.Errorf("expected links with 1 link 'https://example.com/apple' and title 'Apple info', got %+v", deltas.Items[0].Links)
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

func TestHouseholdIconFlushAndOwnerGate(t *testing.T) {
	svc, _ := setupTestService(t)
	ctx := context.Background()

	owner, _ := svc.AdminCreateUser(ctx, "owner@simsli.de")
	member, _ := svc.AdminCreateUser(ctx, "member@simsli.de")
	hh, _ := svc.CreateHousehold(ctx, owner.ID, "hh-icon-test", "Icon Household")

	invite, err := svc.CreateInvite(ctx, owner.ID, hh.ID)
	if err != nil {
		t.Fatalf("create invite failed: %v", err)
	}
	if _, err := svc.AcceptInvite(ctx, member.ID, invite.Token); err != nil {
		t.Fatalf("accept invite failed: %v", err)
	}

	icon := "🏠"
	now := time.Now().UTC()
	ownerFlush := &model.FlushRequest{
		HouseholdID: hh.ID,
		Household:   &model.Household{ID: hh.ID, Name: "Icon Household", Icon: &icon, UpdatedAt: now},
	}
	if err := svc.Flush(ctx, owner.ID, ownerFlush); err != nil {
		t.Fatalf("owner flush with household failed: %v", err)
	}

	deltas, err := svc.GetDeltas(ctx, owner.ID, hh.ID, time.Time{})
	if err != nil {
		t.Fatalf("get deltas failed: %v", err)
	}
	if deltas.Household == nil || deltas.Household.Icon == nil || *deltas.Household.Icon != "🏠" {
		t.Fatalf("expected household icon 🏠 in deltas, got %+v", deltas.Household)
	}

	// Member flushing a household row must be forbidden
	memberFlush := &model.FlushRequest{
		HouseholdID: hh.ID,
		Household:   &model.Household{ID: hh.ID, Name: "Hijacked", UpdatedAt: now},
	}
	if err := svc.Flush(ctx, member.ID, memberFlush); err != ErrForbidden {
		t.Fatalf("expected ErrForbidden for member household flush, got %v", err)
	}

	// Member can still flush non-household entities
	memberItemFlush := &model.FlushRequest{
		HouseholdID: hh.ID,
		Items: []model.Item{
			{ID: "i-m-1", HouseholdID: hh.ID, Name: "Bread", Type: "PERMANENT", CreatedAt: now, UpdatedAt: now},
		},
	}
	if err := svc.Flush(ctx, member.ID, memberItemFlush); err != nil {
		t.Fatalf("member item flush should succeed, got %v", err)
	}

	// Legacy flush (icon nil) must preserve the stored icon
	legacyFlush := &model.FlushRequest{
		HouseholdID: hh.ID,
		Household:   &model.Household{ID: hh.ID, Name: "Icon Household", UpdatedAt: now},
	}
	if err := svc.Flush(ctx, owner.ID, legacyFlush); err != nil {
		t.Fatalf("legacy flush failed: %v", err)
	}
	deltas, err = svc.GetDeltas(ctx, owner.ID, hh.ID, time.Time{})
	if err != nil {
		t.Fatalf("get deltas failed: %v", err)
	}
	if deltas.Household == nil || deltas.Household.Icon == nil || *deltas.Household.Icon != "🏠" {
		t.Fatalf("expected icon preserved after legacy flush, got %+v", deltas.Household)
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

func TestTouchUserVersion(t *testing.T) {
	svc, repo := setupTestService(t)
	ctx := context.Background()

	user, err := svc.AdminCreateUser(ctx, "version@simsli.de")
	if err != nil {
		t.Fatalf("create user failed: %v", err)
	}

	// First touch records the version
	if err := repo.TouchUserVersion(ctx, user.ID, "1.2.3"); err != nil {
		t.Fatalf("touch failed: %v", err)
	}
	got, err := repo.GetUserByID(ctx, user.ID)
	if err != nil {
		t.Fatalf("get user failed: %v", err)
	}
	if got.LastAppVersion == nil || *got.LastAppVersion != "1.2.3" {
		t.Errorf("expected version 1.2.3, got %v", got.LastAppVersion)
	}
	if got.LastSeenAt == nil {
		t.Error("expected last_seen_at to be set")
	}
	firstSeen := *got.LastSeenAt

	// A touch with the same version inside the throttle window must not write
	if err := repo.TouchUserVersion(ctx, user.ID, "1.2.3"); err != nil {
		t.Fatalf("second touch failed: %v", err)
	}
	got, _ = repo.GetUserByID(ctx, user.ID)
	if got.LastSeenAt == nil || !got.LastSeenAt.Equal(firstSeen) {
		t.Errorf("expected throttled write (last_seen_at unchanged), got %v", got.LastSeenAt)
	}

	// A version change writes immediately
	if err := repo.TouchUserVersion(ctx, user.ID, "1.3.0"); err != nil {
		t.Fatalf("version change touch failed: %v", err)
	}
	got, _ = repo.GetUserByID(ctx, user.ID)
	if got.LastAppVersion == nil || *got.LastAppVersion != "1.3.0" {
		t.Errorf("expected version 1.3.0, got %v", got.LastAppVersion)
	}

	// The service-level touch ignores empty versions
	svc.touchUserVersion(ctx, user.ID, ClientInfo{AppVersion: ""})
	got, _ = repo.GetUserByID(ctx, user.ID)
	if got.LastAppVersion == nil || *got.LastAppVersion != "1.3.0" {
		t.Errorf("empty ClientInfo must not overwrite version, got %v", got.LastAppVersion)
	}
}

func TestFlushRecordsMonthlyActivity(t *testing.T) {
	svc, _ := setupTestService(t)
	ctx := context.Background()

	user, _ := svc.AdminCreateUser(ctx, "activity@simsli.de")
	hh, err := svc.CreateHousehold(ctx, user.ID, "hh-activity", "Activity Household")
	if err != nil {
		t.Fatalf("create household failed: %v", err)
	}

	now := time.Now().UTC()
	entry := func(id string, done bool) model.ListEntry {
		return model.ListEntry{
			ID: id, HouseholdID: hh.ID, ItemID: "i-" + id,
			Done: done, CreatedAt: now, UpdatedAt: now,
		}
	}

	// Items backing the entries (list_entries.item_id has an FK to items)
	if err := svc.Flush(ctx, user.ID, &model.FlushRequest{
		HouseholdID: hh.ID,
		Items: []model.Item{
			{ID: "i-e-1", HouseholdID: hh.ID, Name: "Milk", Type: "PERMANENT", CreatedAt: now, UpdatedAt: now},
			{ID: "i-e-2", HouseholdID: hh.ID, Name: "Bread", Type: "PERMANENT", CreatedAt: now, UpdatedAt: now},
			{ID: "i-e-3", HouseholdID: hh.ID, Name: "Candles", Type: "ONE_TIME", CreatedAt: now, UpdatedAt: now},
			{ID: "i-e-new", HouseholdID: hh.ID, Name: "Coffee", Type: "PERMANENT", CreatedAt: now, UpdatedAt: now},
		},
	}); err != nil {
		t.Fatalf("item flush failed: %v", err)
	}

	fetchActivity := func() model.MonthlyActivity {
		t.Helper()
		overview := svc.GetUsageOverview(ctx)
		if len(overview.MonthlyActivity) != 1 {
			t.Fatalf("expected 1 month row, got %+v", overview.MonthlyActivity)
		}
		return overview.MonthlyActivity[0]
	}

	// Add two active entries + one already-done entry (counts checked + added)
	if err := svc.Flush(ctx, user.ID, &model.FlushRequest{
		HouseholdID: hh.ID,
		ListEntries: []model.ListEntry{entry("e-1", false), entry("e-2", false), entry("e-3", true)},
	}); err != nil {
		t.Fatalf("flush failed: %v", err)
	}
	a := fetchActivity()
	if a.Added != 3 || a.Checked != 1 {
		t.Errorf("after add: expected added=3 checked=1, got %+v", a)
	}

	// Check off one entry
	if err := svc.Flush(ctx, user.ID, &model.FlushRequest{
		HouseholdID: hh.ID,
		ListEntries: []model.ListEntry{entry("e-1", true)},
	}); err != nil {
		t.Fatalf("flush failed: %v", err)
	}
	a = fetchActivity()
	if a.Added != 3 || a.Checked != 2 {
		t.Errorf("after check: expected added=3 checked=2, got %+v", a)
	}

	// Un-checking (undo) decrements
	if err := svc.Flush(ctx, user.ID, &model.FlushRequest{
		HouseholdID: hh.ID,
		ListEntries: []model.ListEntry{entry("e-1", false)},
	}); err != nil {
		t.Fatalf("flush failed: %v", err)
	}
	a = fetchActivity()
	if a.Added != 3 || a.Checked != 1 {
		t.Errorf("after undo: expected added=3 checked=1, got %+v", a)
	}

	// Re-checking a freshly re-added (GC'd and recreated) entry counts checked+added
	if err := svc.Flush(ctx, user.ID, &model.FlushRequest{
		HouseholdID: hh.ID,
		ListEntries: []model.ListEntry{entry("e-new", true)},
	}); err != nil {
		t.Fatalf("flush failed: %v", err)
	}
	a = fetchActivity()
	if a.Added != 4 || a.Checked != 2 {
		t.Errorf("after re-add: expected added=4 checked=2, got %+v", a)
	}
}
