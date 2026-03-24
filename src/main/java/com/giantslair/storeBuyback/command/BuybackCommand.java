package com.giantslair.storeBuyback.command;

import com.giantslair.storeBuyback.BuybackManager;
import com.giantslair.storeBuyback.BuybackStorage;
import com.giantslair.storeBuyback.gui.BuybackGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class BuybackCommand implements CommandExecutor {

    private final BuybackManager manager;
    private final BuybackStorage storage;
    private final BuybackGUI gui;

    public BuybackCommand(BuybackManager manager, BuybackStorage storage, BuybackGUI gui) {
        this.manager = manager;
        this.storage = storage;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("open")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can open the buyback GUI.", NamedTextColor.RED));
                return true;
            }
            if (!player.hasPermission("storebyback.use")) {
                player.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
                return true;
            }
            gui.open(player, 0);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("reload")) {
            if (!sender.hasPermission("storebyback.admin")) {
                sender.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
                return true;
            }
            manager.reloadConfig();
            sender.sendMessage(Component.text("StoreBuyback config reloaded.", NamedTextColor.GREEN));
            return true;
        }

        if (sub.equals("clear")) {
            if (!sender.hasPermission("storebyback.admin")) {
                sender.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
                return true;
            }

            UUID targetUUID;
            String targetName;

            if (args.length >= 2) {
                @SuppressWarnings("deprecation")
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                targetUUID = target.getUniqueId();
                targetName = target.getName() != null ? target.getName() : args[1];
            } else if (sender instanceof Player player) {
                targetUUID = player.getUniqueId();
                targetName = player.getName();
            } else {
                sender.sendMessage(Component.text("Usage: /buyback clear <player>", NamedTextColor.RED));
                return true;
            }

            storage.deletePlayerHistory(targetUUID);
            manager.clearCache(targetUUID);
            sender.sendMessage(Component.text("Sell history cleared for ", NamedTextColor.GREEN)
                    .append(Component.text(targetName, NamedTextColor.WHITE))
                    .append(Component.text(".", NamedTextColor.GREEN)));
            return true;
        }

        sender.sendMessage(Component.text("Usage: /buyback [reload|clear [player]]", NamedTextColor.YELLOW));
        return true;
    }
}
