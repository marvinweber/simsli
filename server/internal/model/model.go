package model

import "time"

type User struct {
	ID        string    `json:"id"`
	Email     string    `json:"email"`
	CreatedAt time.Time `json:"created_at"`
	UpdatedAt time.Time `json:"updated_at"`

	// Usage metrics, reported by clients and updated at most hourly. Aggregate,
	// not device-level: one value per account.
	LastSeenAt     *time.Time `json:"last_seen_at,omitempty"`
	LastAppVersion *string    `json:"last_app_version,omitempty"`
}

// VersionDistribution aggregates the app versions users last reported.
type VersionDistribution struct {
	AppVersion string `json:"app_version"`
	Users      int    `json:"users"`
}

// MonthlyActivity is one household's checked/added counters for one month.
type MonthlyActivity struct {
	Year    int `json:"year"`
	Month   int `json:"month"`
	Checked int `json:"checked"`
	Added   int `json:"added"`
}

type Household struct {
	ID               string     `json:"id"`
	Name             string     `json:"name"`
	Icon             *string    `json:"icon,omitempty"` // nullable emoji; nil on Flush = legacy client, preserve
	Plan             string     `json:"plan"`
	Status           string     `json:"status"`
	CurrentPeriodEnd *time.Time `json:"current_period_end,omitempty"`
	CreatedAt        time.Time  `json:"created_at"`
	UpdatedAt        time.Time  `json:"updated_at"`
	DeletedAt        *time.Time `json:"deleted_at,omitempty"`
}

type HouseholdMember struct {
	ID          string    `json:"id"`
	HouseholdID string    `json:"household_id"`
	UserID      string    `json:"user_id"`
	Role        string    `json:"role"` // "owner", "member"
	JoinedAt    time.Time `json:"joined_at"`
	UpdatedAt   time.Time `json:"updated_at"`

	// Joined fields for member listing
	Email string `json:"email,omitempty"`
}

type Store struct {
	ID          string     `json:"id"`
	HouseholdID string     `json:"household_id"`
	Name        string     `json:"name"`
	SortOrder   float64    `json:"sort_order"`
	CreatedAt   time.Time  `json:"created_at"`
	UpdatedAt   time.Time  `json:"updated_at"`
	DeletedAt   *time.Time `json:"deleted_at,omitempty"`
}

type Category struct {
	ID          string     `json:"id"`
	HouseholdID string     `json:"household_id"`
	Name        string     `json:"name"`
	Emoji       string     `json:"emoji"`
	SortOrder   float64    `json:"sort_order"`
	CreatedAt   time.Time  `json:"created_at"`
	UpdatedAt   time.Time  `json:"updated_at"`
	DeletedAt   *time.Time `json:"deleted_at,omitempty"`
}

type StoreCategory struct {
	StoreID    string  `json:"store_id"`
	CategoryID string  `json:"category_id"`
	SortOrder  float64 `json:"sort_order"`
}

type ItemLink struct {
	URL   string  `json:"url"`
	Title *string `json:"title,omitempty"`
}

type Item struct {
	ID          string     `json:"id"`
	HouseholdID string     `json:"household_id"`
	CategoryID  *string    `json:"category_id,omitempty"`
	Name        string     `json:"name"`
	Notes       string     `json:"notes"`
	Type        string     `json:"type"` // "PERMANENT", "ONE_TIME", "CHECKLIST"
	DefaultUnit *string    `json:"default_unit,omitempty"`
	SortOrder   float64    `json:"sort_order"`
	Links       []ItemLink `json:"links"`
	CreatedAt   time.Time  `json:"created_at"`
	UpdatedAt   time.Time  `json:"updated_at"`
	DeletedAt   *time.Time `json:"deleted_at,omitempty"`
}

type ItemStore struct {
	ItemID    string    `json:"item_id"`
	StoreID   string    `json:"store_id"`
	CreatedAt time.Time `json:"created_at"`
}

type ListEntry struct {
	ID          string     `json:"id"`
	HouseholdID string     `json:"household_id"`
	ItemID      string     `json:"item_id"`
	Quantity    *float64   `json:"quantity,omitempty"`
	Unit        *string    `json:"unit,omitempty"`
	Comment     *string    `json:"comment,omitempty"`
	Done        bool       `json:"done"`
	CompletedAt *time.Time `json:"completed_at,omitempty"`
	CreatedBy   *string    `json:"created_by,omitempty"`
	CreatedAt   time.Time  `json:"created_at"`
	UpdatedAt   time.Time  `json:"updated_at"`
}

type InviteToken struct {
	Token       string     `json:"token"`
	HouseholdID string     `json:"household_id"`
	CreatedBy   string     `json:"created_by"`
	CreatedAt   time.Time  `json:"created_at"`
	ExpiresAt   time.Time  `json:"expires_at"`
	UsedAt      *time.Time `json:"used_at,omitempty"`
	UsedBy      *string    `json:"used_by,omitempty"`
}

type MagicLink struct {
	Token     string     `json:"token"`
	Email     string     `json:"email"`
	CreatedAt time.Time  `json:"created_at"`
	ExpiresAt time.Time  `json:"expires_at"`
	UsedAt    *time.Time `json:"used_at,omitempty"`
}

type RefreshToken struct {
	TokenHash string     `json:"token_hash"`
	UserID    string     `json:"user_id"`
	ExpiresAt time.Time  `json:"expires_at"`
	RevokedAt *time.Time `json:"revoked_at,omitempty"`
}

// DeltaResponse encapsulates all delta entities for sync
type DeltaResponse struct {
	Household       *Household      `json:"household,omitempty"`
	Stores          []Store         `json:"stores"`
	Categories      []Category      `json:"categories"`
	StoreCategories []StoreCategory `json:"store_categories"`
	Items           []Item          `json:"items"`
	ItemStores      []ItemStore     `json:"item_stores"`
	ListEntries     []ListEntry     `json:"list_entries"`
	ServerTime      time.Time       `json:"server_time"`
}

// FlushRequest carries outbox mutations
type FlushRequest struct {
	HouseholdID       string          `json:"household_id"`
	Household         *Household      `json:"household,omitempty"`
	Stores            []Store         `json:"stores,omitempty"`
	Categories        []Category      `json:"categories,omitempty"`
	StoreCategories   []StoreCategory `json:"store_categories,omitempty"`
	Items             []Item          `json:"items,omitempty"`
	ItemStores        []ItemStore     `json:"item_stores,omitempty"`
	ListEntries       []ListEntry     `json:"list_entries,omitempty"`
	DeletedStores     []string        `json:"deleted_stores,omitempty"`
	DeletedCategories []string        `json:"deleted_categories,omitempty"`
	DeletedItems      []string        `json:"deleted_items,omitempty"`
	DeletedEntries    []string        `json:"deleted_entries,omitempty"`
}

type ServerInfo struct {
	ServerMode       string `json:"server_mode"`
	DatabaseDriver   string `json:"database_driver"`
	RegistrationOpen bool   `json:"registration_open"`
	BillingEnabled   bool   `json:"billing_enabled"`
	Version          string `json:"version"`
	APIVersion       int    `json:"api_version"`
	MinAppVersion    string `json:"min_app_version"`
	DebugMode        bool   `json:"debug_mode"`
}
