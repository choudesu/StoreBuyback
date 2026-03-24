package com.giantslair.storeBuyback.listener;

import com.giantslair.storeBuyback.BuybackManager;
import net.ess3.api.IEssentials;
import net.ess3.api.IUser;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Phase 1 of two-phase sell interception.
 *
 * Snapshots items from the player's inventory BEFORE EssentialsX processes
 * the /sell command and removes them. The snapshot is stored in BuybackManager
 * so that SellPostListener can retrieve it after the sale completes.
 *
 * Cleanup task prevents memory leaks if the post-event never fires
 * (e.g. sell command was blocked or failed).
 */
public class SellPreListener implements Listener {

    private final BuybackManager manager;
    private final IEssentials essentials;
    private final Plugin plugin;

    private static final long CLEANUP_DELAY_TICKS = 100L; // 5 seconds

    public SellPreListener(BuybackManager manager, IEssentials essentials, Plugin plugin) {
        this.manager = manager;
        this.essentials = essentials;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();

        // Match /sell and /essentials:sell exactly (with or without arguments).
        // Use word-boundary check to avoid matching /sellall, /selltrade, etc.
        String lower = message.toLowerCase();
        boolean isSell = lower.equals("/sell") || lower.startsWith("/sell ");
        boolean isEssSell = lower.equals("/essentials:sell") || lower.startsWith("/essentials:sell ");
        if (!isSell && !isEssSell) {
            return;
        }

        // Normalise: strip the command prefix to get just the argument portion
        String args;
        if (lower.startsWith("/essentials:sell")) {
            args = message.substring("/essentials:sell".length()).trim();
        } else {
            args = message.substring("/sell".length()).trim();
        }

        // No arguments — EssentialsX will show usage, no sell happens
        if (args.isEmpty()) {
            return;
        }

        String firstArg = args.split("\\s+")[0].toLowerCase();

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Get balance before the sell using Essentials API
        double balanceBefore = 0.0;
        try {
            IUser user = essentials.getUser(player);
            if (user != null) {
                balanceBefore = user.getMoney().doubleValue();
            }
        } catch (Exception ignored) {}

        List<ItemStack> snapshots = new ArrayList<>();

        if (firstArg.equals("hand")) {
            if (!manager.isRecordHandSells()) return;
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held.getType().isAir()) return;
            snapshots.add(held.clone());

        } else if (firstArg.equals("inventory") || firstArg.equals("invent") || firstArg.equals("all")) {
            if (!manager.isRecordBulkSells()) return;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && !item.getType().isAir()) {
                    snapshots.add(item.clone());
                }
            }

        } else {
            // Named item or other argument — snapshot the whole inventory as a best-effort capture.
            // SellPostListener will use balance delta to attribute price.
            if (!manager.isRecordHandSells()) return;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && !item.getType().isAir()) {
                    snapshots.add(item.clone());
                }
            }
        }

        if (snapshots.isEmpty()) return;

        BuybackManager.PendingSell pending = new BuybackManager.PendingSell(uuid, snapshots, balanceBefore);
        manager.storePending(uuid, pending);

        // Schedule cleanup in case the post-event never fires
        final double finalBefore = balanceBefore;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> manager.clearPending(uuid), CLEANUP_DELAY_TICKS);
    }
}
