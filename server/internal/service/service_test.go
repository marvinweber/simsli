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

func TestGetHouseholdStats(t *testing.T) {
	svc, _ := setupTestService(t)
	ctx := context.Background()

	owner, _ := svc.AdminCreateUser(ctx, "stats-owner@simsli.de")
	outsider, _ := svc.AdminCreateUser(ctx, "stats-outsider@simsli.de")
	hh, err := svc.CreateHousehold(ctx, owner.ID, "hh-stats", "Stats Household")
	if err != nil {
		t.Fatalf("create household failed: %v", err)
	}

	// No activity yet → empty list, not error
	stats, err := svc.GetHouseholdStats(ctx, owner.ID, hh.ID)
	if err != nil {
		t.Fatalf("get stats failed: %v", err)
	}
	if len(stats.Months) != 0 {
		t.Errorf("expected empty months, got %+v", stats.Months)
	}

	// Generate activity: item + entry added already done
	now := time.Now().UTC()
	if err := svc.Flush(ctx, owner.ID, &model.FlushRequest{
		HouseholdID: hh.ID,
		Items: []model.Item{
			{ID: "i-s1", HouseholdID: hh.ID, Name: "Milk", Type: "PERMANENT", CreatedAt: now, UpdatedAt: now},
		},
		ListEntries: []model.ListEntry{
			{ID: "le-s1", HouseholdID: hh.ID, ItemID: "i-s1", Done: true, CompletedAt: &now, CreatedAt: now, UpdatedAt: now},
		},
	}); err != nil {
		t.Fatalf("flush failed: %v", err)
	}

	stats, err = svc.GetHouseholdStats(ctx, owner.ID, hh.ID)
	if err != nil {
		t.Fatalf("get stats failed: %v", err)
	}
	if len(stats.Months) != 1 {
		t.Fatalf("expected 1 month row, got %+v", stats.Months)
	}
	m := stats.Months[0]
	if m.Year != now.Year() || m.Month != int(now.Month()) {
		t.Errorf("expected %d-%d, got %d-%d", now.Year(), now.Month(), m.Year, m.Month)
	}
	if m.ItemsChecked != 1 || m.ItemsAdded != 1 {
		t.Errorf("expected checked=1 added=1, got %+v", m)
	}

	// Non-members are forbidden
	if _, err := svc.GetHouseholdStats(ctx, outsider.ID, hh.ID); err != ErrForbidden {
		t.Errorf("expected ErrForbidden for outsider, got %v", err)
	}
}

// ADR 0019 regression test: a flush carrying a stale client-stamped updated_at
// (lagging device clock, stale outbox push re-sending old state) must still be
// visible to devices whose watermark already sits past that timestamp. The
// server re-stamps updated_at with server time; without that, the delta query
// (updated_at > since) never selects the row and the write is lost forever.
func TestFlushWithStaleClientTimestampRemainsVisible(t *testing.T) {
	svc, _ := setupTestService(t)
	ctx := context.Background()

	user, err := svc.AdminCreateUser(ctx, "user1@simsli.de")
	if err != nil {
		t.Fatalf("create user failed: %v", err)
	}

	hh, err := svc.CreateHousehold(ctx, user.ID, "hh-drift-1", "Drift Household")
	if err != nil {
		t.Fatalf("create household failed: %v", err)
	}

	// Client clock years behind the server (or a stale outbox row): the pushed
	// updated_at is far below any watermark another device could hold.
	stale := time.Date(2020, 1, 1, 0, 0, 0, 0, time.UTC)
	flushReq := &model.FlushRequest{
		HouseholdID: hh.ID,
		Items: []model.Item{
			{ID: "i-1", HouseholdID: hh.ID, Name: "Milk", Type: "PERMANENT", SortOrder: 1, CreatedAt: stale, UpdatedAt: stale},
		},
		ListEntries: []model.ListEntry{
			{ID: "e-1", HouseholdID: hh.ID, ItemID: "i-1", Done: true, CompletedAt: &stale, CreatedAt: stale, UpdatedAt: stale},
		},
	}
	if err := svc.Flush(ctx, user.ID, flushReq); err != nil {
		t.Fatalf("flush failed: %v", err)
	}

	// A device whose watermark is "recently" (i.e. past the stale timestamp)
	// pulls: the rows must be delivered, and their updated_at must be server
	// time, not the stale client value.
	since := time.Now().UTC().Add(-time.Minute)
	deltas, err := svc.GetDeltas(ctx, user.ID, hh.ID, since)
	if err != nil {
		t.Fatalf("get deltas failed: %v", err)
	}

	var item *model.Item
	for i := range deltas.Items {
		if deltas.Items[i].ID == "i-1" {
			item = &deltas.Items[i]
		}
	}
	if item == nil {
		t.Fatal("expected flushed item in deltas despite stale client updated_at")
	}
	if !item.UpdatedAt.After(stale) {
		t.Errorf("expected item updated_at re-stamped to server time, got %v", item.UpdatedAt)
	}
	if !item.CreatedAt.Equal(stale) {
		t.Errorf("expected created_at preserved from client, got %v", item.CreatedAt)
	}

	var entry *model.ListEntry
	for i := range deltas.ListEntries {
		if deltas.ListEntries[i].ID == "e-1" {
			entry = &deltas.ListEntries[i]
		}
	}
	if entry == nil {
		t.Fatal("expected flushed list entry in deltas despite stale client updated_at")
	}
	if !entry.Done {
		t.Error("expected entry done=true")
	}
	if !entry.UpdatedAt.After(stale) {
		t.Errorf("expected entry updated_at re-stamped to server time, got %v", entry.UpdatedAt)
	}
	if entry.CompletedAt == nil || !entry.CompletedAt.Equal(stale) {
		t.Errorf("expected completed_at preserved from client, got %v", entry.CompletedAt)
	}

	// The delta response's server_time must also be past the re-stamped rows,
	// so the pulling device's next watermark does not fall behind them.
	if !deltas.ServerTime.After(entry.UpdatedAt) && !deltas.ServerTime.Equal(entry.UpdatedAt) {
		t.Errorf("expected server_time %v >= entry updated_at %v", deltas.ServerTime, entry.UpdatedAt)
	}
}

// STORE-7 round trip: store groups, store group selections and item_stores
// membership survive a flush → deltas cycle, and join-row deletions pushed as
// composite ids (deleted_item_store_groups / deleted_item_stores) are actually
// deleted server-side — without that, the full reconcile on other devices
// would resurrect the deleted pairs on every pull (ADR 0020).
func TestFlushStoreGroupsAndJoinDeletions(t *testing.T) {
	svc, _ := setupTestService(t)
	ctx := context.Background()

	user, err := svc.AdminCreateUser(ctx, "user1@simsli.de")
	if err != nil {
		t.Fatalf("create user failed: %v", err)
	}
	hh, err := svc.CreateHousehold(ctx, user.ID, "hh-groups", "Groups Household")
	if err != nil {
		t.Fatalf("create household failed: %v", err)
	}

	now := time.Now().UTC()
	supermarkt := "sg-1"
	flushReq := &model.FlushRequest{
		HouseholdID: hh.ID,
		StoreGroups: []model.StoreGroup{
			{ID: supermarkt, HouseholdID: hh.ID, Name: "Supermarkt", SortOrder: 1, CreatedAt: now, UpdatedAt: now},
		},
		Stores: []model.Store{
			{ID: "s-1", HouseholdID: hh.ID, Name: "REWE", SortOrder: 1, GroupID: &supermarkt, CreatedAt: now, UpdatedAt: now},
			{ID: "s-2", HouseholdID: hh.ID, Name: "Aldi", SortOrder: 2, GroupID: &supermarkt, CreatedAt: now, UpdatedAt: now},
			{ID: "s-3", HouseholdID: hh.ID, Name: "dm", SortOrder: 3, CreatedAt: now, UpdatedAt: now},
		},
		Items: []model.Item{
			{ID: "i-1", HouseholdID: hh.ID, Name: "Bananen", Type: "PERMANENT", SortOrder: 1, CreatedAt: now, UpdatedAt: now},
			{ID: "i-2", HouseholdID: hh.ID, Name: "Spülmittel", Type: "PERMANENT", SortOrder: 2, CreatedAt: now, UpdatedAt: now},
		},
		ItemStoreGroups: []model.ItemStoreGroup{
			{ItemID: "i-1", GroupID: supermarkt, CreatedAt: now},
		},
		ItemStores: []model.ItemStore{
			// i-2: Supermarkt selection minus Aldi (excluded), dm explicit favorite
			{ItemID: "i-2", StoreID: "s-2", Membership: "EXCLUDED", CreatedAt: now},
			{ItemID: "i-2", StoreID: "s-3", Membership: "MEMBER", Favorite: true, CreatedAt: now},
		},
	}
	if err := svc.Flush(ctx, user.ID, flushReq); err != nil {
		t.Fatalf("flush failed: %v", err)
	}

	deltas, err := svc.GetDeltas(ctx, user.ID, hh.ID, time.Time{})
	if err != nil {
		t.Fatalf("get deltas failed: %v", err)
	}
	if len(deltas.StoreGroups) != 1 || deltas.StoreGroups[0].ID != supermarkt || deltas.StoreGroups[0].Name != "Supermarkt" {
		t.Fatalf("expected store group in deltas, got %+v", deltas.StoreGroups)
	}
	var rewe *model.Store
	for i := range deltas.Stores {
		if deltas.Stores[i].ID == "s-1" {
			rewe = &deltas.Stores[i]
		}
	}
	if rewe == nil || rewe.GroupID == nil || *rewe.GroupID != supermarkt {
		t.Fatalf("expected REWE with group_id in deltas, got %+v", rewe)
	}
	if len(deltas.ItemStoreGroups) != 1 || deltas.ItemStoreGroups[0].ItemID != "i-1" {
		t.Fatalf("expected item_store_groups in deltas, got %+v", deltas.ItemStoreGroups)
	}
	var excluded *model.ItemStore
	var dmFav *model.ItemStore
	for i := range deltas.ItemStores {
		if deltas.ItemStores[i].ItemID == "i-2" && deltas.ItemStores[i].StoreID == "s-2" {
			excluded = &deltas.ItemStores[i]
		}
		if deltas.ItemStores[i].ItemID == "i-2" && deltas.ItemStores[i].StoreID == "s-3" {
			dmFav = &deltas.ItemStores[i]
		}
	}
	if excluded == nil || excluded.Membership != "EXCLUDED" {
		t.Fatalf("expected EXCLUDED membership in deltas, got %+v", excluded)
	}
	if dmFav == nil || dmFav.Membership != "MEMBER" || !dmFav.Favorite {
		t.Fatalf("expected MEMBER membership with Favorite=true in deltas, got %+v", dmFav)
	}
	// Legacy clients send no membership at all — it must come back as MEMBER.
	if err := svc.Flush(ctx, user.ID, &model.FlushRequest{
		HouseholdID: hh.ID,
		ItemStores:  []model.ItemStore{{ItemID: "i-1", StoreID: "s-1", CreatedAt: now}},
	}); err != nil {
		t.Fatalf("legacy flush failed: %v", err)
	}
	deltas, err = svc.GetDeltas(ctx, user.ID, hh.ID, time.Time{})
	if err != nil {
		t.Fatalf("get deltas failed: %v", err)
	}
	for _, is := range deltas.ItemStores {
		if is.ItemID == "i-1" && is.StoreID == "s-1" && is.Membership != "MEMBER" {
			t.Errorf("expected legacy membership to normalize to MEMBER, got %q", is.Membership)
		}
	}

	// Now the client pushes join-row deletions (rows already gone locally).
	if err := svc.Flush(ctx, user.ID, &model.FlushRequest{
		HouseholdID:            hh.ID,
		DeletedItemStoreGroups: []string{"i-1:" + supermarkt},
		DeletedItemStores:      []string{"i-2:s-2"},
	}); err != nil {
		t.Fatalf("deletion flush failed: %v", err)
	}

	deltas, err = svc.GetDeltas(ctx, user.ID, hh.ID, time.Time{})
	if err != nil {
		t.Fatalf("get deltas after deletions failed: %v", err)
	}
	for _, isg := range deltas.ItemStoreGroups {
		if isg.ItemID == "i-1" {
			t.Errorf("item_store_group i-1:%s resurrected after deletion", supermarkt)
		}
	}
	for _, is := range deltas.ItemStores {
		if is.ItemID == "i-2" && is.StoreID == "s-2" {
			t.Error("excluded item_store i-2:s-2 resurrected after deletion")
		}
	}
}

// ADR 0019 regression, store_groups edition: a flushed group with a stale
// client-stamped updated_at must still be delivered to a device whose
// watermark already sits past that timestamp.
func TestFlushStaleStoreGroupTimestampRemainsVisible(t *testing.T) {
	svc, _ := setupTestService(t)
	ctx := context.Background()

	user, err := svc.AdminCreateUser(ctx, "user1@simsli.de")
	if err != nil {
		t.Fatalf("create user failed: %v", err)
	}
	hh, err := svc.CreateHousehold(ctx, user.ID, "hh-drift-2", "Drift Household 2")
	if err != nil {
		t.Fatalf("create household failed: %v", err)
	}

	stale := time.Date(2020, 1, 1, 0, 0, 0, 0, time.UTC)
	if err := svc.Flush(ctx, user.ID, &model.FlushRequest{
		HouseholdID: hh.ID,
		StoreGroups: []model.StoreGroup{
			{ID: "sg-stale", HouseholdID: hh.ID, Name: "Supermarkt", SortOrder: 1, CreatedAt: stale, UpdatedAt: stale},
		},
	}); err != nil {
		t.Fatalf("flush failed: %v", err)
	}

	deltas, err := svc.GetDeltas(ctx, user.ID, hh.ID, time.Now().UTC().Add(-time.Minute))
	if err != nil {
		t.Fatalf("get deltas failed: %v", err)
	}
	var group *model.StoreGroup
	for i := range deltas.StoreGroups {
		if deltas.StoreGroups[i].ID == "sg-stale" {
			group = &deltas.StoreGroups[i]
		}
	}
	if group == nil {
		t.Fatal("expected flushed store group in deltas despite stale client updated_at")
	}
	if !group.UpdatedAt.After(stale) {
		t.Errorf("expected store group updated_at re-stamped to server time, got %v", group.UpdatedAt)
	}
	if !group.CreatedAt.Equal(stale) {
		t.Errorf("expected created_at preserved from client, got %v", group.CreatedAt)
	}
}
