# Validation

Validated on Java 21, Minecraft 1.21.1 and NeoForge 21.1.264:

- 8 JUnit tests passed: per-instance identity, native NBT persistence, bounded immutable undo snapshots, invalid save protection, EN/RU catalogue parity and formatting, map request bounds, optional payload round-trips and oversized packet rejection.
- All 6 required native GameTests passed. The tests exercise Minecraft's actual structure spawn selection, preserve registration during reversible filtering, leave other instances active, refresh the native structure-check cache on unregister/undo, preserve a chest with 17 diamonds, enforce owner-bound expiring confirmation, reject conflicting changes, restore native starts from saved history, reject non-operator commands and avoid loading unknown chunks.
- Two separate dedicated server processes passed the restart smoke test. Disabled rules and an invalid structure start survived a normal save/restart. Undo loaded from disk restored the start and preserved the chest inventory.

## Visual verification status

The optional map was checked in an isolated Minecraft client at 1280 × 960, GUI scale 2, with Russian resources. Terrain, chunk lines, coloured instance boundaries, selection details and action labels render clearly. Selecting an instance, changing its spawn rule, undoing that change, opening the unregister confirmation and cancelling it all worked through the real client/server connection.

This check found a render-order defect: vanilla `Screen.render` applied its menu blur after the custom map and labels had been drawn, leaving only the later widgets sharp. The map now suppresses that inherited background pass because it draws its own background. A rebuilt client verified that terrain, boundaries and text remain sharp.

Compact-window interaction and an English visual pass have not been completed. English/Russian catalogue parity and placeholder formatting are covered by the technical tests.

The metadata permits NeoForge 21.1.234 and newer in the 21.1 series, but only 21.1.264 has been runtime-tested so far. A real client without this mod has not yet been connected to the isolated dedicated server; optional networking registration and the server-only startup path have been implemented, but that handshake remains a separate verification item.

The production Minecraft instance is outside the test scope. All development servers and clients use this repository's `run` directory.
