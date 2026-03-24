# StoreByback

A Paper plugin that lets players buy back items they sold to the server via EssentialsX `/sell`. Full NBT data is preserved, making it safe for use with EliteMobs and other plugins that use custom item data.

## Requirements

- Paper (or Purpur) 1.21+
- [EssentialsX](https://essentialsx.net/) — for `/sell` integration
- [Vault](https://www.spigotmc.org/resources/vault.34315/) — for economy integration
- An economy plugin compatible with Vault (e.g. EssentialsX Economy)

## Installation

1. Drop `StoreByback-<version>.jar` into your `plugins/` folder.
2. Ensure Vault and EssentialsX are also installed.
3. Start the server — a default `config.yml` will be generated under `plugins/StoreByback/`.

## Usage

Sell items as normal using EssentialsX:

```
/sell hand
/sell all
/sell inventory
/sell <item>
```

Then open the buyback GUI to reclaim them:

```
/buyback        — opens the buyback GUI
/bb             — alias
```

Click any item in the GUI to buy it back at the configured price. Items are shown with the original sale price, buyback cost, and how long ago they were sold.

## Commands

| Command | Permission | Description |
|---|---|---|
| `/buyback` | `storebyback.use` | Open the buyback GUI |
| `/buyback reload` | `storebyback.admin` | Reload config without restarting |
| `/buyback clear [player]` | `storebyback.admin` | Clear a player's sell history |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `storebyback.use` | All players | Use `/buyback` |
| `storebyback.admin` | OP | Use reload and clear commands |

## Configuration

`plugins/StoreByback/config.yml`:

```yaml
# Max sell records stored per player (oldest are dropped when limit is reached)
max-history: 15

# Price multiplier for buying back items
# 1.0 = same price as sold, 1.5 = 50% markup, 2.0 = double price
price-multiplier: 1.0

# Hours before a sell record expires and disappears from the GUI
# Set to 0 or less to keep records indefinitely
history-expiry-hours: 24

# Whether to record bulk sells (/sell all, /sell inventory)
record-bulk-sells: true

# Whether to record hand/single-item sells (/sell hand, /sell <item>)
record-hand-sells: true
```

## Notes

- **Inventory full:** If your inventory is full when buying back, the item is dropped at your feet.
- **Bulk sells:** Each item type sold in a `/sell all` creates its own record. Only items that were actually removed from your inventory are recorded — items with no `worth.yml` price are not tracked.
- **EliteMobs items:** All custom NBT data (stats, lore, item level, etc.) is fully preserved through the buyback process.
- **History limit:** When `max-history` is reached, the oldest records are removed to make room for new ones.

## Data Storage

Player sell history is stored in `plugins/StoreByback/playerdata/<uuid>.yml`. Each file contains the serialized items and sale prices for that player. Files are safe to delete manually to clear a player's history (or use `/buyback clear <player>`).
