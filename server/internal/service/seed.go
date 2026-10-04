package service

import (
	"context"
	"fmt"
	"time"

	"net.marvinweber.simsli/server/internal/model"
)

func (s *Service) SeedDemoData(ctx context.Context) error {
	now := time.Now().UTC()

	id := func(n int) string {
		return fmt.Sprintf("dd000000-0000-0000-0000-%012d", n)
	}

	householdID := id(1)
	rewe := id(2)
	aldi := id(3)
	dm := id(4)

	// Categories
	obst := id(31)
	milch := id(32)
	back := id(33)
	grund := id(34)
	haushalt := id(35)
	sonstiges := id(36)

	// 1. Users
	user1, err := s.AdminCreateUser(ctx, "test1@simsli.de")
	if err != nil {
		return fmt.Errorf("seed user 1: %w", err)
	}
	user2, err := s.AdminCreateUser(ctx, "test2@simsli.de")
	if err != nil {
		return fmt.Errorf("seed user 2: %w", err)
	}

	// 2. Household & Members
	hh := &model.Household{
		ID:        householdID,
		Name:      "Testhaushalt",
		Plan:      "free",
		Status:    "active",
		CreatedAt: now,
		UpdatedAt: now,
	}
	_ = s.repo.CreateHouseholdWithOwner(ctx, hh, user1.ID)

	// Add user2 as member
	_ = s.repo.AddHouseholdMember(ctx, householdID, user2.ID, "member")

	// 3. Stores, Categories, Items, and Entries via Flush
	flushReq := &model.FlushRequest{
		HouseholdID: householdID,
		Stores: []model.Store{
			{ID: rewe, HouseholdID: householdID, Name: "REWE", SortOrder: 1.0, CreatedAt: now, UpdatedAt: now},
			{ID: aldi, HouseholdID: householdID, Name: "Aldi Süd", SortOrder: 2.0, CreatedAt: now, UpdatedAt: now},
			{ID: dm, HouseholdID: householdID, Name: "dm-drogerie markt", SortOrder: 3.0, CreatedAt: now, UpdatedAt: now},
		},
		Categories: []model.Category{
			{ID: obst, HouseholdID: householdID, Name: "Obst & Gemüse", Emoji: "🍎", SortOrder: 1.0, CreatedAt: now, UpdatedAt: now},
			{ID: milch, HouseholdID: householdID, Name: "Kühlregal", Emoji: "🥛", SortOrder: 2.0, CreatedAt: now, UpdatedAt: now},
			{ID: back, HouseholdID: householdID, Name: "Backwaren", Emoji: "🥖", SortOrder: 3.0, CreatedAt: now, UpdatedAt: now},
			{ID: grund, HouseholdID: householdID, Name: "Vorrat", Emoji: "🍝", SortOrder: 4.0, CreatedAt: now, UpdatedAt: now},
			{ID: haushalt, HouseholdID: householdID, Name: "Drogerie & Haushalt", Emoji: "🧻", SortOrder: 5.0, CreatedAt: now, UpdatedAt: now},
			{ID: sonstiges, HouseholdID: householdID, Name: "Sonstiges", Emoji: "📦", SortOrder: 6.0, CreatedAt: now, UpdatedAt: now},
		},
		StoreCategories: []model.StoreCategory{
			{StoreID: rewe, CategoryID: obst, SortOrder: 1.0},
			{StoreID: rewe, CategoryID: back, SortOrder: 2.0},
			{StoreID: rewe, CategoryID: milch, SortOrder: 3.0},
			{StoreID: rewe, CategoryID: grund, SortOrder: 4.0},
			{StoreID: rewe, CategoryID: haushalt, SortOrder: 5.0},
			{StoreID: rewe, CategoryID: sonstiges, SortOrder: 6.0},

			{StoreID: aldi, CategoryID: obst, SortOrder: 1.0},
			{StoreID: aldi, CategoryID: milch, SortOrder: 2.0},
			{StoreID: aldi, CategoryID: back, SortOrder: 3.0},
			{StoreID: aldi, CategoryID: grund, SortOrder: 4.0},
			{StoreID: aldi, CategoryID: haushalt, SortOrder: 5.0},
			{StoreID: aldi, CategoryID: sonstiges, SortOrder: 6.0},

			{StoreID: dm, CategoryID: haushalt, SortOrder: 1.0},
			{StoreID: dm, CategoryID: sonstiges, SortOrder: 2.0},
		},
		Items: []model.Item{
			{ID: id(101), HouseholdID: householdID, CategoryID: &milch, Name: "Milch", Notes: "3.8% Vollmilch", Type: "PERMANENT", SortOrder: 1.0, CreatedAt: now, UpdatedAt: now},
			{ID: id(102), HouseholdID: householdID, CategoryID: &back, Name: "Brot", Notes: "", Type: "PERMANENT", SortOrder: 2.0, CreatedAt: now, UpdatedAt: now},
			{ID: id(103), HouseholdID: householdID, CategoryID: &milch, Name: "Butter", Notes: "", Type: "PERMANENT", SortOrder: 3.0, CreatedAt: now, UpdatedAt: now},
			{ID: id(104), HouseholdID: householdID, CategoryID: &milch, Name: "Haferdrink", Notes: "Barista-Edition", Type: "PERMANENT", SortOrder: 4.0, CreatedAt: now, UpdatedAt: now},
			{ID: id(105), HouseholdID: householdID, CategoryID: &milch, Name: "Käse", Notes: "Gouda jung", Type: "PERMANENT", SortOrder: 5.0, CreatedAt: now, UpdatedAt: now},
			{ID: id(106), HouseholdID: householdID, CategoryID: &milch, Name: "Eier", Notes: "Freilandhaltung", Type: "PERMANENT", SortOrder: 6.0, CreatedAt: now, UpdatedAt: now},
			{ID: id(107), HouseholdID: householdID, CategoryID: &obst, Name: "Äpfel", Notes: "Elstar", Type: "PERMANENT", SortOrder: 7.0, CreatedAt: now, UpdatedAt: now},
			{ID: id(108), HouseholdID: householdID, CategoryID: &obst, Name: "Bananen", Notes: "", Type: "PERMANENT", SortOrder: 8.0, CreatedAt: now, UpdatedAt: now},
			{ID: id(109), HouseholdID: householdID, CategoryID: &grund, Name: "Kaffee", Notes: "Bohnen, dunkle Röstung", Type: "PERMANENT", SortOrder: 9.0, CreatedAt: now, UpdatedAt: now},
			{ID: id(110), HouseholdID: householdID, CategoryID: &grund, Name: "Nudeln", Notes: "Spaghetti No. 5", Type: "PERMANENT", SortOrder: 10.0, CreatedAt: now, UpdatedAt: now},
			{ID: id(111), HouseholdID: householdID, CategoryID: &grund, Name: "Tomatenpassata", Notes: "", Type: "PERMANENT", SortOrder: 11.0, CreatedAt: now, UpdatedAt: now},
			{ID: id(112), HouseholdID: householdID, CategoryID: &haushalt, Name: "Spülmittel", Notes: "", Type: "PERMANENT", SortOrder: 12.0, CreatedAt: now, UpdatedAt: now},
			{ID: id(113), HouseholdID: householdID, CategoryID: &haushalt, Name: "Toilettenpapier", Notes: "dreilagig", Type: "PERMANENT", SortOrder: 13.0, CreatedAt: now, UpdatedAt: now},
			{ID: id(114), HouseholdID: householdID, CategoryID: &sonstiges, Name: "Geburtstagskerzen", Notes: "", Type: "ONE_TIME", SortOrder: 14.0, CreatedAt: now, UpdatedAt: now},
			{ID: id(115), HouseholdID: householdID, CategoryID: nil, Name: "Geschenkpapier", Notes: "", Type: "ONE_TIME", SortOrder: 15.0, CreatedAt: now, UpdatedAt: now},
		},
		ItemStores: []model.ItemStore{
			{ItemID: id(101), StoreID: rewe, CreatedAt: now},
			{ItemID: id(101), StoreID: aldi, CreatedAt: now},
			{ItemID: id(102), StoreID: rewe, CreatedAt: now},
			{ItemID: id(102), StoreID: aldi, CreatedAt: now},
			{ItemID: id(103), StoreID: rewe, CreatedAt: now},
			{ItemID: id(104), StoreID: rewe, CreatedAt: now},
			{ItemID: id(105), StoreID: aldi, CreatedAt: now},
			{ItemID: id(106), StoreID: rewe, CreatedAt: now},
			{ItemID: id(107), StoreID: rewe, CreatedAt: now},
			{ItemID: id(108), StoreID: aldi, CreatedAt: now},
			{ItemID: id(109), StoreID: rewe, CreatedAt: now},
			{ItemID: id(110), StoreID: aldi, CreatedAt: now},
			{ItemID: id(111), StoreID: rewe, CreatedAt: now},
			{ItemID: id(112), StoreID: rewe, CreatedAt: now},
			{ItemID: id(112), StoreID: dm, CreatedAt: now},
			{ItemID: id(113), StoreID: aldi, CreatedAt: now},
			{ItemID: id(113), StoreID: dm, CreatedAt: now},
			{ItemID: id(114), StoreID: dm, CreatedAt: now},
			{ItemID: id(115), StoreID: dm, CreatedAt: now},
		},
		ListEntries: []model.ListEntry{
			// Active entries
			{ID: id(201), HouseholdID: householdID, ItemID: id(101), Done: false, CreatedAt: now, UpdatedAt: now},
			{ID: id(202), HouseholdID: householdID, ItemID: id(102), Done: false, CreatedAt: now, UpdatedAt: now},
			{ID: id(203), HouseholdID: householdID, ItemID: id(103), Done: false, CreatedAt: now, UpdatedAt: now},
			{ID: id(204), HouseholdID: householdID, ItemID: id(106), Done: false, CreatedAt: now, UpdatedAt: now},
			{ID: id(205), HouseholdID: householdID, ItemID: id(107), Done: false, CreatedAt: now, UpdatedAt: now},
			{ID: id(206), HouseholdID: householdID, ItemID: id(109), Done: false, CreatedAt: now, UpdatedAt: now},
			{ID: id(207), HouseholdID: householdID, ItemID: id(110), Done: false, CreatedAt: now, UpdatedAt: now},
			{ID: id(208), HouseholdID: householdID, ItemID: id(113), Done: false, CreatedAt: now, UpdatedAt: now},

			// Done entries (recently checked)
			{ID: id(209), HouseholdID: householdID, ItemID: id(105), Done: true, CompletedAt: &now, CreatedAt: now, UpdatedAt: now},
			{ID: id(210), HouseholdID: householdID, ItemID: id(108), Done: true, CompletedAt: &now, CreatedAt: now, UpdatedAt: now},
			{ID: id(211), HouseholdID: householdID, ItemID: id(112), Done: true, CompletedAt: &now, CreatedAt: now, UpdatedAt: now},
		},
	}

	if err := s.repo.Flush(ctx, flushReq); err != nil {
		return fmt.Errorf("flush demo data: %w", err)
	}

	s.hub.Broadcast(householdID)
	return nil
}
