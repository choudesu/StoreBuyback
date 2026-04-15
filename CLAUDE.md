# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build

```bash
mvn clean package
```

Output: `target/StoreBuyback-<version>.jar`. The version is controlled via `-Drevision` (CI passes a commit SHA or semver tag). There is no test suite.

## Deployment

Drop the compiled JAR into a Paper 1.21+ server's `plugins/` folder. Requires **Vault** and **EssentialsX** plugins to already be installed, plus an economy plugin registered with Vault (e.g., EssentialsX Economy).

## Architecture

StoreBuyback is a Paper/Bukkit plugin that intercepts EssentialsX `/sell` commands and stores item sale records so players can buy back recently-sold items through a GUI.

### Data Flow

```
Player: /sell hand
  ↓
SellPreListener (HIGH priority) — snapshots inventory & balance before sell
  ↓
EssentialsX processes /sell — removes items, fires UserBalanceUpdateEvent
  ↓
SellPostListener (MONITOR priority) — compares pre/post state, creates SellRecords
  ↓
BuybackManager.addRecord() — caches in memory + persists to YAML
  ↓
Player: /buyback → BuybackGUI opens → click item → deducts price, restores item
```

### Core Classes

| Class | Role |
|---|---|
| `StoreBuyback` | Plugin main class; validates dependencies, wires everything together |
| `BuybackManager` | Central business logic; owns in-memory history cache (`Map<UUID, List<SellRecord>>`), pending-sell map, config, expiry/trim logic |
| `BuybackStorage` | Persistence; reads/writes per-player YAML files under `plugins/StoreBuyback/playerdata/<uuid>.yml`; serializes `ItemStack` to Base64 bytes to preserve full NBT |
| `SellRecord` | Immutable snapshot: recordId, playerUUID, itemBytes, salePrice, soldAmount, timestamp |
| `BuybackGUI` | 6-row chest UI; `BuybackHolder` carries session data; paginated (45 slots/page); drops items at feet if inventory full |
| `SellPreListener` | Captures pre-sell state; schedules 5-second cleanup if post-event never fires |
| `SellPostListener` | Compares inventories after EssentialsX balance update; distributes sale price proportionally across item types |
| `BuybackCommand` | Handles `/buyback [open|reload|clear]` |

### Key Design Decisions

- **Two-phase interception**: `SellPreListener` runs at HIGH priority before EssentialsX, `SellPostListener` runs at MONITOR on `UserBalanceUpdateEvent` after. A `ConcurrentHashMap` of `PendingSell` objects correlates the two phases.
- **Proportional price distribution**: When `/sell all` sells multiple item types, the total sale price is split proportionally by unit count across each item type.
- **Lazy expiry**: History expiry is checked on `BuybackManager.getHistory()` rather than via a background scheduler.
- **Inventory Holder pattern**: `BuybackGUI.BuybackHolder` identifies GUI inventory click events without title-string comparison.
- **In-memory cache with lazy loading**: History is loaded from disk on first access via `computeIfAbsent()` and cleared per-player on quit.

## Configuration

`plugins/StoreBuyback/config.yml` (auto-generated on first startup):

```yaml
max-history: 15             # Records per player; oldest dropped when exceeded
price-multiplier: 1.0       # Multiplier applied when player buys back (1.0 = same price)
history-expiry-hours: 24    # 0 = never expire
record-bulk-sells: true     # Whether to record /sell all, /sell inventory
record-hand-sells: true     # Whether to record /sell hand, /sell <item>
messages: {...}             # MiniMessage format strings
```

## Permissions

- `storebyback.use` — default: all players
- `storebyback.admin` — default: op (grants `reload` and `clear [player]` subcommands)
