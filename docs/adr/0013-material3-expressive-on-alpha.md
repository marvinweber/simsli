# ADR 0013: Material 3 Expressive via pinned material3 alpha

- **Status:** Accepted
- **Date:** 2026-09-26

## Context

CLAUDE.md names Material 3 Expressive as the UI target, but the app still used the stock theme. Adopting it surfaced a versioning fact: the Expressive wave (`MaterialExpressiveTheme`, `MotionScheme`, `FloatingActionButtonMenu`, `ToggleFloatingActionButton`, `LoadingIndicator`, wavy progress) is **not in material3 stable 1.4.0 at all** — the `ExperimentalMaterial3ExpressiveApi` marker is internal there; the components ship only on the 1.5.0-alpha line (verified against the official androidx API file at `1.5.0-alpha29`). Alternatives: stay on stable 1.4.0 without Expressive (delays a stated product decision indefinitely); pin 1.5.0-alpha29 (alpha churn — alpha29 itself is source-breaking for unrelated APIs).

## Decision

Pin `material3 = "1.5.0-alpha29"` explicitly in the version catalog (overriding the Compose BOM) and adopt Expressive now: expressive theme with `MotionScheme.expressive()`, FAB menu, expressive loading indicators, wavy sync indicator. APIs that remain `@ExperimentalMaterial3ExpressiveApi` on alpha29 (`LoadingIndicator`, `ContainedLoadingIndicator`, `MaterialShapes`) carry `@OptIn`; graduated APIs (theme, FAB menu, toolbars, wavy progress) need none.

## Consequences

Easier: the app actually looks and moves like M3 Expressive today; the FAB menu and spring-based motion are the visible payoff.

Harder / accepted trade-offs: alpha upgrades can break the build between pins (accepted — the app is pre-release, upgrades are cheap to stage); the `@OptIn` debt on the loading-indicator family clears when they graduate; when material3 1.5 stabilizes, drop the explicit pin and return to BOM management. Component APIs renamed on the alpha line (e.g. `ToggleFloatingActionButtonMenuButton` → `ToggleFloatingActionButton`) are migrated, not supported under both names.
