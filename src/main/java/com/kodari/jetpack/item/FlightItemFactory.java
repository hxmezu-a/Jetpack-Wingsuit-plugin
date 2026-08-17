package com.kodari.jetpack.item;

import com.cryptomorin.xseries.XMaterial;
import com.kodari.jetpack.JetpackWingsuitPlugin;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class FlightItemFactory {
    private final JetpackWingsuitPlugin plugin;
    private final NamespacedKey jetpackKey;
    private final NamespacedKey wingsuitKey;
    private final NamespacedKey durabilityKey;

    public FlightItemFactory(JetpackWingsuitPlugin plugin) {
        this.plugin = plugin;
        jetpackKey = new NamespacedKey(plugin, "jetpack");
        wingsuitKey = new NamespacedKey(plugin, "wingsuit");
        durabilityKey = new NamespacedKey(plugin, "durability");
    }

    public ItemStack createJetpack() {
        return create("jetpack");
    }

    public ItemStack createWingsuit() {
        return create("wingsuit");
    }

    public boolean isJetpack(ItemStack item) {
        return isFlightItem(item, "jetpack", jetpackKey);
    }

    public boolean isWingsuit(ItemStack item) {
        return isFlightItem(item, "wingsuit", wingsuitKey);
    }

    public void ensure(ItemStack item, String type) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        NamespacedKey typeKey = type.equals("jetpack") ? jetpackKey : wingsuitKey;
        boolean changed = false;
        if (!meta.getPersistentDataContainer().has(typeKey, PersistentDataType.BYTE)) {
            meta.getPersistentDataContainer().set(typeKey, PersistentDataType.BYTE, (byte) 1);
            changed = true;
        }
        if (type.equals("jetpack") && !meta.getPersistentDataContainer().has(durabilityKey, PersistentDataType.INTEGER)) {
            meta.getPersistentDataContainer().set(durabilityKey, PersistentDataType.INTEGER, 0);
            changed = true;
        }
        if (changed) {
            item.setItemMeta(meta);
        }
    }

    public ItemStack createFuel(int amount) {
        XMaterial material = XMaterial.matchXMaterial(plugin.getConfig().getString("fuel.material", "COAL"))
                .orElse(XMaterial.matchXMaterial("COAL").orElse(null));
        if (material == null) {
            return null;
        }
        ItemStack item = material.parseItem();
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(color(plugin.getConfig().getString("fuel.name", "&c&lJetpack Fuel")));
        List<String> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("fuel.lore")) {
            lore.add(color(line));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        item.setAmount(Math.max(1, Math.min(amount, item.getMaxStackSize())));
        return item;
    }

    public boolean isFuel(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        XMaterial configured = XMaterial.matchXMaterial(plugin.getConfig().getString("fuel.material", "COAL"))
                .orElse(null);
        if (configured == null || configured.parseMaterial() != item.getType()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !Objects.equals(meta.getDisplayName(),
                color(plugin.getConfig().getString("fuel.name", "&c&lJetpack Fuel")))) {
            return false;
        }
        List<String> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("fuel.lore")) {
            lore.add(color(line));
        }
        return Objects.equals(meta.getLore(), lore);
    }

    public int countFuel(Player player) {
        int amount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isFuel(item)) {
                amount += item.getAmount();
            }
        }
        return amount;
    }

    public boolean hasFuel(Player player, int amount) {
        return countFuel(player) >= Math.max(1, amount);
    }

    public boolean consumeFuel(Player player, int amount) {
        int remaining = Math.max(1, amount);
        if (!hasFuel(player, remaining)) {
            return false;
        }
        for (int slot = 0; slot < player.getInventory().getContents().length && remaining > 0; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (!isFuel(item)) {
                continue;
            }
            int consumed = Math.min(remaining, item.getAmount());
            remaining -= consumed;
            if (consumed == item.getAmount()) {
                player.getInventory().setItem(slot, null);
            } else {
                item.setAmount(item.getAmount() - consumed);
            }
        }
        return remaining == 0;
    }

    public boolean damageJetpack(ItemStack item, int amount) {
        ensure(item, "jetpack");
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        int damage = meta.getPersistentDataContainer().getOrDefault(durabilityKey, PersistentDataType.INTEGER, 0) + amount;
        int maximum = Math.max(1, plugin.getConfig().getInt("jetpack.durability", 112));
        if (damage >= maximum) {
            return true;
        }
        meta.getPersistentDataContainer().set(durabilityKey, PersistentDataType.INTEGER, damage);
        item.setItemMeta(meta);
        return false;
    }

    public String itemName(String type) {
        return color(plugin.getConfig().getString(type + ".name", type));
    }

    private ItemStack create(String type) {
        XMaterial material = XMaterial.matchXMaterial(plugin.getConfig().getString(type + ".material", "AIR"))
                .orElse(XMaterial.matchXMaterial("AIR").orElse(null));
        if (material == null) {
            return null;
        }
        ItemStack item = material.parseItem();
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(itemName(type));
        List<String> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList(type + ".lore")) {
            lore.add(color(line));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        ensure(item, type);
        return item;
    }

    private boolean isFlightItem(ItemStack item, String type, NamespacedKey key) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            return true;
        }
        XMaterial configured = XMaterial.matchXMaterial(plugin.getConfig().getString(type + ".material", "AIR")).orElse(null);
        if (configured == null || configured.parseMaterial() != item.getType()) {
            return false;
        }
        String displayName = meta.getDisplayName();
        return displayName != null && ChatColor.stripColor(displayName).equalsIgnoreCase(ChatColor.stripColor(itemName(type)));
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }
}
