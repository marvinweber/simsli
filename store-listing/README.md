# Simsli — Google Play Store Listing Assets

This folder contains all required store listing assets for Google Play Console (main store listing and internal test track).

---

## 📁 Directory Structure & Asset Checklist

| Asset | File | Specifications | Status |
| :--- | :--- | :--- | :--- |
| **App Icon** | `shopping-list-logo-playstore-512x512.png` | 512 × 512 px, 32-bit PNG | ✅ Ready |
| **Feature Graphic** | `feature-graphic-1024x500.png` | 1024 × 500 px, 24-bit PNG (no alpha) | ✅ Ready |
| **Vector Logo** | `shopping-list-logo.svg` | SVG source | ✅ Saved |
| **Descriptions (EN)** | `STORE_LISTING_EN.md` | Short: 71 / 80 chars<br>Full: 2,003 / 4,000 chars | ✅ Ready |
| **Descriptions (DE)** | `STORE_LISTING_DE.md` | Short: 71 / 80 chars<br>Full: 2,247 / 4,000 chars | ✅ Ready |
| **Phone Screenshots** | `screenshots/` (7 images) | 1280 × 2560 px (1:2 ratio, Play Console compliant) | ✅ Ready |
| **Privacy Policy** | `PRIVACY.md` | Simple, GDPR & Play Store compliant (DE & EN) | ✅ Ready |

---

## 🔒 Privacy Policy URL (for Google Play Console)

Google Play Console requires a public HTTPS URL:
```text
https://github.com/marvinweber/simsli/blob/main/PRIVACY.md
```

In the Play Console, enter this URL under:
**Policy and programs** → **App content** → **Privacy policy** (Datenschutzerklärung).

---

## 📱 Screenshots Overview (`screenshots/`)

1. **`01_shopping_list.png`** — Main shopping list view with aisle categories, checked entries, and bottom navigation.
2. **`02_store_filter.png`** — Filtering by store (e.g. *REWE*) showing only store-assigned items and recently checked items.
3. **`03_fast_add_search.png`** — Quick-add bottom sheet floating above keyboard with live search autocomplete.
4. **`04_entry_details.png`** — Item entry sheet editing quantity, unit, and personal notes.
5. **`05_catalog.png`** — Complete household catalog with list badges and fast-add actions.
6. **`06_item_edit.png`** — Item configuration showing categories, multi-store assignment (*REWE* + *Aldi*), and item type.
7. **`07_settings_household.png`** — Settings screen showing shared household status, member counts, and Realtime Cloud Sync.

---

## 🚀 Play Console Upload Instructions

1. **Store Listing Details**:
   - Open **Google Play Console** → Select **Simsli**.
   - Go to **Grow** → **Store presence** → **Main store listing**.
   - **App name**: `Simsli: Shared Shopping List` (or `Simsli: Geteilte Einkaufsliste`)
   - **Short description**: Copy from `STORE_LISTING_EN.md` (or `DE`)
   - **Full description**: Copy from `STORE_LISTING_EN.md` (or `DE`)
   - **App icon**: Upload `shopping-list-logo-playstore-512x512.png`
   - **Feature graphic**: Upload `feature-graphic-1024x500.png`
   - **Phone screenshots**: Upload all 7 images from `screenshots/` in numerical order
   - Click **Save**.

2. **Privacy Policy**:
   - Go to **Policy and programs** → **App content**.
   - Under **Privacy Policy**, click **Start**.
   - Paste: `https://github.com/marvinweber/simsli/blob/main/PRIVACY.md`
   - Click **Save**.

