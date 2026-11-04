# Architecture

The server owns all rules and mutations. Clients without the mod remain compatible; the optional client uses negotiated optional payload channels. No content registries or mandatory client payloads are introduced.

Each instance is identified by dimension, registered structure ID and start chunk. Spawn filtering is a narrow redirect within `ChunkGenerator.getMobsAt`: disabled starts are omitted from the structure override check, while the original biome fallback and other instances remain intact. The hot path reads an immutable set and does not load saved data or scan regions.

Commands inspect loaded chunk references and starts. Unregistering records the original native NBT, writes `StructureStart.INVALID_START` through the chunk API, and refreshes the structure-check cache. Reference entries are intentionally retained to make restoration local and reversible. No region files are edited while the server is running.

World SavedData stores an immutable rule snapshot and a bounded shared undo stack. Preview tokens are transient, owner-bound and expire after 60 seconds. Undo uses the native structure loader and refuses to replace a valid structure start.

The map uses loaded client terrain colours, a chunk grid and server-provided structure rectangles. Snapshot requests have a bounded radius and result count, are rate-limited, and require administrator permissions on the server. Mutations revalidate the selected instance server-side. Client display state is never authoritative.

User-facing command strings come from matching UTF-8 EN/RU catalogues. Server replies carry per-player translated fallbacks so server-only installations do not expose translation keys to unmodified clients.
