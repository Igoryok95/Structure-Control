# Structure Control

An administrator utility for **Minecraft 1.21.1 / NeoForge** by Igoryok95.

Technical checks and an isolated interactive map check passed. See [validation results and compatibility limits](docs/validation.md).

Inspect an existing structure, disable its special mob spawns, or unregister it while keeping its blocks. Changes can be made while the server is running. The optional client map shows terrain, a chunk grid, structure boundaries and actions for the selected instance.

## Download

[Download Structure Control 1.0.0](https://github.com/Igoryok95/Structure-Control/releases/download/v1.0.0/structure-control-neoforge-1.21.1-1.0.0.jar), or visit [GitHub Releases](https://github.com/Igoryok95/Structure-Control/releases/latest).

Use the regular JAR for installation. The separate `-sources.jar` contains source code.

## Installation

Install the JAR on the server and restart it once. Players can connect without installing the mod. Administrators who want the map install the same JAR on their client. There are no required third-party mods.

Minecraft 1.21.1, Java 21 or newer, NeoForge 21.1.234 or newer in the 21.1 series. Development validation targets NeoForge 21.1.264.

## Commands

All commands require permission level 2. `/structctl` is an alias for `/structurecontrol`.

| Command | Purpose |
| --- | --- |
| `/structurecontrol map` | Open the optional administrator map |
| `/structurecontrol info [x y z]` | Inspect registered structures at the position |
| `/structurecontrol spawns <structure> off [x y z]` | Disable special spawns for this instance |
| `/structurecontrol spawns <structure> on [x y z]` | Restore the instance's special spawns |
| `/structurecontrol unregister <structure> [x y z]` | Prepare unregistering; receive a confirmation token |
| `/structurecontrol confirm <token>` | Confirm within 60 seconds |
| `/structurecontrol cancel` | Cancel your pending operation |
| `/structurecontrol undo` | Undo the latest recorded change in the world |
| `/structurecontrol history [1–32]` | Inspect the shared undo history |
| `/structurecontrol list` | List disabled instances |

For example, stand inside an outpost and run `/structurecontrol spawns minecraft:pillager_outpost off`. Console commands need explicit coordinates; use `execute in <dimension> run ...` for another dimension.

## Map

Open `/structurecontrol map`, or assign the initially unbound map key in Controls. Drag to pan, scroll to zoom, and click a structure to select it. Repeated clicks cycle overlapping structures. The side panel offers spawn control and unregistering with a separate confirmation. Undo applies to the latest shared world operation, including changes made by another administrator.

Only loaded chunks are inspected. Unloaded terrain is shown as unknown. The map does not search the world or generate new chunks. Terrain colours come from blocks available on the client; structure data comes from the server. Non-administrators cannot request the map or change structures. English and Russian messages work even when the client has no mod resources.

## What spawn control means

Disabling a structure's spawns skips **that instance's structure spawn overrides**. The normal biome spawn lists remain active. Existing mobs, wandering patrols, raids, spawner blocks and mobs placed by a structure template are separate mechanics. Structure detection and `/locate` remain available while only spawns are disabled.

Unregistering instead replaces the native structure start with `INVALID` and refreshes Minecraft's structure-check cache. Blocks, inventories, entities and other structure instances are not removed. Existing reference entries are retained as native links to an invalid start; this is harmless and lets undo restore the original structure without loading or generating a surrounding area. This operation also changes structure detection and locating.

## Persistence and limits

Rules and the last 32 undoable operations are stored with the world in `data/structure_control.dat`. Undo survives a normal world save and restart. The history is shared across dimensions and administrators. Starting another preview replaces your previous preview; tokens expire after 60 seconds and cannot be used by another player.

Only loaded structure starts can be changed. Undo will wait for you to load the original start chunk. It refuses to overwrite a start changed by another mod. A structure snapshot larger than 1 MiB is rejected before mutation. If saved control data cannot be read, mutations are disabled to protect the existing file. Removing the mod stops reversible spawn filtering; structures already unregistered remain unregistered unless restored first.

The initial installation needs a restart. Later command and map operations apply live. Like other world data, unsaved changes can be lost on an unclean crash.

## Development

Build with `./gradlew build`. Run isolated native integration tests with `./gradlew -PgameTests runGameTestServer`. Test-only classes and fixtures are excluded from normal release JARs. See [validation](docs/validation.md) for results and limitations.

## License

MIT. This is an independent NeoForge implementation inspired by the workflow of Structure Remover and chunk-map administration tools. It does not include their code or artwork.
