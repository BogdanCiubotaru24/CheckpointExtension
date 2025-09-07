# Checkpoint Extension

Save and restore complete player state using Typewriter Artifacts. A “checkpoint” captures:

- Facts (all non-zero `ReadableFactEntry` values)
- Position (world + yaw/pitch + coordinates)
- Inventory (storage, armor, offhand)

Applying a checkpoint fully replaces the player’s current facts (resets all `WritableFactEntry` to 0, then sets values from the snapshot and refreshes readable facts), teleports the player, and restores their inventory.

## Installation

1. Build or download `CheckpointExtension.jar`.
2. Place the jar in your Typewriter extensions folder.
3. Restart or reload Typewriter.

The jar name is `CheckpointExtension.jar`.

## Entries

- Save action: `checkpoint_save_action`
- Apply action: `checkpoint_apply_action`
- Artifact entry: `checkpoint_artifact` (stores the snapshot JSON)

You can wire actions inside stories, or use the Typewriter command for quick admin usage.

## Commands (under `/tw`)

- `/tw checkpoint save <name>`
  - Creates or replaces a checkpoint artifact named `<name>` capturing facts, position, and inventory.
  - Autocomplete suggests existing checkpoint names; you can still type a new one (no spaces).

- `/tw checkpoint apply <name> [player]`
  - Restores facts, position, and inventory from the checkpoint named `<name>`.
  - Suggests existing names; resolves checkpoints across all pages.

- `/tw checkpoint delete <name> [-force]`
  - Deletes the checkpoint named `<name>`.
  - Without `-force`, prints a confirmation hint. Append `-force` to actually delete.

- `/tw checkpoint list`
  - Shows all checkpoints grouped by page.

## How it works

- Checkpoints are stored as Typewriter Artifacts (`checkpoint_artifact` entries) in a static page named `checkpoint_artifacts` (created automatically if missing).
- Contents are a JSON payload with
  - `meta`: owner UUID/name and timestamp
  - `facts`: map of fact id → value
  - `position`: `{ world, coordinate }` (yaw/pitch included)
  - `inventory`: storage array, armor array, offhand
- When applying:
  - Runs fact updates off-thread, then schedules Bukkit-specific parts (teleport + inventory writes) on the main thread to satisfy Paper’s constraints.

## Notes

- Checkpoint names are case-insensitive for resolution and must not contain spaces.
- If you prefer content-driven usage, wire the provided actions in your pages and reference a `checkpoint_artifact` entry.

## Building

```
./gradlew clean build -x test
```

Jar output: `build/libs/CheckpointExtension.jar`.
