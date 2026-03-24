package com.giantslair.storeBuyback;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Immutable snapshot of a single sell transaction.
 * Items are stored as raw bytes via ItemStack.serializeAsBytes() to preserve
 * all NBT data including EliteMobs custom attributes.
 */
public final class SellRecord {

    private final String recordId;
    private final UUID playerUUID;
    private final byte[] itemBytes;
    private final double salePrice;
    private final int soldAmount;
    private final long timestamp;

    public SellRecord(UUID playerUUID, byte[] itemBytes, double salePrice, int soldAmount, long timestamp) {
        this.recordId = UUID.randomUUID().toString();
        this.playerUUID = playerUUID;
        this.itemBytes = itemBytes;
        this.salePrice = salePrice;
        this.soldAmount = soldAmount;
        this.timestamp = timestamp;
    }

    /** Constructor used when loading from storage (recordId is preserved). */
    public SellRecord(String recordId, UUID playerUUID, byte[] itemBytes, double salePrice, int soldAmount, long timestamp) {
        this.recordId = recordId;
        this.playerUUID = playerUUID;
        this.itemBytes = itemBytes;
        this.salePrice = salePrice;
        this.soldAmount = soldAmount;
        this.timestamp = timestamp;
    }

    public String getRecordId() { return recordId; }
    public UUID getPlayerUUID() { return playerUUID; }
    public byte[] getItemBytes() { return itemBytes; }
    public double getSalePrice() { return salePrice; }
    public int getSoldAmount() { return soldAmount; }
    public long getTimestamp() { return timestamp; }

    /**
     * Reconstructs the ItemStack from serialized bytes.
     * Returns null if deserialization fails (e.g., corrupt data or missing mod).
     */
    public ItemStack deserializeItem() {
        try {
            return ItemStack.deserializeBytes(itemBytes);
        } catch (Exception e) {
            return null;
        }
    }
}
