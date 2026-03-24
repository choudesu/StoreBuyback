package com.giantslair.storeByback;

import com.giantslair.storeByback.command.BuybackCommand;
import com.giantslair.storeByback.command.BuybackTabCompleter;
import com.giantslair.storeByback.gui.BuybackGUI;
import com.giantslair.storeByback.listener.SellPostListener;
import com.giantslair.storeByback.listener.SellPreListener;
import net.ess3.api.IEssentials;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class StoreByback extends JavaPlugin {

    private BuybackManager manager;

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void onEnable() {
        saveDefaultConfig();

        // Require Vault
        Economy economy = setupEconomy();
        if (economy == null) {
            getLogger().severe("Vault Economy provider not found — disabling StoreByback.");
            setEnabled(false);
            return;
        }

        // Require EssentialsX
        Plugin essPlugin = getServer().getPluginManager().getPlugin("Essentials");
        if (!(essPlugin instanceof IEssentials essentials)) {
            getLogger().severe("EssentialsX not found or not enabled — disabling StoreByback.");
            setEnabled(false);
            return;
        }

        BuybackStorage storage = new BuybackStorage(this);
        manager = new BuybackManager(this, storage, economy);
        manager.initialize();

        BuybackGUI gui = new BuybackGUI(manager, economy, this);

        // Register listeners
        getServer().getPluginManager().registerEvents(new SellPreListener(manager, essentials, this), this);
        getServer().getPluginManager().registerEvents(new SellPostListener(manager), this);
        getServer().getPluginManager().registerEvents(gui, this);

        // Clear cache on player quit to keep memory footprint small
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onQuit(PlayerQuitEvent event) {
                manager.clearCache(event.getPlayer().getUniqueId());
            }
        }, this);

        // Register command
        BuybackCommand commandExecutor = new BuybackCommand(manager, storage, gui);
        BuybackTabCompleter tabCompleter = new BuybackTabCompleter();
        var buybackCommand = getCommand("buyback");
        if (buybackCommand != null) {
            buybackCommand.setExecutor(commandExecutor);
            buybackCommand.setTabCompleter(tabCompleter);
        }

        getLogger().info("StoreByback enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdown();
        }
        getLogger().info("StoreByback disabled.");
    }

    private Economy setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return null;
        return rsp.getProvider();
    }
}
