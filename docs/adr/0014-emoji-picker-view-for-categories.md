# ADR 0014: Official AndroidX EmojiPickerView for category icons

- **Status:** Accepted
- **Date:** 2026-10-01

## Context

ADR-0012 opted for "free emoji icon (no picker — the keyboard is the picker)" to avoid building and maintaining a custom icon picker. However:
1. Android's soft keyboard (IME) framework does not provide an API to open the keyboard directly on its emoji panel; it always defaults to the alphanumeric layout, requiring manual switching by the user.
2. A free-text input field accepts non-emoji characters (letters, numbers, symbols), leading to awkward input and inconsistent icons.
3. Google provides the official Jetpack library `androidx.emoji2:emoji2-emojipicker`, which supplies `EmojiPickerView` — an accessible, categorized, standardized emoji drawer with recent emoji persistence and skin-tone variant support.

## Decision

We will use Google's official `androidx.emoji2:emoji2-emojipicker` component (`EmojiPickerView`) for category icon selection, amending the "no picker" stance in ADR-0012.

Tapping the category icon button in the create/edit category dialog directly opens the `EmojiPickerView` in a dialog. Users pick an emoji with one tap (or tap "Remove" to clear the icon). The category name input field remains standard text, eliminating keyboard mode switching.

Alternatives considered:
- **Keyboard-only with regex validation**: Rejected because it still cannot force the keyboard into emoji mode and makes icon picking cumbersome.
- **Third-party emoji picker libraries (e.g. vanniktech/Emoji)**: Rejected because `androidx.emoji2` is the official Google Jetpack component already aligned with the AndroidX ecosystem.
- **Custom Compose emoji grid with search**: Rejected to avoid maintaining an internal keyword/CLDR search index; the standard categorized tabs in `EmojiPickerView` (Food & Drink 🍔, Produce 🍎, Objects 📦) sufficiently cover grocery and household categories with a single tap.

## Consequences

Easier: Tapping the icon button immediately opens a full, categorized emoji drawer; impossible to enter non-emoji characters; zero custom icon picker UI or emoji database to maintain.

Harder / accepted trade-offs: Adds the official `androidx.emoji2:emoji2-emojipicker` dependency (~200KB); `EmojiPickerView` is an Android View embedded via Compose `AndroidView`; does not have built-in text search (browsing uses category tabs).
