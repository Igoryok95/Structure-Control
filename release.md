# Structure Control 1.0.0

Administrator tools for Minecraft 1.21.1 on NeoForge.

- Inspect loaded structure instances without generating or searching new chunks.
- Disable and restore an instance's special mob-spawn rules while keeping its structure registration.
- Unregister a structure with owner-bound confirmation, keeping its blocks, inventories and entities.
- Undo changes after a save and restart, with a shared history of up to 32 operations.
- Use an optional in-game terrain map with structure boundaries, selection, spawn controls and confirmation.
- English and Russian messages; permission level 2 is required for administration.

Install `structure-control-neoforge-1.21.1-1.0.0.jar` on the server and restart it once. Administrators who want the map install the same JAR on their client, then run `/structurecontrol map`. Client installation is optional for command-only use. The `-sources.jar` is for development.

Requires Minecraft 1.21.1, Java 21 or newer, and NeoForge 21.1.234 or newer in the 21.1 series. Runtime validation used NeoForge 21.1.264.

Validation: 8 unit tests, 6 native GameTests, a two-process restart test and an isolated interactive Russian map check passed. The release includes the map render-order fix that keeps its terrain and labels sharp. See [validation scope and remaining compatibility checks](https://github.com/Igoryok95/Structure-Control/blob/main/docs/validation.md).

Spawn controls affect structure-specific spawn overrides. Existing mobs, biome spawns, patrols, raids and spawner blocks are separate mechanics. Changes apply to loaded structure starts; the mod does not remove structures' blocks.
