DROP INDEX IF EXISTS idx_household_members_user;
DROP INDEX IF EXISTS idx_list_entries_household_updated;
DROP INDEX IF EXISTS idx_items_household_updated;
DROP INDEX IF EXISTS idx_categories_household_updated;
DROP INDEX IF EXISTS idx_stores_household_updated;

DROP TABLE IF EXISTS refresh_tokens;
DROP TABLE IF EXISTS magic_links;
DROP TABLE IF EXISTS invite_tokens;
DROP TABLE IF EXISTS list_entries;
DROP TABLE IF EXISTS item_stores;
DROP TABLE IF EXISTS items;
DROP TABLE IF EXISTS store_categories;
DROP TABLE IF EXISTS categories;
DROP TABLE IF EXISTS stores;
DROP TABLE IF EXISTS household_members;
DROP TABLE IF EXISTS households;
DROP TABLE IF EXISTS users;
