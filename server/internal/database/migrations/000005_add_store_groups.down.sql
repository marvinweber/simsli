DROP TABLE IF EXISTS item_store_groups;

ALTER TABLE item_stores DROP COLUMN membership;
ALTER TABLE stores DROP COLUMN group_id;

DROP TABLE IF EXISTS store_groups;
