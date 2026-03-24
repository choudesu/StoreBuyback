package com.giantslair.storeBuyback.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class BuybackTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("reload", "clear");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String partial = args[0].toLowerCase();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(partial) && sender.hasPermission("storebyback.admin")) {
                    completions.add(sub);
                }
            }
            return completions;
        }
        return List.of();
    }
}
