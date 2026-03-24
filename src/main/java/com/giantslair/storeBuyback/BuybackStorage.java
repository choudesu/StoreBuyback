package com.giantslair.storeBuyback;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Handles all YAML read/write for per-player sell history.
 * Files are stored at: plugins/StoreBuyback/playerdata/<uuid>.yml
 *
 * Items are serialized via ItemStack.serializeAsBytes() and stored as Base64
 * strings to fully preserve NBT data (including EliteMobs custom attributes).
 */
public class BuybackStorage {

    private final StoreBuyback plugin;
    private final File playerDataDir;

    public BuybackStorage(StoreBuyback plugin) {
        this.plugin = plugin;
        this.playerDataDir = new File(plugin.getDataFolder(), "playerdata");
        if (!playerDataDir.exists()) {
            playerDataDir.mkdirs();
        }
    }

    public void savePlayerHistory(UUID uuid, List<SellRecord> records) {
        File file = getPlayerFile(uuid);
        YamlConfiguration config = new YamlConfiguration();

        for (SellRecord record : records) {
            String path = "records." + record.getRecordId();
            config.set(path + ".item", Base64.getEncoder().encodeToString(record.getItemBytes()));
            config.set(path + ".sale-price", record.getSalePrice());
            config.set(path + ".sold-amount", record.getSoldAmount());
            config.set(path + ".timestamp", record.getTimestamp());
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save sell history for " + uuid, e);
        }
    }

    public List<SellRecord> loadPlayerHistory(UUID uuid) {
        File file = getPlayerFile(uuid);
        List<SellRecord> records = new ArrayList<>();

        if (!file.exists()) {
            return records;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("records");
        if (section == null) {
            return records;
        }

        for (String recordId : section.getKeys(false)) {
            try {
                String encoded = section.getString(recordId + ".item");
                if (encoded == null) continue;

                byte[] itemBytes = Base64.getDecoder().decode(encoded);
                double salePrice = section.getDouble(recordId + ".sale-price");
                int soldAmount = section.getInt(recordId + ".sold-amount");
                long timestamp = section.getLong(recordId + ".timestamp");

                records.add(new SellRecord(recordId, uuid, itemBytes, salePrice, soldAmount, timestamp));
            } catch (Exception e) {
                plugin.getLogger().warning("Skipping corrupt sell record '" + recordId + "' for " + uuid + ": " + e.getMessage());
            }
        }

        // Sort most recent first
        records.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        return records;
    }

    public void deletePlayerHistory(UUID uuid) {
        File file = getPlayerFile(uuid);
        if (file.exists()) {
            file.delete();
        }
    }

    private File getPlayerFile(UUID uuid) {
        return new File(playerDataDir, uuid + ".yml");
    }
}
