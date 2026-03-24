package com.giantslair.storeBuyback;

import com.giantslair.storeBuyback.command.BuybackCommand;
import com.giantslair.storeBuyback.command.BuybackTabCompleter;
import com.giantslair.storeBuyback.gui.BuybackGUI;
import com.giantslair.storeBuyback.listener.SellPostListener;
import com.giantslair.storeBuyback.listener.SellPreListener;
import net.ess3.api.IEssentials;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class StoreBuyback extends JavaPlugin {

    private BuybackManager manager;

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void onEnable() {
        saveDefaultConfig();

        // Require Vault
        Economy economy = setupEconomy();
        if (economy == null) {
            getLogger().severe("Vault Economy provider not found — disabling StoreBuyback.");
            setEnabled(false);
            return;
        }

        // Require EssentialsX
        Plugin essPlugin = getServer().getPluginManager().getPlugin("Essentials");
        if (!(essPlugin instanceof IEssentials essentials)) {
            getLogger().severe("EssentialsX not found or not enabled — disabling StoreBuyback.");
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

        getLogger().info("StoreBuyback enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdown();
        }
        getLogger().info("StoreBuyback disabled.");
    }

    private Economy setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return null;
        return rsp.getProvider();
    }
}
