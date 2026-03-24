package com.giantslair.storeByback;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central business logic hub.
 * Manages pending sell state (pre/post interception correlation),
 * in-memory history cache, and all record CRUD operations.
 */
public class BuybackManager {

    /** Snapshot taken just before EssentialsX removes the items from inventory. */
    public static class PendingSell {
        public final UUID playerUUID;
        public final List<ItemStack> snapshots; // cloned copies, not live references
        public final double balanceBefore;

        public PendingSell(UUID playerUUID, List<ItemStack> snapshots, double balanceBefore) {
            this.playerUUID = playerUUID;
            this.snapshots = snapshots;
            this.balanceBefore = balanceBefore;
        }
    }

    private final StoreByback plugin;
    private final BuybackStorage storage;
    private final Economy economy;

    private final Map<UUID, PendingSell> pendingMap = new ConcurrentHashMap<>();
    private final Map<UUID, List<SellRecord>> historyCache = new HashMap<>();

    private int maxHistory;
    private double priceMultiplier;
    private long expiryMillis; // 0 = no expiry
    private boolean recordBulkSells;
    private boolean recordHandSells;

    public BuybackManager(StoreByback plugin, BuybackStorage storage, Economy economy) {
        this.plugin = plugin;
        this.storage = storage;
        this.economy = economy;
    }

    public void initialize() {
        loadConfigValues();
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        loadConfigValues();
    }

    private void loadConfigValues() {
        maxHistory = plugin.getConfig().getInt("max-history", 15);
        priceMultiplier = plugin.getConfig().getDouble("price-multiplier", 1.0);

        if (priceMultiplier < 0) {
            plugin.getLogger().warning("price-multiplier is negative (" + priceMultiplier + ") — resetting to 1.0 to prevent economy exploits.");
            priceMultiplier = 1.0;
        } else if (priceMultiplier == 0) {
            plugin.getLogger().warning("price-multiplier is 0 — players can buy back items for free.");
        }

        long expiryHours = plugin.getConfig().getLong("history-expiry-hours", 24);
        expiryMillis = expiryHours > 0 ? expiryHours * 3_600_000L : 0;
        recordBulkSells = plugin.getConfig().getBoolean("record-bulk-sells", true);
        recordHandSells = plugin.getConfig().getBoolean("record-hand-sells", true);
    }

    // -------------------------------------------------------------------------
    // Pending sell state
    // -------------------------------------------------------------------------

    public void storePending(UUID uuid, PendingSell pending) {
        pendingMap.put(uuid, pending);
    }

    public PendingSell consumePending(UUID uuid) {
        return pendingMap.remove(uuid);
    }

    public void clearPending(UUID uuid) {
        pendingMap.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // History management
    // -------------------------------------------------------------------------

    public void addRecord(UUID uuid, SellRecord record) {
        List<SellRecord> history = getCachedHistory(uuid);
        history.add(0, record); // prepend (most recent first)

        // Trim to max
        while (history.size() > maxHistory) {
            history.remove(history.size() - 1);
        }

        storage.savePlayerHistory(uuid, history);
    }

    public List<SellRecord> getHistory(UUID uuid) {
        List<SellRecord> history = getCachedHistory(uuid);

        if (expiryMillis <= 0) {
            return new ArrayList<>(history);
        }

        long cutoff = System.currentTimeMillis() - expiryMillis;
        List<SellRecord> active = new ArrayList<>();
        boolean removed = false;

        for (SellRecord r : history) {
            if (r.getTimestamp() >= cutoff) {
                active.add(r);
            } else {
                removed = true;
            }
        }

        if (removed) {
            historyCache.put(uuid, active);
            storage.savePlayerHistory(uuid, active);
        }

        return active;
    }

    public boolean removeRecord(UUID uuid, String recordId) {
        List<SellRecord> history = getCachedHistory(uuid);
        boolean removed = history.removeIf(r -> r.getRecordId().equals(recordId));
        if (removed) {
            storage.savePlayerHistory(uuid, history);
        }
        return removed;
    }

    public void clearCache(UUID uuid) {
        historyCache.remove(uuid);
    }

    public void shutdown() {
        // All saves are synchronous in addRecord/removeRecord — nothing to flush.
        pendingMap.clear();
        historyCache.clear();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<SellRecord> getCachedHistory(UUID uuid) {
        return historyCache.computeIfAbsent(uuid, id -> storage.loadPlayerHistory(id));
    }

    public double getBuybackPrice(SellRecord record) {
        return Math.max(0.0, record.getSalePrice() * priceMultiplier);
    }

    public boolean isRecordBulkSells() { return recordBulkSells; }
    public boolean isRecordHandSells() { return recordHandSells; }
    public Economy getEconomy() { return economy; }
}
