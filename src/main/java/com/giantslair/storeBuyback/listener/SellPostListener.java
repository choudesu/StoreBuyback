package com.giantslair.storeBuyback.listener;

import com.giantslair.storeBuyback.BuybackManager;
import com.giantslair.storeBuyback.SellRecord;
import net.ess3.api.events.UserBalanceUpdateEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Phase 2 of two-phase sell interception.
 *
 * Fires after EssentialsX has processed the /sell command and updated the
 * player's balance. Retrieves the pre-sell snapshot stored by SellPreListener,
 * then uses an inventory diff to determine which items were ACTUALLY sold
 * (not all snapshotted items may have had a worth.yml price).
 *
 * Only items whose quantity decreased are recorded. This prevents the exploit
 * of recording unsellable items that were snapshotted but never removed by
 * EssentialsX (e.g. EliteMobs items with no worth.yml entry).
 */
public class SellPostListener implements Listener {

    private final BuybackManager manager;

    public SellPostListener(BuybackManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBalanceUpdate(UserBalanceUpdateEvent event) {
        if (event.getCause() != UserBalanceUpdateEvent.Cause.COMMAND_SELL) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) return;

        UUID uuid = player.getUniqueId();
        BuybackManager.PendingSell pending = manager.consumePending(uuid);
        if (pending == null) return;

        // Actual amount earned this sell
        BigDecimal earned = event.getNewBalance().subtract(event.getOldBalance());
        if (earned.compareTo(BigDecimal.ZERO) <= 0) return;

        double totalEarned = earned.doubleValue();
        List<ItemStack> snapshots = pending.snapshots;
        if (snapshots.isEmpty()) return;

        // Build a mutable copy of what is currently in the player's inventory
        // (post-sell state — EssentialsX has already removed the sold items).
        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                remaining.add(item.clone());
            }
        }

        // Determine which items were actually sold by comparing pre-sell
        // snapshot quantities to post-sell quantities.
        List<ItemStack> soldItems = new ArrayList<>();
        int totalUnitsSold = 0;

        for (ItemStack snapshot : snapshots) {
            int had = snapshot.getAmount();
            int stillHas = consumeFromRemaining(remaining, snapshot);
            int soldCount = had - stillHas;

            if (soldCount <= 0) continue; // EssentialsX didn't sell this item

            ItemStack soldItem = snapshot.clone();
            soldItem.setAmount(soldCount);
            soldItems.add(soldItem);
            totalUnitsSold += soldCount;
        }

        if (soldItems.isEmpty() || totalUnitsSold <= 0) return;

        // Distribute earnings proportionally by units sold.
        double pricePerUnit = totalEarned / totalUnitsSold;
        long now = System.currentTimeMillis();

        for (ItemStack soldItem : soldItems) {
            try {
                byte[] bytes = soldItem.serializeAsBytes();
                double recordPrice = pricePerUnit * soldItem.getAmount();
                manager.addRecord(uuid, new SellRecord(uuid, bytes, recordPrice, soldItem.getAmount(), now));
            } catch (Exception e) {
                // Serialization failure for this item — skip it
            }
        }
    }

    /**
     * Finds items similar to {@code target} in {@code remaining} and "consumes"
     * up to {@code target.getAmount()} units from them.
     *
     * Returns how many units of the target item are still present after consuming.
     * Mutates {@code remaining} by reducing stack amounts (removes empty stacks).
     */
    private int consumeFromRemaining(List<ItemStack> remaining, ItemStack target) {
        int needed = target.getAmount();
        int found = 0;

        for (int i = 0; i < remaining.size() && found < needed; i++) {
            ItemStack r = remaining.get(i);
            if (!r.isSimilar(target)) continue;

            int available = r.getAmount();
            int take = Math.min(available, needed - found);
            found += take;
            r.setAmount(available - take);
            if (r.getAmount() <= 0) {
                remaining.remove(i);
                i--;
            }
        }

        // Returns how many are still present (not consumed = still in inventory)
        return found;
    }
}
