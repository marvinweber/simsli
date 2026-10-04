package repository

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
	"net.marvinweber.simsli/server/internal/database"
	"net.marvinweber.simsli/server/internal/model"
)

var (
	ErrNotFound = errors.New("resource not found")
)

type Repository struct {
	db *database.DB
}

func New(db *database.DB) *Repository {
	return &Repository{db: db}
}

func formatTime(t time.Time) string {
	return t.UTC().Format(time.RFC3339Nano)
}

func parseTime(s string) (time.Time, error) {
	return time.Parse(time.RFC3339Nano, s)
}

func parseNullTime(s sql.NullString) *time.Time {
	if !s.Valid || s.String == "" {
		return nil
	}
	t, err := time.Parse(time.RFC3339Nano, s.String)
	if err != nil {
		return nil
	}
	return &t
}

// --- Users ---

func (r *Repository) GetUserByEmail(ctx context.Context, email string) (*model.User, error) {
	row := r.db.QueryRowContext(ctx, "SELECT id, email, created_at, updated_at FROM users WHERE email = ?", email)
	var u model.User
	var ca, ua string
	if err := row.Scan(&u.ID, &u.Email, &ca, &ua); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrNotFound
		}
		return nil, err
	}
	u.CreatedAt, _ = parseTime(ca)
	u.UpdatedAt, _ = parseTime(ua)
	return &u, nil
}

func (r *Repository) GetUserByID(ctx context.Context, id string) (*model.User, error) {
	row := r.db.QueryRowContext(ctx, "SELECT id, email, created_at, updated_at FROM users WHERE id = ?", id)
	var u model.User
	var ca, ua string
	if err := row.Scan(&u.ID, &u.Email, &ca, &ua); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrNotFound
		}
		return nil, err
	}
	u.CreatedAt, _ = parseTime(ca)
	u.UpdatedAt, _ = parseTime(ua)
	return &u, nil
}

func (r *Repository) CreateUser(ctx context.Context, user *model.User) error {
	now := time.Now().UTC()
	user.CreatedAt = now
	user.UpdatedAt = now
	if user.ID == "" {
		user.ID = uuid.NewString()
	}

	_, err := r.db.ExecContext(ctx,
		"INSERT INTO users (id, email, created_at, updated_at) VALUES (?, ?, ?, ?)",
		user.ID, user.Email, formatTime(user.CreatedAt), formatTime(user.UpdatedAt),
	)
	return err
}

func (r *Repository) ListUsers(ctx context.Context, limit, offset int) ([]model.User, error) {
	rows, err := r.db.QueryContext(ctx, "SELECT id, email, created_at, updated_at FROM users ORDER BY created_at DESC LIMIT ? OFFSET ?", limit, offset)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var users []model.User
	for rows.Next() {
		var u model.User
		var ca, ua string
		if err := rows.Scan(&u.ID, &u.Email, &ca, &ua); err != nil {
			return nil, err
		}
		u.CreatedAt, _ = parseTime(ca)
		u.UpdatedAt, _ = parseTime(ua)
		users = append(users, u)
	}
	return users, rows.Err()
}

func (r *Repository) CountUsers(ctx context.Context) (int, error) {
	var count int
	err := r.db.QueryRowContext(ctx, "SELECT COUNT(*) FROM users").Scan(&count)
	return count, err
}

// --- Magic Links ---

func (r *Repository) CreateMagicLink(ctx context.Context, link *model.MagicLink) error {
	_, err := r.db.ExecContext(ctx,
		"INSERT INTO magic_links (token, email, created_at, expires_at) VALUES (?, ?, ?, ?)",
		link.Token, link.Email, formatTime(link.CreatedAt), formatTime(link.ExpiresAt),
	)
	return err
}

func (r *Repository) GetMagicLink(ctx context.Context, token string) (*model.MagicLink, error) {
	row := r.db.QueryRowContext(ctx, "SELECT token, email, created_at, expires_at, used_at FROM magic_links WHERE token = ?", token)
	var ml model.MagicLink
	var ca, ea string
	var ua sql.NullString
	if err := row.Scan(&ml.Token, &ml.Email, &ca, &ea, &ua); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrNotFound
		}
		return nil, err
	}
	ml.CreatedAt, _ = parseTime(ca)
	ml.ExpiresAt, _ = parseTime(ea)
	ml.UsedAt = parseNullTime(ua)
	return &ml, nil
}

func (r *Repository) MarkMagicLinkUsed(ctx context.Context, token string) error {
	now := formatTime(time.Now().UTC())
	_, err := r.db.ExecContext(ctx, "UPDATE magic_links SET used_at = ? WHERE token = ?", now, token)
	return err
}

func (r *Repository) ListPendingMagicLinks(ctx context.Context, limit int) ([]model.MagicLink, error) {
	nowStr := formatTime(time.Now().UTC())
	rows, err := r.db.QueryContext(ctx,
		"SELECT token, email, created_at, expires_at FROM magic_links WHERE used_at IS NULL AND expires_at > ? ORDER BY created_at DESC LIMIT ?",
		nowStr, limit,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var links []model.MagicLink
	for rows.Next() {
		var ml model.MagicLink
		var ca, ea string
		if err := rows.Scan(&ml.Token, &ml.Email, &ca, &ea); err != nil {
			return nil, err
		}
		ml.CreatedAt, _ = parseTime(ca)
		ml.ExpiresAt, _ = parseTime(ea)
		links = append(links, ml)
	}
	return links, rows.Err()
}

// --- Refresh Tokens ---

func (r *Repository) CreateRefreshToken(ctx context.Context, rt *model.RefreshToken) error {
	_, err := r.db.ExecContext(ctx,
		"INSERT INTO refresh_tokens (token_hash, user_id, expires_at) VALUES (?, ?, ?)",
		rt.TokenHash, rt.UserID, formatTime(rt.ExpiresAt),
	)
	return err
}

func (r *Repository) GetRefreshToken(ctx context.Context, tokenHash string) (*model.RefreshToken, error) {
	row := r.db.QueryRowContext(ctx, "SELECT token_hash, user_id, expires_at, revoked_at FROM refresh_tokens WHERE token_hash = ?", tokenHash)
	var rt model.RefreshToken
	var ea string
	var ra sql.NullString
	if err := row.Scan(&rt.TokenHash, &rt.UserID, &ea, &ra); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrNotFound
		}
		return nil, err
	}
	rt.ExpiresAt, _ = parseTime(ea)
	rt.RevokedAt = parseNullTime(ra)
	return &rt, nil
}

func (r *Repository) RevokeRefreshToken(ctx context.Context, tokenHash string) error {
	now := formatTime(time.Now().UTC())
	_, err := r.db.ExecContext(ctx, "UPDATE refresh_tokens SET revoked_at = ? WHERE token_hash = ?", now, tokenHash)
	return err
}

// --- Households & Members ---

func (r *Repository) CreateHouseholdWithOwner(ctx context.Context, hh *model.Household, ownerUserID string) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	now := time.Now().UTC()
	hh.CreatedAt = now
	hh.UpdatedAt = now
	if hh.ID == "" {
		hh.ID = uuid.NewString()
	}
	if hh.Plan == "" {
		hh.Plan = "free"
	}
	if hh.Status == "" {
		hh.Status = "active"
	}

	_, err = tx.ExecContext(ctx,
		"INSERT INTO households (id, name, plan, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
		hh.ID, hh.Name, hh.Plan, hh.Status, formatTime(hh.CreatedAt), formatTime(hh.UpdatedAt),
	)
	if err != nil {
		return fmt.Errorf("failed to insert household: %w", err)
	}

	memberID := uuid.NewString()
	_, err = tx.ExecContext(ctx,
		"INSERT INTO household_members (id, household_id, user_id, role, joined_at, updated_at) VALUES (?, ?, ?, 'owner', ?, ?)",
		memberID, hh.ID, ownerUserID, formatTime(now), formatTime(now),
	)
	if err != nil {
		return fmt.Errorf("failed to add owner to household: %w", err)
	}

	return tx.Commit()
}

func (r *Repository) AddHouseholdMember(ctx context.Context, householdID, userID, role string) error {
	nowStr := formatTime(time.Now().UTC())
	memberID := uuid.NewString()
	_, err := r.db.ExecContext(ctx,
		"INSERT INTO household_members (id, household_id, user_id, role, joined_at, updated_at) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT(household_id, user_id) DO NOTHING",
		memberID, householdID, userID, role, nowStr, nowStr,
	)
	return err
}

func (r *Repository) GetHousehold(ctx context.Context, id string) (*model.Household, error) {
	row := r.db.QueryRowContext(ctx,
		"SELECT id, name, plan, status, current_period_end, created_at, updated_at, deleted_at FROM households WHERE id = ?",
		id,
	)
	var hh model.Household
	var cpe, da sql.NullString
	var ca, ua string
	if err := row.Scan(&hh.ID, &hh.Name, &hh.Plan, &hh.Status, &cpe, &ca, &ua, &da); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrNotFound
		}
		return nil, err
	}
	hh.CreatedAt, _ = parseTime(ca)
	hh.UpdatedAt, _ = parseTime(ua)
	hh.CurrentPeriodEnd = parseNullTime(cpe)
	hh.DeletedAt = parseNullTime(da)
	return &hh, nil
}

func (r *Repository) ListHouseholds(ctx context.Context, limit, offset int) ([]model.Household, error) {
	rows, err := r.db.QueryContext(ctx,
		"SELECT id, name, plan, status, current_period_end, created_at, updated_at, deleted_at FROM households WHERE deleted_at IS NULL ORDER BY created_at DESC LIMIT ? OFFSET ?",
		limit, offset,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var list []model.Household
	for rows.Next() {
		var hh model.Household
		var cpe, da sql.NullString
		var ca, ua string
		if err := rows.Scan(&hh.ID, &hh.Name, &hh.Plan, &hh.Status, &cpe, &ca, &ua, &da); err != nil {
			return nil, err
		}
		hh.CreatedAt, _ = parseTime(ca)
		hh.UpdatedAt, _ = parseTime(ua)
		hh.CurrentPeriodEnd = parseNullTime(cpe)
		hh.DeletedAt = parseNullTime(da)
		list = append(list, hh)
	}
	return list, rows.Err()
}

func (r *Repository) CountHouseholds(ctx context.Context) (int, error) {
	var count int
	err := r.db.QueryRowContext(ctx, "SELECT COUNT(*) FROM households WHERE deleted_at IS NULL").Scan(&count)
	return count, err
}

func (r *Repository) GetUserMemberships(ctx context.Context, userID string) ([]model.HouseholdMember, error) {
	rows, err := r.db.QueryContext(ctx,
		"SELECT id, household_id, user_id, role, joined_at, updated_at FROM household_members WHERE user_id = ?",
		userID,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var members []model.HouseholdMember
	for rows.Next() {
		var m model.HouseholdMember
		var ja, ua string
		if err := rows.Scan(&m.ID, &m.HouseholdID, &m.UserID, &m.Role, &ja, &ua); err != nil {
			return nil, err
		}
		m.JoinedAt, _ = parseTime(ja)
		m.UpdatedAt, _ = parseTime(ua)
		members = append(members, m)
	}
	return members, rows.Err()
}

func (r *Repository) GetHouseholdMembers(ctx context.Context, householdID string) ([]model.HouseholdMember, error) {
	rows, err := r.db.QueryContext(ctx,
		`SELECT hm.id, hm.household_id, hm.user_id, hm.role, hm.joined_at, hm.updated_at, u.email
		 FROM household_members hm
		 JOIN users u ON u.id = hm.user_id
		 WHERE hm.household_id = ?
		 ORDER BY CASE WHEN hm.role = 'owner' THEN 0 ELSE 1 END, hm.joined_at ASC`,
		householdID,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var members []model.HouseholdMember
	for rows.Next() {
		var m model.HouseholdMember
		var ja, ua string
		if err := rows.Scan(&m.ID, &m.HouseholdID, &m.UserID, &m.Role, &ja, &ua, &m.Email); err != nil {
			return nil, err
		}
		m.JoinedAt, _ = parseTime(ja)
		m.UpdatedAt, _ = parseTime(ua)
		members = append(members, m)
	}
	return members, rows.Err()
}

func (r *Repository) IsHouseholdMember(ctx context.Context, householdID, userID string) (bool, error) {
	var exists int
	err := r.db.QueryRowContext(ctx,
		"SELECT 1 FROM household_members WHERE household_id = ? AND user_id = ?",
		householdID, userID,
	).Scan(&exists)
	if errors.Is(err, sql.ErrNoRows) {
		return false, nil
	}
	return err == nil, err
}

func (r *Repository) IsHouseholdOwner(ctx context.Context, householdID, userID string) (bool, error) {
	var exists int
	err := r.db.QueryRowContext(ctx,
		"SELECT 1 FROM household_members WHERE household_id = ? AND user_id = ? AND role = 'owner'",
		householdID, userID,
	).Scan(&exists)
	if errors.Is(err, sql.ErrNoRows) {
		return false, nil
	}
	return err == nil, err
}

func (r *Repository) RemoveHouseholdMember(ctx context.Context, householdID, targetUserID string) error {
	_, err := r.db.ExecContext(ctx,
		"DELETE FROM household_members WHERE household_id = ? AND user_id = ?",
		householdID, targetUserID,
	)
	return err
}

func (r *Repository) SoftDeleteHousehold(ctx context.Context, householdID string) error {
	now := formatTime(time.Now().UTC())
	_, err := r.db.ExecContext(ctx, "UPDATE households SET deleted_at = ?, updated_at = ? WHERE id = ?", now, now, householdID)
	return err
}

func (r *Repository) UpdateHousehold(ctx context.Context, householdID, name string) error {
	now := formatTime(time.Now().UTC())
	_, err := r.db.ExecContext(ctx, "UPDATE households SET name = ?, updated_at = ? WHERE id = ? AND deleted_at IS NULL", name, now, householdID)
	return err
}

// --- Invites ---

func (r *Repository) CreateInviteToken(ctx context.Context, token *model.InviteToken) error {
	_, err := r.db.ExecContext(ctx,
		"INSERT INTO invite_tokens (token, household_id, created_by, created_at, expires_at) VALUES (?, ?, ?, ?, ?)",
		token.Token, token.HouseholdID, token.CreatedBy, formatTime(token.CreatedAt), formatTime(token.ExpiresAt),
	)
	return err
}

func (r *Repository) GetInviteToken(ctx context.Context, token string) (*model.InviteToken, error) {
	row := r.db.QueryRowContext(ctx,
		"SELECT token, household_id, created_by, created_at, expires_at, used_at, used_by FROM invite_tokens WHERE token = ?",
		token,
	)
	var it model.InviteToken
	var ca, ea string
	var ua, ub sql.NullString
	if err := row.Scan(&it.Token, &it.HouseholdID, &it.CreatedBy, &ca, &ea, &ua, &ub); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrNotFound
		}
		return nil, err
	}
	it.CreatedAt, _ = parseTime(ca)
	it.ExpiresAt, _ = parseTime(ea)
	it.UsedAt = parseNullTime(ua)
	if ub.Valid {
		it.UsedBy = &ub.String
	}
	return &it, nil
}

func (r *Repository) AcceptInvite(ctx context.Context, token, userID string) (string, error) {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return "", err
	}
	defer tx.Rollback()

	now := time.Now().UTC()
	nowStr := formatTime(now)

	// 1. Fetch token and lock
	var householdID string
	var expiresAtStr string
	var usedAt sql.NullString
	err = tx.QueryRowContext(ctx,
		"SELECT household_id, expires_at, used_at FROM invite_tokens WHERE token = ?",
		token,
	).Scan(&householdID, &expiresAtStr, &usedAt)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return "", errors.New("invite token not found")
		}
		return "", err
	}

	if usedAt.Valid {
		return "", errors.New("invite token already used")
	}

	exp, _ := parseTime(expiresAtStr)
	if now.After(exp) {
		return "", errors.New("invite token expired")
	}

	// 2. Mark token used
	_, err = tx.ExecContext(ctx,
		"UPDATE invite_tokens SET used_at = ?, used_by = ? WHERE token = ?",
		nowStr, userID, token,
	)
	if err != nil {
		return "", err
	}

	// 3. Add to new household as member
	memberID := uuid.NewString()
	_, err = tx.ExecContext(ctx,
		"INSERT INTO household_members (id, household_id, user_id, role, joined_at, updated_at) VALUES (?, ?, ?, 'member', ?, ?) ON CONFLICT(household_id, user_id) DO NOTHING",
		memberID, householdID, userID, nowStr, nowStr,
	)
	if err != nil {
		return "", fmt.Errorf("failed to join household: %w", err)
	}

	if err := tx.Commit(); err != nil {
		return "", err
	}

	return householdID, nil
}

// --- Delta Sync ---

func (r *Repository) GetDeltas(ctx context.Context, householdID string, since time.Time) (*model.DeltaResponse, error) {
	sinceStr := formatTime(since)
	resp := &model.DeltaResponse{
		ServerTime: time.Now().UTC(),
	}

	// Household (if updated since)
	var hh model.Household
	var hhCa, hhUa string
	var hhDa sql.NullString
	if err := r.db.QueryRowContext(ctx,
		"SELECT id, name, created_at, updated_at, deleted_at FROM households WHERE id = ? AND updated_at > ?",
		householdID, sinceStr,
	).Scan(&hh.ID, &hh.Name, &hhCa, &hhUa, &hhDa); err == nil {
		hh.CreatedAt, _ = parseTime(hhCa)
		hh.UpdatedAt, _ = parseTime(hhUa)
		hh.DeletedAt = parseNullTime(hhDa)
		resp.Household = &hh
	}

	// Stores
	storeRows, err := r.db.QueryContext(ctx,
		"SELECT id, household_id, name, sort_order, created_at, updated_at, deleted_at FROM stores WHERE household_id = ? AND updated_at > ?",
		householdID, sinceStr,
	)
	if err != nil {
		return nil, fmt.Errorf("query stores: %w", err)
	}
	defer storeRows.Close()
	for storeRows.Next() {
		var s model.Store
		var ca, ua string
		var da sql.NullString
		if err := storeRows.Scan(&s.ID, &s.HouseholdID, &s.Name, &s.SortOrder, &ca, &ua, &da); err != nil {
			return nil, err
		}
		s.CreatedAt, _ = parseTime(ca)
		s.UpdatedAt, _ = parseTime(ua)
		s.DeletedAt = parseNullTime(da)
		resp.Stores = append(resp.Stores, s)
	}

	// Categories
	catRows, err := r.db.QueryContext(ctx,
		"SELECT id, household_id, name, emoji, sort_order, created_at, updated_at, deleted_at FROM categories WHERE household_id = ? AND updated_at > ?",
		householdID, sinceStr,
	)
	if err != nil {
		return nil, fmt.Errorf("query categories: %w", err)
	}
	defer catRows.Close()
	for catRows.Next() {
		var c model.Category
		var ca, ua string
		var da sql.NullString
		if err := catRows.Scan(&c.ID, &c.HouseholdID, &c.Name, &c.Emoji, &c.SortOrder, &ca, &ua, &da); err != nil {
			return nil, err
		}
		c.CreatedAt, _ = parseTime(ca)
		c.UpdatedAt, _ = parseTime(ua)
		c.DeletedAt = parseNullTime(da)
		resp.Categories = append(resp.Categories, c)
	}

	// Items
	itemRows, err := r.db.QueryContext(ctx,
		"SELECT id, household_id, category_id, name, notes, type, default_unit, sort_order, created_at, updated_at, deleted_at FROM items WHERE household_id = ? AND updated_at > ?",
		householdID, sinceStr,
	)
	if err != nil {
		return nil, fmt.Errorf("query items: %w", err)
	}
	defer itemRows.Close()
	for itemRows.Next() {
		var i model.Item
		var catID, du, da sql.NullString
		var ca, ua string
		if err := itemRows.Scan(&i.ID, &i.HouseholdID, &catID, &i.Name, &i.Notes, &i.Type, &du, &i.SortOrder, &ca, &ua, &da); err != nil {
			return nil, err
		}
		if catID.Valid {
			i.CategoryID = &catID.String
		}
		if du.Valid {
			i.DefaultUnit = &du.String
		}
		i.CreatedAt, _ = parseTime(ca)
		i.UpdatedAt, _ = parseTime(ua)
		i.DeletedAt = parseNullTime(da)
		resp.Items = append(resp.Items, i)
	}

	// List entries
	entryRows, err := r.db.QueryContext(ctx,
		"SELECT id, household_id, item_id, quantity, unit, comment, done, completed_at, created_by, created_at, updated_at FROM list_entries WHERE household_id = ? AND updated_at > ?",
		householdID, sinceStr,
	)
	if err != nil {
		return nil, fmt.Errorf("query list_entries: %w", err)
	}
	defer entryRows.Close()
	for entryRows.Next() {
		var le model.ListEntry
		var q sql.NullFloat64
		var u, comm, cb, compAt sql.NullString
		var doneInt int
		var ca, ua string
		if err := entryRows.Scan(&le.ID, &le.HouseholdID, &le.ItemID, &q, &u, &comm, &doneInt, &compAt, &cb, &ca, &ua); err != nil {
			return nil, err
		}
		if q.Valid {
			le.Quantity = &q.Float64
		}
		if u.Valid {
			le.Unit = &u.String
		}
		if comm.Valid {
			le.Comment = &comm.String
		}
		if cb.Valid {
			le.CreatedBy = &cb.String
		}
		le.Done = doneInt == 1
		le.CompletedAt = parseNullTime(compAt)
		le.CreatedAt, _ = parseTime(ca)
		le.UpdatedAt, _ = parseTime(ua)
		resp.ListEntries = append(resp.ListEntries, le)
	}

	// Full-reconcile tables (item_stores, store_categories)
	isRows, err := r.db.QueryContext(ctx,
		"SELECT item_id, store_id, created_at FROM item_stores WHERE item_id IN (SELECT id FROM items WHERE household_id = ?)",
		householdID,
	)
	if err != nil {
		return nil, fmt.Errorf("query item_stores: %w", err)
	}
	defer isRows.Close()
	for isRows.Next() {
		var is model.ItemStore
		var ca string
		if err := isRows.Scan(&is.ItemID, &is.StoreID, &ca); err != nil {
			return nil, err
		}
		is.CreatedAt, _ = parseTime(ca)
		resp.ItemStores = append(resp.ItemStores, is)
	}

	scRows, err := r.db.QueryContext(ctx,
		"SELECT store_id, category_id, sort_order FROM store_categories WHERE store_id IN (SELECT id FROM stores WHERE household_id = ?)",
		householdID,
	)
	if err != nil {
		return nil, fmt.Errorf("query store_categories: %w", err)
	}
	defer scRows.Close()
	for scRows.Next() {
		var sc model.StoreCategory
		if err := scRows.Scan(&sc.StoreID, &sc.CategoryID, &sc.SortOrder); err != nil {
			return nil, err
		}
		resp.StoreCategories = append(resp.StoreCategories, sc)
	}

	return resp, nil
}

func (r *Repository) Flush(ctx context.Context, req *model.FlushRequest) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	nowStr := formatTime(time.Now().UTC())

	// Upsert Household if present
	if req.Household != nil {
		_, err := tx.ExecContext(ctx,
			"UPDATE households SET name = ?, updated_at = ? WHERE id = ? AND deleted_at IS NULL",
			req.Household.Name, nowStr, req.Household.ID,
		)
		if err != nil {
			return fmt.Errorf("flush household: %w", err)
		}
	}

	// Upsert Stores
	for _, s := range req.Stores {
		daStr := sql.NullString{}
		if s.DeletedAt != nil {
			daStr = sql.NullString{String: formatTime(*s.DeletedAt), Valid: true}
		}
		_, err := tx.ExecContext(ctx,
			`INSERT INTO stores (id, household_id, name, sort_order, created_at, updated_at, deleted_at)
			 VALUES (?, ?, ?, ?, ?, ?, ?)
			 ON CONFLICT(id) DO UPDATE SET
			   name=excluded.name,
			   sort_order=excluded.sort_order,
			   updated_at=excluded.updated_at,
			   deleted_at=excluded.deleted_at`,
			s.ID, req.HouseholdID, s.Name, s.SortOrder, formatTime(s.CreatedAt), formatTime(s.UpdatedAt), daStr,
		)
		if err != nil {
			return fmt.Errorf("flush store: %w", err)
		}
	}

	// Upsert Categories
	for _, c := range req.Categories {
		daStr := sql.NullString{}
		if c.DeletedAt != nil {
			daStr = sql.NullString{String: formatTime(*c.DeletedAt), Valid: true}
		}
		_, err := tx.ExecContext(ctx,
			`INSERT INTO categories (id, household_id, name, emoji, sort_order, created_at, updated_at, deleted_at)
			 VALUES (?, ?, ?, ?, ?, ?, ?, ?)
			 ON CONFLICT(id) DO UPDATE SET
			   name=excluded.name,
			   emoji=excluded.emoji,
			   sort_order=excluded.sort_order,
			   updated_at=excluded.updated_at,
			   deleted_at=excluded.deleted_at`,
			c.ID, req.HouseholdID, c.Name, c.Emoji, c.SortOrder, formatTime(c.CreatedAt), formatTime(c.UpdatedAt), daStr,
		)
		if err != nil {
			return fmt.Errorf("flush category: %w", err)
		}
	}

	// Upsert Items
	for _, it := range req.Items {
		daStr := sql.NullString{}
		if it.DeletedAt != nil {
			daStr = sql.NullString{String: formatTime(*it.DeletedAt), Valid: true}
		}
		_, err := tx.ExecContext(ctx,
			`INSERT INTO items (id, household_id, category_id, name, notes, type, default_unit, sort_order, created_at, updated_at, deleted_at)
			 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			 ON CONFLICT(id) DO UPDATE SET
			   category_id=excluded.category_id,
			   name=excluded.name,
			   notes=excluded.notes,
			   type=excluded.type,
			   default_unit=excluded.default_unit,
			   sort_order=excluded.sort_order,
			   updated_at=excluded.updated_at,
			   deleted_at=excluded.deleted_at`,
			it.ID, req.HouseholdID, it.CategoryID, it.Name, it.Notes, it.Type, it.DefaultUnit, it.SortOrder, formatTime(it.CreatedAt), formatTime(it.UpdatedAt), daStr,
		)
		if err != nil {
			return fmt.Errorf("flush item: %w", err)
		}
	}

	// Upsert List Entries
	for _, le := range req.ListEntries {
		doneInt := 0
		if le.Done {
			doneInt = 1
		}
		compAtStr := sql.NullString{}
		if le.CompletedAt != nil {
			compAtStr = sql.NullString{String: formatTime(*le.CompletedAt), Valid: true}
		}
		_, err := tx.ExecContext(ctx,
			`INSERT INTO list_entries (id, household_id, item_id, quantity, unit, comment, done, completed_at, created_by, created_at, updated_at)
			 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			 ON CONFLICT(household_id, item_id) DO UPDATE SET
			   quantity=excluded.quantity,
			   unit=excluded.unit,
			   comment=excluded.comment,
			   done=excluded.done,
			   completed_at=excluded.completed_at,
			   updated_at=excluded.updated_at`,
			le.ID, req.HouseholdID, le.ItemID, le.Quantity, le.Unit, le.Comment, doneInt, compAtStr, le.CreatedBy, formatTime(le.CreatedAt), formatTime(le.UpdatedAt),
		)
		if err != nil {
			return fmt.Errorf("flush list_entry: %w", err)
		}
	}

	// Upsert ItemStores (reconciled)
	for _, is := range req.ItemStores {
		_, err := tx.ExecContext(ctx,
			"INSERT INTO item_stores (item_id, store_id, created_at) VALUES (?, ?, ?) ON CONFLICT(item_id, store_id) DO NOTHING",
			is.ItemID, is.StoreID, formatTime(is.CreatedAt),
		)
		if err != nil {
			return fmt.Errorf("flush item_store: %w", err)
		}
	}

	// Upsert StoreCategories
	for _, sc := range req.StoreCategories {
		_, err := tx.ExecContext(ctx,
			`INSERT INTO store_categories (store_id, category_id, sort_order) VALUES (?, ?, ?)
			 ON CONFLICT(store_id, category_id) DO UPDATE SET sort_order=excluded.sort_order`,
			sc.StoreID, sc.CategoryID, sc.SortOrder,
		)
		if err != nil {
			return fmt.Errorf("flush store_category: %w", err)
		}
	}

	// Soft Deletions
	for _, id := range req.DeletedStores {
		_, err := tx.ExecContext(ctx, "UPDATE stores SET deleted_at = ?, updated_at = ? WHERE id = ? AND household_id = ?", nowStr, nowStr, id, req.HouseholdID)
		if err != nil {
			return err
		}
	}
	for _, id := range req.DeletedCategories {
		_, err := tx.ExecContext(ctx, "UPDATE categories SET deleted_at = ?, updated_at = ? WHERE id = ? AND household_id = ?", nowStr, nowStr, id, req.HouseholdID)
		if err != nil {
			return err
		}
	}
	for _, id := range req.DeletedItems {
		_, err := tx.ExecContext(ctx, "UPDATE items SET deleted_at = ?, updated_at = ? WHERE id = ? AND household_id = ?", nowStr, nowStr, id, req.HouseholdID)
		if err != nil {
			return err
		}
	}
	for _, id := range req.DeletedEntries {
		_, err := tx.ExecContext(ctx, "DELETE FROM list_entries WHERE id = ? AND household_id = ?", id, req.HouseholdID)
		if err != nil {
			return err
		}
	}

	return tx.Commit()
}

func (r *Repository) GetDatabaseDriver() string {
	return r.db.Driver
}
