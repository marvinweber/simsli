# Changelog

All notable changes to the Simsli shopping list app, Wear OS companion, and server will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
- **Server Usage Metrics**: The server now records aggregate usage data — last-seen time and app version per account (updated at most hourly, no device tracking) and per-household monthly totals of checked/added list items. Both are visible on the admin dashboard ("Usage & Versions"); documented in the privacy policy and ADR 0018.

## [0.4.0] - 2026-10-07
### 🚀 What's New
- **Wear OS Companion App**: Access your shopping list directly on your wrist!
  - **Store Filtering & Check-Off**: View lists organized by store, tap to check off items, or tap an item to inspect comments and notes.
  - **Aisle Order Organization**: Items on your watch are sorted and grouped by your configured store aisle categories.
  - **Rotary Input**: Smoothly scroll through your shopping list using the watch crown or rotating bezel with tactile snapping.
  - **Quick Launcher Tile**: Add the "Simsli - Stores" tile to your watch face for instant access to your store lists and open item counts.
  - **Watch Connection Status**: View the paired Wear OS smartwatch connection status in the phone app's Settings.
- **Smart Duplicate Warning**: Warns you with smart similarity matching when adding or renaming an item that already exists in your catalog (distinguishing exact matches, plurals, and common typos).
- **Default Item Units**: Set a default unit (e.g., pcs, kg, pack) directly on catalog items so it is preselected automatically when adding them to your list.
- **Item Deletion**: Easily remove items from your catalog directly from the item details screen, automatically cleaning up active list entries.

### ✨ Improvements
- **Shopping List Filter Bar Redesign**: Floating filter bar now includes an inline category filter alongside store filtering, complete with a one-tap reset button to clear active filters.
- **Modernized Navigation Bars**: Left-aligned top app bars with an overflow menu offering quick sync refresh.
- **Catalog Navigation**: Cleaner category groupings, clearer item type indicators, and streamlined store assignment chips.

### 🐛 Bug Fixes
- **Sync Reliability**: Resolved an issue causing continuous sync loops; added sync diagnostics and improved realtime websocket authentication.
- **Color Contrast**: Improved contrast for temporary one-time item badges and section headers.

## [0.3.3] - 2026-10-04
### Added
- **Server Version Display**: Added version badge to the Admin Dashboard and Database Browser headers and footers.

## [0.3.2] - 2026-10-04
### Added
- **Interactive Item Links**: URLs in item notes are now automatically detected and displayed as interactive preview chips with webpage titles.

## [0.3.1] - 2026-10-04
### Added
- **Admin Dashboard Auth**: Added password login form, secure session cookies, and logout for the server admin dashboard.
### Fixed
- **PostgreSQL Compatibility**: Transparently rebind SQL parameter placeholders for PostgreSQL database backends.

## [0.3.0] - 2026-10-04
### Added
- **Initial Release**: Complete offline-first shared shopping list with store filtering, item catalog, real-time sync, and household sharing.
