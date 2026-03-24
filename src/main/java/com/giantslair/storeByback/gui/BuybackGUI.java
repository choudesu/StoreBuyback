package com.giantslair.storeByback.gui;

import com.giantslair.storeByback.BuybackManager;
import com.giantslair.storeByback.SellRecord;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.*;

/**
 * Chest GUI for the buyback system.
 *
 * Layout: 6-row chest (54 slots)
 *   Slots 0-44: Item slots (45 items per page)
 *   Slot 45-46: Previous page button
 *   Slot 48: Page info item
 *   Slot 52-53: Next page button
 *   Other row-5 slots: Gray glass pane fillers
 *
 * Uses BuybackHolder as the InventoryHolder so click events can be identified
 * without comparing title strings.
 */
public class BuybackGUI implements Listener {

    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9; // 54
    private static final int ITEMS_PER_PAGE = 45; // slots 0-44
    private static final Component TITLE = Component.text("Buyback History", NamedTextColor.DARK_GRAY);

    private static final int SLOT_PREV_LEFT = 45;
    private static final int SLOT_PREV_RIGHT = 46;
    private static final int SLOT_INFO = 48;
    private static final int SLOT_NEXT_LEFT = 51;
    private static final int SLOT_NEXT_RIGHT = 52;

    private final BuybackManager manager;
    private final Economy economy;
    private final Plugin plugin;

    // Active sessions keyed by player UUID
    private final Map<UUID, BuybackSession> sessions = new HashMap<>();

    public BuybackGUI(BuybackManager manager, Economy economy, Plugin plugin) {
        this.manager = manager;
        this.economy = economy;
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // InventoryHolder that carries the session
    // -------------------------------------------------------------------------

    public static final class BuybackHolder implements InventoryHolder {
        private final BuybackSession session;

        BuybackHolder(BuybackSession session) {
            this.session = session;
        }

        public BuybackSession getSession() { return session; }

        @Override
        public Inventory getInventory() { return null; } // not used directly
    }

    // -------------------------------------------------------------------------
    // Session data
    // -------------------------------------------------------------------------

    private static final class BuybackSession {
        final UUID playerUUID;
        int page;
        List<SellRecord> snapshot; // copy at the time the GUI was opened/refreshed

        BuybackSession(UUID playerUUID, int page, List<SellRecord> snapshot) {
            this.playerUUID = playerUUID;
            this.page = page;
            this.snapshot = snapshot;
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void open(Player player, int page) {
        List<SellRecord> history = manager.getHistory(player.getUniqueId());

        if (history.isEmpty()) {
            player.sendMessage(Component.text("You have no items to buy back.", NamedTextColor.YELLOW));
            return;
        }

        // Clamp page
        int maxPage = Math.max(0, (history.size() - 1) / ITEMS_PER_PAGE);
        page = Math.min(page, maxPage);

        BuybackSession session = new BuybackSession(player.getUniqueId(), page, history);
        sessions.put(player.getUniqueId(), session);

        Inventory inv = buildInventory(session);
        player.openInventory(inv);
    }

    // -------------------------------------------------------------------------
    // Inventory construction
    // -------------------------------------------------------------------------

    private Inventory buildInventory(BuybackSession session) {
        BuybackHolder holder = new BuybackHolder(session);
        Inventory inv = Bukkit.createInventory(holder, SIZE, TITLE);

        List<SellRecord> snapshot = session.snapshot;
        int page = session.page;
        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, snapshot.size());

        // Fill item slots 0-44
        for (int i = start; i < end; i++) {
            SellRecord record = snapshot.get(i);
            ItemStack display = buildDisplayItem(record);
            if (display != null) {
                inv.setItem(i - start, display);
            }
        }

        // Fill unused item slots with glass pane
        ItemStack filler = buildFiller();
        for (int slot = (end - start); slot < ITEMS_PER_PAGE; slot++) {
            inv.setItem(slot, filler);
        }

        // Navigation row (row 5, slots 45-53) — filler by default
        for (int slot = ITEMS_PER_PAGE; slot < SIZE; slot++) {
            inv.setItem(slot, filler);
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) snapshot.size() / ITEMS_PER_PAGE));

        // Previous page button
        if (page > 0) {
            ItemStack prev = buildNavButton(Material.ARROW,
                    Component.text("« Previous Page", NamedTextColor.AQUA),
                    Component.text("Page " + page + " of " + totalPages, NamedTextColor.GRAY));
            inv.setItem(SLOT_PREV_LEFT, prev);
            inv.setItem(SLOT_PREV_RIGHT, prev.clone());
        }

        // Info item
        ItemStack info = buildNavButton(Material.BOOK,
                Component.text("Page " + (page + 1) + " / " + totalPages, NamedTextColor.WHITE),
                Component.text("Showing items " + (start + 1) + "–" + end, NamedTextColor.GRAY),
                Component.text("of " + snapshot.size() + " total", NamedTextColor.GRAY));
        inv.setItem(SLOT_INFO, info);

        // Next page button
        if (end < snapshot.size()) {
            ItemStack next = buildNavButton(Material.ARROW,
                    Component.text("Next Page »", NamedTextColor.AQUA),
                    Component.text("Page " + (page + 2) + " of " + totalPages, NamedTextColor.GRAY));
            inv.setItem(SLOT_NEXT_LEFT, next);
            inv.setItem(SLOT_NEXT_RIGHT, next.clone());
        }

        return inv;
    }

    private ItemStack buildDisplayItem(SellRecord record) {
        ItemStack base = record.deserializeItem();
        if (base == null) return null;

        // Clone so we don't mutate the deserialized item
        ItemStack display = base.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) return display;

        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();

        lore.add(Component.empty());
        lore.add(Component.text("─────────────────", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Sold: ", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(record.getSoldAmount() + "x", NamedTextColor.WHITE)));
        lore.add(Component.text("Sold for: ", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("$" + formatPrice(record.getSalePrice()), NamedTextColor.YELLOW)));
        lore.add(Component.text("Buyback cost: ", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("$" + formatPrice(manager.getBuybackPrice(record)), NamedTextColor.GOLD)));
        lore.add(Component.text(formatTimeAgo(record.getTimestamp()), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Click to buy back", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private ItemStack buildFiller() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildNavButton(Material material, Component name, Component... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (Component line : loreLines) {
            lore.add(line.decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // -------------------------------------------------------------------------
    // Event handling
    // -------------------------------------------------------------------------

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BuybackHolder holder)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) return; // clicked outside the GUI

        BuybackSession session = holder.getSession();

        // Navigation row
        if (slot >= ITEMS_PER_PAGE) {
            if (slot == SLOT_PREV_LEFT || slot == SLOT_PREV_RIGHT) {
                if (session.page > 0) {
                    session.page--;
                    player.openInventory(buildInventory(session));
                }
            } else if (slot == SLOT_NEXT_LEFT || slot == SLOT_NEXT_RIGHT) {
                int maxPage = Math.max(0, (session.snapshot.size() - 1) / ITEMS_PER_PAGE);
                if (session.page < maxPage) {
                    session.page++;
                    player.openInventory(buildInventory(session));
                }
            }
            return;
        }

        // Item slot
        int recordIndex = session.page * ITEMS_PER_PAGE + slot;
        if (recordIndex >= session.snapshot.size()) return; // filler slot

        SellRecord record = session.snapshot.get(recordIndex);
        handleBuyback(player, session, record);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof BuybackHolder) {
            sessions.remove(event.getPlayer().getUniqueId());
        }
    }

    // -------------------------------------------------------------------------
    // Buyback logic
    // -------------------------------------------------------------------------

    private void handleBuyback(Player player, BuybackSession session, SellRecord record) {
        if (!player.hasPermission("storebyback.use")) {
            player.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
            return;
        }

        // Claim the record FIRST — atomic on Bukkit's main thread.
        // If it returns false, this record was already claimed by a previous click
        // (e.g. rapid double-click). Abort silently to prevent item duplication.
        if (!manager.removeRecord(player.getUniqueId(), record.getRecordId())) {
            return;
        }

        double price = manager.getBuybackPrice(record);
        double balance = economy.getBalance(player);

        if (!economy.has(player, price)) {
            // Can't afford — put the record back and inform the player
            manager.addRecord(player.getUniqueId(), record);
            player.sendMessage(
                Component.text("You need ", NamedTextColor.RED)
                    .append(Component.text("$" + formatPrice(price), NamedTextColor.GOLD))
                    .append(Component.text(" to buy that back but only have ", NamedTextColor.RED))
                    .append(Component.text("$" + formatPrice(balance), NamedTextColor.GOLD))
                    .append(Component.text(".", NamedTextColor.RED))
            );
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            refreshGUI(player, session);
            return;
        }

        EconomyResponse response = economy.withdrawPlayer(player, price);
        if (response.type != EconomyResponse.ResponseType.SUCCESS) {
            // Economy failure — put the record back
            manager.addRecord(player.getUniqueId(), record);
            player.sendMessage(Component.text("Transaction failed: " + response.errorMessage, NamedTextColor.RED));
            refreshGUI(player, session);
            return;
        }

        ItemStack item = record.deserializeItem();
        if (item == null) {
            // Corrupt data — record is already removed; refund economy charge
            economy.depositPlayer(player, price);
            player.sendMessage(Component.text("That item could not be restored (corrupt data). You were not charged.", NamedTextColor.RED));
            refreshGUI(player, session);
            return;
        }

        // Give item — drop at feet if inventory is full
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
            player.sendMessage(Component.text("Your inventory was full — the item was dropped at your feet.", NamedTextColor.YELLOW));
        }

        // Determine a display name for feedback
        Component itemName = item.getItemMeta() != null && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().displayName()
                : Component.translatable(item);

        player.sendMessage(
            Component.text("Bought back ", NamedTextColor.GREEN)
                .append(Component.text(record.getSoldAmount() + "x ", NamedTextColor.WHITE))
                .append(itemName != null ? itemName : Component.text(item.getType().name(), NamedTextColor.WHITE))
                .append(Component.text(" for ", NamedTextColor.GREEN))
                .append(Component.text("$" + formatPrice(price), NamedTextColor.GOLD))
                .append(Component.text(".", NamedTextColor.GREEN))
        );

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);

        refreshGUI(player, session);
    }

    private void refreshGUI(Player player, BuybackSession session) {
        List<SellRecord> updated = manager.getHistory(player.getUniqueId());
        if (updated.isEmpty()) {
            player.closeInventory();
            player.sendMessage(Component.text("No more items to buy back.", NamedTextColor.YELLOW));
            return;
        }

        int maxPage = Math.max(0, (updated.size() - 1) / ITEMS_PER_PAGE);
        session.page = Math.min(session.page, maxPage);
        session.snapshot = updated;

        // Reopen with fresh inventory (close current, schedule open next tick to avoid Bukkit issues)
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.openInventory(buildInventory(session));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Formatting helpers
    // -------------------------------------------------------------------------

    private static String formatPrice(double price) {
        if (price == Math.floor(price)) {
            return String.format("%,.0f", price);
        }
        return String.format("%,.2f", price);
    }

    private static String formatTimeAgo(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        if (diff < 0) diff = 0;

        Duration d = Duration.ofMillis(diff);
        long hours = d.toHours();
        long minutes = d.toMinutesPart();

        if (hours >= 24) {
            return "Sold " + (hours / 24) + "d " + (hours % 24) + "h ago";
        } else if (hours > 0) {
            return "Sold " + hours + "h " + minutes + "m ago";
        } else if (minutes > 0) {
            return "Sold " + minutes + "m ago";
        } else {
            return "Sold just now";
        }
    }
}
