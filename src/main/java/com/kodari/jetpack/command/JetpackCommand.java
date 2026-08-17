package com.kodari.jetpack.command;

import com.kodari.jetpack.JetpackWingsuitPlugin;
import com.kodari.jetpack.item.FlightItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class JetpackCommand implements CommandExecutor, TabCompleter {
    private final JetpackWingsuitPlugin plugin;
    private final FlightItemFactory items;

    public JetpackCommand(JetpackWingsuitPlugin plugin, FlightItemFactory items) {
        this.plugin = plugin;
        this.items = items;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(message("usage"));
            return true;
        }
        if (!sender.hasPermission("jetpack.admin")) {
            sender.sendMessage(message("no-permission"));
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            sender.sendMessage(message("reloaded"));
            return true;
        }
        if (args[0].equalsIgnoreCase("give")) {
            return give(sender, args);
        }
        if (args[0].equalsIgnoreCase("fuel")) {
            return addFuel(sender, args);
        }
        sender.sendMessage(message("usage"));
        return true;
    }

    private boolean give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(message("usage"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }
        ItemStack item;
        String type;
        if (args[2].equalsIgnoreCase("jetpack")) {
            type = "Jetpack";
            item = items.createJetpack();
        } else if (args[2].equalsIgnoreCase("wingsuit")) {
            type = "Wingsuit";
            item = items.createWingsuit();
        } else {
            sender.sendMessage(message("usage"));
            return true;
        }
        if (item != null) {
            target.getInventory().addItem(item);
            sender.sendMessage(message("given").replace("{item}", type).replace("{player}", target.getName()));
        }
        return true;
    }

    private boolean addFuel(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(message("player-only"));
            return true;
        }
        Player player = (Player) sender;
        int amount = 100;
        if (args.length > 1) {
            try {
                amount = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException ignored) {
                sender.sendMessage(ChatColor.RED + "Fuel amount must be a number.");
                return true;
            }
        }
        int remaining = amount;
        while (remaining > 0) {
            ItemStack fuel = items.createFuel(remaining);
            if (fuel == null) {
                break;
            }
            int offered = fuel.getAmount();
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(fuel);
            int leftoverAmount = 0;
            for (ItemStack leftover : leftovers.values()) {
                leftoverAmount += leftover.getAmount();
            }
            int added = offered - leftoverAmount;
            if (added <= 0) {
                break;
            }
            remaining -= added;
        }
        int added = amount - remaining;
        sender.sendMessage(message("fuel-added").replace("{amount}", String.valueOf(added))
                .replace("{fuel}", String.valueOf(items.countFuel(player))));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partial(args[0], Arrays.asList("give", "fuel", "reload"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return partial(args[1], names);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return partial(args[2], Arrays.asList("jetpack", "wingsuit"));
        }
        return Collections.emptyList();
    }

    private List<String> partial(String input, List<String> options) {
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(input.toLowerCase())) {
                result.add(option);
            }
        }
        return result;
    }

    private String message(String key) {
        return ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages." + key, ""));
    }
}
