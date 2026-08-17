package com.kodari.jetpack.flight;

import com.cryptomorin.xseries.particles.XParticle;
import com.kodari.jetpack.JetpackWingsuitPlugin;
import com.kodari.jetpack.item.FlightItemFactory;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class FlightManager implements Listener {
    private final JetpackWingsuitPlugin plugin;
    private final FlightItemFactory items;
    private final Set<UUID> jetpackFlying = new HashSet<>();
    private final Set<UUID> wingsuitFlying = new HashSet<>();
    private final Map<UUID, Boolean> previousFlightAbility = new HashMap<>();
    private final Map<UUID, Float> previousFlySpeed = new HashMap<>();
    private final Map<UUID, Long> lastBoost = new HashMap<>();
    private long ticks;

    public FlightManager(JetpackWingsuitPlugin plugin, FlightItemFactory items) {
        this.plugin = plugin;
        this.items = items;
    }

    public void tick() {
        ticks++;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            ItemStack chestplate = player.getInventory().getChestplate();
            boolean jetpack = items.isJetpack(chestplate);
            boolean wingsuit = items.isWingsuit(chestplate);

            if (jetpack || wingsuit) {
                prepareFlightAbility(player);
            }
            if (!jetpack && jetpackFlying.contains(player.getUniqueId())) {
                stopJetpack(player);
            }
            if (!wingsuit && wingsuitFlying.contains(player.getUniqueId())) {
                stopWingsuit(player);
            }
            if (!jetpack && !wingsuit) {
                restoreFlightAbility(player);
                continue;
            }
            if (jetpackFlying.contains(player.getUniqueId())) {
                tickJetpack(player, chestplate);
            } else if (wingsuitFlying.contains(player.getUniqueId()) && !player.isOnGround()) {
                tickWingsuit(player, chestplate);
            }

            if (wingsuit && !wingsuitFlying.contains(player.getUniqueId())
                    && player.isOnGround() && player.isSneaking()
                    && isLookingUp(player, "wingsuit.takeoff-upward-angle")) {
                startWingsuit(player, chestplate);
            }
        }
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        ItemStack chestplate = player.getInventory().getChestplate();
        if (items.isJetpack(chestplate)) {
            event.setCancelled(true);
            if (jetpackFlying.contains(player.getUniqueId())) {
                stopJetpack(player);
            } else {
                startJetpack(player, chestplate);
            }
            return;
        }
        if (items.isWingsuit(chestplate)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (isRestricted(event.getPlayer(), event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player && isRestricted((Player) event.getEntity(), event.getBow())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            if (isRestricted(player, event.getCurrentItem())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        clear(event.getEntity());
    }

    private void startJetpack(Player player, ItemStack item) {
        items.ensure(item, "jetpack");
        int cost = Math.max(1, plugin.getConfig().getInt("fuel.consumption.jetpack", 1));
        if (plugin.getConfig().getBoolean("fuel.require-before-flight", true) && !items.hasFuel(player, cost)) {
            return;
        }
        prepareFlightAbility(player);
        previousFlySpeed.putIfAbsent(player.getUniqueId(), player.getFlySpeed());
        jetpackFlying.add(player.getUniqueId());
        player.setFlying(true);
        player.setFallDistance(0);
    }

    private void stopJetpack(Player player) {
        if (!jetpackFlying.remove(player.getUniqueId())) {
            return;
        }
        player.setFlying(false);
        Float flySpeed = previousFlySpeed.remove(player.getUniqueId());
        if (flySpeed != null) {
            player.setFlySpeed(flySpeed);
        }
        if (!wingsuitFlying.contains(player.getUniqueId())) {
            restoreFlightAbility(player);
        }
    }

    private void tickJetpack(Player player, ItemStack item) {
        if (!player.isOnline()) {
            return;
        }
        player.setFlying(true);
        player.setFallDistance(0);
        double speed = plugin.getConfig().getDouble("jetpack.flight-speed", 1.0);
        if (player.isSprinting()) {
            speed *= plugin.getConfig().getDouble("jetpack.sprint-speed-multiplier", 1.65);
        }
        player.setFlySpeed((float) Math.max(0.01, Math.min(1, speed * 0.1)));
        Vector direction = player.getLocation().getDirection().normalize();
        spawnJetpackParticles(player, direction);

        int interval = Math.max(1, plugin.getConfig().getInt("fuel.interval-ticks.jetpack", 40));
        if (ticks % interval == 0 && !useJetpackResources(player, item)) {
            stopJetpack(player);
        }
    }

    private boolean useJetpackResources(Player player, ItemStack item) {
        int cost = Math.max(1, plugin.getConfig().getInt("fuel.consumption.jetpack", 1));
        if (!items.consumeFuel(player, cost)) {
            return false;
        }
        int damage = Math.max(0, plugin.getConfig().getInt("jetpack.durability-usage", 1));
        if (items.damageJetpack(item, damage)) {
            player.getInventory().setChestplate(null);
            player.sendMessage(message("jetpack-broken"));
            return false;
        }
        return true;
    }

    private void startWingsuit(Player player, ItemStack item) {
        items.ensure(item, "wingsuit");
        prepareFlightAbility(player);
        wingsuitFlying.add(player.getUniqueId());
        player.setFallDistance(0);
        lastBoost.put(player.getUniqueId(), ticks);
        Vector launch = player.getLocation().getDirection().normalize()
                .multiply(plugin.getConfig().getDouble("wingsuit.boost.strength", 1.1));
        launch.setY(Math.max(launch.getY(), plugin.getConfig().getDouble("wingsuit.boost.upward-strength", 0.18)));
        player.setVelocity(player.getVelocity().add(launch));
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && wingsuitFlying.contains(player.getUniqueId()) && !player.isOnGround()) {
                player.setGliding(true);
            }
        });
    }

    private void stopWingsuit(Player player) {
        if (!wingsuitFlying.remove(player.getUniqueId())) {
            return;
        }
        player.setGliding(false);
        lastBoost.remove(player.getUniqueId());
        if (!jetpackFlying.contains(player.getUniqueId())) {
            restoreFlightAbility(player);
        }
    }

    private void tickWingsuit(Player player, ItemStack item) {
        if (!player.isGliding()) {
            player.setGliding(true);
            if (!player.isGliding()) {
                stopWingsuit(player);
                return;
            }
        }
        if (player.isSneaking() && isLookingUp(player, "wingsuit.boost.minimum-upward-angle")) {
            long cooldown = Math.max(0, plugin.getConfig().getLong("wingsuit.boost.cooldown-ticks", 12));
            long previous = lastBoost.getOrDefault(player.getUniqueId(), Long.MIN_VALUE);
            if (ticks - previous >= cooldown) {
                if (boostWingsuit(player)) {
                    lastBoost.put(player.getUniqueId(), ticks);
                }
            }
        }
    }

    private boolean boostWingsuit(Player player) {
        int cost = Math.max(1, plugin.getConfig().getInt("fuel.consumption.wingsuit-boost", 3));
        if (!items.hasFuel(player, cost)) {
            return false;
        }
        if (!items.consumeFuel(player, cost)) {
            return false;
        }
        Vector velocity = player.getLocation().getDirection().normalize()
                .multiply(plugin.getConfig().getDouble("wingsuit.boost.strength", 1.1));
        velocity.setY(velocity.getY() + plugin.getConfig().getDouble("wingsuit.boost.upward-strength", 0.18));
        player.setVelocity(player.getVelocity().add(velocity));
        return true;
    }

    private void spawnJetpackParticles(Player player, Vector direction) {
        Location location = player.getLocation().clone().add(direction.clone().multiply(-0.5));
        location.subtract(0, 0.35, 0);
        for (Map<?, ?> particle : plugin.getConfig().getMapList("jetpack.particles")) {
            Object typeValue = particle.get("type");
            if (typeValue == null) {
                continue;
            }
            XParticle.of(String.valueOf(typeValue)).ifPresent(p -> player.getWorld().spawnParticle(
                    p.get(), location,
                    number(particle.get("amount"), 1),
                    decimal(particle.get("offset-x"), 0),
                    decimal(particle.get("offset-y"), 0),
                    decimal(particle.get("offset-z"), 0), 0));
        }
    }

    private boolean isRestricted(Player player, ItemStack item) {
        if (!plugin.getConfig().getBoolean("weapon-restrictions.enabled", true) || item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        boolean flying = jetpackFlying.contains(player.getUniqueId());
        boolean wearing = items.isJetpack(player.getInventory().getChestplate());
        if (!((flying && plugin.getConfig().getBoolean("weapon-restrictions.while-flying", true))
                || (wearing && plugin.getConfig().getBoolean("weapon-restrictions.while-wearing", true)))) {
            return false;
        }
        WeaponReference reference = identifyWeapon(item);
        if (reference == null) {
            return false;
        }
        if (reference.crackShot != null && !isAllowed("weapon-restrictions.allowed-crackshot-weapons", reference.crackShot)) {
            sendWeaponBlocked(player, reference.crackShot);
            return true;
        }
        if (reference.weaponMechanics != null && !isAllowed("weapon-restrictions.allowed-weaponmechanics-weapons", reference.weaponMechanics)) {
            sendWeaponBlocked(player, reference.weaponMechanics);
            return true;
        }
        return false;
    }

    private WeaponReference identifyWeapon(ItemStack item) {
        String crackShot = invokeWeaponTitle("com.shampaggon.crackshot.CSUtility", item);
        String weaponMechanics = invokeWeaponTitle("me.deecaad.weaponmechanics.WeaponMechanicsAPI", item);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            for (org.bukkit.NamespacedKey key : meta.getPersistentDataContainer().getKeys()) {
                String value = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                if (value == null) {
                    continue;
                }
                String namespace = key.getNamespace().toLowerCase();
                String keyName = key.getKey().toLowerCase();
                if (namespace.contains("crackshot") || keyName.contains("crackshot")) {
                    crackShot = value;
                } else if (namespace.contains("weaponmechanics") || keyName.contains("weaponmechanics")) {
                    weaponMechanics = value;
                }
            }
        }
        if (crackShot == null && weaponMechanics == null) {
            return null;
        }
        return new WeaponReference(crackShot, weaponMechanics);
    }

    private String invokeWeaponTitle(String className, ItemStack item) {
        try {
            Class<?> type = Class.forName(className);
            for (Method method : type.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers()) || method.getParameterTypes().length != 1
                        || !method.getParameterTypes()[0].isAssignableFrom(ItemStack.class)) {
                    continue;
                }
                String name = method.getName().toLowerCase();
                if (!(name.contains("title") || name.contains("weapon") || name.contains("item"))) {
                    continue;
                }
                Object result = method.invoke(null, item);
                if (result != null && !String.valueOf(result).isEmpty()) {
                    return String.valueOf(result);
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
        return null;
    }

    private boolean isAllowed(String path, String weapon) {
        for (String allowed : plugin.getConfig().getStringList(path)) {
            if (allowed.equalsIgnoreCase(weapon)) {
                return true;
            }
        }
        return false;
    }

    private void sendWeaponBlocked(Player player, String weapon) {
        player.sendMessage(message("blocked-message").replace("{weapon}", weapon));
    }

    private boolean isLookingUp(Player player, String path) {
        return player.getLocation().getPitch() <= -plugin.getConfig().getDouble(path, 10);
    }

    private void prepareFlightAbility(Player player) {
        previousFlightAbility.putIfAbsent(player.getUniqueId(), player.getAllowFlight());
        player.setAllowFlight(true);
    }

    private void restoreFlightAbility(Player player) {
        Boolean previous = previousFlightAbility.remove(player.getUniqueId());
        if (previous != null) {
            player.setAllowFlight(previous);
        }
    }

    private void clear(Player player) {
        jetpackFlying.remove(player.getUniqueId());
        wingsuitFlying.remove(player.getUniqueId());
        previousFlightAbility.remove(player.getUniqueId());
        previousFlySpeed.remove(player.getUniqueId());
        lastBoost.remove(player.getUniqueId());
    }

    private String message(String key) {
        String value = plugin.getConfig().getString("messages." + key, "");
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    private int number(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private double decimal(Object value, double fallback) {
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    private static final class WeaponReference {
        private final String crackShot;
        private final String weaponMechanics;

        private WeaponReference(String crackShot, String weaponMechanics) {
            this.crackShot = crackShot;
            this.weaponMechanics = weaponMechanics;
        }
    }
}
