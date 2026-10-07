# ADR 0017: FOSS / F-Droid compatibility through modular proprietary feature isolation

- **Status:** Accepted
- **Date:** 2026-10-07

## Context

Simsli is licensed under AGPL-3.0 and prioritizes user privacy, software freedom, and self-hosting. Distributing the app via F-Droid (which strictly forbids proprietary Google Play Services binaries and non-free dependencies) is a long-term project requirement.

At the same time, certain platform integrations rely on platform services provided by Google. Specifically, a lightweight **Wear OS companion app** requires the Google Play Services Wearable Data Layer (`play-services-wearable`) to manage Bluetooth RFCOMM, automatic connection recovery, power management, and local watch-side caching. Attempting to build a pure companion link with custom, raw Bluetooth RFCOMM/GATT sockets without Play Services on modern Wear OS leads to severe battery drain, background process termination by the OS, and conflicts with OEM companion managers (Pixel Watch, Samsung Galaxy Wearable).

We need an architectural policy that allows high-quality Wear OS integration for standard Android devices while guaranteeing that Simsli remains 100% free and open-source for F-Droid.

## Decision

We will mandate that **FOSS and F-Droid compatibility must always be preserved**. When a feature requires proprietary Google Play Services (or any non-FOSS SDK) because an open alternative is technically unfeasible or prohibitive:

1. **Decoupled Interface Boundary:** The feature must interact with the core application exclusively through an abstract interface (e.g., `WearSyncBridge`) defined in domain/common layers without importing proprietary classes.
2. **Modular / Build Flavor Isolation:**
   - **`play` build flavor:** Includes the proprietary dependency (`play-services-wearable`) and binds the real implementation that connects to the `:wear` companion module.
   - **`foss` build flavor:** Compiles without any Google Play Services dependencies, substituting a no-op stub implementation. F-Droid can build this flavor directly from source without exclusions or patches.
3. **Pure Companion Scope:** The Wear OS companion remains strictly a satellite display and check-off tool for the phone app. Standalone watch operations (which would demand separate watch accounts, network stacks, or duplicate sync engines) are avoided.

## Consequences

- **Easier:** Simsli can be accepted and built by F-Droid without policy violations; de-googled devices (GrapheneOS, CalyxOS) run a clean, blob-free APK; Play Store users receive full Wear OS integration.
- **Harder / accepted trade-offs:** Feature developers must design clean abstraction interfaces and maintain build flavors (`play` vs `foss`); users on the `foss` flavor do not get Wear OS companion connectivity unless an open transport is built in the future.
